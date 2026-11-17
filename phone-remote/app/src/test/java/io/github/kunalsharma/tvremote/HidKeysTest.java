package io.github.kunalsharma.tvremote;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class HidKeysTest {
    @Test
    public void keyboardReportIsEightBytesWithTheKeyInTheFirstSlot() {
        byte[] r = HidKeys.keyboardReport(0, HidKeys.KEY_UP);
        assertEquals(8, r.length);
        assertEquals(0x52, r[2]);
        assertEquals(0, r[0]);
        assertEquals(0, r[1]);
        for (int i = 3; i < 8; i++) assertEquals(0, r[i]);
    }

    @Test
    public void releaseReportIsAllZero() {
        assertArrayEquals(new byte[8], HidKeys.keyboardReport(0, 0));
        assertArrayEquals(new byte[2], HidKeys.consumerReport(0));
    }

    @Test
    public void consumerUsageIsLittleEndian() {
        assertArrayEquals(new byte[] {0x24, 0x02}, HidKeys.consumerReport(HidKeys.CONSUMER_BACK));
        assertArrayEquals(new byte[] {0x23, 0x02}, HidKeys.consumerReport(HidKeys.CONSUMER_HOME));
        assertArrayEquals(new byte[] {(byte) 0xE9, 0x00}, HidKeys.consumerReport(HidKeys.CONSUMER_VOLUME_UP));
    }

    @Test
    public void lettersAndDigitsMapToTheirUsages() {
        assertArrayEquals(new int[] {0, 0x04}, HidKeys.forChar('a'));
        assertArrayEquals(new int[] {0, 0x1D}, HidKeys.forChar('z'));
        assertArrayEquals(new int[] {HidKeys.MOD_SHIFT, 0x04}, HidKeys.forChar('A'));
        assertArrayEquals(new int[] {0, 0x1E}, HidKeys.forChar('1'));
        assertArrayEquals(new int[] {0, 0x26}, HidKeys.forChar('9'));
        assertArrayEquals(new int[] {0, 0x27}, HidKeys.forChar('0'));
    }

    @Test
    public void spaceNewlineAndPunctuationWork() {
        assertArrayEquals(new int[] {0, 0x2C}, HidKeys.forChar(' '));
        assertArrayEquals(new int[] {0, 0x28}, HidKeys.forChar('\n'));
        assertArrayEquals(new int[] {HidKeys.MOD_SHIFT, 0x1F}, HidKeys.forChar('@'));
    }

    @Test
    public void unsupportedCharactersAreReportedAsNull() {
        assertNull(HidKeys.forChar('क'));
        assertNull(HidKeys.forChar('~'));
    }

    @Test
    public void descriptorDeclaresBothReportsAndClosesEveryCollection() {
        byte[] d = HidKeys.DESCRIPTOR;
        int collectionsOpened = 0;
        int collectionsClosed = 0;
        boolean sawKeyboardId = false;
        boolean sawConsumerId = false;
        for (int i = 0; i < d.length; i++) {
            int b = d[i] & 0xFF;
            if (b == 0xA1 && i + 1 < d.length && d[i + 1] == 0x01) collectionsOpened++;
            if (b == 0xC0) collectionsClosed++;
            if (b == 0x85 && i + 1 < d.length) {
                if (d[i + 1] == HidKeys.REPORT_KEYBOARD) sawKeyboardId = true;
                if (d[i + 1] == HidKeys.REPORT_CONSUMER) sawConsumerId = true;
            }
        }
        assertEquals(2, collectionsOpened);
        assertEquals(2, collectionsClosed);
        assertEquals(true, sawKeyboardId);
        assertEquals(true, sawConsumerId);
    }
}
