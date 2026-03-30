package ru.hse.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.hse.storage.ObjectStorage;

class IndexManagerTest {

    @TempDir
    Path tempDir;

    private IndexManager indexManager;

    @BeforeEach
    void setUp() throws IOException {
        RepositoryManager.init(tempDir.toString());
        indexManager = new IndexManager(tempDir.toString(), new ObjectStorage(tempDir.toString()));
    }

    @Test
    void addAndLoadPersistEntries() throws IOException {
        Path file = tempDir.resolve("a.txt");
        Files.writeString(file, "hello", StandardCharsets.UTF_8);

        indexManager.add(file);

        IndexManager reloaded = new IndexManager(tempDir.toString(), new ObjectStorage(tempDir.toString()));
        reloaded.load();
        Map<String, String> entries = reloaded.getEntries();

        assertEquals(1, entries.size());
        assertTrue(entries.containsKey("a.txt"));
    }

    @Test
    void addDirectoryIgnoresMyVcsInternalFiles() throws IOException {
        Files.writeString(tempDir.resolve("tracked.txt"), "tracked", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve(".myvcs/internal.txt"), "ignore-me", StandardCharsets.UTF_8);

        indexManager.add(tempDir);
        indexManager.load();

        Map<String, String> entries = indexManager.getEntries();
        assertTrue(entries.containsKey("tracked.txt"));
        assertFalse(entries.containsKey(".myvcs/internal.txt"));
    }

    @Test
    void clearRemovesAllEntries() throws IOException {
        Path file = tempDir.resolve("b.txt");
        Files.writeString(file, "data", StandardCharsets.UTF_8);
        indexManager.add(file);

        indexManager.clear();
        indexManager.load();

        assertTrue(indexManager.getEntries().isEmpty());
        assertTrue(Files.exists(tempDir.resolve(".myvcs/index")));
        assertEquals("", Files.readString(tempDir.resolve(".myvcs/index"), StandardCharsets.UTF_8));
    }
}
