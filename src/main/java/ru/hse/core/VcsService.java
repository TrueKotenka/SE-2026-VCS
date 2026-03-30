package ru.hse.core;

import ru.hse.model.*;
import ru.hse.storage.HashUtils;
import ru.hse.storage.ObjectStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

public class VcsService {

    private final ObjectStorage storage;
    private final IndexManager indexManager;
    private final ReferenceManager referenceManager;

    public VcsService(ObjectStorage storage, IndexManager indexManager, ReferenceManager referenceManager) {
        this.storage = storage;
        this.indexManager = indexManager;
        this.referenceManager = referenceManager;
    }

    /** Выполняет коммит текущего состояния индекса. */
    public String commit(String message, String author) throws IOException {
        indexManager.load();
        Map<String, String> indexEntries = indexManager.getEntries();

        if (indexEntries.isEmpty()) {
            throw new IllegalStateException("Нет файлов для коммита (индекс пуст).");
        }

        // 1. Строим корневое дерево из плоского индекса и сохраняем его
        String rootTreeHash = buildAndSaveTree(indexEntries);

        // 2. Получаем родительские коммиты
        List<String> parentHashes = new ArrayList<>();

        // Первый родитель — это наша текущая ветка (HEAD)
        String parentHash = referenceManager.getCurrentCommitHash();
        if (parentHash != null) {
            parentHashes.add(parentHash);
        }

        // ПРОВЕРЯЕМ: есть ли файл MERGE_HEAD? Если да, у коммита будет второй родитель!
        Path mergeHeadPath = indexManager.getRepoRoot().resolve(".myvcs").resolve("MERGE_HEAD");
        if (Files.exists(mergeHeadPath)) {
            String mergeParent = Files.readString(mergeHeadPath, java.nio.charset.StandardCharsets.UTF_8).trim();
            parentHashes.add(mergeParent);

            // После успешного коммита слияние завершено, удаляем служебный файл
            Files.delete(mergeHeadPath);
        }

        // 3. Создаем и сохраняем сам объект коммита
        Commit commit = new Commit(rootTreeHash, parentHashes, author, Instant.now(), message);
        String commitHash = storage.save(commit);

        // 4. Сдвигаем указатель текущей ветки на новый коммит!
        referenceManager.updateCurrentBranch(commitHash);

        // 5. Очищаем индекс (файлы зафиксированы)
        indexManager.clear();

        System.out.println("Создан коммит: " + commitHash);
        return commitHash;
    }

    // --- Логика построения дерева ---

    /** Вспомогательный класс для построения графа директорий в памяти. */
    private static class DirNode {
        // Имя файла -> Хеш блоба
        final Map<String, String> files = new TreeMap<>();
        // Имя подпапки -> Узел подпапки
        final Map<String, DirNode> dirs = new TreeMap<>();
    }

    private String buildAndSaveTree(Map<String, String> indexEntries) throws IOException {
        DirNode root = new DirNode();

        // Шаг 1. Строим дерево директорий в оперативной памяти
        for (Map.Entry<String, String> entry : indexEntries.entrySet()) {
            String path = entry.getKey(); // Например: "src/main/App.java"
            String blobHash = entry.getValue();

            String[] parts = path.split("/");
            DirNode currentDir = root;

            // Идем по всем частям пути, кроме последней (последняя — это имя файла)
            for (int i = 0; i < parts.length - 1; i++) {
                String dirName = parts[i];
                // Если такой папки еще нет в текущем узле, создаем ее
                currentDir.dirs.putIfAbsent(dirName, new DirNode());
                // Проваливаемся глубже
                currentDir = currentDir.dirs.get(dirName);
            }

            // Последняя часть пути — это сам файл
            String fileName = parts[parts.length - 1];
            currentDir.files.put(fileName, blobHash);
        }

        // Шаг 2. Рекурсивно сохраняем узлы (снизу вверх) и возвращаем хеш корня
        return saveDirNode(root);
    }

