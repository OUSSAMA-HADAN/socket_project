package mobile;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.content.ContextCompat;

import chatfx.mobile.R;
import client.util.Formats;
import common.Message;
import mobile.model.Conversation;
import mobile.ui.ChatsActivity;
import mobile.ui.LoginActivity;

import java.time.ZoneId;
import java.util.List;

/**
 * Everything the app shows in the notification shade:
 * <ul>
 *   <li>the silent, ongoing "Connected as …" notification of {@link ChatService};</li>
 *   <li>one notification per chat for messages received while the app is in the background;</li>
 *   <li>a "Disconnected" notification when the server goes away.</li>
 * </ul>
 */
public final class Notifications {

    public static final String EXTRA_CONVERSATION = "conversation";
    static final int ID_CONNECTION = 1;
    private static final int ID_DISCONNECTED = 2;
    private static final String CHANNEL_MESSAGES = "messages";
    private static final String CHANNEL_CONNECTION = "connection";
    private static final String GROUP_MESSAGES = "chatfx.messages";
    /** How many of a chat's unread messages the notification lists. */
    private static final int MAX_LINES = 6;

    private Notifications() {
    }

    public static void createChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel messages = new NotificationChannel(CHANNEL_MESSAGES, "Messages",
                NotificationManager.IMPORTANCE_HIGH);
        messages.setDescription("New messages while ChatFX is in the background");
        NotificationChannel connection = new NotificationChannel(CHANNEL_CONNECTION, "Connection",
                NotificationManager.IMPORTANCE_LOW); // silent, no pop-up
        connection.setDescription("Shows that ChatFX stays connected to the server");
        connection.setShowBadge(false);
        manager.createNotificationChannel(messages);
        manager.createNotificationChannel(connection);
    }

    /** The foreground service's notification: who I am, how many people are online, a Log out button. */
    static Notification connection(Context context, ChatSession session) {
        String text = session == null ? "Not connected"
                : "Connected as " + session.username() + " · " + session.onlineCount() + " online";
        PendingIntent logout = PendingIntent.getService(context, 1, ChatService.logoutIntent(context),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(context, CHANNEL_CONNECTION)
                .setSmallIcon(R.drawable.ic_stat_chat)
                .setColor(ContextCompat.getColor(context, R.color.tg_primary))
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(openApp(context, null))
                .addAction(0, "Log out", logout)
                .setOngoing(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    /** A chat's unread messages, Telegram style: sender names, newest last. */
    static void showMessage(Context context, ChatSession session, Conversation conversation) {
        if (!canPost(context) || conversation.getUnread() == 0) {
            return;
        }
        Person me = new Person.Builder().setName("You").build();
        NotificationCompat.MessagingStyle style = new NotificationCompat.MessagingStyle(me);
        if (conversation.isGroup()) {
            style.setConversationTitle(conversation.getTitle());
            style.setGroupConversation(true);
        }
        List<Message> messages = conversation.getMessages();
        int lines = Math.min(Math.min(conversation.getUnread(), MAX_LINES), messages.size());
        for (Message m : messages.subList(messages.size() - lines, messages.size())) {
            if (!m.getType().isChat()) {
                continue; // "x joined the chat"
            }
            Person sender = new Person.Builder().setName(m.getSender()).setKey(m.getSender()).build();
            String body = Formats.preview(m, session.username(), false); // the name is shown by the style
            long when = m.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            style.addMessage(body, when, sender);
        }

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_stat_chat)
                .setColor(ContextCompat.getColor(context, R.color.tg_primary))
                .setStyle(style)
                .setNumber(conversation.getUnread())
                .setContentIntent(openApp(context, conversation))
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setGroup(GROUP_MESSAGES)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        post(context, messageId(conversation), notification);
    }

    static void connectionLost(Context context, String reason) {
        if (!canPost(context)) {
            return;
        }
        Intent login = new Intent(context, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_stat_chat)
                .setColor(ContextCompat.getColor(context, R.color.tg_error))
                .setContentTitle("Disconnected from the server")
                .setContentText(reason + " Tap to log in again.")
                .setContentIntent(PendingIntent.getActivity(context, 2, login,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .setAutoCancel(true)
                .build();
        post(context, ID_DISCONNECTED, notification);
    }

    public static void cancelDisconnected(Context context) {
        NotificationManagerCompat.from(context).cancel(ID_DISCONNECTED);
    }

    static void cancel(Context context, Conversation conversation) {
        NotificationManagerCompat.from(context).cancel(messageId(conversation));
    }

    /** When the session ends: the chats are gone, so are their notifications (the service's too). */
    static void cancelAllMessages(Context context) {
        NotificationManagerCompat.from(context).cancelAll();
    }

    /** Tapping a notification opens the chat list (step 4: straight into that chat). */
    private static PendingIntent openApp(Context context, Conversation conversation) {
        Intent intent = new Intent(context, ChatsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (conversation != null) {
            intent.putExtra(EXTRA_CONVERSATION, conversation.getId());
        }
        int requestCode = conversation == null ? 0 : messageId(conversation);
        return PendingIntent.getActivity(context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Stable id per chat, away from the fixed ids above. */
    private static int messageId(Conversation conversation) {
        return 1000 + (conversation.getId().hashCode() & 0xFFFFF);
    }

    /** Android 13+ asks the user before an app may show notifications. */
    private static boolean canPost(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    private static void post(Context context, int id, Notification notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification);
        } catch (SecurityException e) {
            // permission revoked between the check and now: nothing to show
        }
    }
}
