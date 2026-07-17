package com.hamraj37.somechat;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.progressindicator.CircularProgressIndicator;

public class FullMediaActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_media);

        // Ensure status bar icons are white on black background
        androidx.core.view.WindowInsetsControllerCompat windowInsetsController = 
            new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        windowInsetsController.setAppearanceLightStatusBars(false);
        windowInsetsController.setAppearanceLightNavigationBars(false);

        ShapeableImageView fullImage = findViewById(R.id.full_image);
        VideoView fullVideo = findViewById(R.id.full_video);
        CircularProgressIndicator loading = findViewById(R.id.loading_progress);
        ImageButton btnBack = findViewById(R.id.btn_back);

        String type = getIntent().getStringExtra("type");
        String mediaUrl = getIntent().getStringExtra("url");
        String encryptedInfo = getIntent().getStringExtra("encrypted_info");
        String messageId = getIntent().getStringExtra("message_id");

        btnBack.setOnClickListener(v -> finish());

        if (encryptedInfo != null) {
            // Check if already downloaded locally
            String id = messageId;
            if (id == null) {
                try {
                    org.json.JSONObject json = new org.json.JSONObject(encryptedInfo);
                    String url = json.getString("u");
                    id = url.substring(url.lastIndexOf('/') + 1);
                    if (id.contains(".")) id = id.substring(0, id.lastIndexOf('.'));
                } catch (Exception ignored) {}
            }

            java.io.File localFile = com.hamraj37.somechat.utils.MediaUtils.getLocalFileForMedia(this, type, id);
            if (localFile.exists()) {
                displayMedia(localFile.getAbsolutePath(), type, fullImage, fullVideo, loading);
            } else {
                downloadAndDecrypt(encryptedInfo, type, fullImage, fullVideo, loading, id);
            }
        } else if (mediaUrl != null) {
            displayMedia(mediaUrl, type, fullImage, fullVideo, loading);
        }
    }

    private void downloadAndDecrypt(String encryptedInfo, String type, ShapeableImageView fullImage, VideoView fullVideo, CircularProgressIndicator loading, String mediaId) {
        try {
            org.json.JSONObject json = new org.json.JSONObject(encryptedInfo);
            String url = json.getString("u");
            String key = json.getString("k");

            com.hamraj37.somechat.utils.GitHubStorage.downloadFile(url, new okhttp3.Callback() {
                @Override
                public void onFailure(@androidx.annotation.NonNull okhttp3.Call call, @androidx.annotation.NonNull java.io.IOException e) {
                    runOnUiThread(() -> loading.setVisibility(View.GONE));
                }

                @Override
                public void onResponse(@androidx.annotation.NonNull okhttp3.Call call, @androidx.annotation.NonNull okhttp3.Response response) throws java.io.IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            byte[] encryptedBytes = response.body().bytes();
                            javax.crypto.SecretKey secretKey = com.hamraj37.somechat.utils.EncryptionManager.decodeKey(key);
                            byte[] decryptedBytes = com.hamraj37.somechat.utils.EncryptionManager.decryptRaw(encryptedBytes, secretKey);

                            // Save locally
                            com.hamraj37.somechat.utils.MediaUtils.saveMediaLocally(FullMediaActivity.this, decryptedBytes, type, mediaId);
                            java.io.File savedFile = com.hamraj37.somechat.utils.MediaUtils.getLocalFileForMedia(FullMediaActivity.this, type, mediaId);

                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                if ("image".equals(type)) {
                                    fullImage.setVisibility(View.VISIBLE);
                                    com.bumptech.glide.Glide.with(FullMediaActivity.this).load(savedFile).into(fullImage);
                                    loading.setVisibility(View.GONE);
                                } else {
                                    // Show video thumbnail while preparing
                                    showVideoThumbnail(decryptedBytes, fullImage);
                                    fullImage.setVisibility(View.VISIBLE);
                                    displayMedia(savedFile.getAbsolutePath(), type, fullImage, fullVideo, loading);
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(() -> loading.setVisibility(View.GONE));
                        }
                    } else {
                        runOnUiThread(() -> loading.setVisibility(View.GONE));
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            loading.setVisibility(View.GONE);
        }
    }

    private void showVideoThumbnail(byte[] videoBytes, ShapeableImageView imageView) {
        new Thread(() -> {
            try {
                android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                retriever.setDataSource(new android.media.MediaDataSource() {
                    @Override
                    public int readAt(long position, byte[] buffer, int offset, int size) {
                        if (position >= videoBytes.length) return -1;
                        int length = Math.min(size, (int) (videoBytes.length - position));
                        System.arraycopy(videoBytes, (int) position, buffer, offset, length);
                        return length;
                    }
                    @Override public long getSize() { return videoBytes.length; }
                    @Override public void close() {}
                });
                android.graphics.Bitmap bitmap = retriever.getFrameAtTime(0);
                retriever.release();
                if (bitmap != null) {
                    runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void displayMedia(String path, String type, ShapeableImageView fullImage, VideoView fullVideo, CircularProgressIndicator loading) {
        if ("image".equals(type)) {
            fullImage.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(path)
                    .into(fullImage);
            loading.setVisibility(View.GONE);
        } else if ("video".equals(type)) {
            fullVideo.setVisibility(View.VISIBLE);
            if (path.startsWith("content://") || path.startsWith("file://")) {
                fullVideo.setVideoURI(Uri.parse(path));
            } else {
                fullVideo.setVideoPath(path);
            }
            fullVideo.setOnPreparedListener(mp -> {
                loading.setVisibility(View.GONE);
                fullImage.setVisibility(View.GONE);
                fullVideo.start();
            });
            fullVideo.setOnErrorListener((mp, what, extra) -> {
                loading.setVisibility(View.GONE);
                fullImage.setVisibility(View.GONE);
                return false;
            });
            
            android.widget.MediaController mediaController = new android.widget.MediaController(this);
            mediaController.setAnchorView(fullVideo);
            fullVideo.setMediaController(mediaController);
        }
    }
}
