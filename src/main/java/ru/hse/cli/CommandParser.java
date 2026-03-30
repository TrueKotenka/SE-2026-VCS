package ru.hse.cli;

import ru.hse.cli.commands.*;
import ru.hse.core.IndexManager;
import ru.hse.core.VcsService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CommandParser {

    private final Map<String, Command> commands = new HashMap<>();

    // Передаем сюда только те сервисы, которые нужны командам
    public CommandParser(IndexManager indexManager, VcsService vcsService) {
        // Регистрируем команды при создании парсера
        commands.put("init", new InitCommand());
        commands.put("add", new AddCommand(indexManager));
        commands.put("commit", new CommitCommand(vcsService));
        commands.put("log", new LogCommand(vcsService));
        commands.put("checkout", new CheckoutCommand(vcsService));
        commands.put("branch", new BranchCommand(vcsService));
        commands.put("merge", new MergeCommand(vcsService));
        commands.put("status", new StatusCommand(vcsService));
    }

    /**
     * Главный метод, который парсит аргументы и запускает нужную команду.
     */
    public void parseAndExecute(String[] args) {
        if (args.length == 0) {
            printHelp();
            return;
        }

        String commandName = args[0];
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

        Command command = commands.get(commandName);

        if (command == null) {
            System.err.println("Неизвестная команда: " + commandName);
            printHelp();
            return;
        }

        try {
            command.execute(commandArgs);
        } catch (Exception e) {
            System.err.println("Ошибка при выполнении команды '" + commandName + "': " + e.getMessage());
            // Для отладки можно раскомментировать:
            // e.printStackTrace();
        }
    }

    private void printHelp() {
        System.out.println("Использование: vcs <команда> [аргументы]");
        System.out.println("Доступные команды: " + String.join(", ", commands.keySet()));
    }
}
