package ru.hse.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.hse.model.Commit;
import ru.hse.model.CommitNode;
import ru.hse.storage.ObjectStorage;

class VcsServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private ObjectStorage storage;
    private IndexManager indexManager;
    private ReferenceManager referenceManager;
    private VcsService vcsService;

    @BeforeEach
    void setUp() throws IOException {
        RepositoryManager.init(tempDir.toString());
        storage = new ObjectStorage(tempDir.toString());
        indexManager = new IndexManager(tempDir.toString(), storage);
        referenceManager = new ReferenceManager(tempDir.toString());
        vcsService = new VcsService(storage, indexManager, referenceManager);
    }

    @Test
    void initAddCommitLogFlowWorks() throws IOException {
        Path file = tempDir.resolve("readme.md");
        Files.writeString(file, "hello", StandardCharsets.UTF_8);
        indexManager.add(file);

        String commitHash = vcsService.commit("initial", "Tester <t@t>");

        List<CommitNode> log = vcsService.getLog();
        assertEquals(1, log.size());
        assertEquals(commitHash, log.get(0).hash());
        assertEquals("initial", log.get(0).commit().message());
        assertEquals("Tester <t@t>", log.get(0).commit().author());
    }

    @Test
    void checkoutBranchAndHashDetachedHeadWork() throws IOException {
        Path file = tempDir.resolve("note.txt");
        Files.writeString(file, "v1", StandardCharsets.UTF_8);
        indexManager.add(file);
        String firstCommit = vcsService.commit("c1", "Tester <t@t>");

        vcsService.createBranch("feature");
        vcsService.checkout("feature");
        Files.writeString(file, "v2-feature", StandardCharsets.UTF_8);
        indexManager.add(file);
        String secondCommit = vcsService.commit("c2", "Tester <t@t>");

        vcsService.checkout("master");
        assertEquals("master", referenceManager.getActiveBranch());
        assertEquals(firstCommit, referenceManager.getCurrentCommitHash());
        assertEquals("v1", Files.readString(file, StandardCharsets.UTF_8));

        vcsService.checkout(secondCommit);
        assertNull(referenceManager.getActiveBranch());
        assertEquals(secondCommit, referenceManager.getCurrentCommitHash());
        assertEquals("v2-feature", Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void createAndDeleteBranchThroughServiceWork() throws IOException {
        Path file = tempDir.resolve("main.txt");
        Files.writeString(file, "base", StandardCharsets.UTF_8);
        indexManager.add(file);
        String baseCommit = vcsService.commit("base", "Tester <t@t>");

        vcsService.createBranch("dev");
        assertEquals(baseCommit,
                Files.readString(tempDir.resolve(".myvcs/refs/heads/dev"), StandardCharsets.UTF_8).trim());

        vcsService.deleteBranch("dev");
        assertFalse(Files.exists(tempDir.resolve(".myvcs/refs/heads/dev")));
    }

    @Test
    void mergeFastForwardMovesCurrentBranchPointer() throws IOException {
        Path file = tempDir.resolve("app.txt");
        Files.writeString(file, "v1", StandardCharsets.UTF_8);
        indexManager.add(file);
        vcsService.commit("base", "Tester <t@t>");

        vcsService.createBranch("feature");
        vcsService.checkout("feature");
        Files.writeString(file, "v2-feature", StandardCharsets.UTF_8);
        indexManager.add(file);
        String featureCommit = vcsService.commit("feature work", "Tester <t@t>");

        vcsService.checkout("master");
        vcsService.merge("feature");

        assertEquals("master", referenceManager.getActiveBranch());
        assertEquals(featureCommit, referenceManager.getCurrentCommitHash());
        assertEquals(featureCommit,
                Files.readString(tempDir.resolve(".myvcs/refs/heads/master"), StandardCharsets.UTF_8).trim());
        assertEquals("v2-feature", Files.readString(file, StandardCharsets.UTF_8));
        assertFalse(Files.exists(tempDir.resolve(".myvcs/MERGE_HEAD")));
    }

    @Test
    void mergeConflictCreatesMarkersAndCommitUsesTwoParents() throws IOException {
        Path file = tempDir.resolve("conflict.txt");
        Files.writeString(file, "base", StandardCharsets.UTF_8);
        indexManager.add(file);
        vcsService.commit("base", "Tester <t@t>");

        vcsService.createBranch("feature");

        Files.writeString(file, "master change", StandardCharsets.UTF_8);
        indexManager.add(file);
        String masterCommit = vcsService.commit("master change", "Tester <t@t>");

        vcsService.checkout("feature");
        Files.writeString(file, "feature change", StandardCharsets.UTF_8);
        indexManager.add(file);
        String featureCommit = vcsService.commit("feature change", "Tester <t@t>");

        vcsService.checkout("master");
        vcsService.merge("feature");

        String conflictedContent = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(conflictedContent.contains("<<<<<<< HEAD"));
        assertTrue(conflictedContent.contains("======="));
        assertTrue(conflictedContent.contains(">>>>>>> feature"));
        assertEquals(featureCommit,
                Files.readString(tempDir.resolve(".myvcs/MERGE_HEAD"), StandardCharsets.UTF_8).trim());

        Files.writeString(file, "resolved content", StandardCharsets.UTF_8);
        indexManager.add(file);
        String mergeCommitHash = vcsService.commit("resolve conflict", "Tester <t@t>");

        Commit mergeCommit = (Commit) ObjectParser.parse(storage.loadRaw(mergeCommitHash));
        assertEquals(2, mergeCommit.parentHashes().size());
        assertEquals(masterCommit, mergeCommit.parentHashes().get(0));
        assertEquals(featureCommit, mergeCommit.parentHashes().get(1));
        assertFalse(Files.exists(tempDir.resolve(".myvcs/MERGE_HEAD")));
    }
}
