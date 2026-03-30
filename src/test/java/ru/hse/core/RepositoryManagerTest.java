package ru.hse.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void initCreatesRepositoryStructureAndHead() throws IOException {
        RepositoryManager.init(tempDir.toString());

        assertTrue(Files.exists(tempDir.resolve(".myvcs")));
        assertTrue(Files.exists(tempDir.resolve(".myvcs/objects")));
        assertTrue(Files.exists(tempDir.resolve(".myvcs/refs/heads")));
        assertEquals("ref: refs/heads/master",
                Files.readString(tempDir.resolve(".myvcs/HEAD"), StandardCharsets.UTF_8).trim());
    }

    @Test
    void initSecondTimeDoesNotReinitializeExistingRepository() throws IOException {
        RepositoryManager.init(tempDir.toString());
        Files.writeString(tempDir.resolve(".myvcs/HEAD"), "detached-hash\n", StandardCharsets.UTF_8);

        IOResult io = captureIo(() -> RepositoryManager.init(tempDir.toString()));

        assertTrue(io.out.contains("Репозиторий уже существует"));
        assertEquals("detached-hash", Files.readString(tempDir.resolve(".myvcs/HEAD"), StandardCharsets.UTF_8).trim());
    }

    private static IOResult captureIo(ThrowingRunnable action) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out));
            action.run();
            return new IOResult(out.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.setOut(originalOut);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record IOResult(String out) {
    }
}
