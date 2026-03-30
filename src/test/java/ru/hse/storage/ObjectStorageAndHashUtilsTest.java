package ru.hse.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.hse.model.Blob;

class ObjectStorageAndHashUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRawRoundTripWorks() throws IOException {
        ObjectStorage storage = new ObjectStorage(tempDir.toString());
        Blob blob = new Blob("hello-storage".getBytes(StandardCharsets.UTF_8));

        String hash = storage.save(blob);
        byte[] raw = storage.loadRaw(hash);

        byte[] expectedHeader = ("blob " + blob.serialize().length + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] expectedRaw = new byte[expectedHeader.length + blob.serialize().length];
        System.arraycopy(expectedHeader, 0, expectedRaw, 0, expectedHeader.length);
        System.arraycopy(blob.serialize(), 0, expectedRaw, expectedHeader.length, blob.serialize().length);

        assertArrayEquals(expectedRaw, raw);
    }

    @Test
    void saveSameObjectTwiceReturnsSameHash() throws IOException {
        ObjectStorage storage = new ObjectStorage(tempDir.toString());
        Blob blob = new Blob("same-content".getBytes(StandardCharsets.UTF_8));

        String hash1 = storage.save(blob);
        String hash2 = storage.save(blob);

        assertEquals(hash1, hash2);
    }

    @Test
    void loadRawThrowsForMissingObject() {
        ObjectStorage storage = new ObjectStorage(tempDir.toString());
        assertThrows(IllegalArgumentException.class, () -> storage.loadRaw("deadbeef"));
    }

    @Test
    void hashUtilsSha256IsDeterministicAndHexEncoded() {
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);

        String hash = HashUtils.sha256(data);

        assertEquals(HashUtils.sha256(data), hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }
}
