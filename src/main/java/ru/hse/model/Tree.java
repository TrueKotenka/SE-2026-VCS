package ru.hse.model;

import java.nio.charset.StandardCharsets;
import java.util.List;

public record Tree(List<TreeEntry> entries) implements VcsObject {

    // Вспомогательный класс для хранения одной строчки в дереве
    public record TreeEntry(String mode, // Права доступа, например "100644" для обычного файла
            String type, // "blob" или "tree"
            String hash, // SHA-256 хеш объекта, на который мы ссылаемся
            String name // Имя файла или папки (например, "Main.java")
    ) {
        public String toLine() {
            return String.format("%s %s %s\t%s", mode, type, hash, name);
        }
    }

    @Override
    public String type() {
        return "tree";
    }

    @Override
    public byte[] serialize() {
        // Собираем все записи в одну строку, разделенную переносами
        StringBuilder sb = new StringBuilder();
        for (TreeEntry entry : entries) {
            sb.append(entry.toLine()).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
