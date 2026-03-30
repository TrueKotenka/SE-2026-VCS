package ru.hse.cli.commands;

public interface Command {
    /**
     * Выполняет команду с переданными аргументами.
     *
     * @param args
     *            аргументы командной строки (без имени самой команды)
     */
    void execute(String[] args) throws Exception;
}
