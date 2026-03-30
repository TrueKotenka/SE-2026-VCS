package ru.hse.model;

public interface VcsObject {
    /**
     * Возвращает тип объекта: "blob", "tree" или "commit".
     */
    String type();

    /**
     * Преобразует состояние объекта в массив байтов.
     * От этого массива вычисляется SHA-256 хеш.
     */
    byte[] serialize();
}