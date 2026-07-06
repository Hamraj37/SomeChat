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
    private android.content.Context context;

    public MessageAdapter(List<Message> messageList) {
        this.messageList = messageList;
        this.currentUserId = FirebaseAuth.getInstance().getUid();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.context = recyclerView.getContext();
    }

    public void updateUploadProgress(String messageId, int progress) {
        uploadProgressMap.put(messageId, progress);
        for (int i = 0; i < messageList.size(); i++) {
            if (messageId.equals(messageList.get(i).getMessageId())) {
                notifyItemChanged(i + 1, "progress"); // +1 for header
                break;
            }
        }
    }

    public void removeUploadProgress(String messageId) {
        uploadProgressMap.remove(messageId);
        for (int i = 0; i < messageList.size(); i++) {
            if (messageId.equals(messageList.get(i).getMessageId())) {
                notifyItemChanged(i + 1, "progress"); // +1 for header
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
        Message displayMessage = new Message(
                message.getMessageId(),
                message.getSenderId(),
                message.getReceiverId(),
                com.samechat37.utils.EncryptionManager.decrypt(message.getText(), holder.itemView.getContext(), isSender),
                message.getTimestamp(),
                message.getType(),
                com.samechat37.utils.EncryptionManager.decrypt(message.getMediaUrl(), holder.itemView.getContext(), isSender),
                message.getDuration()
        );
        displayMessage.setSeen(message.isSeen());

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

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
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

            if (message.isSeen()) {
                imageStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), R.color.whatsapp_blue));
            } else {
                imageStatus.setColorFilter(androidx.core.content.ContextCompat.getColor(itemView.getContext(), android.R.color.darker_gray));
            }
        }
    }

    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
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
        TextView textDuration;
        TextView textTimestamp;

        public VoiceReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            btnPlayPause = itemView.findViewById(R.id.btn_play_pause);
            voiceProgress = itemView.findViewById(R.id.voice_progress);
            textDuration = itemView.findViewById(R.id.text_duration);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
        }

        void bind(Message message, int position) {
            textTimestamp.setText(formatDate(message.getTimestamp()));
            textDuration.setText(formatDuration(message.getDuration()));

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
            
            String mediaData = message.getMediaUrl();
            if (mediaData != null && mediaData.startsWith("local:")) {
                com.bumptech.glide.Glide.with(itemView.getContext())
                        .load(android.net.Uri.parse(mediaData.substring(6)))
                        .into(imageMessage);
            } else {
                loadEncryptedMedia(imageMessage, mediaData, false);
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

        public ImageReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            imageMessage = itemView.findViewById(R.id.image_message);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
        }

        void bind(Message message) {
            textTimestamp.setText(formatDate(message.getTimestamp()));
            loadEncryptedMedia(imageMessage, message.getMediaUrl(), false);
            itemView.setOnClickListener(v -> openFullMedia(v.getContext(), message));
        }
    }

    private void loadEncryptedMedia(android.widget.ImageView imageView, String encryptedInfo, boolean isVideo) {
        if (encryptedDataIsEmpty(encryptedInfo)) return;

        try {
            org.json.JSONObject json = new org.json.JSONObject(encryptedInfo);
            String url = json.getString("u");
            String key = json.getString("k");

            com.samechat37.utils.GitHubStorage.downloadFile(url, new okhttp3.Callback() {
                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {}

                @Override
                public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            byte[] encryptedBytes = response.body().bytes();
                            javax.crypto.SecretKey secretKey = com.samechat37.utils.EncryptionManager.decodeKey(key);
                            byte[] decryptedBytes = com.samechat37.utils.EncryptionManager.decryptRaw(encryptedBytes, secretKey);
                            
                            if (context instanceof android.app.Activity) {
                                ((android.app.Activity) context).runOnUiThread(() -> {
                                    if (isVideo) {
                                        com.bumptech.glide.Glide.with(context)
                                                .load(decryptedBytes)
                                                .frame(1000000)
                                                .into(imageView);
                                    } else {
                                        com.bumptech.glide.Glide.with(context)
                                                .load(decryptedBytes)
                                                .into(imageView);
                                    }
                                });
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        } catch (Exception e) {
            com.bumptech.glide.Glide.with(imageView.getContext()).load(encryptedInfo).into(imageView);
        }
    }

    private static boolean encryptedDataIsEmpty(String data) {
        return data == null || data.isEmpty();
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
            
            String mediaData = message.getMediaUrl();
            if (mediaData != null && mediaData.startsWith("local:")) {
                com.bumptech.glide.Glide.with(itemView.getContext())
                        .load(android.net.Uri.parse(mediaData.substring(6)))
                        .frame(1000000)
                        .into(videoThumbnail);
            } else {
                loadEncryptedMedia(videoThumbnail, mediaData, true);
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

        public VideoReceivedViewHolder(@NonNull View itemView) {
            super(itemView);
            videoThumbnail = itemView.findViewById(R.id.video_thumbnail);
            textTimestamp = itemView.findViewById(R.id.text_timestamp);
        }

        void bind(Message message) {
            textTimestamp.setText(formatDate(message.getTimestamp()));
            loadEncryptedMedia(videoThumbnail, message.getMediaUrl(), true);
            itemView.setOnClickListener(v -> openFullMedia(v.getContext(), message));
        }
    }

    private static void openFullMedia(android.content.Context context, Message message) {
        String mediaData = message.getMediaUrl();
        if (mediaData == null) return;

        if (mediaData.startsWith("local:")) {
            android.content.Intent intent = new android.content.Intent(context, com.samechat37.FullMediaActivity.class);
            intent.putExtra("type", message.getType());
            intent.putExtra("url", mediaData.substring(6));
            context.startActivity(intent);
            return;
        }

        try {
            org.json.JSONObject json = new org.json.JSONObject(mediaData);
            // For full screen, we pass the encrypted info, FullMediaActivity will handle download/decrypt
            android.content.Intent intent = new android.content.Intent(context, com.samechat37.FullMediaActivity.class);
            intent.putExtra("type", message.getType());
            intent.putExtra("encrypted_info", mediaData);
            context.startActivity(intent);
        } catch (Exception e) {
            // Fallback
            android.content.Intent intent = new android.content.Intent(context, com.samechat37.FullMediaActivity.class);
            intent.putExtra("type", message.getType());
            intent.putExtra("url", mediaData);
            context.startActivity(intent);
        }
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
        
        try {
            if (mediaData.startsWith("local:")) {
                startMediaPlayer(mediaData.substring(6), position);
            } else {
                // Encrypted playback
                org.json.JSONObject json = new org.json.JSONObject(mediaData);
                String url = json.getString("u");
                String key = json.getString("k");

                com.samechat37.utils.GitHubStorage.downloadFile(url, new okhttp3.Callback() {
                    @Override
                    public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {}

                    @Override
                    public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) {
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                byte[] encryptedBytes = response.body().bytes();
                                javax.crypto.SecretKey secretKey = com.samechat37.utils.EncryptionManager.decodeKey(key);
                                byte[] decryptedBytes = com.samechat37.utils.EncryptionManager.decryptRaw(encryptedBytes, secretKey);

                                java.io.File tempFile = java.io.File.createTempFile("voice", ".3gp", context.getCacheDir());
                                java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
                                fos.write(decryptedBytes);
                                fos.close();

                                if (context instanceof android.app.Activity) {
                                    ((android.app.Activity) context).runOnUiThread(() -> startMediaPlayer(tempFile.getAbsolutePath(), position));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
        } catch (Exception e) {
            // Fallback for old plain URLs
            startMediaPlayer(mediaData, position);
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