    private String saveDirNode(DirNode node) throws IOException {
        List<Tree.TreeEntry> treeEntries = new ArrayList<>();

        // Сначала рекурсивно обрабатываем и сохраняем все вложенные папки
        for (Map.Entry<String, DirNode> dirEntry : node.dirs.entrySet()) {
            String dirName = dirEntry.getKey();
            // Рекурсивный вызов: сохраняем подпапку и получаем её хеш!
            String childTreeHash = saveDirNode(dirEntry.getValue());

            // "040000" - стандартный Git-режим доступа для директорий
            treeEntries.add(new Tree.TreeEntry("040000", "tree", childTreeHash, dirName));
        }

        // Затем добавляем все файлы в текущей папке
        for (Map.Entry<String, String> fileEntry : node.files.entrySet()) {
            String fileName = fileEntry.getKey();
            String blobHash = fileEntry.getValue();

            // "100644" - стандартный режим для обычного файла
            treeEntries.add(new Tree.TreeEntry("100644", "blob", blobHash, fileName));
        }

        // Git требует, чтобы записи в дереве были строго отсортированы по имени.
        // TreeMap нам в этом частично помог, но смешанный список файлов и папок нужно
        // отсортировать:
        treeEntries.sort(Comparator.comparing(Tree.TreeEntry::name));

        // Создаем объект Tree, сериализуем и сохраняем на диск
        Tree tree = new Tree(treeEntries);
        return storage.save(tree);
    }

    /**
     * Возвращает историю коммитов, начиная с текущего (HEAD) и до самого первого.
     */
    public List<CommitNode> getLog() throws IOException {
        List<CommitNode> history = new ArrayList<>();

        // 1. Узнаем, где мы сейчас находимся
        String currentHash = referenceManager.getCurrentCommitHash();

        // 2. Идем по цепочке родителей, пока хеш не станет null
        while (currentHash != null) {
            // Читаем сырые байты коммита из хранилища
            byte[] rawData = storage.loadRaw(currentHash);

            // Парсим их в объект
            VcsObject obj = ObjectParser.parse(rawData);

            if (!(obj instanceof Commit commit)) {
                throw new IllegalStateException(
                        "Повреждение репозитория: объект " + currentHash + " не является коммитом!");
            }

            // Добавляем в историю
            history.add(new CommitNode(currentHash, commit));

            // Переходим к родителю.
            // Для простой команды log мы пока идем только по первой линии (первому
            // родителю),
            // игнорируя боковые ветки слияний (merge).
            if (commit.parentHashes().isEmpty()) {
                currentHash = null; // Мы дошли до самого первого (root) коммита
            } else {
                currentHash = commit.parentHashes().get(0);
            }
        }

        return history;
    }

    /**
     * Восстанавливает состояние файлов из указанной ревизии (имя ветки или хеш
     * коммита).
     */
    public void checkout(String revision) throws IOException {
        // 1. Находим нужный коммит
        String commitHash = referenceManager.resolveReference(revision);
        byte[] rawData;
        try {
            rawData = storage.loadRaw(commitHash);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ревизия не найдена: " + revision);
        }

        Commit commit = (Commit) ObjectParser.parse(rawData);

        // 2. Очищаем рабочую директорию от старых файлов (удаляем только то, что есть в
        // индексе)
        indexManager.load();
        Path repoRoot = indexManager.getRepoRoot();

        for (String relativePath : indexManager.getEntries().keySet()) {
            Path filePath = repoRoot.resolve(relativePath);
            Files.deleteIfExists(filePath);
            // Примечание: для идеальной чистоты тут можно удалять и пустые директории,
            // но для простоты пока оставим так.
        }

        // Очищаем сам индекс
        indexManager.clear();

        // 3. Рекурсивно распаковываем новое дерево и сразу заполняем индекс
        unpackTree(commit.treeHash(), repoRoot, "");
        indexManager.save();

        // 4. Обновляем HEAD
        referenceManager.setHead(revision);
        System.out.println("Успешно переключено на: " + revision);
    }

    /**
     * Рекурсивно обходит дерево и восстанавливает файлы на диск.
     *
     * @param treeHash
     *            хеш дерева для распаковки
     * @param currentDir
     *            физический путь, куда распаковывать
     * @param prefixPath
     *            префикс пути для записи в индекс (например, "src/main/")
     */
    private void unpackTree(String treeHash, Path currentDir, String prefixPath) throws IOException {
        if (!Files.exists(currentDir)) {
            Files.createDirectories(currentDir);
        }

        byte[] rawTree = storage.loadRaw(treeHash);
        Tree tree = (Tree) ObjectParser.parse(rawTree);

        for (Tree.TreeEntry entry : tree.entries()) {
            Path targetPath = currentDir.resolve(entry.name());
            String newPrefix = prefixPath.isEmpty() ? entry.name() : prefixPath + "/" + entry.name();

            if ("tree".equals(entry.type())) {
                // Проваливаемся во вложенную папку
                unpackTree(entry.hash(), targetPath, newPrefix);
            } else if ("blob".equals(entry.type())) {
                // Восстанавливаем файл из базы объектов
                byte[] rawBlob = storage.loadRaw(entry.hash());
                Blob blob = (Blob) ObjectParser.parse(rawBlob);
                Files.write(targetPath, blob.content());

                // Добавляем восстановленный файл в индекс
                indexManager.putToIndex(newPrefix, entry.hash());
            }
        }
    }

