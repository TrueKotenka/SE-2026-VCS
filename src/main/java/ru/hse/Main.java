package ru.hse;

import ru.hse.cli.CommandParser;
import ru.hse.core.IndexManager;
import ru.hse.core.ReferenceManager;
import ru.hse.core.VcsService;
import ru.hse.storage.ObjectStorage;

public class Main {
    public static void main(String[] args) {
        // 1. Конфигурация: определяем рабочую директорию
        String currentDir = System.getProperty("user.dir");

        // 2. Инициализация слоя Storage
        ObjectStorage storage = new ObjectStorage(currentDir);

        // 3. Инициализация слоя Core (Бизнес-логика)
        IndexManager indexManager = new IndexManager(currentDir, storage);
        ReferenceManager referenceManager = new ReferenceManager(currentDir);
        VcsService vcsService = new VcsService(storage, indexManager, referenceManager);

        // 4. Инициализация слоя Presentation (CLI) и передача управления
        CommandParser cliParser = new CommandParser(indexManager, vcsService);
        cliParser.parseAndExecute(args);
    }
}
