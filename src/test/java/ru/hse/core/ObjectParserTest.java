package ru.hse.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.hse.model.Blob;
import ru.hse.model.Commit;
import ru.hse.model.Tree;
import ru.hse.model.VcsObject;

class ObjectParserTest {

    @Test
    void parseBlobObject() {
        byte[] raw = withHeader("blob", "hello".getBytes(StandardCharsets.UTF_8));
        VcsObject parsed = ObjectParser.parse(raw);

        Blob blob = assertInstanceOf(Blob.class, parsed);
        assertEquals("hello", new String(blob.content(), StandardCharsets.UTF_8));
    }

    @Test
    void parseTreeObject() {
        String treePayload = "100644 blob abc123\tfile.txt\n040000 tree def456\tsrc\n";
        byte[] raw = withHeader("tree", treePayload.getBytes(StandardCharsets.UTF_8));

        VcsObject parsed = ObjectParser.parse(raw);
        Tree tree = assertInstanceOf(Tree.class, parsed);

        assertEquals(2, tree.entries().size());
        assertEquals("file.txt", tree.entries().get(0).name());
        assertEquals("src", tree.entries().get(1).name());
    }

    @Test
    void parseCommitObjectWithParentsAndMultilineMessage() {
        Commit expected = new Commit(
                "treehash",
                List.of("p1", "p2"),
                "Tester <t@t>",
                Instant.ofEpochSecond(1_700_000_000L),
                "line1\nline2");
        byte[] raw = withHeader("commit", expected.serialize());

        VcsObject parsed = ObjectParser.parse(raw);
        Commit commit = assertInstanceOf(Commit.class, parsed);

        assertEquals(expected.treeHash(), commit.treeHash());
        assertEquals(expected.parentHashes(), commit.parentHashes());
        assertEquals(expected.author(), commit.author());
        assertEquals(expected.timestamp(), commit.timestamp());
        assertEquals(expected.message(), commit.message());
    }

    @Test
    void parseThrowsForUnknownType() {
        byte[] raw = withHeader("unknown", "x".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> ObjectParser.parse(raw));
    }

    @Test
    void parseThrowsWhenHeaderTerminatorIsMissing() {
        byte[] invalid = "blob 5hello".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> ObjectParser.parse(invalid));
    }

    private static byte[] withHeader(String type, byte[] payload) {
        byte[] header = (type + " " + payload.length + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] raw = new byte[header.length + payload.length];
        System.arraycopy(header, 0, raw, 0, header.length);
        System.arraycopy(payload, 0, raw, header.length, payload.length);
        return raw;
    }
}
