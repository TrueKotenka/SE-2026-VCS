package ru.hse.core;

import ru.hse.model.Commit;
import ru.hse.model.Tree;
import ru.hse.storage.ObjectStorage;

import java.io.IOException;
import java.util.*;

public class MergeResolver {

    private final ObjectStorage storage;

    public MergeResolver(ObjectStorage storage) {
        this.storage = storage;
    }

    /**
     * Ищет ближайшего общего предка (Lowest Common Ancestor) для двух коммитов.
     */
    public String findCommonAncestor(String hashA, String hashB) throws IOException {
        // Собираем всех предков ветки A
        Set<String> ancestorsA = new HashSet<>();
        Queue<String> queueA = new LinkedList<>();
        queueA.add(hashA);

        while (!queueA.isEmpty()) {
            String current = queueA.poll();
            ancestorsA.add(current);
            Commit commit = (Commit) ObjectParser.parse(storage.loadRaw(current));
            queueA.addAll(commit.parentHashes());
        }

        // Идем по предкам ветки B и ищем первое совпадение с предками A
        Queue<String> queueB = new LinkedList<>();
        queueB.add(hashB);

        while (!queueB.isEmpty()) {
            String current = queueB.poll();
            if (ancestorsA.contains(current)) {
                return current; // Нашли!
            }
            Commit commit = (Commit) ObjectParser.parse(storage.loadRaw(current));
            queueB.addAll(commit.parentHashes());
        }

        return null; // Истории абсолютно независимы (например, два разных init)
    }

    /**
     * Разворачивает корневое дерево коммита в плоскую мапу: "относительный путь" -> "хеш файла".
     */
    public Map<String, String> getCommitState(String commitHash) throws IOException {
        Map<String, String> state = new TreeMap<>();
        if (commitHash == null) return state;

        Commit commit = (Commit) ObjectParser.parse(storage.loadRaw(commitHash));
        collectFiles(commit.treeHash(), "", state);
        return state;
    }

    private void collectFiles(String treeHash, String prefix, Map<String, String> state) throws IOException {
        Tree tree = (Tree) ObjectParser.parse(storage.loadRaw(treeHash));
        for (Tree.TreeEntry entry : tree.entries()) {
            String path = prefix.isEmpty() ? entry.name() : prefix + "/" + entry.name();
            if ("tree".equals(entry.type())) {
                collectFiles(entry.hash(), path, state);
            } else if ("blob".equals(entry.type())) {
                state.put(path, entry.hash());
            }
        }
    }
}
