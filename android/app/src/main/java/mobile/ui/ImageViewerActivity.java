package mobile.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import chatfx.mobile.R;
import common.Message;
import mobile.ChatSession;
import mobile.media.Attachments;
import mobile.model.Conversation;

/**
 * A received or sent photo, full screen (desktop ImageViewer), with a save button.
 */
public class ImageViewerActivity extends AppCompatActivity {

    private static final String EXTRA_CONVERSATION = "conversation";
    private static final String EXTRA_MESSAGE = "message";

    public static void open(Context context, String conversationId, String messageId) {
        context.startActivity(new Intent(context, ImageViewerActivity.class)
                .putExtra(EXTRA_CONVERSATION, conversationId)
                .putExtra(EXTRA_MESSAGE, messageId));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SaveAs saveAs = new SaveAs(this);
        Message message = find(getIntent().getStringExtra(EXTRA_CONVERSATION), getIntent().getStringExtra(EXTRA_MESSAGE));
        if (message == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_image);
        EdgeToEdge.enable(this);
        View root = findViewById(R.id.root);
        View bar = findViewById(R.id.bar);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            bar.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        ((TextView) findViewById(R.id.title)).setText(message.getSender() + " · " + message.getFileName());
        findViewById(R.id.back).setOnClickListener(v -> finish());
        findViewById(R.id.save).setOnClickListener(v -> saveAs.save(message));
        ImageView image = findViewById(R.id.image);
        image.setOnClickListener(v -> bar.setVisibility(bar.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        // Decode at screen size in the background (a 12-megapixel photo would not fit in memory as is).
        DisplayMetrics screen = getResources().getDisplayMetrics();
        new Thread(() -> {
            Bitmap bitmap = Attachments.decode(message.getData(), screen.widthPixels, screen.heightPixels);
            runOnUiThread(() -> {
                if (!isDestroyed() && bitmap != null) {
                    image.setImageBitmap(bitmap);
                }
            });
        }, "image-viewer").start();
    }

    private static Message find(String conversationId, String messageId) {
        ChatSession session = ChatSession.current();
        Conversation c = session == null ? null : session.conversation(conversationId);
        if (c == null) {
            return null;
        }
        for (Message m : c.getMessages()) {
            if (m.getId().equals(messageId)) {
                return m;
            }
        }
        return null;
    }
}
