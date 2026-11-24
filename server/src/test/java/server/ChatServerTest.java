package server;

import client.network.ChatClient;
import client.network.MessageListener;
import common.Config;
import common.Message;
import common.MessageType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests: a real server on a free port, real sockets, and the same {@link ChatClient}
 * the desktop and Android apps use. Nothing here calls the AI service (no API quota is spent).
 * Run with: mvn test
 */
class ChatServerTest {

    private static final int TIMEOUT_SECONDS = 10;
    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    private static ChatServer server;
    private static Thread serverThread;
    private static int port;

    @BeforeAll
    static void startServer() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        server = new ChatServer(port);
        serverThread = new Thread(server::start, "test-server");
        serverThread.setDaemon(true);
        serverThread.start();
        long deadline = System.currentTimeMillis() + 5000;
        while (true) { // wait until it accepts connections
            try (Socket ignored = new Socket("localhost", port)) {
                return;
            } catch (IOException e) {
                if (System.currentTimeMillis() > deadline) {
                    throw e;
                }
                Thread.sleep(50);
            }
        }
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        server.stop();
        serverThread.join(3000);
    }

    // ---------------------------------------------------------------- tests

    @Test
    void everyoneSeesTheUserListWithTheAssistant() throws Exception {
        try (TestClient alice = TestClient.join(name("alice")); TestClient bob = TestClient.join(name("bob"))) {
            Message list = alice.await(m -> m.getType() == MessageType.USER_LIST
                    && m.getUsers().contains(bob.name));
            assertTrue(list.getUsers().contains(alice.name));
            assertTrue(list.getUsers().contains(Config.AI_BOT_NAME), "the assistant is always online");
        }
    }

    @Test
    void takenAndReservedNamesAreRefused() throws Exception {
        try (TestClient alice = TestClient.join(name("alice"))) {
            IOException taken = assertThrows(IOException.class, () -> ChatClient.connect("localhost", port, alice.name));
            assertTrue(taken.getMessage().contains("already taken"), taken.getMessage());
        }
        IOException reserved = assertThrows(IOException.class,
                () -> ChatClient.connect("localhost", port, Config.AI_BOT_NAME));
        assertTrue(reserved.getMessage().contains("reserved"), reserved.getMessage());
        assertThrows(IOException.class, () -> ChatClient.connect("localhost", port, "bad#name"));
    }

    @Test
    void groupMessagesReachEveryoneElseAndAreNotEchoed() throws Exception {
        try (TestClient alice = TestClient.join(name("alice")); TestClient bob = TestClient.join(name("bob"))) {
            alice.send(Message.text(alice.name, null, "hello group"));
            Message received = bob.await(m -> m.getType() == MessageType.TEXT && alice.name.equals(m.getSender()));
            assertEquals("hello group", received.getText());
            assertNull(received.getRecipient(), "group messages have no recipient");
            alice.assertNothing(m -> m.getType() == MessageType.TEXT, 500);
        }
    }

    @Test
    void privateMessagesReachOnlyTheirRecipient() throws Exception {
        try (TestClient alice = TestClient.join(name("alice"));
             TestClient bob = TestClient.join(name("bob"));
             TestClient carol = TestClient.join(name("carol"))) {
            alice.send(Message.text(alice.name, bob.name, "just for bob"));
            Message received = bob.await(m -> m.getType() == MessageType.TEXT);
            assertEquals("just for bob", received.getText());
            assertEquals(bob.name, received.getRecipient());
            carol.assertNothing(m -> m.getType() == MessageType.TEXT, 500);
        }
    }

    @Test
    void attachmentsArriveByteForByte() throws Exception {
        try (TestClient alice = TestClient.join(name("alice")); TestClient bob = TestClient.join(name("bob"))) {
            Map<MessageType, byte[]> sent = new HashMap<>();
            sent.put(MessageType.IMAGE, randomBytes(200_000));
            sent.put(MessageType.AUDIO, randomBytes(300_000));
            sent.put(MessageType.VOICE, randomBytes(64_044));
            sent.put(MessageType.FILE, randomBytes(2_000_000));
            for (Map.Entry<MessageType, byte[]> e : sent.entrySet()) {
                alice.send(Message.attachment(e.getKey(), alice.name, bob.name, "test." + e.getKey(), e.getValue()));
            }
            for (Map.Entry<MessageType, byte[]> e : sent.entrySet()) {
                Message received = bob.await(m -> m.getType() == e.getKey());
                assertEquals("test." + e.getKey(), received.getFileName());
                assertArrayEquals(e.getValue(), received.getData(), e.getKey() + " bytes changed on the way");
            }
        }
    }

    @Test
    void emojiSurviveTheTrip() throws Exception {
        String text = "👨‍👩‍👧 🇩🇿 ❤️ 1️⃣ 👍🏽 done";
        try (TestClient alice = TestClient.join(name("alice")); TestClient bob = TestClient.join(name("bob"))) {
            alice.send(Message.text(alice.name, bob.name, text));
            assertEquals(text, bob.await(m -> m.getType() == MessageType.TEXT).getText());
        }
    }

    @Test
    void spoofedSendersAreIgnored() throws Exception {
        try (TestClient alice = TestClient.join(name("alice")); TestClient bob = TestClient.join(name("bob"))) {
            alice.send(Message.text(bob.name, null, "pretending to be bob"));
            bob.assertNothing(m -> m.getType() == MessageType.TEXT, 700);
            alice.assertNothing(m -> m.getType() == MessageType.TEXT, 1);
        }
    }

    @Test
    void tooLargeAttachmentsAreRefused() throws Exception {
        try (TestClient alice = TestClient.join(name("alice")); TestClient bob = TestClient.join(name("bob"))) {
            byte[] huge = new byte[(int) Config.MAX_ATTACHMENT_BYTES + 1];
            alice.send(Message.attachment(MessageType.FILE, alice.name, bob.name, "huge.bin", huge));
            assertTrue(alice.await(m -> m.getType() == MessageType.ERROR).getText().contains("too large"));
            bob.assertNothing(m -> m.getType() == MessageType.FILE, 500);
        }
    }

    @Test
    void assistantRepliesToAttachmentsWithoutCallingTheApi() throws Exception {
        try (TestClient alice = TestClient.join(name("alice"))) {
            alice.send(Message.attachment(MessageType.IMAGE, alice.name, Config.AI_BOT_NAME, "pic.png", new byte[]{1}));
            Message reply = alice.await(m -> m.getType() == MessageType.TEXT && Config.AI_BOT_NAME.equals(m.getSender()));
            assertEquals(alice.name, reply.getRecipient());
            assertTrue(reply.getText().contains("only read text"), reply.getText());
        }
    }

    @Test
    void leavingUpdatesEveryoneElse() throws Exception {
        try (TestClient bob = TestClient.join(name("bob"))) {
            TestClient carol = TestClient.join(name("carol"));
            bob.await(m -> m.getType() == MessageType.USER_LIST && m.getUsers().contains(carol.name));
            carol.close();
            // Both must arrive (the server sends the new list, then the notice); order doesn't matter here.
            boolean listUpdated = false;
            boolean noticeShown = false;
            while (!listUpdated || !noticeShown) {
                Message m = bob.await(msg -> msg.getType() == MessageType.USER_LIST || msg.getType() == MessageType.SYSTEM);
                if (m.getType() == MessageType.USER_LIST) {
                    listUpdated |= !m.getUsers().contains(carol.name);
                } else {
                    noticeShown |= m.getText().contains(carol.name + " left");
                }
            }
        }
    }

    /**
     * The concurrency test: many clients talk at the same time from their own threads.
     * Every message must be delivered exactly once to everyone else, and each sender's messages
     * must arrive in the order they were sent (each client has one writer queue on the server).
     */
    @Test
    void manyClientsTalkingAtOnce() throws Exception {
        int clients = 8;
        int perClient = 25;
        List<TestClient> all = new ArrayList<>();
        try {
            for (int i = 0; i < clients; i++) {
                all.add(TestClient.join(name("user" + i)));
            }
            String last = all.get(clients - 1).name;
            for (TestClient c : all) { // everyone knows everyone before the burst starts
                c.await(m -> m.getType() == MessageType.USER_LIST && m.getUsers().contains(last));
            }

            CountDownLatch go = new CountDownLatch(1);
            List<Thread> senders = new ArrayList<>();
            for (TestClient c : all) {
                Thread t = new Thread(() -> {
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    for (int n = 0; n < perClient; n++) {
                        c.send(Message.text(c.name, null, String.valueOf(n)));
                    }
                }, "sender-" + c.name);
                senders.add(t);
                t.start();
            }
            go.countDown(); // all threads start sending together

            for (TestClient c : all) {
                Map<String, Integer> nextExpected = new HashMap<>();
                int expected = (clients - 1) * perClient;
                for (int i = 0; i < expected; i++) {
                    Message m = c.await(msg -> msg.getType() == MessageType.TEXT);
                    int number = Integer.parseInt(m.getText());
                    int wanted = nextExpected.getOrDefault(m.getSender(), 0);
                    assertEquals(wanted, number, "messages from " + m.getSender() + " out of order at " + c.name);
                    nextExpected.put(m.getSender(), wanted + 1);
                }
                assertEquals(clients - 1, nextExpected.size());
                c.assertNothing(m -> m.getType() == MessageType.TEXT, 200); // no duplicates
            }
            for (Thread t : senders) {
                t.join();
            }
        } finally {
            for (TestClient c : all) {
                c.close();
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Unique names, so tests never collide with each other. */
    private static String name(String base) {
        return base + NEXT_ID.incrementAndGet();
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        new Random(size).nextBytes(data);
        return data;
    }

    /** A connected client that collects everything it receives. */
    private static final class TestClient implements MessageListener, AutoCloseable {
        final String name;
        final ChatClient client;
        final LinkedBlockingQueue<Message> inbox = new LinkedBlockingQueue<>();

        private TestClient(ChatClient client) {
            this.client = client;
            this.name = client.getUsername();
        }

        static TestClient join(String name) throws IOException {
            TestClient c = new TestClient(ChatClient.connect("localhost", port, name));
            c.client.startListening(c);
            return c;
        }

        void send(Message message) {
            client.send(message);
        }

        /** The next received message matching the condition (others are skipped). */
        Message await(Predicate<Message> condition) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (true) {
                long left = deadline - System.nanoTime();
                Message m = left > 0 ? inbox.poll(left, TimeUnit.NANOSECONDS) : null;
                if (m == null) {
                    throw new AssertionError(name + " did not receive the expected message within "
                            + TIMEOUT_SECONDS + " s");
                }
                if (condition.test(m)) {
                    return m;
                }
            }
        }

        void assertNothing(Predicate<Message> condition, long millis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + millis;
            while (System.currentTimeMillis() < deadline) {
                Message m = inbox.poll(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                if (m != null && condition.test(m)) {
                    throw new AssertionError(name + " received an unexpected " + m.getType()
                            + " from " + m.getSender() + ": " + m.getText());
                }
            }
        }

        @Override
        public void onMessage(Message message) {
            inbox.add(message);
        }

        @Override
        public void onDisconnected(String reason) {
        }

        @Override
        public void close() {
            client.disconnect();
        }
    }
}