    public void createBranch(String name) throws IOException {
        referenceManager.createBranch(name);
        System.out.println("Создана ветка: " + name);
    }

    public void deleteBranch(String name) throws IOException {
        referenceManager.deleteBranch(name);
        System.out.println("Удалена ветка: " + name);
    }

    public void printBranches() throws IOException {
        List<String> branches = referenceManager.listBranches();
        String activeBranch = referenceManager.getActiveBranch();

        if (branches.isEmpty()) {
            System.out.println("Веток пока нет.");
            return;
        }

        for (String branch : branches) {
            if (branch.equals(activeBranch)) {
                // Выделяем текущую ветку зеленым цветом и звездочкой (как в Git)
                System.out.println("\033[32m* " + branch + "\033[0m");
            } else {
                System.out.println("  " + branch);
            }
        }
    }

    public void merge(String targetBranch) throws IOException {
        String headHash = referenceManager.getCurrentCommitHash();
        String targetHash = referenceManager.resolveReference(targetBranch);

        if (headHash == null || targetHash == null) {
            throw new IllegalStateException("Невозможно выполнить merge: одна из веток не существует.");
        }
        if (headHash.equals(targetHash)) {
            System.out.println("Уже обновлено (Already up-to-date).");
            return;
        }

        MergeResolver resolver = new MergeResolver(storage); // Можно внедрить через конструктор
        String lcaHash = resolver.findCommonAncestor(headHash, targetHash);

        // 1. Fast-forward merge
        if (headHash.equals(lcaHash)) {
            System.out.println("Выполняется Fast-forward слияние...");
            checkout(targetBranch);
            // Если мы были на ветке master, checkout переключил нас на targetBranch.
            // Нужно вернуть HEAD на master, но сдвинуть его на targetHash.
            String activeBranch = referenceManager.getActiveBranch();
            if (activeBranch != null) {
                referenceManager.setHead(activeBranch); // возвращаем HEAD
                referenceManager.updateCurrentBranch(targetHash); // двигаем ветку
            }
            return;
        }

        // 2. 3-way merge
        System.out.println("Выполняется 3-way слияние...");

        Map<String, String> baseState = resolver.getCommitState(lcaHash);
        Map<String, String> headState = resolver.getCommitState(headHash);
        Map<String, String> targetState = resolver.getCommitState(targetHash);

        Set<String> allFiles = new TreeSet<>();
        allFiles.addAll(baseState.keySet());
        allFiles.addAll(headState.keySet());
        allFiles.addAll(targetState.keySet());

        boolean hasConflicts = false;
        indexManager.load(); // Загружаем текущий индекс

        for (String path : allFiles) {
            String baseFile = baseState.get(path);
            String headFile = headState.get(path);
            String targetFile = targetState.get(path);

            // Если файл одинаковый в HEAD и Target, ничего делать не надо
            if (Objects.equals(headFile, targetFile))
                continue;

            // Если файл не менялся у нас (HEAD == Base), но изменился у них (Target) ->
            // берем Target
            if (Objects.equals(headFile, baseFile) && !Objects.equals(targetFile, baseFile)) {
                if (targetFile == null) {
                    // Файл удалили в целевой ветке
                    Files.deleteIfExists(indexManager.getRepoRoot().resolve(path));
                    // В реальной системе нужно удалить из индекса, для простоты считаем, что
                    // пересоберем его
                } else {
                    // Файл добавили или изменили в целевой ветке
                    restoreFileFromBlob(targetFile, path);
                    indexManager.putToIndex(path, targetFile);
                }
                continue;
            }

            // Если файл изменился у нас, а у них остался как в базе -> оставляем наш
            // (HEAD), ничего не делаем
            if (Objects.equals(targetFile, baseFile) && !Objects.equals(headFile, baseFile)) {
                continue;
            }

            // Если мы дошли сюда, значит файл изменился И у нас, И у них
            hasConflicts = true;
            System.out.println("КОНФЛИКТ (изменение/изменение): " + path);

            // 1. Читаем содержимое нашего файла (HEAD)
            String headContent = "";
            if (headFile != null) {
                Blob headBlob = (Blob) ObjectParser.parse(storage.loadRaw(headFile));
                headContent = new String(headBlob.content(), java.nio.charset.StandardCharsets.UTF_8);
            }

            // 2. Читаем содержимое приходящего файла (Target)
            String targetContent = "";
            if (targetFile != null) {
                Blob targetBlob = (Blob) ObjectParser.parse(storage.loadRaw(targetFile));
                targetContent = new String(targetBlob.content(), java.nio.charset.StandardCharsets.UTF_8);
            }

            // 3. Формируем строку с маркерами конфликта
            String conflictedText = "<<<<<<< HEAD\n" + headContent + "=======\n" + targetContent + ">>>>>>> "
                    + targetBranch + "\n";

            // 4. Записываем эту строку прямо в рабочий файл на диске
            Path filePath = indexManager.getRepoRoot().resolve(path);
            Files.writeString(filePath, conflictedText, java.nio.charset.StandardCharsets.UTF_8);
        }

        indexManager.save();

        // Записываем хеш сливаемой ветки, чтобы commit знал второго родителя
        Path mergeHeadPath = indexManager.getRepoRoot().resolve(".myvcs").resolve("MERGE_HEAD");
        Files.writeString(mergeHeadPath, targetHash + "\n", java.nio.charset.StandardCharsets.UTF_8);

        if (hasConflicts) {
            System.out.println("Автоматическое слияние не удалось. Файлы содержат маркеры конфликтов.");
            System.out
                    .println("Разрешите конфликты (удалите маркеры), добавьте файлы через 'add' и сделайте 'commit'.");
        } else {
            System.out.println("Слияние прошло успешно! Сделайте коммит для завершения (MERGE_HEAD создан).");
        }
    }

