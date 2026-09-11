package com.example.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageServiceTest {

    @TempDir Path tmp;
    LocalFileStorageService storage = new LocalFileStorageService();

    @BeforeEach
    void setDir() {
        ReflectionTestUtils.setField(storage, "uploadDir", tmp.toString());
    }

    @Test
    void copyToDetainMediaCopiesExistingJpeg() throws Exception {
        Path src = tmp.resolve("dispatch/7/a.jpg");
        Files.createDirectories(src.getParent());
        Files.write(src, new byte[] {1, 2, 3});

        String dest = storage.copyToDetainMedia(88L, "dispatch/7/a.jpg");

        assertNotNull(dest);
        assertTrue(dest.startsWith("detain/88/"));
        assertTrue(dest.endsWith(".jpg"));
        assertTrue(Files.exists(tmp.resolve(dest)));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(tmp.resolve(dest)));
    }

    @Test
    void copyToDetainMediaReturnsNullWhenMissing() {
        assertNull(storage.copyToDetainMedia(1L, "dispatch/9/nope.jpg"));
        assertNull(storage.copyToDetainMedia(1L, null));
        assertNull(storage.copyToDetainMedia(1L, "dispatch/9/x.gif"));
    }

    @Test
    void copyToDetainMediaUsesDistinctDestNamesForBackToBackCopies() throws Exception {
        Path src1 = tmp.resolve("dispatch/7/a.jpg");
        Path src2 = tmp.resolve("dispatch/7/b.jpg");
        Files.createDirectories(src1.getParent());
        byte[] bytes1 = new byte[] {1, 2, 3};
        byte[] bytes2 = new byte[] {4, 5, 6};
        Files.write(src1, bytes1);
        Files.write(src2, bytes2);

        String dest1 = storage.copyToDetainMedia(88L, "dispatch/7/a.jpg");
        String dest2 = storage.copyToDetainMedia(88L, "dispatch/7/b.jpg");

        assertNotNull(dest1);
        assertNotNull(dest2);
        assertNotEquals(dest1, dest2);
        assertTrue(dest1.startsWith("detain/88/"));
        assertTrue(dest1.endsWith(".jpg"));
        assertTrue(dest2.startsWith("detain/88/"));
        assertTrue(dest2.endsWith(".jpg"));
        assertTrue(Files.exists(tmp.resolve(dest1)));
        assertTrue(Files.exists(tmp.resolve(dest2)));
        assertArrayEquals(bytes1, Files.readAllBytes(tmp.resolve(dest1)));
        assertArrayEquals(bytes2, Files.readAllBytes(tmp.resolve(dest2)));
    }
}
