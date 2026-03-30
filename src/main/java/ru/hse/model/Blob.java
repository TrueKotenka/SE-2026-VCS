package ru.hse.model;

import java.util.Arrays;

public record Blob(byte[] content) implements VcsObject {

    @Override
    public String type() {
        return "blob";
    }

    @Override
    public byte[] serialize() {
        // Для блоба сериализация — это просто возврат его содержимого.
        // (В реальном Git к контенту добавляется заголовок "blob <size>\0",
        // мы можем реализовать это на слое Storage, чтобы не засорять модель)
        return content;
    }

    // Переопределяем toString, equals и hashCode для корректной работы с массивами
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Blob blob = (Blob) o;
        return Arrays.equals(content, blob.content);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(content);
    }
}
