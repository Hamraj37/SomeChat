package com.samechat37.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.samechat37.R;
import com.samechat37.models.Message;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PinnedMessagesAdapter extends RecyclerView.Adapter<PinnedMessagesAdapter.PinnedViewHolder> {

    private final Context context;
    private final List<Message> pinnedMessages;
    private final String currentUserId;
    private final String otherUserName;
    private final OnPinnedMessageClickListener listener;

    public interface OnPinnedMessageClickListener {
        void onPinnedMessageClick(Message message);
    }

    public PinnedMessagesAdapter(Context context, List<Message> pinnedMessages, String currentUserId, String otherUserName, OnPinnedMessageClickListener listener) {
        this.context = context;
        this.pinnedMessages = pinnedMessages;
        this.currentUserId = currentUserId;
        this.otherUserName = otherUserName;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PinnedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_pinned_message_profile, parent, false);
        return new PinnedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PinnedViewHolder holder, int position) {
        Message message = pinnedMessages.get(position);
        
        boolean isMe = message.getSenderId().equals(currentUserId);
        holder.senderName.setText(isMe ? "You" : otherUserName);
        
        String displayText;
        if (message.getType() == null || "text".equals(message.getType())) {
            // Text or unknown type - already decrypted in ProfileInfoActivity
            displayText = message.getText();
        } else {
            String type = message.getType();
            displayText = type.substring(0, 1).toUpperCase() + type.substring(1);
        }
        holder.messageText.setText(displayText);
        
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        holder.timestamp.setText(sdf.format(new Date(message.getTimestamp())));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPinnedMessageClick(message);
            }
        });
    }

    @Override
    public int getItemCount() {
        return pinnedMessages.size();
    }

    static class PinnedViewHolder extends RecyclerView.ViewHolder {
        TextView senderName;
        TextView messageText;
        TextView timestamp;

        public PinnedViewHolder(@NonNull View itemView) {
            super(itemView);
            senderName = itemView.findViewById(R.id.pinned_sender_name);
            messageText = itemView.findViewById(R.id.pinned_message_text);
            timestamp = itemView.findViewById(R.id.pinned_timestamp);
        }
    }
}
