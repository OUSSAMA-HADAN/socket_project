package client.util;

import common.Message;
import common.MessageType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Checks the texts shown in the user interface. Run with: mvn test */
class FormatsTest {

    @Test
    void sizesAreReadable() {
        assertEquals("512 B", Formats.size(512));
        assertEquals("2.0 KB", Formats.size(2048));
        assertEquals("3.0 MB", Formats.size(3 * 1024 * 1024));
    }

    @Test
    void durationsAreMinutesAndSeconds() {
        assertEquals("0:05", Formats.duration(5));
        assertEquals("1:15", Formats.duration(75));
    }

    @Test
    void recentDaysHaveNames() {
        assertEquals("Today", Formats.day(LocalDate.now()));
        assertEquals("Yesterday", Formats.day(LocalDate.now().minusDays(1)));
    }

    @Test
    void previewsSayWhoWrote() {
        Message mine = Message.text("alice", null, "hello");
        Message theirs = Message.text("bob", null, "hi");
        assertEquals("You: hello", Formats.preview(mine, "alice", true));
        assertEquals("bob: hi", Formats.preview(theirs, "alice", true));  // group: name shown
        assertEquals("hi", Formats.preview(theirs, "alice", false));      // private: no name
    }

    @Test
    void attachmentPreviewsUseIcons() {
        Message voice = Message.attachment(MessageType.VOICE, "bob", "alice", "v.wav", new byte[]{1});
        assertEquals("🎤 Voice message", Formats.preview(voice, "alice", false));
    }
}
