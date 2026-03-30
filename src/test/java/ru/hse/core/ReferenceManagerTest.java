package ru.hse.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReferenceManagerTest {

    @TempDir
    Path tempDir;

    private ReferenceManager referenceManager;

    @BeforeEach
    void setUp() throws IOException {
        RepositoryManager.init(tempDir.toString());
        referenceManager = new ReferenceManager(tempDir.toString());
    }

    @Test
    void getCurrentCommitHashReturnsNullBeforeFirstCommit() throws IOException {
        assertNull(referenceManager.getCurrentCommitHash());
    }

    @Test
    void updateCurrentBranchWritesHashToActiveBranch() throws IOException {
        referenceManager.updateCurrentBranch("abc123");

        assertEquals("abc123", referenceManager.getCurrentCommitHash());
        assertEquals("abc123",
                Files.readString(tempDir.resolve(".myvcs/refs/heads/master"), StandardCharsets.UTF_8).trim());
    }

    @Test
    void detachedHeadIsRecognizedAndUpdatedDirectly() throws IOException {
        referenceManager.setHead("deadbeef");

        assertNull(referenceManager.getActiveBranch());
        assertEquals("deadbeef", referenceManager.getCurrentCommitHash());

        referenceManager.updateCurrentBranch("cafebabe");
        assertEquals("cafebabe", Files.readString(tempDir.resolve(".myvcs/HEAD"), StandardCharsets.UTF_8).trim());
    }

    @Test
    void createResolveListAndDeleteBranchWork() throws IOException {
        referenceManager.updateCurrentBranch("c0ffee");
        referenceManager.createBranch("dev");

        assertEquals("c0ffee", referenceManager.resolveReference("dev"));
        List<String> branches = referenceManager.listBranches();
        assertTrue(branches.contains("master"));
        assertTrue(branches.contains("dev"));

        referenceManager.setHead("dev");
        assertEquals("dev", referenceManager.getActiveBranch());
        referenceManager.deleteBranch("master");
        assertThrows(IllegalStateException.class, () -> referenceManager.deleteBranch("dev"));
    }

    @Test
    void createBranchWithoutCommitsThrows() {
        assertThrows(IllegalStateException.class, () -> referenceManager.createBranch("feature"));
    }
}
