package com.hamraj37.somechat.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.hamraj37.somechat.R;
import com.hamraj37.somechat.models.Message;
import com.hamraj37.somechat.utils.EncryptionManager;
import com.hamraj37.somechat.utils.GitHubStorage;
import com.hamraj37.somechat.utils.MediaUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.List;

import javax.crypto.SecretKey;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.MediaViewHolder> {

    private final Context context;
    private final List<Message> mediaMessages;
    private final OnMediaClickListener listener;

    public interface OnMediaClickListener {
        void onMediaClick(Message message);
    }

    public MediaAdapter(Context context, List<Message> mediaMessages, OnMediaClickListener listener) {
        this.context = context;
        this.mediaMessages = mediaMessages;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_media_thumbnail, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        Message message = mediaMessages.get(position);
        String type = message.getType();
        holder.videoIcon.setVisibility("video".equalsIgnoreCase(type) ? View.VISIBLE : View.GONE);

        loadThumbnail(holder.thumbnail, message);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onMediaClick(message);
        });
    }

    private void loadThumbnail(ImageView imageView, Message message) {
        String mediaInfo = message.getMediaUrl();
        if (mediaInfo == null || mediaInfo.isEmpty()) {
            imageView.setImageResource(R.color.whatsapp_green_dark); // Placeholder
            return;
        }

        String type = message.getType();
        String messageId = message.getMessageId();
        File localFile = MediaUtils.getLocalFileForMedia(context, type, messageId);

        if (localFile.exists()) {
            displayLocalMedia(imageView, localFile, "video".equalsIgnoreCase(type));
            return;
        }

        // Not local, try to download if it's a JSON info
        if (mediaInfo.startsWith("{")) {
            imageView.setImageResource(R.color.whatsapp_green_dark);
            imageView.setTag(messageId);
            
            try {
                JSONObject json = new JSONObject(mediaInfo);
                String url = json.getString("u");
                String key = json.getString("k");

                GitHubStorage.downloadFile(url, new GitHubStorage.DownloadCallback() {
                    @Override
                    public void onProgress(int progress) {}

                    @Override
                    public void onFailure(@NonNull Exception e) {
                        imageView.post(() -> {
                            if (messageId.equals(imageView.getTag())) {
                                imageView.setImageResource(android.R.drawable.ic_menu_report_image);
                            }
                        });
                    }

                    @Override
                    public void onSuccess(byte[] data) {
                        try {
                            SecretKey secretKey = EncryptionManager.decodeKey(key);
                            byte[] decryptedBytes = EncryptionManager.decryptRaw(data, secretKey);
                            MediaUtils.saveMediaLocally(context, decryptedBytes, type, messageId);

                            imageView.post(() -> {
                                if (messageId.equals(imageView.getTag())) {
                                    displayLocalMedia(imageView, localFile, "video".equalsIgnoreCase(type));
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Direct URL (fallback)
            Glide.with(context)
                    .load(mediaInfo)
                    .centerCrop()
                    .placeholder(R.color.whatsapp_green_dark)
                    .into(imageView);
        }
    }

    private void displayLocalMedia(ImageView imageView, File file, boolean isVideo) {
        if (isVideo) {
            new Thread(() -> {
                try {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(file.getAbsolutePath());
                    Bitmap bitmap = retriever.getFrameAtTime(0);
                    retriever.release();
                    if (bitmap != null) {
                        imageView.post(() -> imageView.setImageBitmap(bitmap));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        } else {
            Glide.with(context)
                    .load(file)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .placeholder(R.color.whatsapp_green_dark)
                    .into(imageView);
        }
    }

    @Override
    public int getItemCount() {
        return mediaMessages.size();
    }

    public static class MediaViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        ImageView videoIcon;

        public MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            videoIcon = itemView.findViewById(R.id.video_icon);
        }
    }
}