    // Вспомогательный метод для извлечения файла прямо на диск
    private void restoreFileFromBlob(String blobHash, String relativePath) throws IOException {
        Path targetPath = indexManager.getRepoRoot().resolve(relativePath);
        Files.createDirectories(targetPath.getParent()); // Убеждаемся, что папки существуют
        Blob blob = (Blob) ObjectParser.parse(storage.loadRaw(blobHash));
        Files.write(targetPath, blob.content());
    }

    /**
     * Сканирует рабочую директорию и возвращает мапу "относительный путь" -> "хеш
     * текущего содержимого".
     */
    private Map<String, String> scanWorkspace() throws IOException {
        Map<String, String> workspaceState = new TreeMap<>();
        Path repoRoot = indexManager.getRepoRoot();

        try (Stream<Path> stream = Files.walk(repoRoot)) {
            stream.filter(Files::isRegularFile).filter(p -> !p.startsWith(repoRoot.resolve(".myvcs"))) // Игнорируем
                                                                                                       // служебную
                                                                                                       // папку
                    .forEach(file -> {
                        try {
                            String relativePath = repoRoot.relativize(file).toString().replace("\\", "/");
                            byte[] content = Files.readAllBytes(file);

                            // Вычисляем хеш так же, как в ObjectStorage, но без сохранения
                            Blob blob = new Blob(content);
                            byte[] data = blob.serialize();
                            byte[] header = (blob.type() + " " + data.length + "\0").getBytes();
                            byte[] fullData = new byte[header.length + data.length];
                            System.arraycopy(header, 0, fullData, 0, header.length);
                            System.arraycopy(data, 0, fullData, header.length, data.length);

                            workspaceState.put(relativePath, HashUtils.sha256(fullData));
                        } catch (IOException e) {
                            throw new RuntimeException("Ошибка чтения файла: " + file, e);
                        }
                    });
        }
        return workspaceState;
    }

