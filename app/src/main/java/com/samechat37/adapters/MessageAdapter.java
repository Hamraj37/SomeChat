package com.samechat37.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.samechat37.R;
import com.samechat37.models.Message;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

    private List<Message> messageList;
    private String currentUserId;
    private android.media.MediaPlayer mediaPlayer;
    private int playingPosition = -1;
    private android.os.Handler progressHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.Map<String, Integer> uploadProgressMap = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> downloadProgressMap = new java.util.HashMap<>();
    private android.content.Context context;
    private OnMessageClickListener listener;
    private String highlightMessageId = null;

    public interface OnMessageClickListener {
        void onReplyClick(String messageId);
    }

    public MessageAdapter(List<Message> messageList, OnMessageClickListener listener) {
        this.messageList = messageList;
        this.listener = listener;
        updateCurrentUserId();
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
        updateCurrentUserId();
    }

    public void updateUploadProgress(String messageId, int progress) {
        uploadProgressMap.put(messageId, progress);
        for (int i = 0; i < messageList.size(); i++) {
            if (messageId.equals(messageList.get(i).getMessageId())) {
                notifyItemChanged(i + 1); // +1 for header
                break;
            }
        }
    }

    public void removeUploadProgress(String messageId) {
        uploadProgressMap.remove(messageId);
        for (int i = 0; i < messageList.size(); i++) {
            if (messageId.equals(messageList.get(i).getMessageId())) {
                notifyItemChanged(i + 1);
                break;
            }
        }
    }

    public void updateDownloadProgress(String messageId, int progress) {
        downloadProgressMap.put(messageId, progress);
        for (int i = 0; i < messageList.size(); i++) {
            if (messageId.equals(messageList.get(i).getMessageId())) {
                notifyItemChanged(i + 1);
                break;
            }
        }
    }

    public void removeDownloadProgress(String messageId) {
        downloadProgressMap.remove(messageId);
        for (int i = 0; i < messageList.size(); i++) {
            if (messageId.equals(messageList.get(i).getMessageId())) {
                notifyItemChanged(i + 1);
                break;
            }
        }
    }

    public void highlightMessage(String messageId) {
        this.highlightMessageId = messageId;
        for (int i = 0; i < messageList.size(); i++) {
            if (messageList.get(i).getMessageId().equals(messageId)) {
                final int pos = i + 1;
                notifyItemChanged(pos);
                
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    this.highlightMessageId = null;
                    notifyItemChanged(pos);
                }, 1000);
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return VIEW_TYPE_HEADER;

        Message message = messageList.get(position - 1);
        if (message == null) return VIEW_TYPE_RECEIVED;
        
        String senderId = message.getSenderId();
        boolean isSent = senderId != null && senderId.equals(currentUserId);

        String type = message.getType();
        if (type == null) type = "text";

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
            default:
                return new SentMessageViewHolder(inflater.inflate(R.layout.item_message_sent, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) return;

        Message message = messageList.get(position - 1);
        if (message == null) return;

        boolean isSender = message.getSenderId().equals(currentUserId);
        
        // Decrypt message data if needed
        String decryptedText = com.samechat37.utils.EncryptionManager.decrypt(message.getText(), holder.itemView.getContext(), isSender);
        String decryptedMediaUrl = com.samechat37.utils.EncryptionManager.decrypt(message.getMediaUrl(), holder.itemView.getContext(), isSender);

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
        displayMessage.setSeen(message.isSeen());
        displayMessage.setReplyToId(message.getReplyToId());
        displayMessage.setReplyToName(message.getReplyToName());
        displayMessage.setReplyToText(message.getReplyToText());

        boolean isHighlighted = message.getMessageId() != null && message.getMessageId().equals(highlightMessageId);
        holder.itemView.setAlpha(isHighlighted ? 0.5f : 1.0f);
        // Better: change bubble background. But for simplicity let's stick to alpha or a dedicated View.
        // Let's use a subtle alpha change for now, or if I can find the bubble views.
        
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
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size() + 1;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        stopPlayback();
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage;
        TextView textTimestamp;
        android.widget.ImageView imageStatus;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.text_message);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            imageStatus = itemView.findViewById(R.id.image_status);
        }

        void bind(Message message) {
            textMessage.setText(message.getText());
            textTimestamp.setText(formatDate(message.getTimestamp()));
            bindReply(itemView, message);

            if (message.isSeen()) {
                imageStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), R.color.whatsapp_blue));
            } else {
                imageStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), android.R.color.darker_gray));
            }
        }
    }

    class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView textMessage;
        TextView textTimestamp;

        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.text_message);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
        }

        void bind(Message message) {
            textMessage.setText(message.getText());
            textTimestamp.setText(formatDate(message.getTimestamp()));
            bindReply(itemView, message);
        }
    }

    class VoiceSentViewHolder extends RecyclerView.ViewHolder {
        android.widget.ImageView btnPlayPause;
        com.google.android.material.progressindicator.LinearProgressIndicator voiceProgress;
        com.google.android.material.progressindicator.CircularProgressIndicator uploadProgress;
        TextView textDuration;
        TextView textTimestamp;
        android.widget.ImageView imageStatus;

        public VoiceSentViewHolder(@NonNull View itemView) {
            super(itemView);
            btnPlayPause = itemView.findViewById(R.id.btn_play_pause);
            voiceProgress = itemView.findViewById(R.id.voice_progress);
            uploadProgress = itemView.findViewById(R.id.upload_progress);
            textDuration = itemView.findViewById(R.id.text_duration);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            imageStatus = itemView.findViewById(R.id.image_status);
        }

        void bind(Message message, int position) {
            textTimestamp.setText(formatDate(message.getTimestamp()));
            textDuration.setText(formatDuration(message.getDuration()));
            bindReply(itemView, message);
            
            if (message.isSeen()) {
                imageStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), R.color.whatsapp_blue));
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

            btnPlayPause.setOnClickListener(v -> togglePlay(message, position));
        }
    }

    class VoiceReceivedViewHolder extends RecyclerView.ViewHolder {
        android.widget.ImageView btnPlayPause;
        com.google.android.material.progressindicator.LinearProgressIndicator voiceProgress;
        com.google.android.material.progressindicator.CircularProgressIndicator downloadProgress;
        TextView textDuration;
        TextView textTimestamp;

        public VoiceReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            btnPlayPause = itemView.findViewById(R.id.btn_play_pause);
            voiceProgress = itemView.findViewById(R.id.voice_progress);
            downloadProgress = itemView.findViewById(R.id.download_progress);
            textDuration = itemView.findViewById(R.id.text_duration);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
        }

        void bind(Message message, int position) {
            textTimestamp.setText(formatDate(message.getTimestamp()));
            textDuration.setText(formatDuration(message.getDuration()));
            bindReply(itemView, message);

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

            btnPlayPause.setOnClickListener(v -> togglePlay(message, position));
        }
    }

    class ImageSentViewHolder extends RecyclerView.ViewHolder {
        android.widget.ImageView imageMessage;
        TextView textTimestamp;
        android.widget.ImageView imageStatus;
        com.google.android.material.progressindicator.CircularProgressIndicator uploadProgress;

        public ImageSentViewHolder(@NonNull View itemView) {
            super(itemView);
            imageMessage = itemView.findViewById(R.id.image_message);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            imageStatus = itemView.findViewById(R.id.image_status);
            uploadProgress = itemView.findViewById(R.id.upload_progress);
        }

        void bind(Message message) {
            textTimestamp.setText(formatDate(message.getTimestamp()));
            bindReply(itemView, message);
            
            String mediaData = message.getMediaUrl();
            if (mediaData != null && mediaData.startsWith("local:")) {
                com.bumptech.glide.Glide.with(itemView.getContext())
                        .load(android.net.Uri.parse(mediaData.substring(6)))
                        .into(imageMessage);
            } else {
                loadEncryptedMedia(imageMessage, mediaData, false, message.getMessageId());
            }

            if (message.isSeen()) {
                imageStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), R.color.whatsapp_blue));
            } else {
                imageStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), android.R.color.darker_gray));
            }

            Integer progress = uploadProgressMap.get(message.getMessageId());
            if (progress != null && progress < 100) {
                uploadProgress.setVisibility(View.VISIBLE);
                uploadProgress.setProgress(progress);
            } else {
                uploadProgress.setVisibility(View.GONE);
                itemView.setOnClickListener(v -> openFullMedia(v.getContext(), message));
            }
        }
    }

    class ImageReceivedViewHolder extends RecyclerView.ViewHolder {
        android.widget.ImageView imageMessage;
        TextView textTimestamp;
        com.google.android.material.progressindicator.CircularProgressIndicator downloadProgress;

        public ImageReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            imageMessage = itemView.findViewById(R.id.image_message);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            downloadProgress = itemView.findViewById(R.id.download_progress);
        }

        void bind(Message message) {
            textTimestamp.setText(formatDate(message.getTimestamp()));
            bindReply(itemView, message);
            
            Integer progress = downloadProgressMap.get(message.getMessageId());
            if (progress != null && progress < 100) {
                downloadProgress.setVisibility(View.VISIBLE);
                downloadProgress.setProgress(progress);
            } else {
                downloadProgress.setVisibility(View.GONE);
            }

            loadEncryptedMedia(imageMessage, message.getMediaUrl(), false, message.getMessageId());
            itemView.setOnClickListener(v -> openFullMedia(v.getContext(), message));
        }
    }

    class VideoSentViewHolder extends RecyclerView.ViewHolder {
        android.widget.ImageView videoThumbnail;
        TextView textTimestamp;
        android.widget.ImageView imageStatus;
        com.google.android.material.progressindicator.CircularProgressIndicator uploadProgress;

        public VideoSentViewHolder(@NonNull View itemView) {
            super(itemView);
            videoThumbnail = itemView.findViewById(R.id.video_thumbnail);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            imageStatus = itemView.findViewById(R.id.image_status);
            uploadProgress = itemView.findViewById(R.id.upload_progress);
        }

        void bind(Message message) {
            textTimestamp.setText(formatDate(message.getTimestamp()));
            bindReply(itemView, message);
            
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
                imageStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), R.color.whatsapp_blue));
            } else {
                imageStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), android.R.color.darker_gray));
            }
            
            Integer progress = uploadProgressMap.get(message.getMessageId());
            if (progress != null && progress < 100) {
                uploadProgress.setVisibility(View.VISIBLE);
                uploadProgress.setProgress(progress);
            } else {
                uploadProgress.setVisibility(View.GONE);
                itemView.setOnClickListener(v -> openFullMedia(v.getContext(), message));
            }
        }
    }

    class VideoReceivedViewHolder extends RecyclerView.ViewHolder {
        android.widget.ImageView videoThumbnail;
        TextView textTimestamp;
        com.google.android.material.progressindicator.CircularProgressIndicator downloadProgress;

        public VideoReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            videoThumbnail = itemView.findViewById(R.id.video_thumbnail);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
            downloadProgress = itemView.findViewById(R.id.download_progress);
        }

        void bind(Message message) {
            textTimestamp.setText(formatDate(message.getTimestamp()));
            bindReply(itemView, message);

            Integer progress = downloadProgressMap.get(message.getMessageId());
            if (progress != null && progress < 100) {
                downloadProgress.setVisibility(View.VISIBLE);
                downloadProgress.setProgress(progress);
            } else {
                downloadProgress.setVisibility(View.GONE);
            }

            loadEncryptedMedia(videoThumbnail, message.getMediaUrl(), true, message.getMessageId());
            itemView.setOnClickListener(v -> openFullMedia(v.getContext(), message));
        }
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
        java.io.File localFile = com.samechat37.utils.MediaUtils.getLocalFileForMedia(imageView.getContext(), type, messageId);
        if (localFile.exists()) {
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
        imageView.setImageResource(android.R.color.darker_gray);

        if (!encryptedInfo.trim().startsWith("{")) {
            com.bumptech.glide.Glide.with(imageView)
                    .load(encryptedInfo)
                    .centerCrop()
                    .placeholder(android.R.color.darker_gray)
                    .into(imageView);
            return;
        }

        try {
            org.json.JSONObject json = new org.json.JSONObject(encryptedInfo);
            String url = json.getString("u");
            String key = json.getString("k");

            com.samechat37.utils.GitHubStorage.downloadFile(url, new com.samechat37.utils.GitHubStorage.DownloadCallback() {
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
                    imageView.post(() -> {
                        if (encryptedInfo.equals(imageView.getTag())) {
                            imageView.setImageResource(android.R.drawable.ic_menu_report_image);
                        }
                    });
                }

                @Override
                public void onSuccess(byte[] data) {
                    android.content.Context ctx = imageView.getContext();
                    if (ctx instanceof android.app.Activity) {
                        ((android.app.Activity) ctx).runOnUiThread(() -> removeDownloadProgress(messageId));
                    }
                    try {
                        javax.crypto.SecretKey secretKey = com.samechat37.utils.EncryptionManager.decodeKey(key);
                        byte[] decryptedBytes = com.samechat37.utils.EncryptionManager.decryptRaw(data, secretKey);
                        com.samechat37.utils.MediaUtils.saveMediaLocally(ctx, decryptedBytes, type, messageId);

                        if (isVideo) {
                            android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                            try {
                                retriever.setDataSource(new android.media.MediaDataSource() {
                                    @Override
                                    public int readAt(long position, byte[] buffer, int offset, int size) {
                                        if (position >= decryptedBytes.length) return -1;
                                        int length = Math.min(size, (int) (decryptedBytes.length - position));
                                        System.arraycopy(decryptedBytes, (int) position, buffer, offset, length);
                                        return length;
                                    }
                                    @Override public long getSize() { return decryptedBytes.length; }
                                    @Override public void close() {}
                                });
                                android.graphics.Bitmap bitmap = retriever.getFrameAtTime(0);
                                if (bitmap != null) {
                                    imageView.post(() -> {
                                        if (encryptedInfo.equals(imageView.getTag())) {
                                            imageView.setImageBitmap(bitmap);
                                        }
                                    });
                                }
                            } finally {
                                try { retriever.release(); } catch (Exception ignored) {}
                            }
                        } else {
                            imageView.post(() -> {
                                if (encryptedInfo.equals(imageView.getTag())) {
                                    com.bumptech.glide.Glide.with(imageView)
                                            .load(decryptedBytes)
                                            .centerCrop()
                                            .placeholder(android.R.color.darker_gray)
                                            .into(imageView);
                                }
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            imageView.post(() -> {
                if (encryptedInfo.equals(imageView.getTag())) {
                    com.bumptech.glide.Glide.with(imageView)
                            .load(encryptedInfo)
                            .centerCrop()
                            .placeholder(android.R.color.darker_gray)
                            .into(imageView);
                }
            });
        }
    }

    private static boolean encryptedDataIsEmpty(String data) {
        return data == null || data.isEmpty();
    }

    private static void openFullMedia(android.content.Context context, Message message) {
        String mediaData = message.getMediaUrl();
        if (mediaData == null) return;

        android.content.Intent intent = new android.content.Intent(context, com.samechat37.FullMediaActivity.class);
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
        
        Message message = messageList.get(position - 1);
        String messageId = message.getMessageId();

        try {
            if (mediaData.startsWith("local:")) {
                startMediaPlayer(mediaData.substring(6), position);
            } else if (mediaData.trim().startsWith("{")) {
                org.json.JSONObject json = new org.json.JSONObject(mediaData);
                String url = json.getString("u");
                String key = json.getString("k");

                com.samechat37.utils.GitHubStorage.downloadFile(url, new com.samechat37.utils.GitHubStorage.DownloadCallback() {
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
                        if (context instanceof android.app.Activity) {
                            ((android.app.Activity) context).runOnUiThread(() -> removeDownloadProgress(messageId));
                        }
                        try {
                            byte[] decryptedBytes = com.samechat37.utils.EncryptionManager.decryptRaw(data, com.samechat37.utils.EncryptionManager.decodeKey(key));
                            com.samechat37.utils.MediaUtils.saveMediaLocally(context, decryptedBytes, "voice", messageId);
                            
                            java.io.File tempFile = com.samechat37.utils.MediaUtils.getLocalFileForMedia(context, "voice", messageId);
                            if (context instanceof android.app.Activity) {
                                ((android.app.Activity) context).runOnUiThread(() -> startMediaPlayer(tempFile.getAbsolutePath(), position));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            } else {
                startMediaPlayer(mediaData, position);
            }
        } catch (Exception e) {
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
