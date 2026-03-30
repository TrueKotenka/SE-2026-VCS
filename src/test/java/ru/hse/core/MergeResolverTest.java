package ru.hse.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.hse.model.Blob;
import ru.hse.model.Commit;
import ru.hse.model.Tree;
import ru.hse.storage.ObjectStorage;

class MergeResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void findCommonAncestorReturnsBaseCommit() throws IOException {
        ObjectStorage storage = new ObjectStorage(tempDir.toString());
        MergeResolver resolver = new MergeResolver(storage);

        String base = saveCommitWithFiles(storage, List.of(), Map.of("a.txt", "base"));
        String left = saveCommitWithFiles(storage, List.of(base), Map.of("a.txt", "left"));
        String right = saveCommitWithFiles(storage, List.of(base), Map.of("a.txt", "right"));

        String lca = resolver.findCommonAncestor(left, right);

        assertEquals(base, lca);
    }

    @Test
    void findCommonAncestorReturnsNullForIndependentHistories() throws IOException {
        ObjectStorage storage = new ObjectStorage(tempDir.toString());
        MergeResolver resolver = new MergeResolver(storage);

        String rootA = saveCommitWithFiles(storage, List.of(), Map.of("a.txt", "a"));
        String rootB = saveCommitWithFiles(storage, List.of(), Map.of("b.txt", "b"));

        assertNull(resolver.findCommonAncestor(rootA, rootB));
    }

    @Test
    void getCommitStateFlattensNestedTreePaths() throws IOException {
        ObjectStorage storage = new ObjectStorage(tempDir.toString());
        MergeResolver resolver = new MergeResolver(storage);

        String readmeHash = storage.save(new Blob("hello".getBytes()));
        String nestedHash = storage.save(new Blob("world".getBytes()));

        Tree srcTree = new Tree(List.of(new Tree.TreeEntry("100644", "blob", nestedHash, "Main.java")));
        String srcTreeHash = storage.save(srcTree);
        Tree rootTree = new Tree(List.of(new Tree.TreeEntry("100644", "blob", readmeHash, "README.md"),
                new Tree.TreeEntry("040000", "tree", srcTreeHash, "src")));
        String rootTreeHash = storage.save(rootTree);

        String commitHash = storage.save(new Commit(rootTreeHash, List.of(), "Tester <test@example.com>",
                Instant.ofEpochSecond(1_700_000_000L), "snapshot"));

        Map<String, String> state = resolver.getCommitState(commitHash);

        assertEquals(2, state.size());
        assertEquals(readmeHash, state.get("README.md"));
        assertEquals(nestedHash, state.get("src/Main.java"));
    }

    private static String saveCommitWithFiles(ObjectStorage storage, List<String> parents, Map<String, String> files)
            throws IOException {
        List<Tree.TreeEntry> entries = files.entrySet().stream().map(entry -> {
            try {
                String blobHash = storage.save(new Blob(entry.getValue().getBytes()));
                return new Tree.TreeEntry("100644", "blob", blobHash, entry.getKey());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).toList();

        String treeHash = storage.save(new Tree(entries));
        Commit commit = new Commit(treeHash, parents, "Tester <test@example.com>",
                Instant.ofEpochSecond(1_700_000_000L), "msg");
        return storage.save(commit);
    }
}
