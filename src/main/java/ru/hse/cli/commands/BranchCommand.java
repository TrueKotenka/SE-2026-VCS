package ru.hse.cli.commands;

import ru.hse.core.VcsService;

public class BranchCommand implements Command {
    private final VcsService vcsService;

    public BranchCommand(VcsService vcsService) {
        this.vcsService = vcsService;
    }

    @Override
    public void execute(String[] args) throws Exception {
        // 1. Вывод списка веток: "vcs branch"
        if (args.length == 0) {
            vcsService.printBranches();
            return;
        }

        // 2. Удаление ветки: "vcs branch -d <name>"
        if (args.length == 2 && "-d".equals(args[0])) {
            String branchName = args[1];
            try {
                vcsService.deleteBranch(branchName);
            } catch (Exception e) {
                System.err.println("Ошибка при удалении: " + e.getMessage());
            }
            return;
        }

        // 3. Создание ветки: "vcs branch <name>"
        if (args.length == 1) {
            String branchName = args[0];

            // Простейшая валидация имени (запрещаем слэши и спецсимволы для безопасности
            // ФС)
            if (!branchName.matches("[a-zA-Z0-9_-]+")) {
                System.err.println("Ошибка: имя ветки может содержать только буквы, цифры, '_' и '-'.");
                return;
            }

            try {
                vcsService.createBranch(branchName);
            } catch (Exception e) {
                System.err.println("Ошибка при создании: " + e.getMessage());
            }
            return;
        }

        System.err.println("Неверные аргументы. Использование:");
        System.err.println("  vcs branch          - список веток");
        System.err.println("  vcs branch <name>   - создать ветку");
        System.err.println("  vcs branch -d <name>- удалить ветку");
    }
}
