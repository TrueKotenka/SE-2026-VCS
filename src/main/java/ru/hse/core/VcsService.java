package ru.hse.core;

import ru.hse.model.*;
import ru.hse.storage.ObjectStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public class VcsService {

    private final ObjectStorage storage;
    private final IndexManager indexManager;
    private final ReferenceManager referenceManager;

    public VcsService(ObjectStorage storage, IndexManager indexManager, ReferenceManager referenceManager) {
        this.storage = storage;
        this.indexManager = indexManager;
        this.referenceManager = referenceManager;
    }

    /**
     * Выполняет коммит текущего состояния индекса.
     */
    public String commit(String message, String author) throws IOException {
        indexManager.load();
        Map<String, String> indexEntries = indexManager.getEntries();

        if (indexEntries.isEmpty()) {
            throw new IllegalStateException("Нет файлов для коммита (индекс пуст).");
        }

        // 1. Строим корневое дерево из плоского индекса и сохраняем его
        String rootTreeHash = buildAndSaveTree(indexEntries);

        // 2. Получаем родительский коммит из ReferenceManager
        List<String> parentHashes = new ArrayList<>();
        String parentHash = referenceManager.getCurrentCommitHash();
        if (parentHash != null) {
            parentHashes.add(parentHash);
        }

        // 3. Создаем и сохраняем сам объект коммита
        Commit commit = new Commit(
                rootTreeHash,
                parentHashes,
                author,
                Instant.now(),
                message
        );
        String commitHash = storage.save(commit);

        // 4. Сдвигаем указатель текущей ветки на новый коммит!
        referenceManager.updateCurrentBranch(commitHash);

        // 5. Очищаем индекс (файлы зафиксированы)
        indexManager.clear();

        System.out.println("Создан коммит: " + commitHash);
        return commitHash;
    }

    // --- Логика построения дерева ---

    /**
     * Вспомогательный класс для построения графа директорий в памяти.
     */
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
            String path = entry.getKey();      // Например: "src/main/App.java"
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
        // TreeMap нам в этом частично помог, но смешанный список файлов и папок нужно отсортировать:
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
                throw new IllegalStateException("Повреждение репозитория: объект " + currentHash + " не является коммитом!");
            }

            // Добавляем в историю
            history.add(new CommitNode(currentHash, commit));

            // Переходим к родителю.
            // Для простой команды log мы пока идем только по первой линии (первому родителю),
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
     * Восстанавливает состояние файлов из указанной ревизии (имя ветки или хеш коммита).
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

        // 2. Очищаем рабочую директорию от старых файлов (удаляем только то, что есть в индексе)
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
     * @param treeHash хеш дерева для распаковки
     * @param currentDir физический путь, куда распаковывать
     * @param prefixPath префикс пути для записи в индекс (например, "src/main/")
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
}
