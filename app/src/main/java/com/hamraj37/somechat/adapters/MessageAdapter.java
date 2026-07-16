package com.hamraj37.somechat.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.hamraj37.somechat.R;
import com.hamraj37.somechat.models.Message;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.noties.markwon.Markwon;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    private static final int VIEW_TYPE_VOICE_SENT = 3;
    private static final int VIEW_TYPE_VOICE_RECEIVED = 4;
    private static final int VIEW_TYPE_IMAGE_SENT = 5;
    private static final int VIEW_TYPE_IMAGE_RECEIVED = 6;
    private static final int VIEW_TYPE_VIDEO_SENT = 7;
    private static final int VIEW_TYPE_VIDEO_RECEIVED = 8;
    private static final int VIEW_TYPE_SYSTEM = 9;

    private List<Message> messageList;
    private java.util.Set<String> selectedMessageIds = new java.util.HashSet<>();
    private boolean isSelectionMode = false;
    private String currentUserId;
    private android.media.MediaPlayer mediaPlayer;
    private int playingPosition = -1;
    private android.os.Handler progressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.Map<String, Integer> uploadProgressMap = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> downloadProgressMap = new java.util.HashMap<>();
    private final java.util.Map<String, com.hamraj37.somechat.models.LinkMetadata> linkMetadataCache = new java.util.HashMap<>();
    private android.content.Context context;
    private OnMessageClickListener listener;
    private boolean isGroup = false;
    private boolean showHeader = true;
    private String highlightMessageId = null;

    private int themeSentColor = -1;
    private int themeReceivedColor = -1;
    private int themeSentTextColor = -1;
    private int themeReceivedTextColor = -1;

    private Markwon markwon;

    public interface OnMessageClickListener {
        void onReplyClick(String messageId);
        void onMessageLongClick(Message message, View view);
        void onMessageClick(Message message);
        void onSelectionChanged(int count);
        void onReactionClick(Message message, String emoji);
    }

    public MessageAdapter(List<Message> messageList, OnMessageClickListener listener) {
        this.messageList = messageList;
        this.listener = listener;
        updateCurrentUserId();
    }

    public MessageAdapter(List<Message> messageList, boolean isGroup, OnMessageClickListener listener) {
        this.messageList = messageList;
        this.isGroup = isGroup;
        this.listener = listener;
        updateCurrentUserId();
    }

    public void setShowHeader(boolean showHeader) {
        this.showHeader = showHeader;
    }

    private int getAdapterPosition(int listIndex) {
        return listIndex + (showHeader ? 1 : 0);
    }

    private int getListIndex(int adapterPosition) {
        return adapterPosition - (showHeader ? 1 : 0);
    }

    private void updateCurrentUserId() {
        if (currentUserId == null) {
            currentUserId = FirebaseAuth.getInstance().getUid();
        }
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.context = recyclerView.getContext();
        this.markwon = Markwon.create(context);
        updateCurrentUserId();
        loadThemeColors();
    }

    private void loadThemeColors() {
        if (context == null) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
        themeSentColor = prefs.getInt("theme_sent_color", -1);
        themeReceivedColor = prefs.getInt("theme_received_color", -1);
        themeSentTextColor = prefs.getInt("theme_sent_text_color", -1);
        themeReceivedTextColor = prefs.getInt("theme_received_text_color", -1);
    }

    public void updateUploadProgress(String messageId, int progress) {
        uploadProgressMap.put(messageId, progress);
        for (int i = 0; i < messageList.size(); i++) {
            if (messageId.equals(messageList.get(i).getMessageId())) {
                notifyItemChanged(getAdapterPosition(i));
                break;
            }
        }
    }

    public void removeUploadProgress(String messageId) {
        uploadProgressMap.remove(messageId);
        for (int i = 0; i < messageList.size(); i++) {
            if (messageId.equals(messageList.get(i).getMessageId())) {
                notifyItemChanged(getAdapterPosition(i));
                break;
            }
        }
    }

    public void updateDownloadProgress(String messageId, int progress) {
        downloadProgressMap.put(messageId, progress);
        for (int i = 0; i < messageList.size(); i++) {
            if (messageId.equals(messageList.get(i).getMessageId())) {
                notifyItemChanged(getAdapterPosition(i));
                break;
            }
        }
    }

    public void removeDownloadProgress(String messageId) {
        downloadProgressMap.remove(messageId);
        for (int i = 0; i < messageList.size(); i++) {
            if (messageId.equals(messageList.get(i).getMessageId())) {
                notifyItemChanged(getAdapterPosition(i));
                break;
            }
        }
    }

    public void highlightMessage(String messageId) {
        this.highlightMessageId = messageId;
        for (int i = 0; i < messageList.size(); i++) {
            if (messageList.get(i).getMessageId().equals(messageId)) {
                final int pos = getAdapterPosition(i);
                notifyItemChanged(pos);
                
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    this.highlightMessageId = null;
                    notifyItemChanged(pos);
                }, 1000);
                break;
            }
        }
    }

    public void toggleSelection(String messageId) {
        if (selectedMessageIds.contains(messageId)) {
            selectedMessageIds.remove(messageId);
        } else {
            selectedMessageIds.add(messageId);
        }
        
        if (selectedMessageIds.isEmpty()) {
            isSelectionMode = false;
        } else {
            isSelectionMode = true;
        }

        for (int i = 0; i < messageList.size(); i++) {
            if (messageId.equals(messageList.get(i).getMessageId())) {
                notifyItemChanged(getAdapterPosition(i));
                break;
            }
        }
        
        if (listener != null) {
            listener.onSelectionChanged(selectedMessageIds.size());
        }
    }

    public void clearSelection() {
        java.util.Set<String> previousSelected = new java.util.HashSet<>(selectedMessageIds);
        selectedMessageIds.clear();
        isSelectionMode = false;
        for (int i = 0; i < messageList.size(); i++) {
            if (previousSelected.contains(messageList.get(i).getMessageId())) {
                notifyItemChanged(getAdapterPosition(i));
            }
        }
        if (listener != null) {
            listener.onSelectionChanged(0);
        }
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public java.util.Set<String> getSelectedMessageIds() {
        return selectedMessageIds;
    }

    public List<Message> getSelectedMessages() {
        List<Message> selected = new java.util.ArrayList<>();
        for (Message m : messageList) {
            if (selectedMessageIds.contains(m.getMessageId())) {
                selected.add(m);
            }
        }
        return selected;
    }

    @Override
    public int getItemViewType(int position) {
        if (showHeader && position == 0) return VIEW_TYPE_HEADER;

        int messagePos = showHeader ? position - 1 : position;
        Message message = messageList.get(messagePos);
        if (message == null) return VIEW_TYPE_RECEIVED;
        
        String senderId = message.getSenderId();
        boolean isSent = senderId != null && senderId.equals(currentUserId);

        String type = message.getType();
        if (type == null) type = "text";

        if ("system".equals(type)) return VIEW_TYPE_SYSTEM;

        if (isSent) {
            switch (type) {
                case "voice": return VIEW_TYPE_VOICE_SENT;
                case "image": return VIEW_TYPE_IMAGE_SENT;
                case "video": return VIEW_TYPE_VIDEO_SENT;
                default: return VIEW_TYPE_SENT;
            }
        } else {
            switch (type) {
                case "voice": return VIEW_TYPE_VOICE_RECEIVED;
                case "image": return VIEW_TYPE_IMAGE_RECEIVED;
                case "video": return VIEW_TYPE_VIDEO_RECEIVED;
                default: return VIEW_TYPE_RECEIVED;
            }
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_HEADER:
                return new HeaderViewHolder(inflater.inflate(R.layout.layout_encryption_header, parent, false));
            case VIEW_TYPE_SENT:
                return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false));
            case VIEW_TYPE_RECEIVED:
                return new ReceivedMessageViewHolder(inflater.inflate(R.layout.item_message_received, parent, false));
            case VIEW_TYPE_VOICE_SENT:
                return new VoiceSentViewHolder(inflater.inflate(R.layout.item_voice_sent, parent, false));
            case VIEW_TYPE_VOICE_RECEIVED:
                return new VoiceReceivedViewHolder(inflater.inflate(R.layout.item_voice_received, parent, false));
            case VIEW_TYPE_IMAGE_SENT:
                return new ImageSentViewHolder(inflater.inflate(R.layout.item_image_sent, parent, false));
            case VIEW_TYPE_IMAGE_RECEIVED:
                return new ImageReceivedViewHolder(inflater.inflate(R.layout.item_image_received, parent, false));
            case VIEW_TYPE_VIDEO_SENT:
                return new VideoSentViewHolder(inflater.inflate(R.layout.item_video_sent, parent, false));
            case VIEW_TYPE_VIDEO_RECEIVED:
                return new VideoReceivedViewHolder(inflater.inflate(R.layout.item_video_received, parent, false));
            case VIEW_TYPE_SYSTEM:
                return new SystemMessageViewHolder(inflater.inflate(R.layout.item_message_system, parent, false));
            default:
                return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) return;

        int messagePos = showHeader ? position - 1 : position;
        Message message = messageList.get(messagePos);
        if (message == null) return;

        boolean isSender = message.getSenderId().equals(currentUserId);
        
        // Decrypt message data if needed
        String decryptedText = com.hamraj37.somechat.utils.EncryptionManager.decrypt(message.getText(), holder.itemView.getContext(), isSender);
        String decryptedMediaUrl = com.hamraj37.somechat.utils.EncryptionManager.decrypt(message.getMediaUrl(), holder.itemView.getContext(), isSender);

        Message displayMessage = new Message(
                message.getMessageId(),
                message.getSenderId(),
                message.getReceiverId(),
                decryptedText,
                message.getTimestamp(),
                message.getType(),
                decryptedMediaUrl,
                message.getDuration()
        );
        displayMessage.setSenderName(message.getSenderName());
        displayMessage.setSenderUsername(message.getSenderUsername());
        displayMessage.setSeen(message.isSeen());
        displayMessage.setForwarded(message.isForwarded());
        displayMessage.setReplyToId(message.getReplyToId());
        displayMessage.setReplyToName(message.getReplyToName());
        displayMessage.setReplyToText(message.getReplyToText());
        displayMessage.setPinned(message.isPinned());
        displayMessage.setReactions(message.getReactions());
        displayMessage.setEdited(message.isEdited());

        boolean isHighlighted = message.getMessageId() != null && message.getMessageId().equals(highlightMessageId);
        boolean isSelected = selectedMessageIds.contains(message.getMessageId());
        
        holder.itemView.setAlpha(isHighlighted ? 0.5f : 1.0f);
        holder.itemView.setBackgroundColor(isSelected ? 0x20000000 : android.graphics.Color.TRANSPARENT);
        
        View bubble = null;
        if (holder.itemView instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) holder.itemView;
            for (int i=0; i<vg.getChildCount(); i++) {
                if (vg.getChildAt(i) instanceof com.google.android.material.card.MaterialCardView) {
                    bubble = vg.getChildAt(i);
                    break;
                }
            }
        }
        
        if (bubble != null) {
            bubble.setActivated(isHighlighted);
        }

        applyBubbleTheme(holder.itemView, isSender);

        if (holder instanceof SentMessageViewHolder) {
            ((SentMessageViewHolder) holder).bind(displayMessage);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ((ReceivedMessageViewHolder) holder).bind(displayMessage);
        } else if (holder instanceof VoiceSentViewHolder) {
            ((VoiceSentViewHolder) holder).bind(displayMessage, position);
        } else if (holder instanceof VoiceReceivedViewHolder) {
            ((VoiceReceivedViewHolder) holder).bind(displayMessage, position);
        } else if (holder instanceof ImageSentViewHolder) {
            ((ImageSentViewHolder) holder).bind(displayMessage);
        } else if (holder instanceof ImageReceivedViewHolder) {
            ((ImageReceivedViewHolder) holder).bind(displayMessage);
        } else if (holder instanceof VideoSentViewHolder) {
            ((VideoSentViewHolder) holder).bind(displayMessage);
        } else if (holder instanceof VideoReceivedViewHolder) {
            ((VideoReceivedViewHolder) holder).bind(displayMessage);
        } else if (holder instanceof SystemMessageViewHolder) {
            ((SystemMessageViewHolder) holder).bind(displayMessage);
        }

        bindReactions(holder.itemView, displayMessage);
    }

    private void applyBubbleTheme(View itemView, boolean isSent) {
        int bubbleColor = isSent ? themeSentColor : themeReceivedColor;
        int textColor = isSent ? themeSentTextColor : themeReceivedTextColor;
        
        if (bubbleColor == -1) return;

        com.google.android.material.card.MaterialCardView bubble = null;
        if (itemView instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) itemView;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (vg.getChildAt(i) instanceof com.google.android.material.card.MaterialCardView) {
                    bubble = (com.google.android.material.card.MaterialCardView) vg.getChildAt(i);
                    break;
                }
            }
        }

        if (bubble != null) {
            bubble.setCardBackgroundColor(bubbleColor);
            
            if (textColor != -1) {
                // Apply text color to main message text
                TextView textMsg = bubble.findViewById(R.id.text_message);
                if (textMsg != null) textMsg.setTextColor(textColor);

                // Apply to other elements (icons, etc)
                android.widget.ImageView playBtn = bubble.findViewById(R.id.btn_play_pause);
                if (playBtn != null) playBtn.setColorFilter(textColor);

                TextView duration = bubble.findViewById(R.id.text_duration);
                if (duration != null) duration.setTextColor(textColor);
                
                TextView replyN = bubble.findViewById(R.id.reply_name);
                if (replyN != null) replyN.setTextColor(textColor);
                
                TextView replyT = bubble.findViewById(R.id.reply_text);
                if (replyT != null) replyT.setTextColor(textColor);
            }
        }
    }

    private void bindReactions(View itemView, Message message) {
        View reactionsContainer = itemView.findViewById(R.id.reactions_container);
        if (reactionsContainer == null) return;

        TextView reactionsText = itemView.findViewById(R.id.reactions_text);
        if (message.getReactions() != null && !message.getReactions().isEmpty()) {
            reactionsContainer.setVisibility(View.VISIBLE);
            
            java.util.Map<String, Integer> emojiCounts = new java.util.HashMap<>();
            for (String emoji : message.getReactions().values()) {
                if (emoji == null) continue;
                Integer count = emojiCounts.get(emoji);
                emojiCounts.put(emoji, (count == null ? 0 : count) + 1);
            }

            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> entry : emojiCounts.entrySet()) {
                sb.append(entry.getKey());
                if (entry.getValue() > 1) {
                    sb.append(" ").append(entry.getValue());
                }
                sb.append(" ");
            }
            reactionsText.setText(sb.toString().trim());
        } else {
            reactionsContainer.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size() + (showHeader ? 1 : 0);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        stopPlayback();
    }

    class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage;

        public SystemMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.text_message);
        }

        void bind(Message message) {
            textMessage.setText(message.getText());
        }
    }

    private void renderDynamicContent(LinearLayout container, TextView fallbackTv, String text, boolean isSent) {
        container.removeAllViews();
        if (text == null || text.isEmpty()) return;

        // Pattern to match code blocks with optional language identifier
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("```(\\w*)\\n?([\\s\\S]*?)```");
        java.util.regex.Matcher matcher = pattern.matcher(text);

        int lastEnd = 0;
        boolean foundCode = false;

        while (matcher.find()) {
            foundCode = true;
            String beforeText = text.substring(lastEnd, matcher.start()).trim();
            if (!beforeText.isEmpty()) {
                addTextPart(container, beforeText, isSent);
            }

            String language = matcher.group(1).trim();
            String codeContent = matcher.group(2).trim();
            
            if (language.isEmpty()) language = "code";
            
            addCodePart(container, codeContent, language);

            lastEnd = matcher.end();
        }

        String afterText = text.substring(lastEnd).trim();
        if (!afterText.isEmpty()) {
            addTextPart(container, afterText, isSent);
        }

        if (!foundCode) {
            fallbackTv.setVisibility(View.VISIBLE);
            container.setVisibility(View.GONE);
            if (markwon != null) markwon.setMarkdown(fallbackTv, text);
            else fallbackTv.setText(text);
        } else {
            fallbackTv.setVisibility(View.GONE);
            container.setVisibility(View.VISIBLE);
        }
    }

    private void addTextPart(LinearLayout container, String text, boolean isSent) {
        TextView tv = new TextView(container.getContext());
        tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int p12 = (int) (12 * container.getContext().getResources().getDisplayMetrics().density);
        int p4 = (int) (4 * container.getContext().getResources().getDisplayMetrics().density);
        tv.setPadding(p12, p4, p12, p4);
        
        int colorAttr = isSent ? com.google.android.material.R.attr.colorOnPrimary : com.google.android.material.R.attr.colorOnSurfaceVariant;
        tv.setTextColor(com.google.android.material.color.MaterialColors.getColor(container.getContext(), colorAttr, 0));
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16); 
        
        if (markwon != null) markwon.setMarkdown(tv, text);
        else tv.setText(text);
        container.addView(tv);
    }

    private void addCodePart(LinearLayout container, String code, String language) {
        View codeView = LayoutInflater.from(container.getContext()).inflate(R.layout.layout_code_block, container, false);
        TextView langTv = codeView.findViewById(R.id.code_language);
        TextView codeTv = codeView.findViewById(R.id.code_text);
        View copyBtn = codeView.findViewById(R.id.btn_copy_code);

        langTv.setText(language.toLowerCase());
        codeTv.setText(code);
        copyBtn.setOnClickListener(v -> copyToClipboard(code));
        container.addView(codeView);
    }

    private int pixel(int dp, View view) {
        return (int) (dp * view.getContext().getResources().getDisplayMetrics().density);
    }

    private void copyToClipboard(String text) {
        if (context == null) return;
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard != null && text != null) {
            android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Code", text);
            clipboard.setPrimaryClip(clip);
            android.widget.Toast.makeText(context, context.getString(R.string.code_copied), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    class SentMessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout contentContainer;
        TextView textMessage;
        TextView textTimestamp;
        android.widget.ImageView imageStatus;
        android.widget.ImageView imagePin;
        TextView textEdited;
        View forwardIndicator;
        View linkPreviewCard;
        android.widget.ImageView linkImage;
        TextView linkTitle;
        TextView linkDescription;
        TextView linkUrl;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            contentContainer = itemView.findViewById(R.id.message_content_container);
            textMessage = itemView.findViewById(R.id.text_message);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            imageStatus = itemView.findViewById(R.id.image_status);
            imagePin = itemView.findViewById(R.id.image_pin);
            textEdited = itemView.findViewById(R.id.text_edited);
            forwardIndicator = itemView.findViewById(R.id.forward_indicator);
            linkPreviewCard = itemView.findViewById(R.id.link_preview);
            linkImage = itemView.findViewById(R.id.link_image);
            linkTitle = itemView.findViewById(R.id.link_title);
            linkDescription = itemView.findViewById(R.id.link_description);
            linkUrl = itemView.findViewById(R.id.link_url);
        }

        void bind(Message message) {
            if (contentContainer != null) {
                renderDynamicContent(contentContainer, textMessage, message.getText(), true);
                View btnCopy = itemView.findViewById(R.id.btn_copy_text);
                if (btnCopy != null) btnCopy.setVisibility(View.GONE);
            } else {
                textMessage.setVisibility(View.VISIBLE);
                if (markwon != null) {
                    markwon.setMarkdown(textMessage, message.getText());
                } else {
                    textMessage.setText(message.getText());
                }
            }
            textTimestamp.setText(formatDate(message.getTimestamp()));
            bindReply(itemView, message);
            bindLinkPreview(message, linkPreviewCard, linkImage, linkTitle, linkDescription, linkUrl);
            
            if (textEdited != null) {
                textEdited.setVisibility(message.isEdited() ? View.VISIBLE : View.GONE);
            }

            if (imagePin != null) {
                imagePin.setVisibility(message.isPinned() ? View.VISIBLE : View.GONE);
            }

            if (forwardIndicator != null) {
                forwardIndicator.setVisibility(message.isForwarded() ? View.VISIBLE : View.GONE);
            }

            if (message.isSeen()) {
                int color = com.google.android.material.color.MaterialColors.getColor(itemView, androidx.appcompat.R.attr.colorPrimary);
                imageStatus.setColorFilter(color);
            } else {
                imageStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), android.R.color.darker_gray));
            }

            itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(message.getMessageId());
                } else if (listener != null) {
                    listener.onMessageClick(message);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongClick(message, v);
                }
                return true;
            });
        }
    }

    class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        View senderInfoContainer;
        TextView senderName;
        TextView senderUsername;
        LinearLayout contentContainer;
        TextView textMessage;
        View btnCopy;
        TextView textTimestamp;
        android.widget.ImageView imagePin;
        TextView textEdited;
        View forwardIndicator;
        View linkPreviewCard;
        android.widget.ImageView linkImage;
        TextView linkTitle;
        TextView linkDescription;
        TextView linkUrl;

        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            senderInfoContainer = itemView.findViewById(R.id.sender_info_container);
            senderName = itemView.findViewById(R.id.sender_name);
            senderUsername = itemView.findViewById(R.id.sender_username);
            contentContainer = itemView.findViewById(R.id.message_content_container);
            textMessage = itemView.findViewById(R.id.text_message);
            btnCopy = itemView.findViewById(R.id.btn_copy_text);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            imagePin = itemView.findViewById(R.id.image_pin);
            textEdited = itemView.findViewById(R.id.text_edited);
            forwardIndicator = itemView.findViewById(R.id.forward_indicator);
            linkPreviewCard = itemView.findViewById(R.id.link_preview);
            linkImage = itemView.findViewById(R.id.link_image);
            linkTitle = itemView.findViewById(R.id.link_title);
            linkDescription = itemView.findViewById(R.id.link_description);
            linkUrl = itemView.findViewById(R.id.link_url);
        }

        void bind(Message message) {
            if (senderInfoContainer != null) {
                if (isGroup && message.getSenderName() != null) {
                    senderInfoContainer.setVisibility(View.VISIBLE);
                    senderName.setText(message.getSenderName());
                    if (senderUsername != null && message.getSenderUsername() != null) {
                        senderUsername.setText("@" + message.getSenderUsername());
                        senderUsername.setVisibility(View.VISIBLE);
                    } else if (senderUsername != null) {
                        senderUsername.setVisibility(View.GONE);
                    }
                } else {
                    senderInfoContainer.setVisibility(View.GONE);
                }
            }
            
            if (contentContainer != null) {
                renderDynamicContent(contentContainer, textMessage, message.getText(), false);
                if (btnCopy != null) btnCopy.setVisibility(View.GONE);
            } else {
                textMessage.setVisibility(View.VISIBLE);
                if (markwon != null) {
                    markwon.setMarkdown(textMessage, message.getText());
                } else {
                    textMessage.setText(message.getText());
                }
                
                if (btnCopy != null) {
                    String msgText = message.getText();
                    boolean hasCode = msgText != null && msgText.contains("```");
                    btnCopy.setVisibility(hasCode ? View.VISIBLE : View.GONE);
                    
                    btnCopy.setOnClickListener(v -> {
                        String textToCopy = msgText;
                        if (textToCopy != null && textToCopy.contains("```")) {
                            try {
                                int start = textToCopy.indexOf("```") + 3;
                                int firstNewline = textToCopy.indexOf("\n", start);
                                int nextBackticks = textToCopy.indexOf("```", start);
                                if (firstNewline != -1 && (nextBackticks == -1 || firstNewline < nextBackticks)) {
                                    String possibleLang = textToCopy.substring(start, firstNewline).trim();
                                    if (!possibleLang.contains(" ") && possibleLang.length() < 15) {
                                        start = firstNewline + 1;
                                    }
                                }
                                int end = textToCopy.indexOf("```", start);
                                if (end != -1) {
                                    textToCopy = textToCopy.substring(start, end).trim();
                                }
                            } catch (Exception ignored) {}
                        }
                        copyToClipboard(textToCopy);
                    });
                }
            }

            textTimestamp.setText(formatDate(message.getTimestamp()));
            bindReply(itemView, message);
            bindLinkPreview(message, linkPreviewCard, linkImage, linkTitle, linkDescription, linkUrl);

            if (textEdited != null) {
                textEdited.setVisibility(message.isEdited() ? View.VISIBLE : View.GONE);
            }

            if (forwardIndicator != null) {
                forwardIndicator.setVisibility(message.isForwarded() ? View.VISIBLE : View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(message.getMessageId());
                } else if (listener != null) {
                    listener.onMessageClick(message);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongClick(message, v);
                }
                return true;
            });
        }

    }

    class VoiceSentViewHolder extends RecyclerView.ViewHolder {
        android.widget.ImageView btnPlayPause;
        com.google.android.material.progressindicator.LinearProgressIndicator voiceProgress;
        com.google.android.material.progressindicator.CircularProgressIndicator uploadProgress;
        TextView textDuration;
        TextView textTimestamp;
        android.widget.ImageView imageStatus;
        android.widget.ImageView imagePin;
        View forwardIndicator;

        public VoiceSentViewHolder(@NonNull View itemView) {
            super(itemView);
            btnPlayPause = itemView.findViewById(R.id.btn_play_pause);
            voiceProgress = itemView.findViewById(R.id.voice_progress);
            uploadProgress = itemView.findViewById(R.id.upload_progress);
            textDuration = itemView.findViewById(R.id.text_duration);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            imageStatus = itemView.findViewById(R.id.image_status);
            imagePin = itemView.findViewById(R.id.image_pin);
            forwardIndicator = itemView.findViewById(R.id.forward_indicator);
        }

        void bind(Message message, int position) {
            textTimestamp.setText(formatDate(message.getTimestamp()));
            textDuration.setText(formatDuration(message.getDuration()));
            bindReply(itemView, message);
            
            if (imagePin != null) {
                imagePin.setVisibility(message.isPinned() ? View.VISIBLE : View.GONE);
            }

            if (forwardIndicator != null) {
                forwardIndicator.setVisibility(message.isForwarded() ? View.VISIBLE : View.GONE);
            }
            
            if (message.isSeen()) {
                int color = com.google.android.material.color.MaterialColors.getColor(itemView, androidx.appcompat.R.attr.colorPrimary);
                imageStatus.setColorFilter(color);
            } else {
                imageStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), android.R.color.darker_gray));
            }

            Integer progress = uploadProgressMap.get(message.getMessageId());
            if (progress != null && progress < 100) {
                uploadProgress.setVisibility(View.VISIBLE);
                uploadProgress.setProgress(progress);
                btnPlayPause.setVisibility(View.GONE);
            } else {
                uploadProgress.setVisibility(View.GONE);
                btnPlayPause.setVisibility(View.VISIBLE);
            }

            updatePlayPauseUI(btnPlayPause, voiceProgress, message, position);

            btnPlayPause.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(message.getMessageId());
                } else {
                    togglePlay(message, position);
                }
            });

            itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(message.getMessageId());
                } else if (listener != null) {
                    listener.onMessageClick(message);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongClick(message, v);
                }
                return true;
            });
        }
    }

    class VoiceReceivedViewHolder extends RecyclerView.ViewHolder {
        View senderInfoContainer;
        TextView senderName;
        TextView senderUsername;
        android.widget.ImageView btnPlayPause;
        com.google.android.material.progressindicator.LinearProgressIndicator voiceProgress;
        com.google.android.material.progressindicator.CircularProgressIndicator downloadProgress;
        TextView textDuration;
        TextView textTimestamp;
        android.widget.ImageView imagePin;
        View forwardIndicator;

        public VoiceReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            senderInfoContainer = itemView.findViewById(R.id.sender_info_container);
            senderName = itemView.findViewById(R.id.sender_name);
            senderUsername = itemView.findViewById(R.id.sender_username);
            btnPlayPause = itemView.findViewById(R.id.btn_play_pause);
            voiceProgress = itemView.findViewById(R.id.voice_progress);
            downloadProgress = itemView.findViewById(R.id.download_progress);
            textDuration = itemView.findViewById(R.id.text_duration);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            imagePin = itemView.findViewById(R.id.image_pin);
            forwardIndicator = itemView.findViewById(R.id.forward_indicator);
        }

        void bind(Message message, int position) {
            if (senderInfoContainer != null) {
                if (isGroup && message.getSenderName() != null) {
                    senderInfoContainer.setVisibility(View.VISIBLE);
                    senderName.setText(message.getSenderName());
                    if (senderUsername != null && message.getSenderUsername() != null) {
                        senderUsername.setText("@" + message.getSenderUsername());
                        senderUsername.setVisibility(View.VISIBLE);
                    } else if (senderUsername != null) {
                        senderUsername.setVisibility(View.GONE);
                    }
                } else {
                    senderInfoContainer.setVisibility(View.GONE);
                }
            }
            textTimestamp.setText(formatDate(message.getTimestamp()));
            textDuration.setText(formatDuration(message.getDuration()));
            bindReply(itemView, message);

            if (imagePin != null) {
                imagePin.setVisibility(message.isPinned() ? View.VISIBLE : View.GONE);
            }

            if (forwardIndicator != null) {
                forwardIndicator.setVisibility(message.isForwarded() ? View.VISIBLE : View.GONE);
            }

            Integer progress = downloadProgressMap.get(message.getMessageId());
            if (progress != null && progress < 100) {
                downloadProgress.setVisibility(View.VISIBLE);
                downloadProgress.setProgress(progress);
                btnPlayPause.setVisibility(View.GONE);
            } else {
                downloadProgress.setVisibility(View.GONE);
                btnPlayPause.setVisibility(View.VISIBLE);
            }

            updatePlayPauseUI(btnPlayPause, voiceProgress, message, position);

            btnPlayPause.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(message.getMessageId());
                } else {
                    togglePlay(message, position);
                }
            });

            itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(message.getMessageId());
                } else if (listener != null) {
                    listener.onMessageClick(message);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongClick(message, v);
                }
                return true;
            });
        }
    }

    class ImageSentViewHolder extends RecyclerView.ViewHolder {
        android.widget.ImageView imageMessage;
        TextView textTimestamp;
        android.widget.ImageView imageStatus;
        android.widget.ImageView imagePin;
        com.google.android.material.progressindicator.CircularProgressIndicator uploadProgress;
        View forwardIndicator;

        public ImageSentViewHolder(@NonNull View itemView) {
            super(itemView);
            imageMessage = itemView.findViewById(R.id.image_message);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            imageStatus = itemView.findViewById(R.id.image_status);
            imagePin = itemView.findViewById(R.id.image_pin);
            uploadProgress = itemView.findViewById(R.id.upload_progress);
            forwardIndicator = itemView.findViewById(R.id.forward_indicator);
        }

        void bind(Message message) {
            textTimestamp.setText(formatDate(message.getTimestamp()));
            bindReply(itemView, message);

            if (imagePin != null) {
                imagePin.setVisibility(message.isPinned() ? View.VISIBLE : View.GONE);
            }

            if (forwardIndicator != null) {
                forwardIndicator.setVisibility(message.isForwarded() ? View.VISIBLE : View.GONE);
            }
            
            String mediaData = message.getMediaUrl();
            if (mediaData != null && mediaData.startsWith("local:")) {
                com.bumptech.glide.Glide.with(itemView.getContext())
                        .load(android.net.Uri.parse(mediaData.substring(6)))
                        .into(imageMessage);
            } else {
                loadEncryptedMedia(imageMessage, mediaData, false, message.getMessageId());
            }

            if (message.isSeen()) {
                int color = com.google.android.material.color.MaterialColors.getColor(itemView, androidx.appcompat.R.attr.colorPrimary);
                imageStatus.setColorFilter(color);
            } else {
                imageStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), android.R.color.darker_gray));
            }

            Integer progress = uploadProgressMap.get(message.getMessageId());
            if (progress != null && progress < 100) {
                uploadProgress.setVisibility(View.VISIBLE);
                uploadProgress.setProgress(progress);
            } else {
                uploadProgress.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(message.getMessageId());
                } else {
                    if (com.hamraj37.somechat.utils.MediaUtils.isMediaDownloaded(v.getContext(), "image", message.getMessageId())) {
                        openFullMedia(v.getContext(), message);
                    } else {
                        downloadMediaManual(imageMessage, message.getMediaUrl(), false, message.getMessageId());
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongClick(message, v);
                }
                return true;
            });
        }
    }

    class ImageReceivedViewHolder extends RecyclerView.ViewHolder {
        View senderInfoContainer;
        TextView senderName;
        TextView senderUsername;
        android.widget.ImageView imageMessage;
        TextView textTimestamp;
        android.widget.ImageView imagePin;
        com.google.android.material.progressindicator.CircularProgressIndicator downloadProgress;
        View forwardIndicator;

        public ImageReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            senderInfoContainer = itemView.findViewById(R.id.sender_info_container);
            senderName = itemView.findViewById(R.id.sender_name);
            senderUsername = itemView.findViewById(R.id.sender_username);
            imageMessage = itemView.findViewById(R.id.image_message);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            imagePin = itemView.findViewById(R.id.image_pin);
            downloadProgress = itemView.findViewById(R.id.download_progress);
            forwardIndicator = itemView.findViewById(R.id.forward_indicator);
        }

        void bind(Message message) {
            if (senderInfoContainer != null) {
                if (isGroup && message.getSenderName() != null) {
                    senderInfoContainer.setVisibility(View.VISIBLE);
                    senderName.setText(message.getSenderName());
                    if (senderUsername != null && message.getSenderUsername() != null) {
                        senderUsername.setText("@" + message.getSenderUsername());
                        senderUsername.setVisibility(View.VISIBLE);
                    } else if (senderUsername != null) {
                        senderUsername.setVisibility(View.GONE);
                    }
                } else {
                    senderInfoContainer.setVisibility(View.GONE);
                }
            }
            textTimestamp.setText(formatDate(message.getTimestamp()));
            bindReply(itemView, message);

            if (imagePin != null) {
                imagePin.setVisibility(message.isPinned() ? View.VISIBLE : View.GONE);
            }

            if (forwardIndicator != null) {
                forwardIndicator.setVisibility(message.isForwarded() ? View.VISIBLE : View.GONE);
            }
            
            Integer progress = downloadProgressMap.get(message.getMessageId());
            if (progress != null && progress < 100) {
                downloadProgress.setVisibility(View.VISIBLE);
                downloadProgress.setProgress(progress);
            } else {
                downloadProgress.setVisibility(View.GONE);
            }

            loadEncryptedMedia(imageMessage, message.getMediaUrl(), false, message.getMessageId());
            
            itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(message.getMessageId());
                } else {
                    if (com.hamraj37.somechat.utils.MediaUtils.isMediaDownloaded(v.getContext(), "image", message.getMessageId())) {
                        openFullMedia(v.getContext(), message);
                    } else {
                        downloadMediaManual(imageMessage, message.getMediaUrl(), false, message.getMessageId());
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongClick(message, v);
                }
                return true;
            });
        }
    }

    class VideoSentViewHolder extends RecyclerView.ViewHolder {
        android.widget.ImageView videoThumbnail;
        TextView textTimestamp;
        android.widget.ImageView imageStatus;
        android.widget.ImageView imagePin;
        com.google.android.material.progressindicator.CircularProgressIndicator uploadProgress;
        View forwardIndicator;

        public VideoSentViewHolder(@NonNull View itemView) {
            super(itemView);
            videoThumbnail = itemView.findViewById(R.id.video_thumbnail);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            imageStatus = itemView.findViewById(R.id.image_status);
            imagePin = itemView.findViewById(R.id.image_pin);
            uploadProgress = itemView.findViewById(R.id.upload_progress);
            forwardIndicator = itemView.findViewById(R.id.forward_indicator);
        }

        void bind(Message message) {
            textTimestamp.setText(formatDate(message.getTimestamp()));
            bindReply(itemView, message);

            if (imagePin != null) {
                imagePin.setVisibility(message.isPinned() ? View.VISIBLE : View.GONE);
            }

            if (forwardIndicator != null) {
                forwardIndicator.setVisibility(message.isForwarded() ? View.VISIBLE : View.GONE);
            }
            
            String mediaData = message.getMediaUrl();
            if (mediaData != null && mediaData.startsWith("local:")) {
                com.bumptech.glide.Glide.with(itemView.getContext())
                        .load(android.net.Uri.parse(mediaData.substring(6)))
                        .frame(0)
                        .into(videoThumbnail);
            } else {
                loadEncryptedMedia(videoThumbnail, mediaData, true, message.getMessageId());
            }

            if (message.isSeen()) {
                int color = com.google.android.material.color.MaterialColors.getColor(itemView, androidx.appcompat.R.attr.colorPrimary);
                imageStatus.setColorFilter(color);
            } else {
                imageStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), android.R.color.darker_gray));
            }
            
            Integer progress = uploadProgressMap.get(message.getMessageId());
            if (progress != null && progress < 100) {
                uploadProgress.setVisibility(View.VISIBLE);
                uploadProgress.setProgress(progress);
            } else {
                uploadProgress.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(message.getMessageId());
                } else {
                    if (com.hamraj37.somechat.utils.MediaUtils.isMediaDownloaded(v.getContext(), "video", message.getMessageId())) {
                        openFullMedia(v.getContext(), message);
                    } else {
                        downloadMediaManual(videoThumbnail, message.getMediaUrl(), true, message.getMessageId());
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongClick(message, v);
                }
                return true;
            });
        }
    }

    class VideoReceivedViewHolder extends RecyclerView.ViewHolder {
        View senderInfoContainer;
        TextView senderName;
        TextView senderUsername;
        android.widget.ImageView videoThumbnail;
        TextView textTimestamp;
        android.widget.ImageView imagePin;
        com.google.android.material.progressindicator.CircularProgressIndicator downloadProgress;
        View forwardIndicator;

        public VideoReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            senderInfoContainer = itemView.findViewById(R.id.sender_info_container);
            senderName = itemView.findViewById(R.id.sender_name);
            senderUsername = itemView.findViewById(R.id.sender_username);
            videoThumbnail = itemView.findViewById(R.id.video_thumbnail);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            imagePin = itemView.findViewById(R.id.image_pin);
            downloadProgress = itemView.findViewById(R.id.download_progress);
            forwardIndicator = itemView.findViewById(R.id.forward_indicator);
        }

        void bind(Message message) {
            if (senderInfoContainer != null) {
                if (isGroup && message.getSenderName() != null) {
                    senderInfoContainer.setVisibility(View.VISIBLE);
                    senderName.setText(message.getSenderName());
                    if (senderUsername != null && message.getSenderUsername() != null) {
                        senderUsername.setText("@" + message.getSenderUsername());
                        senderUsername.setVisibility(View.VISIBLE);
                    } else if (senderUsername != null) {
                        senderUsername.setVisibility(View.GONE);
                    }
                } else {
                    senderInfoContainer.setVisibility(View.GONE);
                }
            }
            textTimestamp.setText(formatDate(message.getTimestamp()));
            bindReply(itemView, message);

            if (imagePin != null) {
                imagePin.setVisibility(message.isPinned() ? View.VISIBLE : View.GONE);
            }

            if (forwardIndicator != null) {
                forwardIndicator.setVisibility(message.isForwarded() ? View.VISIBLE : View.GONE);
            }

            Integer progress = downloadProgressMap.get(message.getMessageId());
            if (progress != null && progress < 100) {
                downloadProgress.setVisibility(View.VISIBLE);
                downloadProgress.setProgress(progress);
            } else {
                downloadProgress.setVisibility(View.GONE);
            }

            loadEncryptedMedia(videoThumbnail, message.getMediaUrl(), true, message.getMessageId());
            
            itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(message.getMessageId());
                } else {
                    if (com.hamraj37.somechat.utils.MediaUtils.isMediaDownloaded(v.getContext(), "video", message.getMessageId())) {
                        openFullMedia(v.getContext(), message);
                    } else {
                        downloadMediaManual(videoThumbnail, message.getMediaUrl(), true, message.getMessageId());
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMessageLongClick(message, v);
                }
                return true;
            });
        }
    }

    private void bindLinkPreview(Message message, View card, android.widget.ImageView image, TextView title, TextView description, TextView url) {
        if (card == null) return;
        String firstUrl = com.hamraj37.somechat.utils.LinkPreviewUtils.findFirstUrl(message.getText());
        if (firstUrl == null) {
            card.setVisibility(View.GONE);
            return;
        }

        boolean isSent = message.getSenderId().equals(currentUserId);
        
        // WhatsApp style backgrounds
        if (isSent) {
            card.setBackgroundColor(0x25000000); // Darken sent bubble slightly
            if (title != null) title.setTextColor(0xFFFFFFFF);
            if (description != null) description.setTextColor(0xCCFFFFFF);
            if (url != null) url.setTextColor(0xBBFFFFFF);
        } else {
            card.setBackgroundColor(0x10000000); // Subtle on received
            if (title != null) title.setTextColor(0xFF000000);
            if (description != null) description.setTextColor(0x99000000);
            if (url != null) url.setTextColor(0xFF25D366); // WhatsApp Green for URLs
        }

        com.hamraj37.somechat.models.LinkMetadata cached = linkMetadataCache.get(firstUrl);
        card.setVisibility(View.VISIBLE);

        if (cached != null) {
            if (title != null) title.setText(cached.getTitle());
            if (description != null) {
                description.setText(cached.getDescription());
                description.setVisibility(cached.getDescription() != null && !cached.getDescription().isEmpty() ? View.VISIBLE : View.GONE);
            }
            
            if (url != null) {
                try {
                    String host = new java.net.URI(firstUrl).getHost();
                    url.setText(host != null ? host : firstUrl);
                } catch (Exception e) {
                    url.setText(firstUrl);
                }
            }

            if (cached.getImageUrl() != null && !cached.getImageUrl().isEmpty()) {
                image.setVisibility(View.VISIBLE);
                com.bumptech.glide.Glide.with(image)
                        .load(cached.getImageUrl())
                        .centerCrop()
                        .into(image);
            } else {
                image.setVisibility(View.GONE);
            }
        } else {
            // Fallback UI if preview not yet loaded or failed
            if (title != null) title.setText(firstUrl);
            if (description != null) description.setVisibility(View.GONE);
            if (image != null) image.setVisibility(View.GONE);
            if (url != null) url.setText("Open link");

            com.hamraj37.somechat.utils.LinkPreviewUtils.fetchMetadata(firstUrl, new com.hamraj37.somechat.utils.LinkPreviewUtils.LinkPreviewCallback() {
                @Override
                public void onMetadataFetched(com.hamraj37.somechat.models.LinkMetadata metadata) {
                    linkMetadataCache.put(firstUrl, metadata);
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            for (int i = 0; i < messageList.size(); i++) {
                                if (messageList.get(i).getMessageId().equals(message.getMessageId())) {
                                    notifyItemChanged(getAdapterPosition(i));
                                    break;
                                }
                            }
                        });
                    }
                }

                @Override
                public void onError(Exception e) {
                    // Even on error, we keep the card visible with the URL as title so it's "openeble"
                }
            });
        }

        card.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(message.getMessageId());
            } else {
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(firstUrl));
                    context.startActivity(intent);
                } catch (Exception e) {
                    android.widget.Toast.makeText(context, "Cannot open link", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });

        card.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onMessageLongClick(message, v);
            }
            return true;
        });
    }

    private void bindReply(View itemView, Message message) {
        View replyContainer = itemView.findViewById(R.id.reply_container);
        if (replyContainer == null) return;
        
        if (message.getReplyToId() != null) {
            replyContainer.setVisibility(View.VISIBLE);
            ((TextView) itemView.findViewById(R.id.reply_name)).setText(message.getReplyToName());
            ((TextView) itemView.findViewById(R.id.reply_text)).setText(message.getReplyToText());
            
            replyContainer.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReplyClick(message.getReplyToId());
                }
            });
        } else {
            replyContainer.setVisibility(View.GONE);
            replyContainer.setOnClickListener(null);
        }
    }

    private void loadEncryptedMedia(android.widget.ImageView imageView, String encryptedInfo, boolean isVideo, String messageId) {
        if (encryptedDataIsEmpty(encryptedInfo)) {
            imageView.setImageDrawable(null);
            return;
        }

        String type = isVideo ? "video" : "image";
        java.io.File localFile = com.hamraj37.somechat.utils.MediaUtils.getLocalFileForMedia(imageView.getContext(), type, messageId);
        if (localFile.exists()) {
            imageView.setAlpha(1.0f);
            imageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            if (isVideo) {
                new Thread(() -> {
                    try {
                        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                        retriever.setDataSource(localFile.getAbsolutePath());
                        android.graphics.Bitmap bitmap = retriever.getFrameAtTime(0);
                        retriever.release();
                        if (bitmap != null) {
                            imageView.post(() -> imageView.setImageBitmap(bitmap));
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }).start();
            } else {
                com.bumptech.glide.Glide.with(imageView)
                        .load(localFile)
                        .centerCrop()
                        .placeholder(android.R.color.darker_gray)
                        .into(imageView);
            }
            return;
        }

        imageView.setTag(encryptedInfo);
        
        // Don't download automatically for thumbnails. 
        // User must click to trigger download.
        imageView.setImageResource(android.R.drawable.stat_sys_download);
        imageView.setAlpha(0.6f);
        imageView.setScaleType(android.widget.ImageView.ScaleType.CENTER);

        if (!encryptedInfo.trim().startsWith("{")) {
            com.bumptech.glide.Glide.with(imageView)
                    .load(encryptedInfo)
                    .centerCrop()
                    .placeholder(android.R.color.darker_gray)
                    .into(imageView);
        }
    }

    private void downloadMediaManual(android.widget.ImageView imageView, String encryptedInfo, boolean isVideo, String messageId) {
        if (downloadProgressMap.containsKey(messageId)) return;

        String type = isVideo ? "video" : "image";
        try {
            org.json.JSONObject json = new org.json.JSONObject(encryptedInfo);
            String url = json.getString("u");
            String key = json.getString("k");

            updateDownloadProgress(messageId, 0);

            com.hamraj37.somechat.utils.GitHubStorage.downloadFile(url, new com.hamraj37.somechat.utils.GitHubStorage.DownloadCallback() {
                @Override
                public void onProgress(int progress) {
                    if (imageView.getContext() instanceof android.app.Activity) {
                        ((android.app.Activity) imageView.getContext()).runOnUiThread(() -> updateDownloadProgress(messageId, progress));
                    }
                }

                @Override
                public void onFailure(@NonNull Exception e) {
                    if (imageView.getContext() instanceof android.app.Activity) {
                        ((android.app.Activity) imageView.getContext()).runOnUiThread(() -> removeDownloadProgress(messageId));
                    }
                }

                @Override
                public void onSuccess(byte[] data) {
                    android.content.Context ctx = imageView.getContext();
                    try {
                        javax.crypto.SecretKey secretKey = com.hamraj37.somechat.utils.EncryptionManager.decodeKey(key);
                        byte[] decryptedBytes = com.hamraj37.somechat.utils.EncryptionManager.decryptRaw(data, secretKey);
                        com.hamraj37.somechat.utils.MediaUtils.saveMediaLocally(ctx, decryptedBytes, type, messageId);

                        if (ctx instanceof android.app.Activity) {
                            ((android.app.Activity) ctx).runOnUiThread(() -> {
                                removeDownloadProgress(messageId);
                                notifyItemChanged(getPositionForMessageId(messageId));
                            });
                        }
                    } catch (Exception e) {
                        if (ctx instanceof android.app.Activity) {
                            ((android.app.Activity) ctx).runOnUiThread(() -> removeDownloadProgress(messageId));
                        }
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            removeDownloadProgress(messageId);
            e.printStackTrace();
        }
    }

    private int getPositionForMessageId(String messageId) {
        for (int i = 0; i < messageList.size(); i++) {
            if (messageList.get(i).getMessageId().equals(messageId)) {
                return getAdapterPosition(i);
            }
        }
        return -1;
    }

    private static boolean encryptedDataIsEmpty(String data) {
        return data == null || data.isEmpty();
    }

    private static void openFullMedia(android.content.Context context, Message message) {
        String mediaData = message.getMediaUrl();
        if (mediaData == null) return;

        android.content.Intent intent = new android.content.Intent(context, com.hamraj37.somechat.FullMediaActivity.class);
        intent.putExtra("type", message.getType());
        intent.putExtra("message_id", message.getMessageId());

        if (mediaData.startsWith("local:")) {
            intent.putExtra("url", mediaData.substring(6));
        } else if (mediaData.trim().startsWith("{")) {
            intent.putExtra("encrypted_info", mediaData);
        } else {
            intent.putExtra("url", mediaData);
        }
        context.startActivity(intent);
    }

    private void updatePlayPauseUI(android.widget.ImageView btn, com.google.android.material.progressindicator.LinearProgressIndicator progress, Message message, int position) {
        if (playingPosition == position && mediaPlayer != null && mediaPlayer.isPlaying()) {
            btn.setImageResource(android.R.drawable.ic_media_pause);
            startProgressUpdate(progress, message);
        } else {
            btn.setImageResource(android.R.drawable.ic_media_play);
            progress.setProgress(0);
        }
    }

    private void togglePlay(Message message, int position) {
        if (playingPosition == position) {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            } else if (mediaPlayer != null) {
                mediaPlayer.start();
            }
        } else {
            stopPlayback();
            playAudio(message.getMediaUrl(), position);
        }
        notifyItemChanged(position);
    }

    private void playAudio(String mediaData, int position) {
        if (mediaData == null || mediaData.isEmpty()) return;
        
        Message message = messageList.get(getListIndex(position));
        String messageId = message.getMessageId();

        try {
            if (mediaData.startsWith("local:")) {
                startMediaPlayer(mediaData.substring(6), position);
            } else if (mediaData.trim().startsWith("{")) {
                if (downloadProgressMap.containsKey(messageId)) return;

                org.json.JSONObject json = new org.json.JSONObject(mediaData);
                String url = json.getString("u");
                String key = json.getString("k");

                updateDownloadProgress(messageId, 0);

                com.hamraj37.somechat.utils.GitHubStorage.downloadFile(url, new com.hamraj37.somechat.utils.GitHubStorage.DownloadCallback() {
                    @Override
                    public void onProgress(int progress) {
                        if (context instanceof android.app.Activity) {
                            ((android.app.Activity) context).runOnUiThread(() -> updateDownloadProgress(messageId, progress));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (context instanceof android.app.Activity) {
                            ((android.app.Activity) context).runOnUiThread(() -> removeDownloadProgress(messageId));
                        }
                    }

                    @Override
                    public void onSuccess(byte[] data) {
                        try {
                            byte[] decryptedBytes = com.hamraj37.somechat.utils.EncryptionManager.decryptRaw(data, com.hamraj37.somechat.utils.EncryptionManager.decodeKey(key));
                            com.hamraj37.somechat.utils.MediaUtils.saveMediaLocally(context, decryptedBytes, "voice", messageId);
                            
                            if (context instanceof android.app.Activity) {
                                ((android.app.Activity) context).runOnUiThread(() -> removeDownloadProgress(messageId));
                            }
                            
                            java.io.File tempFile = com.hamraj37.somechat.utils.MediaUtils.getLocalFileForMedia(context, "voice", messageId);
                            if (context instanceof android.app.Activity) {
                                ((android.app.Activity) context).runOnUiThread(() -> startMediaPlayer(tempFile.getAbsolutePath(), position));
                            }
                        } catch (Exception e) {
                            if (context instanceof android.app.Activity) {
                                ((android.app.Activity) context).runOnUiThread(() -> removeDownloadProgress(messageId));
                            }
                            e.printStackTrace();
                        }
                    }
                });
            } else {
                startMediaPlayer(mediaData, position);
            }
        } catch (Exception e) {
            removeDownloadProgress(messageId);
            e.printStackTrace();
        }
    }

    private void startMediaPlayer(String path, int position) {
        try {
            mediaPlayer = new android.media.MediaPlayer();
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                playingPosition = position;
                mp.start();
                notifyItemChanged(position);
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                stopPlayback();
                notifyItemChanged(position);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        playingPosition = -1;
        progressHandler.removeCallbacksAndMessages(null);
    }

    private void startProgressUpdate(com.google.android.material.progressindicator.LinearProgressIndicator progress, Message message) {
        progressHandler.removeCallbacksAndMessages(null);
        progressHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    int current = mediaPlayer.getCurrentPosition();
                    int total = mediaPlayer.getDuration();
                    if (total > 0) {
                        progress.setProgress((int) ((current / (float) total) * 100));
                    }
                    progressHandler.postDelayed(this, 100);
                }
            }
        });
    }

    private static String formatDuration(int seconds) {
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }

    private static String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
