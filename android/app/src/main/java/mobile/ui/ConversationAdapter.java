package mobile.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import chatfx.mobile.R;
import client.util.Formats;
import common.Message;
import mobile.model.Conversation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Rows of the chat list: avatar, name, time of the last message, preview and unread badge.
 */
class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.Holder> {

    interface OnOpen {
        void open(Conversation conversation);
    }

    private final String me;
    private final OnOpen onOpen;
    private List<Conversation> items = new ArrayList<>();

    ConversationAdapter(String me, OnOpen onOpen) {
        this.me = me;
        this.onOpen = onOpen;
    }

    /** The list is short (one row per person online), so a full refresh is simple and cheap. */
    void submit(List<Conversation> conversations) {
        items = conversations;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Conversation c = items.get(position);
        h.itemView.setOnClickListener(v -> onOpen.open(c));
        h.avatar.set(c.getTitle(), c.isAi(), c.getKind() == Conversation.Kind.PRIVATE && c.isOnline());
        h.title.setText(c.getTitle());

        Message last = c.getLastMessage();
        h.time.setText(last == null ? "" : when(last));

        boolean typing = c.isTyping();
        h.preview.setText(typing ? "typing…" : preview(c, last));
        h.preview.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                typing ? R.color.tg_primary : R.color.tg_secondary_text));

        h.badge.setVisibility(c.getUnread() > 0 ? View.VISIBLE : View.GONE);
        h.badge.setText(c.getUnread() > 99 ? "99+" : String.valueOf(c.getUnread()));
    }

    private String preview(Conversation c, Message last) {
        if (last == null) {
            return c.isGroup() ? "Group chat with everyone online"
                    : c.isAi() ? "Ask me anything 🤖"
                    : c.isOnline() ? "online" : "offline";
        }
        return Formats.preview(last, me, c.isGroup());
    }

    /** Today: "14:05"; before: "Yesterday", "November 21"... */
    private static String when(Message message) {
        LocalDate day = message.getTimestamp().toLocalDate();
        return day.equals(LocalDate.now()) ? Formats.time(message.getTimestamp()) : Formats.day(day);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final AvatarView avatar;
        final TextView title;
        final TextView time;
        final TextView preview;
        final TextView badge;

        Holder(View view) {
            super(view);
            avatar = view.findViewById(R.id.avatar);
            title = view.findViewById(R.id.title);
            time = view.findViewById(R.id.time);
            preview = view.findViewById(R.id.preview);
            badge = view.findViewById(R.id.badge);
        }
    }
}
