package mobile.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.emoji2.emojipicker.EmojiPickerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import chatfx.mobile.R;
import client.util.FileKinds;
import client.util.Formats;
import common.Message;
import common.MessageType;
import mobile.ChatSession;
import mobile.media.Attachments;
import mobile.media.VoiceRecorder;
import mobile.model.Conversation;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One conversation (desktop ChatPane + InputBar): header, messages, and the input bar with
 * emoji panel, attachments (photos, audio, files) and voice messages.
 * <p>
 * <b>How it fits together</b>
 * <ul>
 *   <li>Messages are shown by a RecyclerView with {@link MessageAdapter}.</li>
 *   <li>New messages arrive through ChatSession.Listener.onMessage (already on the main thread).</li>
 *   <li>Sending goes through ChatSession (sendText / sendAttachment), which uses the shared ChatClient.</li>
 *   <li>System pickers (photos, files) and permission questions use "ActivityResultLauncher":
 *       you register them once, launch them later, and a callback receives the answer.</li>
 * </ul>
 */
public class ChatActivity extends AppCompatActivity implements ChatSession.Listener, MessageAdapter.Actions {

    private static final String EXTRA_ID = "conversation";
    /** Shorter recordings are treated as an accidental tap (same as the desktop). */
    private static final long MIN_VOICE_MILLIS = 700;
    private static final int MAX_PICKED_PHOTOS = 10;

    private ChatSession session;
    private Conversation conversation;
    private MessageAdapter adapter;
    private RecyclerView messages;
    private TextView subtitle;
    private EditText input;
    private ImageButton action;
    private ImageButton emojiButton;
    private View compose;
    private View recordBar;
    private TextView recordTime;
    private EmojiPickerView emojiPicker;
    private SaveAs saveAs;

    private final VoiceRecorder recorder = new VoiceRecorder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Reading picked files can take a moment (25 MB): never on the main thread. */
    private final ExecutorService reader = Executors.newSingleThreadExecutor(r -> new Thread(r, "attachment-reader"));
    private final Runnable recordTick = new Runnable() {
        @Override
        public void run() {
            recordTime.setText(Formats.duration(recorder.elapsedMillis() / 1000));
            handler.postDelayed(this, 250);
        }
    };

