package ru.hse.cli.commands;

import ru.hse.core.VcsService;

public class CheckoutCommand implements Command {
    private final VcsService vcsService;

    public CheckoutCommand(VcsService vcsService) {
        this.vcsService = vcsService;
    }

    @Override
    public void execute(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Ошибка: укажите имя ветки или хеш коммита.");
            System.err.println("Использование: vcs checkout <revision>");
            return;
        }

        String revision = args[0];
        vcsService.checkout(revision);
    }
}
