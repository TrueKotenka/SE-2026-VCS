package ru.hse.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

public record Commit(String treeHash, // Хеш корневого объекта Tree
        List<String> parentHashes, // Список хешей родительских коммитов
        String author, // Имя автора (например, "Kotenka <mail@example.com>")
        Instant timestamp, // Время создания
        String message // Сообщение коммита
) implements VcsObject {

    @Override
    public String type() {
        return "commit";
    }

    @Override
    public byte[] serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("tree ").append(treeHash).append("\n");

        for (String parent : parentHashes) {
            sb.append("parent ").append(parent).append("\n");
        }

        sb.append("author ").append(author).append(" ").append(timestamp.getEpochSecond()).append("\n");
        sb.append("\n"); // Пустая строка отделяет метаданные от сообщения
        sb.append(message).append("\n");

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