    // System pickers. None of them needs a storage permission.
    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhotos = registerForActivityResult(
            new ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_PHOTOS), this::sendPhotos);
    private final ActivityResultLauncher<String[]> pickAudio = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(), uris -> sendFiles(uris, MessageType.AUDIO));
    private final ActivityResultLauncher<String[]> pickFiles = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(), uris -> sendFiles(uris, MessageType.FILE));
    private final ActivityResultLauncher<String> micPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startRecording();
                } else {
                    Toast.makeText(this, "ChatFX needs the microphone to record voice messages.",
                            Toast.LENGTH_LONG).show();
                }
            });

    /** Opens the chat screen for a conversation. putExtra attaches the conversation's id to the request. */
    public static void open(Context context, String conversationId) {
        context.startActivity(new Intent(context, ChatActivity.class).putExtra(EXTRA_ID, conversationId));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        saveAs = new SaveAs(this); // registers its result handler: must happen in onCreate
        session = ChatSession.current();
        conversation = session == null ? null : session.conversation(getIntent().getStringExtra(EXTRA_ID));
        if (conversation == null) {
            finish(); // logged out meanwhile: the chat list sends us back to login
            return;
        }
        setContentView(R.layout.activity_chat);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        Ui.edgeToEdge(this, findViewById(R.id.root), toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ((AvatarView) findViewById(R.id.headerAvatar)).set(conversation.getTitle(), conversation.isAi(), false);
        ((TextView) findViewById(R.id.headerTitle)).setText(conversation.getTitle());
        subtitle = findViewById(R.id.headerSubtitle);

        adapter = new MessageAdapter(this, session.username(), conversation.isGroup(), this);
        adapter.setMessages(conversation.getMessages());
        messages = findViewById(R.id.messages);
        LinearLayoutManager layout = new LinearLayoutManager(this);
        layout.setStackFromEnd(true); // start at the newest message, like every chat app
        messages.setLayoutManager(layout);
        messages.setAdapter(adapter);
        scrollToBottom();
        // When the keyboard or the emoji panel opens the list gets shorter: keep the last message in sight.
        messages.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b < ob) {
                scrollToBottom();
            }
        });

        setUpInputBar();
        updateHeader();
        session.addListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (session != null) {
            session.setVisible(conversation); // marks it read, clears its notification
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (recorder.isRecording()) {
            cancelRecording(); // a call or the Home button shouldn't send half a sentence
        }
        if (session != null) {
            session.leave(conversation);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        reader.shutdownNow();
        if (adapter != null) {
            adapter.release();
        }
        if (session != null) {
            session.removeListener(this);
        }
    }

    // ---------------------------------------------------------------- input bar

    private void setUpInputBar() {
        input = findViewById(R.id.input);
        action = findViewById(R.id.action);
        emojiButton = findViewById(R.id.emojiButton);
        compose = findViewById(R.id.compose);
        recordBar = findViewById(R.id.recordBar);
        recordTime = findViewById(R.id.recordTime);
        emojiPicker = findViewById(R.id.emojiPicker);

        action.setOnClickListener(v -> onAction());
        findViewById(R.id.recordCancel).setOnClickListener(v -> cancelRecording());
        findViewById(R.id.attachButton).setOnClickListener(this::showAttachMenu);
        emojiButton.setOnClickListener(v -> toggleEmojiPanel());
        emojiPicker.setOnEmojiPickedListener(item -> {
            int start = Math.max(input.getSelectionStart(), 0);
            int end = Math.max(input.getSelectionEnd(), 0);
            input.getText().replace(Math.min(start, end), Math.max(start, end), item.getEmoji());
        });
        input.setOnClickListener(v -> showEmojiPanel(false)); // tapping the text brings the keyboard back
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateActionButton();
            }
        });
        // Back closes the emoji panel first, then the chat.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (emojiPicker.getVisibility() == View.VISIBLE) {
                    showEmojiPanel(false);
                } else {
                    finish();
                }
            }
        });
        updateActionButton();
    }

    /** The round button: send text, or start/finish a voice message (same logic as the desktop). */
    private void onAction() {
        if (recorder.isRecording()) {
            finishRecording();
        } else if (!input.getText().toString().trim().isEmpty()) {
            session.sendText(conversation, input.getText().toString().trim());
            input.setText("");
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO); // then starts recording
        }
    }

    private void updateActionButton() {
        boolean send = recorder.isRecording() || !input.getText().toString().trim().isEmpty();
        action.setImageResource(send ? R.drawable.ic_send : R.drawable.ic_mic);
        action.setContentDescription(getString(send ? R.string.send : R.string.recording));
    }

    private void toggleEmojiPanel() {
        showEmojiPanel(emojiPicker.getVisibility() != View.VISIBLE);
    }

    /** The emoji panel takes the keyboard's place, like in Telegram. */
    private void showEmojiPanel(boolean show) {
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (show) {
            keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            emojiPicker.setVisibility(View.VISIBLE);
            emojiButton.setImageResource(R.drawable.ic_keyboard);
        } else if (emojiPicker.getVisibility() == View.VISIBLE) {
            emojiPicker.setVisibility(View.GONE);
            emojiButton.setImageResource(R.drawable.ic_emoji);
            input.requestFocus();
            keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void showAttachMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.attach);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.attach_photo) {
                pickPhotos.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
            } else if (id == R.id.attach_audio) {
                pickAudio.launch(new String[]{"audio/*"});
            } else {
                pickFiles.launch(new String[]{"*/*"});
            }
            return true;
        });
        menu.show();
    }

    // ---------------------------------------------------------------- sending attachments

    /**
     * Photos picked in the gallery. A Uri is Android's address of a file (often not a normal path).
     * Each photo is read on the "attachment-reader" thread, then sent from the main thread.
     */
    private void sendPhotos(List<Uri> uris) {
        for (Uri uri : uris) {
            reader.execute(() -> {
                try {
                    Attachments.Picked photo = Attachments.readPhoto(getContentResolver(), uri);
                    if (photo == null) { // not a picture after all: send it as a document
                        Attachments.Picked file = Attachments.read(getContentResolver(), uri);
                        post(() -> session.sendAttachment(conversation, MessageType.FILE, file.name, file.data));
                    } else {
                        post(() -> session.sendAttachment(conversation, MessageType.IMAGE, photo.name, photo.data));
                    }
                } catch (IOException e) {
                    post(() -> toast(e.getMessage()));
                }
            });
        }
    }

    /** @param requested AUDIO from the "Audio" picker, FILE from the "File" picker (as on the desktop) */
    private void sendFiles(List<Uri> uris, MessageType requested) {
        for (Uri uri : uris) {
            reader.execute(() -> {
                try {
                    Attachments.Picked file = Attachments.read(getContentResolver(), uri);
                    // Only formats the desktop can play are sent as audio; anything else as a document.
                    MessageType type = requested == MessageType.AUDIO
                            && FileKinds.detect(file.name) != MessageType.AUDIO ? MessageType.FILE : requested;
                    post(() -> session.sendAttachment(conversation, type, file.name, file.data));
                } catch (IOException e) {
                    post(() -> toast(e.getMessage()));
                }
            });
        }
    }

    /** Back to the main thread, unless the screen closed or we logged out meanwhile. */
    private void post(Runnable action) {
        handler.post(() -> {
            if (session != null && !isDestroyed()) {
                action.run();
            }
        });
    }

    // ---------------------------------------------------------------- voice messages

    /** Starts the microphone and switches the input bar to "recording" mode (blinking dot + timer). */
    private void startRecording() {
        showEmojiPanel(false);
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            recorder.start();
        } catch (IOException | SecurityException e) {
            toast("Microphone unavailable: " + e.getMessage());
            return;
        }
        compose.setVisibility(View.GONE);
        recordBar.setVisibility(View.VISIBLE);
        View dot = findViewById(R.id.recordDot);
        AlphaAnimation blink = new AlphaAnimation(1f, 0.2f);
        blink.setDuration(600);
        blink.setRepeatMode(Animation.REVERSE);
        blink.setRepeatCount(Animation.INFINITE);
        dot.startAnimation(blink);
        handler.post(recordTick);
        updateActionButton();
    }

    /** Second tap on the round button: stop, check the recording is long and loud enough, send it. */
    private void finishRecording() {
        long length = recorder.elapsedMillis();
        byte[] wav = recorder.stop();
        resetRecordBar();
        if (length < MIN_VOICE_MILLIS || wav.length <= 44) {
            toast("Tap the mic, speak, then tap send.");
            return;
        }
        if (recorder.peak() < VoiceRecorder.SILENCE_PEAK) {
            // Don't send an "empty" voice note: the microphone delivered only silence.
            toast("The microphone heard nothing, so the voice message wasn't sent. "
                    + "On the emulator: ⋯ (Extended controls) → Microphone → turn on "
                    + "\"Virtual microphone uses host audio input\".");
            return;
        }
        String name = "voice-" + System.currentTimeMillis() + ".wav"; // same naming as the desktop
        session.sendAttachment(conversation, MessageType.VOICE, name, wav);
    }

    private void cancelRecording() {
        recorder.cancel();
        resetRecordBar();
    }

    private void resetRecordBar() {
        handler.removeCallbacks(recordTick);
        findViewById(R.id.recordDot).clearAnimation();
        recordBar.setVisibility(View.GONE);
        compose.setVisibility(View.VISIBLE);
        updateActionButton();
    }

    // ---------------------------------------------------------------- attachment actions (from the bubbles)

    @Override
    public void openImage(Message message) {
        ImageViewerActivity.open(this, conversation.getId(), message.getId());
    }

    /** Hands the file to another app (PDF reader, music player...). */
    @Override
    public void openFile(Message message) {
        try {
            File file = Attachments.cacheFile(this, message);
            Uri uri = FileProvider.getUriForFile(this, "chatfx.mobile.files", file);
            Intent view = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, Attachments.mimeType(message.getFileName()))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(view, message.getFileName()));
        } catch (ActivityNotFoundException e) {
            toast("No app can open this file. Use the save button instead.");
        } catch (IOException | IllegalArgumentException e) {
            toast("Cannot open the file: " + e.getMessage());
        }
    }

    @Override
    public void save(Message message) {
        saveAs.save(message);
    }

    // ---------------------------------------------------------------- session events

    /** A message was added somewhere; if it's in this chat, show it (and scroll down if we were at the bottom). */
    @Override
    public void onMessage(Conversation c, Message message) {
        if (c != conversation) {
            return; // a message for another chat: the chat list shows it as unread
        }
        boolean wasAtBottom = !messages.canScrollVertically(1);
        adapter.append(message);
        if (wasAtBottom || session.username().equals(message.getSender())) {
            scrollToBottom();
        }
    }

    @Override
    public void onConversationsChanged() {
        updateHeader();
    }

    @Override
    public void onServerError(String text) {
        toast(text);
    }

    @Override
    public void onSessionEnded(String reason) {
        session = null;
        finish(); // the chat list below us shows the reason and goes back to login
    }

    // ---------------------------------------------------------------- helpers

    /** Same subtitles as the desktop header. */
    private void updateHeader() {
        if (conversation.isGroup()) {
            int n = session.onlineCount();
            subtitle.setText(n + (n == 1 ? " member online" : " members online"));
        } else if (conversation.isTyping()) {
            subtitle.setText("typing…");
        } else if (conversation.isAi()) {
            subtitle.setText("AI assistant · always online");
        } else {
            subtitle.setText(conversation.isOnline() ? "online" : "offline");
        }
    }

    private void scrollToBottom() {
        if (adapter.getItemCount() > 0) {
            messages.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
