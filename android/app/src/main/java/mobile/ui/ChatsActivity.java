package mobile.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import chatfx.mobile.R;
import mobile.ChatSession;
import mobile.Notifications;
import mobile.model.Conversation;

/**
 * Home screen: the chat list (desktop Sidebar). General and the AI Assistant are pinned on top,
 * then private chats, most recent first. Tapping a row (or a notification) opens the chat.
 */
public class ChatsActivity extends AppCompatActivity implements ChatSession.Listener {

    private ChatSession session;
    private MaterialToolbar toolbar;
    private ConversationAdapter adapter;

    /** Android 13+: "Allow ChatFX to send you notifications?" (asked once). */
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, "Without notifications you won't see new messages while ChatFX is closed.",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = ChatSession.current();
        if (session == null) {
            backToLogin();
            return;
        }
        setContentView(R.layout.activity_chats);
        toolbar = findViewById(R.id.toolbar);
        Ui.edgeToEdge(this, findViewById(R.id.root), toolbar);
        toolbar.inflateMenu(R.menu.chats);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                session.logout(); // -> onSessionEnded
                return true;
            }
            return false;
        });

        adapter = new ConversationAdapter(session.username(), c -> ChatActivity.open(this, c.getId()));
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        session.addListener(this);
        askForNotifications();
        refresh();
        openFromNotification(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (session != null) {
            refresh(); // unread counts may have grown while we were in the background
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openFromNotification(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (session != null) {
            session.removeListener(this);
        }
    }

    // ---------------------------------------------------------------- session events

    @Override
    public void onConversationsChanged() {
        refresh();
    }

    @Override
    public void onSessionEnded(String reason) {
        if (reason != null) {
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
        }
        session = null;
        backToLogin();
    }

    // ---------------------------------------------------------------- helpers

    private void refresh() {
        toolbar.setSubtitle(session.username() + " · " + session.onlineCount() + " online");
        adapter.submit(session.conversations());
    }

    /** A message notification was tapped: go straight into that chat (the list stays below it). */
    private void openFromNotification(Intent intent) {
        String id = intent.getStringExtra(Notifications.EXTRA_CONVERSATION);
        if (id != null && session != null) {
            intent.removeExtra(Notifications.EXTRA_CONVERSATION); // not again on rotation
            Conversation c = session.conversation(id);
            if (c != null) {
                ChatActivity.open(this, c.getId());
            }
        }
    }

    private void askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void backToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
