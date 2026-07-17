package com.hamraj37.somechat;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.models.Status;
import com.hamraj37.somechat.utils.GitHubStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatusPreviewActivity extends BaseActivity {

    private Uri mediaUri;
    private String type;
    private ImageView previewImage;
    private VideoView previewVideo;
    private EditText captionInput;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Full screen immersive
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        setContentView(R.layout.activity_status_preview);

        mediaUri = getIntent().getParcelableExtra("uri");
        type = getIntent().getStringExtra("type");
        currentUserId = FirebaseAuth.getInstance().getUid();

        if (mediaUri == null || type == null) {
            finish();
            return;
        }

        previewImage = findViewById(R.id.preview_image);
        previewVideo = findViewById(R.id.preview_video);
        captionInput = findViewById(R.id.caption_input);
        View btnPost = findViewById(R.id.btn_post);

        setupMedia();

        // Adjust UI for system bars and notches
        View topBar = findViewById(R.id.top_bar);
        View bottomContainer = findViewById(R.id.bottom_container);
        View rootLayout = findViewById(R.id.status_preview_root);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            
            topBar.setPadding(topBar.getPaddingLeft(), systemBars.top, topBar.getPaddingRight(), topBar.getPaddingBottom());
            
            boolean isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.ime() | WindowInsetsCompat.Type.navigationBars()).bottom;
            
            if (isKeyboardVisible) {
                bottomContainer.setTranslationY(-bottomInset);
            } else {
                bottomContainer.setTranslationY(0);
                // Also account for nav bar via padding if needed, but translationY(0) is cleaner if it's pinned to bottom
            }
            
            return insets;
        });

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
        btnPost.setOnClickListener(v -> performUpload());
    }

    private void setupMedia() {
        if ("video".equals(type)) {
            previewImage.setVisibility(View.GONE);
            previewVideo.setVisibility(View.VISIBLE);
            previewVideo.setVideoURI(mediaUri);
            previewVideo.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                previewVideo.start();
            });
        } else {
            previewImage.setVisibility(View.VISIBLE);
            previewVideo.setVisibility(View.GONE);
            Glide.with(this).load(mediaUri).into(previewImage);
        }
    }

    private void performUpload() {
        String caption = captionInput.getText().toString().trim();
        String extension = "video".equals(type) ? ".mp4" : ".jpg";

        LinearProgressIndicator progressIndicator = new LinearProgressIndicator(this);
        progressIndicator.setIndeterminate(false);
        progressIndicator.setMax(100);
        
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.uploading_status)
                .setMessage("Please wait...")
                .setView(progressIndicator)
                .setCancelable(false)
                .show();

        try {
            java.io.InputStream is = getContentResolver().openInputStream(mediaUri);
            if (is == null) {
                progressDialog.dismiss();
                return;
            }
            
            java.io.ByteArrayOutputStream byteBuffer = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            byte[] bytes = byteBuffer.toByteArray();
            is.close();

            String fileName = currentUserId + "_" + System.currentTimeMillis() + extension;
            GitHubStorage.uploadBytes(bytes, "statuses", fileName, new GitHubStorage.UploadCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        saveStatusToFirebase(downloadUrl, type, caption);
                    });
                }

                @Override
                public void onProgress(int progress) {
                    runOnUiThread(() -> {
                        progressIndicator.setProgress(progress);
                        progressDialog.setMessage("Uploading: " + progress + "%");
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(StatusPreviewActivity.this, "Failed to upload status", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            progressDialog.dismiss();
            e.printStackTrace();
        }
    }

    private void saveStatusToFirebase(String mediaUrl, String type, String caption) {
        DatabaseReference myStatusRef = FirebaseDatabase.getInstance().getReference("statuses").child(currentUserId);
        
        FirebaseDatabase.getInstance().getReference("users").child(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String name = snapshot.child("displayName").getValue(String.class);
                        String profile = snapshot.child("photoUrl").getValue(String.class);
                        
                        Status.StatusItem newItem = new Status.StatusItem(
                                String.valueOf(System.currentTimeMillis()),
                                mediaUrl,
                                type,
                                System.currentTimeMillis(),
                                caption
                        );

                        myStatusRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot statusSnapshot) {
                                Status existingStatus = statusSnapshot.getValue(Status.class);
                                if (existingStatus == null) {
                                    List<Status.StatusItem> items = new ArrayList<>();
                                    items.add(newItem);
                                    Status s = new Status(currentUserId, currentUserId, name, profile, System.currentTimeMillis(), items);
                                    myStatusRef.setValue(s).addOnCompleteListener(task -> {
                                        Toast.makeText(StatusPreviewActivity.this, "Status updated", Toast.LENGTH_SHORT).show();
                                        finish();
                                    });
                                } else {
                                    List<Status.StatusItem> items = existingStatus.getItems();
                                    if (items == null) items = new ArrayList<>();
                                    
                                    // Clean up old items before adding new one
                                    List<Status.StatusItem> validItems = new ArrayList<>();
                                    long yesterday = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
                                    for (Status.StatusItem item : items) {
                                        if (item.getTimestamp() > yesterday) {
                                            validItems.add(item);
                                        }
                                    }
                                    validItems.add(newItem);
                                    
                                    Map<String, Object> updates = new HashMap<>();
                                    updates.put("items", validItems);
                                    updates.put("lastUpdated", System.currentTimeMillis());
                                    myStatusRef.updateChildren(updates).addOnCompleteListener(task -> {
                                        Toast.makeText(StatusPreviewActivity.this, "Status updated", Toast.LENGTH_SHORT).show();
                                        finish();
                                    });
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                finish();
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        finish();
                    }
                });
    }
}
