package client.util;

import common.MessageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Checks how files are classified by their extension. Run with: mvn test */
class FileKindsTest {

    @Test
    void picturesAreImages() {
        assertEquals(MessageType.IMAGE, FileKinds.detect("holiday.jpg"));
        assertEquals(MessageType.IMAGE, FileKinds.detect("SCREEN.PNG")); // upper case too
    }

    @Test
    void soundsAreAudio() {
        assertEquals(MessageType.AUDIO, FileKinds.detect("song.mp3"));
        assertEquals(MessageType.AUDIO, FileKinds.detect("voice-123.wav"));
    }

    @Test
    void everythingElseIsAFile() {
        assertEquals(MessageType.FILE, FileKinds.detect("report.pdf"));
        assertEquals(MessageType.FILE, FileKinds.detect("photo.heic")); // the desktop can't show HEIC
        assertEquals(MessageType.FILE, FileKinds.detect("README"));     // no extension
    }

    @Test
    void extensionIsLowerCaseWithoutDot() {
        assertEquals("jpg", FileKinds.extension("Photo.JPG"));
        assertEquals("gz", FileKinds.extension("archive.tar.gz")); // only the last part
        assertEquals("", FileKinds.extension("noext"));
        assertEquals("", FileKinds.extension(null));
    }

    @Test
    void patternsForFileChoosers() {
        assertEquals(List.of("*.png", "*.jpg"), FileKinds.patterns(List.of("png", "jpg")));
    }
}
