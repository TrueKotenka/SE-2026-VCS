package ru.hse.core;

import ru.hse.model.Blob;
import ru.hse.model.Commit;
import ru.hse.model.Tree;
import ru.hse.model.VcsObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ObjectParser {

    /**
     * Превращает сырые распакованные байты из ObjectStorage обратно в VcsObject.
     */
    public static VcsObject parse(byte[] rawData) {
        // 1. Ищем нулевой байт (разделитель заголовка и тела)
        int nullIndex = -1;
        for (int i = 0; i < rawData.length; i++) {
            if (rawData[i] == 0) {
                nullIndex = i;
                break;
            }
        }

        if (nullIndex == -1) {
            throw new IllegalArgumentException("Поврежденный объект: отсутствует нулевой байт заголовка");
        }

        // 2. Читаем заголовок и извлекаем тип
        String header = new String(rawData, 0, nullIndex, StandardCharsets.UTF_8);
        String[] headerParts = header.split(" ");
        String type = headerParts[0];

        // 3. Выделяем полезную нагрузку (всё, что после \0)
        byte[] content = Arrays.copyOfRange(rawData, nullIndex + 1, rawData.length);

        // 4. Маршрутизируем парсинг в зависимости от типа
        return switch (type) {
            case "blob" -> parseBlob(content);
            case "tree" -> parseTree(content);
            case "commit" -> parseCommit(content);
            default -> throw new IllegalArgumentException("Неизвестный тип объекта: " + type);
        };
    }

    private static Blob parseBlob(byte[] content) {
        // Blob — это просто сырые данные файла, парсить нечего
        return new Blob(content);
    }

    private static Tree parseTree(byte[] content) {
        String treeStr = new String(content, StandardCharsets.UTF_8);
        List<Tree.TreeEntry> entries = new ArrayList<>();

        if (!treeStr.isEmpty()) {
            String[] lines = treeStr.split("\n");
            for (String line : lines) {
                if (line.isEmpty()) continue;

                // Формат строки: mode type hash\tname
                int tabIndex = line.indexOf('\t');
                String meta = line.substring(0, tabIndex);
                String name = line.substring(tabIndex + 1);

                String[] metaParts = meta.split(" ");
                entries.add(new Tree.TreeEntry(metaParts[0], metaParts[1], metaParts[2], name));
            }
        }
        return new Tree(entries);
    }

    private static Commit parseCommit(byte[] content) {
        String commitStr = new String(content, StandardCharsets.UTF_8);
        String[] lines = commitStr.split("\n");

        String treeHash = null;
        List<String> parents = new ArrayList<>();
        String author = null;
        Instant timestamp = null;

        int i = 0;
        // Читаем метаданные до первой пустой строки
        for (; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                i++; // Пропускаем пустую строку, дальше пойдет commit message
                break;
            }

            if (line.startsWith("tree ")) {
                treeHash = line.substring(5);
            } else if (line.startsWith("parent ")) {
                parents.add(line.substring(7));
            } else if (line.startsWith("author ")) {
                // author Name <email> 1679821300
                String authorLine = line.substring(7);
                int lastSpace = authorLine.lastIndexOf(' ');
                author = authorLine.substring(0, lastSpace);
                timestamp = Instant.ofEpochSecond(Long.parseLong(authorLine.substring(lastSpace + 1)));
            }
        }

        // Всё оставшееся — это сообщение коммита (может быть многострочным)
        StringBuilder message = new StringBuilder();
        for (; i < lines.length; i++) {
            message.append(lines[i]);
            if (i < lines.length - 1) {
                message.append("\n");
            }
        }

        return new Commit(treeHash, parents, author, timestamp, message.toString());
    }
}
