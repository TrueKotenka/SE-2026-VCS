package ru.hse.cli.commands;

import ru.hse.core.VcsService;

public class CommitCommand implements Command {
    private final VcsService vcsService;

    public CommitCommand(VcsService vcsService) {
        this.vcsService = vcsService;
    }

    @Override
    public void execute(String[] args) throws Exception {
        String message = "Без сообщения";

        // Простейший парсинг флага -m
        for (int i = 0; i < args.length; i++) {
            if ("-m".equals(args[i]) && i + 1 < args.length) {
                message = args[i + 1];
                break;
            }
        }

        // В настоящих системах автор берется из конфига,
        // но для нашего приложения зададим дефолтное значение
        String author = System.getenv("VCS_AUTHOR");
        if (author == null) {
            author = "Kotenka <kotenka@localhost>";
        }

        try {
            vcsService.commit(message, author);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage()); // Например, если индекс пуст
        }
    }
}
