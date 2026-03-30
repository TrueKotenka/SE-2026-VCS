package ru.hse.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.hse.core.IndexManager;
import ru.hse.core.VcsService;
import ru.hse.storage.ObjectStorage;

class CommandParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parseAndExecuteNoArgsPrintsHelp() {
        FakeIndexManager indexManager = new FakeIndexManager(tempDir);
        FakeVcsService vcsService = new FakeVcsService();
        CommandParser parser = new CommandParser(indexManager, vcsService);

        IOResult io = captureIo(() -> parser.parseAndExecute(new String[0]));

        assertTrue(io.out.contains("Использование: vcs <команда> [аргументы]"));
        assertTrue(io.out.contains("Доступные команды:"));
    }

    @Test
    void parseAndExecuteUnknownCommandPrintsErrorAndHelp() {
        FakeIndexManager indexManager = new FakeIndexManager(tempDir);
        FakeVcsService vcsService = new FakeVcsService();
        CommandParser parser = new CommandParser(indexManager, vcsService);

        IOResult io = captureIo(() -> parser.parseAndExecute(new String[] {"abracadabra"}));

        assertTrue(io.err.contains("Неизвестная команда: abracadabra"));
        assertTrue(io.out.contains("Доступные команды:"));
    }

    @Test
    void parseAndExecuteAddDispatchesToIndexManager() {
        FakeIndexManager indexManager = new FakeIndexManager(tempDir);
        FakeVcsService vcsService = new FakeVcsService();
        CommandParser parser = new CommandParser(indexManager, vcsService);

        parser.parseAndExecute(new String[] {"add", "file1.txt", "dir/file2.txt"});

        assertEquals(1, indexManager.loadCalls);
        assertEquals(List.of("file1.txt", "dir/file2.txt"), indexManager.addedPaths);
    }

    @Test
    void parseAndExecuteCommitDispatchesToVcsService() {
        FakeIndexManager indexManager = new FakeIndexManager(tempDir);
        FakeVcsService vcsService = new FakeVcsService();
        CommandParser parser = new CommandParser(indexManager, vcsService);

        parser.parseAndExecute(new String[] {"commit", "-m", "my message"});

        assertEquals(1, vcsService.commitCalls);
        assertEquals("my message", vcsService.lastCommitMessage);
    }

    @Test
    void parseAndExecuteCheckoutDispatchesToVcsService() {
        FakeIndexManager indexManager = new FakeIndexManager(tempDir);
        FakeVcsService vcsService = new FakeVcsService();
        CommandParser parser = new CommandParser(indexManager, vcsService);

        parser.parseAndExecute(new String[] {"checkout", "master"});

        assertEquals(1, vcsService.checkoutCalls);
        assertEquals("master", vcsService.lastCheckoutRevision);
    }

    private static IOResult captureIo(ThrowingRunnable action) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out));
            System.setErr(new PrintStream(err));
            action.run();
            return new IOResult(out.toString(), err.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record IOResult(String out, String err) {
    }

    private static class FakeVcsService extends VcsService {
        int commitCalls;
        int checkoutCalls;
        String lastCommitMessage;
        String lastCheckoutRevision;

        FakeVcsService() {
            super(null, null, null);
        }

        @Override
        public String commit(String message, String author) {
            commitCalls++;
            lastCommitMessage = message;
            return "fake-hash";
        }

        @Override
        public void checkout(String revision) {
            checkoutCalls++;
            lastCheckoutRevision = revision;
        }
    }

    private static class FakeIndexManager extends IndexManager {
        int loadCalls;
        final List<String> addedPaths = new ArrayList<>();

        FakeIndexManager(Path repoRoot) {
            super(repoRoot.toString(), new ObjectStorage(repoRoot.toString()));
        }

        @Override
        public void load() {
            loadCalls++;
        }

        @Override
        public void add(Path targetPath) {
            addedPaths.add(targetPath.toString().replace("\\", "/"));
        }
    }
}
