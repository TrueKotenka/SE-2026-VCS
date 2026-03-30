package ru.hse.cli.commands;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import ru.hse.core.VcsService;
import ru.hse.model.Commit;
import ru.hse.model.CommitNode;

public class LogCommand implements Command {

    private final VcsService vcsService;
    // Форматтер для красивого отображения времени
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy Z")
            .withZone(ZoneId.systemDefault());

    public LogCommand(VcsService vcsService) {
        this.vcsService = vcsService;
    }

    @Override
    public void execute(String[] args) throws Exception {
        List<CommitNode> log = vcsService.getLog();

        if (log.isEmpty()) {
            System.out.println("История пуста. Сделайте свой первый коммит!");
            return;
        }

        // Выводим каждый коммит в стиле классического Git
        for (CommitNode node : log) {
            Commit commit = node.commit();

            // ANSI escape-код \033[33m делает текст желтым в современных консолях
            System.out.println("\033[33mcommit " + node.hash() + "\033[0m");
            System.out.println("Author: " + commit.author());
            System.out.println("Date:   " + FORMATTER.format(commit.timestamp()));
            System.out.println();

            // Добавляем отступ для сообщения коммита
            String[] messageLines = commit.message().split("\n");
            for (String line : messageLines) {
                System.out.println("    " + line);
            }
            System.out.println();
        }
    }
}
