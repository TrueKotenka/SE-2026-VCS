package ru.hse.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RepositoryManager {

    /** Инициализирует пустой репозиторий в указанной директории. */
    public static void init(String targetPath) throws IOException {
        Path repoRoot = Paths.get(targetPath).toAbsolutePath().normalize();
        Path vcsDir = repoRoot.resolve(".myvcs");

        if (Files.exists(vcsDir)) {
            System.out.println("Репозиторий уже существует в: " + vcsDir);
            return;
        }

        // 1. Создаем основные директории
        Files.createDirectories(vcsDir.resolve("objects"));
        Files.createDirectories(vcsDir.resolve("refs").resolve("heads"));

        // 2. Создаем файл HEAD, который по умолчанию указывает на ветку master
        Path headPath = vcsDir.resolve("HEAD");
        String defaultHeadContent = "ref: refs/heads/master\n";
        Files.writeString(headPath, defaultHeadContent, StandardCharsets.UTF_8);

        System.out.println("Инициализирован пустой репозиторий в " + vcsDir);
    }
}
