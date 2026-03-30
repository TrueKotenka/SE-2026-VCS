package ru.hse.cli.commands;

import ru.hse.core.VcsService;

public class MergeCommand implements Command {
    private final VcsService vcsService;

    public MergeCommand(VcsService vcsService) {
        this.vcsService = vcsService;
    }

    @Override
    public void execute(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Использование: vcs merge <имя_ветки>");
            return;
        }

        String targetBranch = args[0];
        vcsService.merge(targetBranch);
    }
}
