package mobile.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import chatfx.mobile.R;
import client.util.Formats;
import common.Message;
import common.MessageType;
import mobile.media.Attachments;
import mobile.media.AudioPlayer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The messages of one chat (desktop MessageBubble): text, big emoji, photos, files, audio files
 * and voice notes, plus centered pills for date separators and "x joined the chat".
 * <p>
 * Photos are decoded on a background thread, at the size they're shown, and kept in a memory cache,
 * so scrolling stays smooth.
 * <p>
 * Two row types: PILL (item_pill.xml) and BUBBLE (item_message.xml). One bubble layout holds every
 * possible part (sender name, photo, attachment row, text, time); {@code onBindViewHolder} shows
 * only the parts this message needs and hides the others. Because rows are recycled, every part
 * must be reset each time (a row that showed a photo may now show a text).
 */
class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    /** What a tap on an attachment does (implemented by ChatActivity). */
    interface Actions {
        void openImage(Message message);

        void openFile(Message message);

        void save(Message message);
    }

    private static final int PILL = 0;
    private static final int BUBBLE = 1;
    /** Partial update: only the play button and progress of an audio message. */
    private static final Object AUDIO_PROGRESS = new Object();
    private static final float MAX_BUBBLE_WIDTH = 0.78f;
    private static final int MAX_PHOTO_HEIGHT_DP = 320;

    private static final class Row {
        final String pill;
        final Message message;

        Row(String pill, Message message) {
            this.pill = pill;
            this.message = message;
        }
    }

    private final Context context;
    private final String me;
    private final boolean group;
    private final Actions actions;
    private final AudioPlayer player;
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Integer> positions = new HashMap<>();
    private LocalDate lastDay;

    private final ExecutorService decoder = Executors.newSingleThreadExecutor(r -> new Thread(r, "image-decoder"));
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    /** Decoded photos by message id; sized to an eighth of the app's memory. */
    private final LruCache<String, Bitmap> bitmaps = new LruCache<String, Bitmap>(
            (int) (Runtime.getRuntime().maxMemory() / 8)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    MessageAdapter(Context context, String me, boolean group, Actions actions) {
        this.context = context;
        this.me = me;
        this.group = group;
        this.actions = actions;
        this.player = new AudioPlayer(context, this::onAudioChanged);
    }

    // ---------------------------------------------------------------- content

    void setMessages(List<Message> messages) {
        rows.clear();
        positions.clear();
        lastDay = null;
        for (Message m : messages) {
            addRows(m);
        }
        notifyDataSetChanged();
    }

    void append(Message message) {
        int before = rows.size();
        addRows(message);
        notifyItemRangeInserted(before, rows.size() - before);
    }

    private void addRows(Message m) {
        LocalDate day = m.getTimestamp().toLocalDate();
        if (!day.equals(lastDay)) {
            rows.add(new Row(Formats.day(day), null));
            lastDay = day;
        }
        if (m.getType() == MessageType.SYSTEM) {
            rows.add(new Row(m.getText(), null));
        } else {
            positions.put(m.getId(), rows.size());
            rows.add(new Row(null, m));
        }
    }

    /** Stop sounds and background work when the chat screen closes. */
    void release() {
        player.release();
        decoder.shutdownNow();
    }

    private void onAudioChanged(String messageId) {
        Integer position = positions.get(messageId);
        if (position != null) {
            notifyItemChanged(position, AUDIO_PROGRESS);
        }
    }

    // ---------------------------------------------------------------- RecyclerView

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).message == null ? PILL : BUBBLE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == PILL) {
            return new PillHolder(inflater.inflate(R.layout.item_pill, parent, false));
        }
        BubbleHolder h = new BubbleHolder(inflater.inflate(R.layout.item_message, parent, false));
        int maxWidth = maxBubbleContentWidth();
        h.body.setMaxWidth(maxWidth);
        h.sender.setMaxWidth(maxWidth);
        return h;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(AUDIO_PROGRESS) && holder instanceof BubbleHolder) {
            bindAudioState((BubbleHolder) holder, rows.get(position).message);
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof PillHolder) {
            ((PillHolder) holder).pill.setText(row.pill);
            return;
        }
        BubbleHolder h = (BubbleHolder) holder;
        Message m = row.message;
        boolean mine = me.equals(m.getSender());
        h.message = m;

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) h.bubble.getLayoutParams();
        params.gravity = mine ? Gravity.END : Gravity.START;
        h.bubble.setLayoutParams(params);

        boolean showSender = group && !mine;
        h.sender.setVisibility(showSender ? View.VISIBLE : View.GONE);
        if (showSender) {
            h.sender.setText(m.getSender());
            h.sender.setTextColor(AvatarView.colorFor(m.getSender()));
        }

        // reset everything a recycled view may still show
        h.photo.setVisibility(View.GONE);
        h.attachment.setVisibility(View.GONE);
        h.body.setVisibility(View.GONE);
        h.bubble.setOnClickListener(null);
        h.bubble.setOnLongClickListener(null);

        int jumbo = m.getType() == MessageType.TEXT ? EmojiUtil.emojiOnlyCount(m.getText()) : 0;
        boolean big = jumbo >= 1 && jumbo <= 3;
        styleBubble(h, mine, big);

        switch (m.getType()) {
            case TEXT:
                bindText(h, m.getText(), big ? (jumbo == 1 ? 64 : 44) : 16);
                break;
            case IMAGE:
                bindPhoto(h, m);
                break;
            case AUDIO:
            case VOICE:
                bindAudio(h, m, mine);
                break;
            default:
                bindFile(h, m, mine);
        }
        h.meta.setText(Formats.time(m.getTimestamp()));
    }

    private void styleBubble(BubbleHolder h, boolean mine, boolean bigEmoji) {
        if (bigEmoji) { // no bubble, time on a small dark pill (desktop .jumbo-bubble)
            h.bubble.setBackground(null);
            h.bubble.setElevation(0);
            h.meta.setBackgroundResource(R.drawable.bg_meta_pill);
            h.meta.setTextColor(context.getColor(R.color.white));
            int p = Ui.dp(context, 6);
            h.meta.setPadding(p, 1, p, 1);
        } else {
            h.bubble.setBackgroundResource(mine ? R.drawable.bg_bubble_out : R.drawable.bg_bubble_in);
            h.bubble.setElevation(Ui.dp(context, 1));
            h.meta.setBackground(null);
            h.meta.setPadding(0, 0, 0, 0);
            h.meta.setTextColor(context.getColor(mine ? R.color.tg_out_accent : R.color.tg_secondary_text));
        }
    }

    private void bindText(BubbleHolder h, String text, int sizeSp) {
        h.body.setVisibility(View.VISIBLE);
        h.body.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        h.body.setText(text);
        h.bubble.setOnLongClickListener(v -> copy(text));
        h.body.setOnLongClickListener(v -> copy(text));
    }

    // ---------------------------------------------------------------- photos

    private void bindPhoto(BubbleHolder h, Message m) {
        int[] size = Attachments.imageSize(m.getData());
        if (size[0] <= 0 || size[1] <= 0) { // damaged picture: show it as a file instead
            bindFile(h, m, me.equals(m.getSender()));
            return;
        }
        // Reserve the final size now, so the list doesn't jump when the picture appears.
        int maxW = maxBubbleContentWidth();
        int maxH = Ui.dp(context, MAX_PHOTO_HEIGHT_DP);
        float scale = Math.min(Math.min((float) maxW / size[0], (float) maxH / size[1]), 3f);
        int width = Math.max(Ui.dp(context, 80), Math.round(size[0] * scale));
        int height = Math.max(Ui.dp(context, 60), Math.round(size[1] * scale));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) h.photo.getLayoutParams();
        lp.width = Math.min(width, maxW);
        lp.height = Math.min(height, maxH);
        h.photo.setLayoutParams(lp);
        h.photo.setVisibility(View.VISIBLE);
        h.photo.setOnClickListener(v -> actions.openImage(m));
        h.photo.setOnLongClickListener(v -> {
            actions.save(m);
            return true;
        });

        Bitmap cached = bitmaps.get(m.getId());
        h.photo.setImageBitmap(cached);
        if (cached == null) {
            String id = m.getId();
            int targetW = lp.width;
            int targetH = lp.height;
            decoder.execute(() -> {
                Bitmap bitmap = Attachments.decode(m.getData(), targetW, targetH);
                if (bitmap == null) {
                    return;
                }
                mainThread.post(() -> {
                    bitmaps.put(id, bitmap);
                    if (h.message != null && id.equals(h.message.getId())) { // view not reused meanwhile
                        h.photo.setImageBitmap(bitmap);
                    }
                });
            });
        }
    }

    // ---------------------------------------------------------------- files and audio

    private void bindFile(BubbleHolder h, Message m, boolean mine) {
        showAttachmentRow(h, mine, R.drawable.ic_file);
        h.attachmentTitle.setText(m.getFileName());
        h.attachmentInfo.setText(Formats.size(m.getSize()));
        h.attachmentSeek.setVisibility(View.GONE);
        h.attachmentButton.setOnClickListener(v -> actions.openFile(m));
        h.attachment.setOnClickListener(v -> actions.openFile(m));
        h.saveButton.setOnClickListener(v -> actions.save(m));
    }

    private void bindAudio(BubbleHolder h, Message m, boolean mine) {
        showAttachmentRow(h, mine, R.drawable.ic_play);
        h.attachmentTitle.setText(m.getType() == MessageType.VOICE ? "Voice message" : m.getFileName());
        h.attachmentSeek.setVisibility(View.VISIBLE);
        h.attachmentSeek.setProgressTintList(ColorStateList.valueOf(accent(mine)));
        h.attachmentSeek.setThumbTintList(ColorStateList.valueOf(accent(mine)));
        h.attachmentButton.setOnClickListener(v -> player.toggle(m));
        h.attachment.setOnClickListener(null);
        h.saveButton.setOnClickListener(v -> actions.save(m));
        h.attachmentSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                player.seek(m.getId(), seekBar.getProgress() / (float) seekBar.getMax());
            }
        });
        bindAudioState(h, m);
    }

    /** Play/pause icon, progress and "0:03 / 0:12" (also called alone, ~5 times a second). */
    private void bindAudioState(BubbleHolder h, Message m) {
        if (m == null || (m.getType() != MessageType.AUDIO && m.getType() != MessageType.VOICE)) {
            return;
        }
        boolean current = player.isCurrent(m.getId());
        h.attachmentButton.setImageResource(player.isPlaying(m.getId()) ? R.drawable.ic_pause : R.drawable.ic_play);
        int duration = current ? player.durationMillis() : 0;
        int position = current ? player.positionMillis() : 0;
        h.attachmentSeek.setProgress(duration > 0 ? (int) (1000L * position / duration) : 0);
        if (current && duration > 0) {
            h.attachmentInfo.setText(Formats.duration(position / 1000) + " / " + Formats.duration(duration / 1000));
        } else {
            long wav = Attachments.wavDurationMillis(m.getData());
            h.attachmentInfo.setText(wav >= 0 ? Formats.duration(wav / 1000) : Formats.size(m.getSize()));
        }
    }

    private void showAttachmentRow(BubbleHolder h, boolean mine, int icon) {
        h.attachment.setVisibility(View.VISIBLE);
        h.attachmentButton.setImageResource(icon);
        h.attachmentButton.setBackgroundTintList(ColorStateList.valueOf(accent(mine)));
        h.attachmentTitle.setTextColor(context.getColor(mine ? R.color.tg_out_accent : R.color.tg_text));
    }

    /** Blue in other people's bubbles, green in mine (desktop .bubble-out .audio-button). */
    private int accent(boolean mine) {
        return context.getColor(mine ? R.color.tg_out_accent : R.color.tg_primary);
    }

    private int maxBubbleContentWidth() {
        return (int) (context.getResources().getDisplayMetrics().widthPixels * MAX_BUBBLE_WIDTH)
                - Ui.dp(context, 21);
    }

    private boolean copy(String text) {
        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText("message", text));
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show();
        return true;
    }

    // ---------------------------------------------------------------- holders

    static class PillHolder extends RecyclerView.ViewHolder {
        final TextView pill;

        PillHolder(View view) {
            super(view);
            pill = view.findViewById(R.id.pill);
        }
    }

    static class BubbleHolder extends RecyclerView.ViewHolder {
        final View bubble;
        final TextView sender;
        final ImageView photo;
        final View attachment;
        final ImageButton attachmentButton;
        final TextView attachmentTitle;
        final SeekBar attachmentSeek;
        final TextView attachmentInfo;
        final ImageButton saveButton;
        final TextView body;
        final TextView meta;
        /** The message currently shown (views are recycled while photos decode). */
        Message message;

        BubbleHolder(View view) {
            super(view);
            bubble = view.findViewById(R.id.bubble);
            sender = view.findViewById(R.id.sender);
            photo = view.findViewById(R.id.photo);
            attachment = view.findViewById(R.id.attachment);
            attachmentButton = view.findViewById(R.id.attachmentButton);
            attachmentTitle = view.findViewById(R.id.attachmentTitle);
            attachmentSeek = view.findViewById(R.id.attachmentSeek);
            attachmentInfo = view.findViewById(R.id.attachmentInfo);
            saveButton = view.findViewById(R.id.saveButton);
            body = view.findViewById(R.id.body);
            meta = view.findViewById(R.id.meta);
        }
    }
}
