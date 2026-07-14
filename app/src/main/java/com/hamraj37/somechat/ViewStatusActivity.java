package com.hamraj37.somechat;

import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ViewStatusActivity extends BaseActivity {

    private Status status;
    private int currentIndex = 0;
    private long duration = 5000; // 5 seconds per status
    
    private ImageView statusImageView;
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
    
    private Handler handler = new Handler();
    private Runnable nextStatusRunnable;
    private List<ProgressBar> progressBars = new ArrayList<>();
    private boolean isPaused = false;
    private String currentUserId;
    private String statusOwnerPublicKey = null;
    private DatabaseReference myStatusListenerRef;
    private ValueEventListener myStatusListener;

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

        statusImageView = findViewById(R.id.status_image_view);
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

        currentUserId = FirebaseAuth.getInstance().getUid();
        
        if (currentUserId != null && currentUserId.equals(status.getUserId())) {
            findViewById(R.id.reply_input_container).setVisibility(View.GONE);
            findViewById(R.id.tv_reply_label).setVisibility(View.GONE);
            viewsCountContainer.setVisibility(View.VISIBLE);
            setupMyStatusListener();
        } else {
            fetchStatusOwnerPublicKey();
        }

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
        updateUI();

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
        findViewById(R.id.skip).setOnClickListener(v -> nextStatus());
        findViewById(R.id.reverse).setOnClickListener(v -> previousStatus());
        
        startTimer();
    }

    private void setupMyStatusListener() {
        myStatusListenerRef = FirebaseDatabase.getInstance().getReference("statuses").child(currentUserId);
        myStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Status updatedStatus = snapshot.getValue(Status.class);
                if (updatedStatus != null) {
                    status = updatedStatus;
                    updateUI(); // Refresh UI with new counts
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        myStatusListenerRef.addValueEventListener(myStatusListener);
    }

    private void recordView() {
        if (currentUserId == null || status == null || status.getItems() == null || currentIndex >= status.getItems().size()) return;
        
        Status.StatusItem item = status.getItems().get(currentIndex);
        DatabaseReference viewsRef = FirebaseDatabase.getInstance().getReference("statuses")
                .child(status.getUserId())
                .child("items")
                .child(String.valueOf(currentIndex)) // Using index as child if saved as list
                .child("views");
        
        viewsRef.child(currentUserId).setValue(System.currentTimeMillis());
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

        Status.StatusItem currentItem = status.getItems().get(currentIndex);
        
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
        for (int i = 0; i < status.getItems().size(); i++) {
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
        Status.StatusItem item = status.getItems().get(currentIndex);
        
        userNameText.setText(status.getUserName());
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        statusTimeText.setText(sdf.format(new Date(item.getTimestamp())));

        if (status.getProfilePic() != null && !status.getProfilePic().isEmpty()) {
            Glide.with(this).load(status.getProfilePic()).circleCrop().into(profileImage);
        }

        if ("text".equals(item.getType())) {
            statusImageView.setVisibility(View.GONE);
            statusTextView.setVisibility(View.VISIBLE);
            statusTextView.setText(item.getCaption());
            
            // Generate a background color based on status id
            int color = BG_COLORS[Math.abs(item.getId().hashCode()) % BG_COLORS.length];
            statusTextView.setBackgroundColor(color);
        } else {
            statusImageView.setVisibility(View.VISIBLE);
            statusTextView.setVisibility(View.GONE);
            Glide.with(this).load(item.getMediaUrl()).into(statusImageView);
        }

        if (currentUserId != null && currentUserId.equals(status.getUserId())) {
            int viewsCount = (item.getViews() != null) ? item.getViews().size() : 0;
            tvViewsCount.setText(String.valueOf(viewsCount));
        } else {
            recordView();
        }

        for (int i = 0; i < progressBars.size(); i++) {
            if (i < currentIndex) progressBars.get(i).setProgress(100);
            else progressBars.get(i).setProgress(0);
        }
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
    }

    private void resumeStatus() {
        isPaused = false;
    }

    private void nextStatus() {
        if (currentIndex < status.getItems().size() - 1) {
            currentIndex++;
            updateUI();
            startTimer();
        } else {
            finish();
        }
    }

    private void previousStatus() {
        if (currentIndex > 0) {
            currentIndex--;
            updateUI();
            startTimer();
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
        if (myStatusListenerRef != null && myStatusListener != null) {
            myStatusListenerRef.removeEventListener(myStatusListener);
        }
    }
}