    /**
     * Выводит статус репозитория: отслеживаемые, неотслеживаемые и измененные
     * файлы.
     */
    public void status() throws IOException {
        String activeBranch = referenceManager.getActiveBranch();
        if (activeBranch != null) {
            System.out.println("Текущая ветка: " + activeBranch);
        } else {
            System.out.println("HEAD отсоединен (указывает на " + referenceManager.getCurrentCommitHash() + ")");
        }

        // Проверяем, не находимся ли мы в процессе слияния
        Path mergeHeadPath = indexManager.getRepoRoot().resolve(".myvcs").resolve("MERGE_HEAD");
        if (Files.exists(mergeHeadPath)) {
            System.out.println(
                    "\033[33mУ вас есть незавершенное слияние (разрешите конфликты и сделайте commit).\033[0m");
        }
        System.out.println();

        // 1. Получаем состояние HEAD (последний коммит)
        String headHash = referenceManager.getCurrentCommitHash();
        Map<String, String> headState = new TreeMap<>();
        if (headHash != null) {
            MergeResolver resolver = new MergeResolver(storage);
            headState = resolver.getCommitState(headHash);
        }

        // 2. Получаем состояние Индекса (Staged Area)
        indexManager.load();
        Map<String, String> indexState = indexManager.getEntries();

        // 3. Получаем состояние Рабочей директории (на диске)
        Map<String, String> workspaceState = scanWorkspace();

        // Категории файлов
        Set<String> stagedNew = new TreeSet<>();
        Set<String> stagedModified = new TreeSet<>();
        Set<String> stagedDeleted = new TreeSet<>();

        Set<String> unstagedModified = new TreeSet<>();
        Set<String> unstagedDeleted = new TreeSet<>();
        Set<String> untracked = new TreeSet<>();

        // СРАВНЕНИЕ 1: Индекс vs HEAD (Изменения, готовые к коммиту - ЗЕЛЕНЫЕ)
        for (Map.Entry<String, String> entry : indexState.entrySet()) {
            String path = entry.getKey();
            String indexHash = entry.getValue();
            String headHashForFile = headState.get(path);

            if (headHashForFile == null) {
                stagedNew.add(path);
            } else if (!headHashForFile.equals(indexHash)) {
                stagedModified.add(path);
            }
        }
        for (String path : headState.keySet()) {
            if (!indexState.containsKey(path)) {
                stagedDeleted.add(path);
            }
        }

        // СРАВНЕНИЕ 2: Рабочая директория vs Индекс (Изменения, НЕ готовые к коммиту -
        // КРАСНЫЕ)
        for (Map.Entry<String, String> entry : workspaceState.entrySet()) {
            String path = entry.getKey();
            String wsHash = entry.getValue();
            String indexHashForFile = indexState.get(path);

            if (indexHashForFile == null && !headState.containsKey(path)) {
                untracked.add(path); // Файла нет ни в индексе, ни в прошлом коммите
            } else if (indexHashForFile != null && !indexHashForFile.equals(wsHash)) {
                unstagedModified.add(path); // Файл в индексе есть, но на диске он уже другой
            }
        }
        for (String path : indexState.keySet()) {
            if (!workspaceState.containsKey(path)) {
                unstagedDeleted.add(path);
            }
        }

        // --- ВЫВОД РЕЗУЛЬТАТОВ ---

        boolean hasStaged = !stagedNew.isEmpty() || !stagedModified.isEmpty() || !stagedDeleted.isEmpty();
        if (hasStaged) {
            System.out.println("Изменения, которые будут включены в коммит:");
            System.out.println("  (используйте 'vcs commit' для фиксации)");
            for (String p : stagedNew)
                System.out.println("\033[32m\tновый файл:   " + p + "\033[0m");
            for (String p : stagedModified)
                System.out.println("\033[32m\tизменено:     " + p + "\033[0m");
            for (String p : stagedDeleted)
                System.out.println("\033[32m\tудалено:      " + p + "\033[0m");
            System.out.println();
        }

        boolean hasUnstaged = !unstagedModified.isEmpty() || !unstagedDeleted.isEmpty();
        if (hasUnstaged) {
            System.out.println("Изменения, которые не добавлены в индекс для коммита:");
            System.out.println("  (используйте 'vcs add <файл>' для добавления)");
            for (String p : unstagedModified)
                System.out.println("\033[31m\tизменено:     " + p + "\033[0m");
            for (String p : unstagedDeleted)
                System.out.println("\033[31m\tудалено:      " + p + "\033[0m");
            System.out.println();
        }

        if (!untracked.isEmpty()) {
            System.out.println("Неотслеживаемые файлы:");
            System.out.println("  (используйте 'vcs add <файл>' чтобы добавить в индекс)");
            for (String p : untracked)
                System.out.println("\033[31m\t" + p + "\033[0m");
            System.out.println();
        }

        if (!hasStaged && !hasUnstaged && untracked.isEmpty()) {
            System.out.println("Нечего коммитить, рабочее дерево чистое.");
        }
    }
}
