package mobile.media;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import common.Message;
import common.MessageType;

import java.io.IOException;

/**
 * Plays one voice note or audio file at a time (starting another one stops the first),
 * like the desktop's AudioMessageView. Main thread only; MediaPlayer decodes on its own threads.
 */
public final class AudioPlayer {

    /** Told about play/pause and progress (every 200 ms while playing). */
    public interface Listener {
        void onAudioChanged(String messageId);
    }

    private static final long TICK_MILLIS = 200;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private MediaPlayer player;
    private String currentId;
    private boolean prepared;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                listener.onAudioChanged(currentId);
                handler.postDelayed(this, TICK_MILLIS);
            }
        }
    };

    public AudioPlayer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    /** Play, or pause/resume if this message is already the current one. */
    public void toggle(Message message) {
        if (message.getId().equals(currentId) && player != null) {
            if (!prepared) {
                return;
            }
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.start();
                handler.post(tick);
            }
            listener.onAudioChanged(currentId);
            return;
        }
        release();
        currentId = message.getId();
        try {
            MediaPlayer p = new MediaPlayer();
            p.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(message.getType() == MessageType.VOICE
                            ? AudioAttributes.CONTENT_TYPE_SPEECH : AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            p.setDataSource(Attachments.cacheFile(context, message).getPath());
            p.setOnPreparedListener(mp -> {
                prepared = true;
                mp.start();
                handler.post(tick);
                listener.onAudioChanged(currentId);
            });
            p.setOnCompletionListener(mp -> stopCurrent());
            p.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(context, "This audio format can't be played here. Save it and open it with another app.",
                        Toast.LENGTH_LONG).show();
                stopCurrent();
                return true;
            });
            player = p;
            p.prepareAsync();
            listener.onAudioChanged(currentId);
        } catch (IOException e) {
            Toast.makeText(context, "Cannot play: " + e.getMessage(), Toast.LENGTH_LONG).show();
            stopCurrent();
        }
    }

    public boolean isCurrent(String messageId) {
        return messageId.equals(currentId);
    }

    public boolean isPlaying(String messageId) {
        return isCurrent(messageId) && player != null && prepared && player.isPlaying();
    }

    public int positionMillis() {
        return player != null && prepared ? player.getCurrentPosition() : 0;
    }

    public int durationMillis() {
        return player != null && prepared ? player.getDuration() : 0;
    }

    /** @param fraction 0..1 of the track */
    public void seek(String messageId, float fraction) {
        if (isCurrent(messageId) && player != null && prepared) {
            player.seekTo((int) (player.getDuration() * fraction));
            listener.onAudioChanged(currentId);
        }
    }

    /** Stops playback and frees the decoder (call when the chat screen goes away). */
    public void release() {
        handler.removeCallbacks(tick);
        if (player != null) {
            player.release();
            player = null;
        }
        prepared = false;
        currentId = null;
    }

    private void stopCurrent() {
        String id = currentId;
        release();
        if (id != null) {
            listener.onAudioChanged(id);
        }
    }
}
