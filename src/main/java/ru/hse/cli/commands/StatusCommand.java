package ru.hse.cli.commands;

import ru.hse.core.VcsService;

public class StatusCommand implements Command {
    private final VcsService vcsService;

    public StatusCommand(VcsService vcsService) {
        this.vcsService = vcsService;
    }

    @Override
    public void execute(String[] args) throws Exception {
        vcsService.status();
    }
}
