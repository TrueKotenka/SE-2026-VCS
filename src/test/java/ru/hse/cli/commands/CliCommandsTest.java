package ru.hse.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.hse.core.IndexManager;
import ru.hse.core.VcsService;
import ru.hse.model.Commit;
import ru.hse.model.CommitNode;
import ru.hse.storage.ObjectStorage;

class CliCommandsTest {

    @TempDir
    Path tempDir;

    @Test
    void initCommandCreatesMyVcsDirectory() throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            new InitCommand().execute(new String[0]);
            assertTrue(Files.exists(tempDir.resolve(".myvcs")));
            assertTrue(Files.exists(tempDir.resolve(".myvcs/HEAD")));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void addCommandWithoutArgsPrintsError() {
        FakeIndexManager index = new FakeIndexManager(tempDir);
        AddCommand command = new AddCommand(index);

        IOResult io = captureIo(() -> command.execute(new String[0]));

        assertTrue(io.err.contains("Ошибка: не указан путь к файлу"));
        assertEquals(0, index.loadCalls);
        assertTrue(index.addedPaths.isEmpty());
    }

    @Test
    void addCommandLoadsIndexAndAddsAllPaths() throws Exception {
        FakeIndexManager index = new FakeIndexManager(tempDir);
        AddCommand command = new AddCommand(index);

        command.execute(new String[] {"a.txt", "b/c.txt"});

        assertEquals(1, index.loadCalls);
        assertEquals(List.of("a.txt", "b/c.txt"), index.addedPaths);
    }

    @Test
    void commitCommandParsesMessageAndCallsService() throws Exception {
        FakeVcsService vcs = new FakeVcsService();
        CommitCommand command = new CommitCommand(vcs);

        command.execute(new String[] {"-m", "hello"});

        assertEquals(1, vcs.commitCalls);
        assertEquals("hello", vcs.lastCommitMessage);
    }

    @Test
    void commitCommandHandlesEmptyIndexError() throws Exception {
        FakeVcsService vcs = new FakeVcsService();
        vcs.throwOnCommit = true;
        CommitCommand command = new CommitCommand(vcs);

        IOResult io = captureIo(() -> command.execute(new String[] {"-m", "x"}));

        assertTrue(io.err.contains("Нет файлов для коммита"));
    }

    @Test
    void checkoutCommandWithoutArgsPrintsUsage() {
        FakeVcsService vcs = new FakeVcsService();
        CheckoutCommand command = new CheckoutCommand(vcs);

        IOResult io = captureIo(() -> command.execute(new String[0]));

        assertTrue(io.err.contains("Использование: vcs checkout <revision>"));
        assertEquals(0, vcs.checkoutCalls);
    }

    @Test
    void checkoutCommandCallsService() throws Exception {
        FakeVcsService vcs = new FakeVcsService();
        CheckoutCommand command = new CheckoutCommand(vcs);

        command.execute(new String[] {"dev"});

        assertEquals(1, vcs.checkoutCalls);
        assertEquals("dev", vcs.lastCheckoutRevision);
    }

    @Test
    void logCommandPrintsEmptyMessageForNoCommits() throws Exception {
        FakeVcsService vcs = new FakeVcsService();
        LogCommand command = new LogCommand(vcs);

        IOResult io = captureIo(() -> command.execute(new String[0]));

        assertTrue(io.out.contains("История пуста. Сделайте свой первый коммит!"));
    }

    @Test
    void logCommandPrintsCommitData() throws Exception {
        FakeVcsService vcs = new FakeVcsService();
        vcs.log.add(new CommitNode("abc123",
                new Commit("tree", List.of(), "Tester <t@t>", Instant.ofEpochSecond(1_700_000_000L), "msg")));
        LogCommand command = new LogCommand(vcs);

        IOResult io = captureIo(() -> command.execute(new String[0]));

        assertTrue(io.out.contains("commit abc123"));
        assertTrue(io.out.contains("Author: Tester <t@t>"));
        assertTrue(io.out.contains("msg"));
    }

    @Test
    void branchCommandCreatesBranch() throws Exception {
        FakeVcsService vcs = new FakeVcsService();
        BranchCommand command = new BranchCommand(vcs);

        command.execute(new String[] {"feature1"});

        assertEquals(1, vcs.createBranchCalls);
        assertEquals("feature1", vcs.lastCreatedBranch);
    }

    @Test
    void branchCommandRejectsInvalidBranchName() throws Exception {
        FakeVcsService vcs = new FakeVcsService();
        BranchCommand command = new BranchCommand(vcs);

        IOResult io = captureIo(() -> command.execute(new String[] {"feature/test"}));

        assertTrue(io.err.contains("Ошибка: имя ветки может содержать только буквы"));
        assertEquals(0, vcs.createBranchCalls);
    }

    @Test
    void branchCommandDeleteModeCallsService() throws Exception {
        FakeVcsService vcs = new FakeVcsService();
        BranchCommand command = new BranchCommand(vcs);

        command.execute(new String[] {"-d", "feature1"});

        assertEquals(1, vcs.deleteBranchCalls);
        assertEquals("feature1", vcs.lastDeletedBranch);
    }

    @Test
    void mergeCommandWithoutTargetPrintsUsage() {
        FakeVcsService vcs = new FakeVcsService();
        MergeCommand command = new MergeCommand(vcs);

        IOResult io = captureIo(() -> command.execute(new String[0]));

        assertTrue(io.err.contains("Использование: vcs merge <имя_ветки>"));
        assertEquals(0, vcs.mergeCalls);
    }

    @Test
    void mergeCommandCallsService() throws Exception {
        FakeVcsService vcs = new FakeVcsService();
        MergeCommand command = new MergeCommand(vcs);

        command.execute(new String[] {"develop"});

        assertEquals(1, vcs.mergeCalls);
        assertEquals("develop", vcs.lastMergeTarget);
    }

    @Test
    void statusCommandCallsService() throws Exception {
        FakeVcsService vcs = new FakeVcsService();
        StatusCommand command = new StatusCommand(vcs);

        command.execute(new String[0]);

        assertEquals(1, vcs.statusCalls);
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
        int createBranchCalls;
        int deleteBranchCalls;
        int mergeCalls;
        int statusCalls;
        String lastCommitMessage;
        String lastCheckoutRevision;
        String lastCreatedBranch;
        String lastDeletedBranch;
        String lastMergeTarget;
        boolean throwOnCommit;
        final List<CommitNode> log = new ArrayList<>();

        FakeVcsService() {
            super(null, null, null);
        }

        @Override
        public String commit(String message, String author) {
            if (throwOnCommit) {
                throw new IllegalStateException("Нет файлов для коммита (индекс пуст).");
            }
            commitCalls++;
            lastCommitMessage = message;
            return "hash";
        }

        @Override
        public void checkout(String revision) {
            checkoutCalls++;
            lastCheckoutRevision = revision;
        }

        @Override
        public List<CommitNode> getLog() {
            return log;
        }

        @Override
        public void createBranch(String name) {
            createBranchCalls++;
            lastCreatedBranch = name;
        }

        @Override
        public void deleteBranch(String name) {
            deleteBranchCalls++;
            lastDeletedBranch = name;
        }

        @Override
        public void merge(String targetBranch) {
            mergeCalls++;
            lastMergeTarget = targetBranch;
        }

        @Override
        public void status() {
            statusCalls++;
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
        public void add(Path targetPath) throws IOException {
            addedPaths.add(targetPath.toString().replace("\\", "/"));
        }
    }
}
