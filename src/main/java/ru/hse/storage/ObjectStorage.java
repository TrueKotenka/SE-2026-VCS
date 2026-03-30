package ru.hse.storage;

import ru.hse.model.VcsObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class ObjectStorage {

    // Путь к скрытой директории нашего репозитория (по умолчанию в текущей папке)
    private final Path repoPath;

    public ObjectStorage(String repoPathStr) {
        this.repoPath = Paths.get(repoPathStr, ".myvcs");
    }

    /**
     * Сохраняет VcsObject в базу данных объектов и возвращает его SHA-256 хеш.
     */
    public String save(VcsObject vcsObject) throws IOException {
        // 1. Получаем байтовое представление объекта
        byte[] data = vcsObject.serialize();

        // В реальном Git к данным добавляется заголовок (тип и размер).
        // Добавим его, чтобы формат был более надежным: "type size\0content"
        byte[] header = (vcsObject.type() + " " + data.length + "\0").getBytes();
        byte[] fullData = new byte[header.length + data.length];
        System.arraycopy(header, 0, fullData, 0, header.length);
        System.arraycopy(data, 0, fullData, header.length, data.length);

        // 2. Вычисляем хеш от полных данных
        String hash = HashUtils.sha256(fullData);

        // 3. Формируем путь: .myvcs/objects/xx/yyyy...
        String dirName = hash.substring(0, 2);
        String fileName = hash.substring(2);
        Path objectDir = repoPath.resolve("objects").resolve(dirName);
        Path objectPath = objectDir.resolve(fileName);

        // Если такой объект уже есть, перезаписывать его не нужно (он идентичен)
        if (Files.exists(objectPath)) {
            return hash;
        }

        // 4. Создаем директорию, если её нет
        Files.createDirectories(objectDir);

        // 5. Записываем данные с использованием Zlib компрессии
        try (OutputStream os = Files.newOutputStream(objectPath);
             DeflaterOutputStream dos = new DeflaterOutputStream(os)) {
            dos.write(fullData);
        }

        return hash;
    }

    /**
     * Читает и распаковывает сырые байты объекта по его хешу.
     * Парсинг в конкретный класс (Blob, Tree, Commit) будет выполняться на уровне бизнес-логики.
     */
    public byte[] loadRaw(String hash) throws IOException {
        String dirName = hash.substring(0, 2);
        String fileName = hash.substring(2);
        Path objectPath = repoPath.resolve("objects").resolve(dirName).resolve(fileName);

        if (!Files.exists(objectPath)) {
            throw new IllegalArgumentException("Объект с хешем " + hash + " не найден");
        }

        // Читаем и распаковываем данные (InflaterInputStream)
        try (InputStream is = Files.newInputStream(objectPath);
             InflaterInputStream iis = new InflaterInputStream(is);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = iis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }
}