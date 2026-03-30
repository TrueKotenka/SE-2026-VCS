package ru.hse.cli.commands;

import ru.hse.core.RepositoryManager;

public class InitCommand implements Command {
    @Override
    public void execute(String[] args) throws Exception {
        // Инициализируем репозиторий в текущей директории
        String currentDir = System.getProperty("user.dir");
        RepositoryManager.init(currentDir);
    }
}
