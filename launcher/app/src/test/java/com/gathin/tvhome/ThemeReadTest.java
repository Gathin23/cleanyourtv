package com.gathin.tvhome;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ThemeReadTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private static final String GOOD = "{\"columns\":5}";

    private File write(String name, String text) throws IOException {
        File file = new File(folder.getRoot(), name);
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private File inaccessible(File file) {
        return new File(file.getPath()) {
            @Override public File getParentFile() {
                return new File(folder.getRoot().getPath()) {
                    @Override public boolean canRead() { return false; }
                };
            }
        };
    }

    @Test public void readableThemeReturnsExternalContent() throws IOException {
        assertEquals(GOOD, ThemeRead.read(write("config.json", GOOD)));
    }

    @Test public void unavailableThemeThrowsRatherThanAcknowledgingRemoval() throws IOException {
        File source = inaccessible(write("config.json", GOOD));
        assertThrows(IOException.class, () -> ThemeRead.read(source));
    }

    @Test public void nullFileMeansUnavailableStorage() {
        assertThrows(IOException.class, () -> ThemeRead.read((File) null));
    }

    @Test public void missingConfigOnAccessibleDirectoryConfirmsRemoval() throws IOException {
        assertNull(ThemeRead.read(new File(folder.getRoot(), "config.json")));
    }

    @Test public void missingDirectoryIsUnavailableRatherThanAReset() {
        File source = new File(folder.getRoot(), "missing/config.json");
        assertThrows(IOException.class, () -> ThemeRead.read(source));
    }

    @Test public void invalidJsonIsPassedToExistingConfigurationValidation() throws IOException {
        assertEquals("invalid JSON", ThemeRead.read(write("config.json", "invalid JSON")));
    }

    @Test public void metadataAccessDeniedIsAnIoFailure() throws IOException {
        File parentDenied = new File(folder.getRoot(), "config.json") {
            @Override public File getParentFile() { throw new SecurityException("access denied"); }
        };
        assertThrows(IOException.class, () -> ThemeRead.read(parentDenied));
        File lengthDenied = new File(write("config.json", GOOD).getPath()) {
            @Override public long length() { throw new SecurityException("access denied"); }
        };
        assertThrows(IOException.class, () -> ThemeRead.read(lengthDenied));
    }

    @Test public void utf8BomIsRemoved() throws IOException {
        assertEquals(GOOD, ThemeRead.read(write("config.json", "\uFEFF" + GOOD)));
    }

    @Test public void partialReadsAreCompleted() throws IOException {
        byte[] bytes = GOOD.getBytes(StandardCharsets.UTF_8);
        InputStream in = new FilterInputStream(new ByteArrayInputStream(bytes)) {
            @Override public int read(byte[] b, int offset, int count) throws IOException {
                return super.read(b, offset, Math.min(count, 1));
            }
        };
        assertEquals(GOOD, ThemeRead.read(in, bytes.length));
    }

    @Test public void readFailureAfterSomeBytesDoesNotAcknowledgeTheTheme() {
        InputStream in = new FilterInputStream(new ByteArrayInputStream(GOOD.getBytes(StandardCharsets.UTF_8))) {
            private boolean read;
            @Override public int read(byte[] b, int offset, int count) throws IOException {
                if (read) throw new IOException("injected read failure");
                read = true;
                return super.read(b, offset, 1);
            }
        };
        assertThrows(IOException.class, () -> ThemeRead.read(in, GOOD.length()));
    }

    @Test public void closeFailureDoesNotAcknowledgeTheTheme() {
        InputStream in = new FilterInputStream(new ByteArrayInputStream(GOOD.getBytes(StandardCharsets.UTF_8))) {
            @Override public void close() throws IOException {
                super.close();
                throw new IOException("injected close failure");
            }
        };
        assertThrows(IOException.class, () -> ThemeRead.read(in, GOOD.length()));
    }

    @Test public void truncatedOrGrowingFilesFailTheRead() {
        byte[] bytes = GOOD.getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> ThemeRead.read(new ByteArrayInputStream(bytes), bytes.length + 1));
        assertThrows(IOException.class, () -> ThemeRead.read(new ByteArrayInputStream(bytes), bytes.length - 1));
    }

    @Test public void anOversizedThemeRetainsTheConfigurationLimit() throws IOException {
        File source = write("config.json", new String(new char[65537]).replace('\0', ' '));
        assertNull(ThemeRead.read(source));
    }

    @Test public void recoveryWaitsForReadableFilesAndAcceptsAnEmptyResetDirectory() throws IOException {
        File source = write("config.json", GOOD);
        assertThrows(IOException.class, () -> ThemeRead.read(inaccessible(source)));
        assertThrows(IOException.class, () -> ThemeRead.read(inaccessible(source)));
        assertEquals(GOOD, ThemeRead.read(source));
        assertTrue(source.delete());
        assertNull(ThemeRead.read(source));
    }
}
