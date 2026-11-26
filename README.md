# ChatFX

A Telegram-style chat in Java: one multithreaded server, a JavaFX desktop client and an Android
client. Everyone talks to the same server with the same protocol, so desktop and phone users chat
together: group chat, private chats, an AI assistant, emoji, photos, files, audio files and voice
messages.

```
prj2/
├─ common/    protocol (Message, MessageType, Config) + network client (ChatClient) + helpers
│             shared by all three apps; compiled for Java 17 so Android can use it
├─ server/    ChatServer, one ClientHandler per client, the AI assistant (Gemini)
├─ desktop/   JavaFX client (UI built in Java code, no FXML)
├─ android/   Android client (Gradle; compiles common/ directly)
└─ ai.properties.example   AI key template for the server
```

## Requirements

| Part | Tools |
|---|---|
| Server, desktop | JDK 25, Maven (IntelliJ IDEA works out of the box) |
| Android | Android Studio (SDK, emulator and Gradle included) |

## 1. Start the server

IntelliJ: run the **Server** configuration. Command line:

```
mvn install
mvn -pl server exec:java
```

It listens on port **5050**. (Not 5555: the Android emulator uses that port for itself.)

**AI assistant (optional):** copy `ai.properties.example` to `ai.properties` in the project root and
paste a free Gemini key from https://aistudio.google.com/apikey. `ai.properties` is git-ignored.

## 2. Desktop clients

IntelliJ: run the **Client** configuration, once per user ("Allow multiple instances" is on).
Command line: `mvn -pl desktop javafx:run`. Host `localhost`, port `5050`.

## 3. Android client

1. Android Studio → **Open** → the `android` folder. Wait for the Gradle sync to finish.
2. Pick the emulator or a phone and press **Run**.
3. Log in:
   - **Emulator:** host `10.0.2.2` (filled in automatically: that's the PC as seen from the emulator).
   - **Real phone:** the PC's Wi-Fi address (`ipconfig` → "IPv4 Address", e.g. `192.168.1.20`),
     phone and PC on the same Wi-Fi.

The app asks for these permissions when they're first needed:

| Permission | Why |
|---|---|
| Local network (Android 17+) | reach the server on your PC; without it the connection silently times out |
| Notifications | new messages while the app is in the background |
| Microphone | voice messages |

**Real phone:** allow the port in Windows Firewall once (PowerShell as administrator):

```powershell
New-NetFirewallRule -DisplayName "ChatFX server" -Direction Inbound -Protocol TCP -LocalPort 5050 -Action Allow -Profile Private
```

**Emulator tips:**
- Smooth graphics: Device Manager → Edit → Graphics: **Hardware**, then **Cold Boot**.
- Microphone: emulator ⋯ → Microphone → "Virtual microphone uses host audio input".

## Tests

```
mvn test
```

`server/src/test/java/server/ChatServerTest.java` starts a real server on a free port and connects
real clients: user list, name rules, group and private routing, attachments byte for byte, emoji,
spoofed senders, size limit, and 8 clients sending at once (exactly-once delivery, per-sender order).
It never calls the AI service, so it costs no API quota.

## Where to start reading the code

Every class is commented for beginners. Read them in the order a message travels:

1. `common/.../Message.java`: the one object that travels over the network
2. `common/.../ChatClient.java`: how a client connects, sends and receives
3. `server/.../ClientHandler.java`: the two threads the server runs per client
4. `server/.../ChatServer.java`: who receives which message (`route()`)
5. `desktop/.../ui/ChatView.java`: the desktop's brain
6. `android/.../mobile/ChatSession.java`: the same brain on Android

## How the concurrency works

**Server**
- One reader thread per client (cached thread pool).
- One writer thread per client draining its own queue, so a slow client never blocks the others.
- Online users are stored in a `ConcurrentHashMap`.
- The AI assistant runs on its own small thread pool, with a per-user lock so each conversation stays in order.

**Clients**
- A listener thread reads from the socket.
- A single sender thread writes, so sending a 25 MB file never freezes the screen.
- UI updates go back to the UI thread: `Platform.runLater` on the desktop, the main `Handler` on Android.

**Android**
- A foreground service keeps the connection open while the app is in the background.
- Photos are decoded, and picked files read, on background threads.
