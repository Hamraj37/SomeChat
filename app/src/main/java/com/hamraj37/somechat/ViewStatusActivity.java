package com.hamraj37.somechat;

import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.models.Message;
import com.hamraj37.somechat.models.Status;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ViewStatusActivity extends BaseActivity {

    private Status status;
    private final List<Status.StatusItem> statusItems = new ArrayList<>();
    private int currentIndex = 0;
    private long duration = 5000; // 5 seconds per status default
    
    private ImageView statusImageView;
    private android.widget.VideoView statusVideoView;
    private android.widget.ProgressBar videoLoadingProgress;
    private TextView statusTextView;
    private ImageView profileImage;
    private TextView userNameText;
    private TextView statusTimeText;
    private LinearLayout progressContainer;
    private EditText replyInput;
    private View btnSendReply;
    private View replyContainer;
    private View viewsCountContainer;
    private TextView tvViewsCount;
    private ImageView btnLike;
    private TextView tvLikesCount;
    
    private final Handler handler = new Handler();
    private Runnable nextStatusRunnable;
    private final List<ProgressBar> progressBars = new ArrayList<>();
    private boolean isPaused = false;
    private String currentUserId;
    private String statusOwnerPublicKey = null;
    private DatabaseReference statusListenerRef;
    private ValueEventListener statusListener;
    private GestureDetector gestureDetector;

    private static final int[] BG_COLORS = {
        0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3,
        0xFF009688, 0xFF4CAF50, 0xFFFF9800, 0xFF795548, 0xFF607D8B
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        setContentView(R.layout.activity_view_status);

        status = (Status) getIntent().getSerializableExtra("status");
        if (status == null || status.getItems() == null || status.getItems().isEmpty()) {
            finish();
            return;
        }

        sortItems();

        statusImageView = findViewById(R.id.status_image_view);
        statusVideoView = findViewById(R.id.status_video_view);
        videoLoadingProgress = findViewById(R.id.video_loading_progress);
        statusTextView = findViewById(R.id.status_text_view);
        profileImage = findViewById(R.id.status_profile_image);
        userNameText = findViewById(R.id.status_user_name);
        statusTimeText = findViewById(R.id.status_time);
        progressContainer = findViewById(R.id.progress_container);
        replyInput = findViewById(R.id.reply_input);
        btnSendReply = findViewById(R.id.btn_send_reply);
        replyContainer = findViewById(R.id.reply_container);
        viewsCountContainer = findViewById(R.id.views_count_container);
        tvViewsCount = findViewById(R.id.tv_views_count);
        btnLike = findViewById(R.id.btn_like);
        tvLikesCount = findViewById(R.id.tv_likes_count);

        currentUserId = FirebaseAuth.getInstance().getUid();
        
        if (currentUserId != null && currentUserId.equals(status.getUserId())) {
            findViewById(R.id.reply_input_container).setVisibility(View.GONE);
            viewsCountContainer.setVisibility(View.VISIBLE);
        } else {
            fetchStatusOwnerPublicKey();
        }
        setupStatusListener();

        // Adjust UI elements to avoid system bars
        View topUiContainer = findViewById(R.id.top_ui_container);
        View rootLayout = findViewById(R.id.root_layout);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (topUiContainer != null) {
                topUiContainer.setPadding(0, systemBars.top, 0, 0);
            }
            
            // Adjust bottom reply UI for keyboard
            boolean isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            if (isKeyboardVisible) {
                pauseStatus();
                int keyboardHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                replyContainer.setTranslationY(-keyboardHeight);
            } else {
                replyContainer.setTranslationY(0);
                if (replyInput.hasFocus()) {
                    replyInput.clearFocus();
                }
                resumeStatus();
            }
            
            return insets;
        });

        setupProgressBars();
        setupReplyListeners();
        setupGestureDetector();
        updateUI();

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
        
        viewsCountContainer.setOnClickListener(v -> showInsights());
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 != null && e2 != null && e1.getY() - e2.getY() > 100) { // Swipe up
                    showInsights();
                    return true;
                }
                return super.onFling(e1, e2, velocityX, velocityY);
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return super.onSingleTapUp(e);
            }
        });

        View root = findViewById(R.id.root_layout);
        root.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pauseStatus();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                resumeStatus();
            }
            return true;
        });
        
        findViewById(R.id.reverse).setOnClickListener(v -> previousStatus());
        findViewById(R.id.skip).setOnClickListener(v -> nextStatus());
        
        // Add long press to pause on these views as well
        View.OnTouchListener pauseListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pauseStatus();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                resumeStatus();
            }
            return false; // Let click listener handle the rest
        };
        
        findViewById(R.id.reverse).setOnTouchListener(pauseListener);
        findViewById(R.id.skip).setOnTouchListener(pauseListener);
    }

    private void showInsights() {
        if (currentUserId == null || !currentUserId.equals(status.getUserId())) return;
        
        pauseStatus();
        Status.StatusItem currentItem = statusItems.get(currentIndex);
        StatusInsightsBottomSheet bottomSheet = StatusInsightsBottomSheet.newInstance(currentItem.getViews(), currentItem.getLikes());
        bottomSheet.show(getSupportFragmentManager(), "insights");
        
        // Resume when dismissed
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentDestroyed(@NonNull androidx.fragment.app.FragmentManager fm, @NonNull androidx.fragment.app.Fragment f) {
                super.onFragmentDestroyed(fm, f);
                if (f instanceof StatusInsightsBottomSheet) {
                    resumeStatus();
                    getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(this);
                }
            }
        }, false);
    }


    private void sortItems() {
        statusItems.clear();
        if (status.getItems() != null) {
            statusItems.addAll(status.getItems());
            statusItems.sort((i1, i2) -> Long.compare(i1.getTimestamp(), i2.getTimestamp()));
        }
    }

    private void setupStatusListener() {
        statusListenerRef = FirebaseDatabase.getInstance().getReference("statuses").child(status.getUserId());
        statusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Status updatedStatus = snapshot.getValue(Status.class);
                if (updatedStatus != null && !isFinishing() && !isDestroyed()) {
                    status = updatedStatus;
                    sortItems();
                    updateUI(); // Refresh UI with new counts/likes
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        statusListenerRef.addValueEventListener(statusListener);
    }

    private void recordView() {
        if (currentUserId == null || status == null || statusItems.isEmpty() || currentIndex >= statusItems.size()) return;
        
        Status.StatusItem item = statusItems.get(currentIndex);
        int dbIndex = -1;
        if (status.getItems() != null) {
            for (int i = 0; i < status.getItems().size(); i++) {
                if (status.getItems().get(i).getId().equals(item.getId())) {
                    dbIndex = i;
                    break;
                }
            }
        }
        
        if (dbIndex != -1) {
            DatabaseReference viewsRef = FirebaseDatabase.getInstance().getReference("statuses")
                    .child(status.getUserId())
                    .child("items")
                    .child(String.valueOf(dbIndex)) 
                    .child("views");
            
            viewsRef.child(currentUserId).setValue(System.currentTimeMillis());
        }
    }

    private void setupReplyListeners() {
        replyInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) pauseStatus();
            else resumeStatus();
        });

        replyInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSendReply.setVisibility(s.toString().trim().isEmpty() ? View.GONE : View.VISIBLE);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        btnSendReply.setOnClickListener(v -> sendReply());
        btnLike.setOnClickListener(v -> toggleLike());
    }

    private void toggleLike() {
        if (currentUserId == null || status == null || statusItems.isEmpty()) return;
        
        Status.StatusItem item = statusItems.get(currentIndex);
        int dbIndex = -1;
        if (status.getItems() != null) {
            for (int i = 0; i < status.getItems().size(); i++) {
                if (status.getItems().get(i).getId().equals(item.getId())) {
                    dbIndex = i;
                    break;
                }
            }
        }

        if (dbIndex == -1) return;

        DatabaseReference likeRef = FirebaseDatabase.getInstance().getReference("statuses")
                .child(status.getUserId())
                .child("items")
                .child(String.valueOf(dbIndex))
                .child("likes")
                .child(currentUserId);
        
        boolean currentlyLiked = item.getLikes() != null && Boolean.TRUE.equals(item.getLikes().get(currentUserId));
        
        if (currentlyLiked) {
            likeRef.removeValue();
        } else {
            likeRef.setValue(true);
        }
    }

    private void fetchStatusOwnerPublicKey() {
        FirebaseDatabase.getInstance().getReference("users").child(status.getUserId()).child("publicKey")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        statusOwnerPublicKey = snapshot.getValue(String.class);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void sendReply() {
        String text = replyInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        Status.StatusItem currentItem = statusItems.get(currentIndex);
        
        // Create unique chat ID
        String receiverId = status.getUserId();
        String chatId = currentUserId.compareTo(receiverId) < 0 
                ? currentUserId + "_" + receiverId 
                : receiverId + "_" + currentUserId;

        DatabaseReference chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId);
        String messageId = chatRef.push().getKey();

        // Prepare encrypted text
        String encryptedText = text;
        String myPubKey = com.hamraj37.somechat.utils.EncryptionManager.getMyPublicKey(this);
        if (statusOwnerPublicKey != null && myPubKey != null) {
            encryptedText = com.hamraj37.somechat.utils.EncryptionManager.encrypt(text, statusOwnerPublicKey, myPubKey);
        }

        Message message = new Message(messageId, currentUserId, receiverId, encryptedText, System.currentTimeMillis());
        
        // Add status reply context
        message.setReplyToId(currentItem.getId());
        message.setReplyToName(status.getUserName() + "'s status");
        message.setReplyToText("Status update"); 

        if (messageId != null) {
            chatRef.child(messageId).setValue(message).addOnSuccessListener(aVoid -> {
                replyInput.setText("");
                replyInput.clearFocus();
                // Hide keyboard
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(replyInput.getWindowToken(), 0);
                
                Toast.makeText(this, "Reply sent", Toast.LENGTH_SHORT).show();
                resumeStatus();
            });
        }
    }

    private void setupProgressBars() {
        progressContainer.removeAllViews();
        progressBars.clear();
        for (int i = 0; i < statusItems.size(); i++) {
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setLayoutParams(new LinearLayout.LayoutParams(0, 8, 1));
            pb.setPadding(4, 0, 4, 0);
            pb.setMax(100);
            pb.setProgress(0);
            progressContainer.addView(pb);
            progressBars.add(pb);
        }
    }

    private void updateUI() {
        if (statusItems.isEmpty() || isFinishing() || isDestroyed()) return;
        Status.StatusItem item = statusItems.get(currentIndex);
        
        userNameText.setText(status.getUserName());
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        statusTimeText.setText(sdf.format(new Date(item.getTimestamp())));

        if (status.getProfilePic() != null && !status.getProfilePic().isEmpty()) {
            Glide.with(this).load(status.getProfilePic()).circleCrop().into(profileImage);
        }

        statusVideoView.stopPlayback();
        statusVideoView.setVisibility(View.GONE);
        videoLoadingProgress.setVisibility(View.GONE);

        if ("text".equals(item.getType())) {
            statusImageView.setVisibility(View.GONE);
            statusTextView.setVisibility(View.VISIBLE);
            statusTextView.setText(item.getCaption());
            
            // Generate a background color based on status id
            int color = BG_COLORS[Math.abs(item.getId().hashCode()) % BG_COLORS.length];
            statusTextView.setBackgroundColor(color);
            duration = 5000;
            showContent();
        } else {
            loadMedia(item);
        }

        if (currentUserId != null && currentUserId.equals(status.getUserId())) {
            int viewsCount = (item.getViews() != null) ? item.getViews().size() : 0;
            tvViewsCount.setText(String.valueOf(viewsCount));
            
            int likesCount = (item.getLikes() != null) ? item.getLikes().size() : 0;
            tvLikesCount.setText(String.valueOf(likesCount));
        } else {
            recordView();
            
            boolean isLiked = item.getLikes() != null && Boolean.TRUE.equals(item.getLikes().get(currentUserId));
            btnLike.setImageResource(isLiked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
            btnLike.setColorFilter(isLiked ? 0xFFFF4444 : 0xFFFFFFFF); // Red if liked, white if not
        }
    }

    private void loadMedia(Status.StatusItem item) {
        String url = item.getMediaUrl();
        File localFile = getLocalFile(item);

        if (localFile.exists()) {
            displayMedia(localFile.getAbsolutePath(), item.getType());
        } else {
            videoLoadingProgress.setVisibility(View.VISIBLE);
            com.hamraj37.somechat.utils.GitHubStorage.downloadToFile(url, localFile, new com.hamraj37.somechat.utils.GitHubStorage.UploadCallback() {
                @Override
                public void onSuccess(String path) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            displayMedia(path, item.getType());
                        }
                    });
                }

                @Override
                public void onProgress(int progress) {}

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            videoLoadingProgress.setVisibility(View.GONE);
                            Toast.makeText(ViewStatusActivity.this, "Failed to download media", Toast.LENGTH_SHORT).show();
                            nextStatus();
                        }
                    });
                }
            });
        }
    }

    private void displayMedia(String path, String type) {
        if (isFinishing() || isDestroyed()) return;
        videoLoadingProgress.setVisibility(View.GONE);
        if ("video".equals(type)) {
            statusImageView.setVisibility(View.GONE);
            statusTextView.setVisibility(View.GONE);
            statusVideoView.setVisibility(View.VISIBLE);
            statusVideoView.setVideoPath(path);
            statusVideoView.setOnPreparedListener(mp -> {
                mp.setLooping(false);
                duration = mp.getDuration();
                statusVideoView.start();
                showContent();
                
                mp.setOnInfoListener((mp1, what, extra) -> {
                    if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                        videoLoadingProgress.setVisibility(View.VISIBLE);
                        pauseStatus();
                    } else if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                        videoLoadingProgress.setVisibility(View.GONE);
                        resumeStatus();
                    }
                    return false;
                });
            });
            statusVideoView.setOnCompletionListener(mp -> nextStatus());
            statusVideoView.setOnErrorListener((mp, what, extra) -> {
                nextStatus();
                return true;
            });
        } else {
            statusImageView.setVisibility(View.VISIBLE);
            statusTextView.setVisibility(View.GONE);
            Glide.with(this).load(path).into(statusImageView);
            duration = 5000;
            showContent();
        }
    }

    private void showContent() {
        if (isFinishing() || isDestroyed()) return;
        for (int i = 0; i < progressBars.size(); i++) {
            if (i < currentIndex) progressBars.get(i).setProgress(100);
            else progressBars.get(i).setProgress(0);
        }
        startTimer();
    }

    private File getLocalFile(Status.StatusItem item) {
        String extension = item.getType().equals("video") ? ".mp4" : ".jpg";
        String fileName = "status_" + item.getId() + extension;
        return new File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), fileName);
    }


    private int progressValue = 0;
    private void startTimer() {
        handler.removeCallbacksAndMessages(null);
        progressValue = 0;
        nextStatusRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPaused) {
                    handler.postDelayed(this, 100);
                    return;
                }
                if (progressValue <= 100) {
                    progressBars.get(currentIndex).setProgress(progressValue);
                    progressValue += 2; // Increment progress
                    handler.postDelayed(this, duration / 50);
                } else {
                    nextStatus();
                }
            }
        };
        handler.post(nextStatusRunnable);
    }

    private void pauseStatus() {
        isPaused = true;
        if (statusVideoView.getVisibility() == View.VISIBLE && statusVideoView.isPlaying()) {
            statusVideoView.pause();
        }
    }

    private void resumeStatus() {
        isPaused = false;
        if (statusVideoView.getVisibility() == View.VISIBLE && !statusVideoView.isPlaying()) {
            statusVideoView.start();
        }
    }

    private void nextStatus() {
        if (currentIndex < statusItems.size() - 1) {
            currentIndex++;
            updateUI();
        } else {
            finish();
        }
    }

    private void previousStatus() {
        if (currentIndex > 0) {
            currentIndex--;
            updateUI();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (statusListenerRef != null && statusListener != null) {
            statusListenerRef.removeEventListener(statusListener);
        }
    }
}
