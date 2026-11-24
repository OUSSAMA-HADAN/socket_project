package mobile;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

/**
 * Keeps ChatFX connected while it's in the background.
 * <p>
 * Without it, Android may kill the app's process soon after the user leaves it, closing the
 * socket. A <i>foreground service</i> tells Android "this app is doing something the user knows
 * about" (shown as an ongoing notification), so the process, the {@link ChatSession} and its
 * network threads stay alive and messages keep arriving.
 * <p>
 * The service doesn't own the connection: it follows the session, and stops when the session ends.
 */
public class ChatService extends Service implements ChatSession.Listener {

    private static final String ACTION_LOGOUT = "chatfx.mobile.action.LOGOUT";

    private ChatSession session;
    private int shownOnlineCount = -1;

    /** Called by the login screen right after connecting (the app is in the foreground then). */
    public static void start(Context context) {
        ContextCompat.startForegroundService(context, new Intent(context, ChatService.class));
    }

    static Intent logoutIntent(Context context) {
        return new Intent(context, ChatService.class).setAction(ACTION_LOGOUT);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ChatSession current = ChatSession.current();
        if (intent != null && ACTION_LOGOUT.equals(intent.getAction())) {
            if (current != null) {
                current.logout(); // ends the session; onSessionEnded below stops the service
            } else {
                stopSelf();
            }
            return START_NOT_STICKY;
        }

        // Must be called promptly after startForegroundService(), even if we stop right after.
        ServiceCompat.startForeground(this, Notifications.ID_CONNECTION,
                Notifications.connection(this, current),
                Build.VERSION.SDK_INT >= 34 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING : 0);

        if (current == null) {
            stop();
        } else if (current != session) {
            if (session != null) {
                session.removeListener(this);
            }
            session = current;
            session.addListener(this);
            shownOnlineCount = session.onlineCount();
        }
        // Not sticky: if Android kills us anyway, the socket is gone; the user logs in again.
        return START_NOT_STICKY;
    }

    @Override
    public void onConversationsChanged() {
        if (session != null && session.onlineCount() != shownOnlineCount) {
            shownOnlineCount = session.onlineCount();
            try {
                NotificationManagerCompat.from(this)
                        .notify(Notifications.ID_CONNECTION, Notifications.connection(this, session));
            } catch (SecurityException ignored) {
                // notifications not allowed: the service keeps running anyway
            }
        }
    }

    @Override
    public void onSessionEnded(String reason) {
        session = null;
        if (reason != null) {
            Notifications.connectionLost(this, reason);
        }
        stop();
    }

    private void stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            session.removeListener(this);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // started service only
    }
}
