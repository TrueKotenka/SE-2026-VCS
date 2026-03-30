package ru.hse.cli.commands;

import ru.hse.core.IndexManager;

import java.nio.file.Path;
import java.nio.file.Paths;

public class AddCommand implements Command {
    private final IndexManager indexManager;

    public AddCommand(IndexManager indexManager) {
        this.indexManager = indexManager;
    }

    @Override
    public void execute(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Ошибка: не указан путь к файлу. Использование: add <файл_или_папка>");
            return;
        }

        indexManager.load(); // Загружаем текущий индекс
        for (String arg : args) {
            Path targetPath = Paths.get(arg);
            indexManager.add(targetPath);
        }
    }
}
