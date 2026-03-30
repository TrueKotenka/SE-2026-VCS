package ru.hse.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ReferenceManager {

    private final Path vcsDir;

    public ReferenceManager(String repoPathStr) {
        this.vcsDir = Paths.get(repoPathStr).toAbsolutePath().normalize().resolve(".myvcs");
    }

    /**
     * Читает файл HEAD и возвращает хеш текущего коммита. Если репозиторий пуст
     * (веток еще нет), возвращает null.
     */
    public String getCurrentCommitHash() throws IOException {
        Path headPath = vcsDir.resolve("HEAD");
        if (!Files.exists(headPath)) {
            return null;
        }

        String headContent = Files.readString(headPath, StandardCharsets.UTF_8).trim();

        // Если HEAD указывает на ветку (например, "ref: refs/heads/master")
        if (headContent.startsWith("ref: ")) {
            String branchPathStr = headContent.substring(5); // отрезаем "ref: "
            Path branchPath = vcsDir.resolve(branchPathStr);

            if (Files.exists(branchPath)) {
                return Files.readString(branchPath, StandardCharsets.UTF_8).trim();
            } else {
                // Ветка указана в HEAD, но самого файла ветки еще нет (первый коммит)
                return null;
            }
        }

        // Если у нас "detached HEAD" (мы сделали checkout конкретного коммита, а не
        // ветки)
        return headContent;
    }

    /**
     * Обновляет текущую ветку (на которую указывает HEAD), записывая в нее новый
     * хеш коммита.
     */
    public void updateCurrentBranch(String newCommitHash) throws IOException {
        Path headPath = vcsDir.resolve("HEAD");
        String headContent = Files.readString(headPath, StandardCharsets.UTF_8).trim();

        if (headContent.startsWith("ref: ")) {
            // Обновляем файл самой ветки (например, .myvcs/refs/heads/master)
            String branchPathStr = headContent.substring(5);
            Path branchPath = vcsDir.resolve(branchPathStr);

            // Если файла ветки не было, он будет создан
            Files.writeString(branchPath, newCommitHash + "\n", StandardCharsets.UTF_8);
        } else {
            // Если мы были в состоянии detached HEAD, просто обновляем сам HEAD
            Files.writeString(headPath, newCommitHash + "\n", StandardCharsets.UTF_8);
        }
    }

    /** Превращает имя ветки или хеш в реальный хеш коммита. */
    public String resolveReference(String rev) throws IOException {
        Path branchPath = vcsDir.resolve("refs").resolve("heads").resolve(rev);
        if (Files.exists(branchPath)) {
            // Если это имя ветки, читаем хеш из её файла
            return Files.readString(branchPath, StandardCharsets.UTF_8).trim();
        }
        // Иначе предполагаем, что пользователь передал прямой хеш коммита
        return rev;
    }

    /** Переключает HEAD на указанную ветку или хеш. */
    public void setHead(String rev) throws IOException {
        Path headPath = vcsDir.resolve("HEAD");
        Path branchPath = vcsDir.resolve("refs").resolve("heads").resolve(rev);

        if (Files.exists(branchPath)) {
            // Переключаемся на ветку
            Files.writeString(headPath, "ref: refs/heads/" + rev + "\n", StandardCharsets.UTF_8);
        } else {
            // Detached HEAD (переключились на конкретный коммит)
            Files.writeString(headPath, rev + "\n", StandardCharsets.UTF_8);
        }
    }
}
