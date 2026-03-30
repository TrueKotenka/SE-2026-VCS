package ru.hse.core;

import ru.hse.model.Blob;
import ru.hse.storage.ObjectStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public class IndexManager {

    private final Path repoRoot;
    private final Path indexPath;
    private final ObjectStorage storage;

    // TreeMap гарантирует алфавитный порядок путей.
    // Ключ: относительный путь (например, "src/Main.java"), Значение: SHA-256 хеш блоба.
    private final Map<String, String> indexEntries;

    public IndexManager(String repoPathStr, ObjectStorage storage) {
        this.repoRoot = Paths.get(repoPathStr).toAbsolutePath().normalize();
        this.indexPath = repoRoot.resolve(".myvcs").resolve("index");
        this.storage = storage;
        this.indexEntries = new TreeMap<>();
    }

    /**
     * Загружает текущее состояние индекса с диска в память.
     */
    public void load() throws IOException {
        indexEntries.clear();
        if (!Files.exists(indexPath)) {
            return; // Индекса еще нет, это нормально (например, сразу после init)
        }

        List<String> lines = Files.readAllLines(indexPath, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.isBlank()) continue;
            // Формат строки в файле: <hash> <описание пути>
            String[] parts = line.split(" ", 2);
            if (parts.length == 2) {
                String hash = parts[0];
                String relativePath = parts[1];
                indexEntries.put(relativePath, hash);
            }
        }
    }

    /**
     * Сохраняет состояние индекса из памяти на диск.
     */
    public void save() throws IOException {
        StringBuilder sb = new StringBuilder();
        // Благодаря TreeMap записи уже отсортированы
        for (Map.Entry<String, String> entry : indexEntries.entrySet()) {
            sb.append(entry.getValue()).append(" ").append(entry.getKey()).append("\n");
        }
        Files.writeString(indexPath, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Аналог `git add`. Добавляет файл или директорию в индекс.
     */
    public void add(Path targetPath) throws IOException {
        Path absoluteTarget = targetPath.toAbsolutePath().normalize();

        if (!Files.exists(absoluteTarget)) {
            throw new IllegalArgumentException("Путь не существует: " + absoluteTarget);
        }

        if (Files.isDirectory(absoluteTarget)) {
            // Если передали папку, обходим все вложенные файлы
            try (Stream<Path> stream = Files.walk(absoluteTarget)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> !p.startsWith(repoRoot.resolve(".myvcs"))) // Игнорируем саму служебную папку VCS!
                        .forEach(this::addSingleFileSilently);
            }
        } else {
            addSingleFile(absoluteTarget);
        }

        save(); // Сохраняем индекс после всех добавлений
    }

    /**
     * Возвращает неизменяемую копию записей индекса (понадобится для команды commit).
     */
    public Map<String, String> getEntries() {
        return Map.copyOf(indexEntries);
    }

    /**
     * Очищает индекс (используется после успешного коммита).
     */
    public void clear() throws IOException {
        indexEntries.clear();
        save();
    }

    /**
     * Возвращает корень репозитория
     */
    public Path getRepoRoot() {
        return repoRoot;
    }

    /**
     * Добавляет файл в индекс
     */
    public void putToIndex(String relativePath, String hash) {
        indexEntries.put(relativePath, hash);
    }

    // --- Приватные вспомогательные методы ---

    private void addSingleFileSilently(Path file) {
        try {
            addSingleFile(file);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при добавлении файла: " + file, e);
        }
    }

    private void addSingleFile(Path file) throws IOException {
        // 1. Вычисляем относительный путь от корня репозитория
        Path relativePath = repoRoot.relativize(file);

        // 2. Приводим путь к единому стандарту (прямые слеши),
        // чтобы хеши деревьев совпадали на Windows и Linux
        String normalizedPath = relativePath.toString().replace("\\", "/");

        // 3. Читаем содержимое файла
        byte[] content = Files.readAllBytes(file);

        // 4. Создаем объект Blob и сохраняем его в ObjectStorage
        Blob blob = new Blob(content);
        String hash = storage.save(blob);

        // 5. Обновляем запись в индексе
        indexEntries.put(normalizedPath, hash);
        System.out.println("Добавлен файл: " + normalizedPath);
    }
}
