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
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.models.Message;
import com.hamraj37.somechat.models.Highlight;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.UUID;
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
    private ImageView btnMore;
    private ImageView ivHighlightedStar;
    private TextView tvCaption;
    private boolean isStatusLoadedFromDb = false;
    
    private String currentlyShowingId = null;
    private final Handler handler = new Handler();
    private Runnable nextStatusRunnable;
    private final List<ProgressBar> progressBars = new ArrayList<>();
    private boolean isPaused = false;
    private String currentUserId;
    private String statusOwnerPublicKey = null;
    private DatabaseReference statusListenerRef;
    private ValueEventListener statusListener;
    private GestureDetector gestureDetector;
    private boolean isHighlight = false;
    private final List<String> highlightedItemIds = new ArrayList<>();

    private static final int[] BG_COLORS = {
        0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3,
        0xFF009688, 0xFF4CAF50, 0xFFFF9800, 0xFF795548, 0xFF607D8B
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Truly immersive full screen
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        
        setContentView(R.layout.activity_view_status);

        status = (Status) getIntent().getSerializableExtra("status");
        if (status == null || status.getItems() == null || status.getItems().isEmpty()) {
            finish();
            return;
        }
        isHighlight = getIntent().getBooleanExtra("is_highlight", false);

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
        btnMore = findViewById(R.id.btn_more);
        ivHighlightedStar = findViewById(R.id.iv_highlighted_star);
        tvCaption = findViewById(R.id.tv_caption);

        currentUserId = FirebaseAuth.getInstance().getUid();
        
        if (currentUserId != null && currentUserId.equals(status.getUserId())) {
            findViewById(R.id.reply_input_container).setVisibility(View.GONE);
            viewsCountContainer.setVisibility(View.VISIBLE);
            btnMore.setVisibility(View.VISIBLE);
            btnMore.setOnClickListener(this::showMoreOptions);
        } else {
            fetchStatusOwnerPublicKey();
            viewsCountContainer.setVisibility(View.GONE);
            btnMore.setVisibility(View.GONE);
        }
        loadHighlightedIds();
        setupStatusListener();

        // Adjust UI elements to avoid system bars and notches
        View topUiContainer = findViewById(R.id.top_ui_container);
        View rootLayout = findViewById(R.id.root_layout);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            
            // Add extra padding to ensure progress bars are below status bar area/notch
            int topPadding = systemBars.top;
            if (topPadding == 0) {
                // Fallback if insets are hidden: use a safe default like 32dp
                topPadding = (int) (32 * getResources().getDisplayMetrics().density);
            }
            
            if (topUiContainer != null) {
                topUiContainer.setPadding(0, topPadding, 0, 0);
            }
            
            // Adjust bottom reply UI for keyboard
            boolean isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int bottomInset = insets.getInsets(WindowInsetsCompat.Type.ime() | WindowInsetsCompat.Type.navigationBars()).bottom;
            
            if (isKeyboardVisible) {
                pauseStatus();
                replyContainer.setTranslationY(-bottomInset);
            } else {
                replyContainer.setTranslationY(-bottomInset); // Always account for nav bar if present
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

    private void showMoreOptions(View v) {
        pauseStatus();
        android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(this, v);
        
        if (currentUserId != null && currentUserId.equals(status.getUserId())) {
            if (isHighlight) {
                popupMenu.getMenu().add(0, 1, 0, R.string.remove_from_highlight);
            } else {
                popupMenu.getMenu().add(0, 2, 0, R.string.add_to_highlight);
                popupMenu.getMenu().add(0, 3, 0, R.string.delete);
            }
        } else {
            popupMenu.getMenu().add(0, 4, 0, "Report");
        }
        
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 3) {
                deleteCurrentStatus();
            } else if (id == 2) {
                showAddToHighlightDialog();
            } else if (id == 1) {
                removeFromHighlight();
            } else if (id == 4) {
                android.widget.Toast.makeText(this, R.string.status_reported, android.widget.Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popupMenu.setOnDismissListener(menu -> resumeStatus());
        popupMenu.show();
    }

    private void showAddToHighlightDialog() {
        FirebaseDatabase.getInstance().getReference("highlights").child(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Highlight> existingHighlights = new ArrayList<>();
                        List<String> highlightNames = new ArrayList<>();
                        highlightNames.add(getString(R.string.new_highlight));

                        for (DataSnapshot ds : snapshot.getChildren()) {
                            Highlight h = ds.getValue(Highlight.class);
                            if (h != null) {
                                existingHighlights.add(h);
                                highlightNames.add(h.getTitle());
                            }
                        }

                        new MaterialAlertDialogBuilder(ViewStatusActivity.this)
                                .setTitle(R.string.add_to_highlight)
                                .setItems(highlightNames.toArray(new String[0]), (dialog, which) -> {
                                    if (which == 0) {
                                        showCreateHighlightDialog();
                                    } else {
                                        addToExistingHighlight(existingHighlights.get(which - 1));
                                    }
                                })
                                .setOnDismissListener(d -> resumeStatus())
                                .show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        resumeStatus();
                    }
                });
    }

    private void showCreateHighlightDialog() {
        EditText input = new EditText(this);
        input.setHint("Highlight Name");
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.addView(input);
        input.setPadding(padding, padding, padding, padding);

        new MaterialAlertDialogBuilder(this)
                .setTitle("New Highlight")
                .setView(container)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        createNewHighlight(name);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createNewHighlight(String title) {
        String highlightId = UUID.randomUUID().toString();
        Status.StatusItem currentItem = statusItems.get(currentIndex);
        Highlight highlight = new Highlight(highlightId, currentUserId, title, currentItem.getMediaUrl());
        highlight.getItems().add(currentItem);

        FirebaseDatabase.getInstance().getReference("highlights")
                .child(currentUserId)
                .child(highlightId)
                .setValue(highlight)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Added to " + title, Toast.LENGTH_SHORT).show());
    }

    private void addToExistingHighlight(Highlight highlight) {
        Status.StatusItem currentItem = statusItems.get(currentIndex);
        
        // Check if item already exists in highlight
        for (Status.StatusItem item : highlight.getItems()) {
            if (item.getId().equals(currentItem.getId())) {
                Toast.makeText(this, "Already in " + highlight.getTitle(), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        highlight.getItems().add(currentItem);
        FirebaseDatabase.getInstance().getReference("highlights")
                .child(currentUserId)
                .child(highlight.getHighlightId())
                .setValue(highlight)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Added to " + highlight.getTitle(), Toast.LENGTH_SHORT).show());
    }

    private void removeFromHighlight() {
        if (!isHighlight || statusItems.isEmpty() || currentIndex >= statusItems.size()) return;
        
        Status.StatusItem itemToRemove = statusItems.get(currentIndex);
        
        // Find the index in the current status.items list
        int originalIndex = -1;
        if (status.getItems() != null) {
            for (int i = 0; i < status.getItems().size(); i++) {
                if (status.getItems().get(i).getId().equals(itemToRemove.getId())) {
                    originalIndex = i;
                    break;
                }
            }
        }
        
        if (originalIndex != -1) {
            List<Status.StatusItem> updatedItems = new ArrayList<>(status.getItems());
            updatedItems.remove(originalIndex);
            
            if (updatedItems.isEmpty()) {
                // If last item removed, delete the whole highlight
                getBaseRef().removeValue().addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, R.string.highlight_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                });
            } else {
                getBaseRef().child("items").setValue(updatedItems).addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, R.string.removed_from_highlight, Toast.LENGTH_SHORT).show();
                    // Listener will trigger updateUI
                }).addOnFailureListener(e -> {
                    Toast.makeText(this, R.string.failed_to_remove, Toast.LENGTH_SHORT).show();
                    resumeStatus();
                });
            }
        }
    }

    private void deleteCurrentStatus() {
        if (statusItems.isEmpty() || currentIndex >= statusItems.size()) return;
        
        Status.StatusItem itemToDelete = statusItems.get(currentIndex);
        
        // Find the index in the original status.items list
        int originalIndex = -1;
        if (status.getItems() != null) {
            for (int i = 0; i < status.getItems().size(); i++) {
                if (status.getItems().get(i).getId().equals(itemToDelete.getId())) {
                    originalIndex = i;
                    break;
                }
            }
        }
        
        if (originalIndex != -1) {
            // Remove the item from the list and update the entire items list to maintain indexing
            List<Status.StatusItem> updatedItems = new ArrayList<>(status.getItems());
            updatedItems.remove(originalIndex);
            
            DatabaseReference itemsRef = getBaseRef().child("items");
            
            itemsRef.setValue(updatedItems).addOnSuccessListener(aVoid -> {
                String message = isHighlight ? getString(R.string.removed_from_highlight) : getString(R.string.status_deleted);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                if (updatedItems.isEmpty()) {
                    if (isHighlight) {
                        getBaseRef().removeValue();
                    }
                    finish();
                } else {
                    // The value listener will trigger updateUI
                    // We might need to adjust currentIndex if we deleted the last item
                    if (currentIndex >= updatedItems.size()) {
                        currentIndex = updatedItems.size() - 1;
                    }
                    // updateUI() will be called by listener, but we can call it here for immediate feedback if needed
                    // though listener is usually fast.
                }
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to delete status", Toast.LENGTH_SHORT).show();
                resumeStatus();
            });
        }
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

    private DatabaseReference getBaseRef() {
        if (isHighlight) {
            return FirebaseDatabase.getInstance().getReference("highlights")
                    .child(status.getUserId())
                    .child(status.getStatusId());
        } else {
            return FirebaseDatabase.getInstance().getReference("statuses")
                    .child(status.getUserId());
        }
    }

    private void setupStatusListener() {
        statusListenerRef = getBaseRef();
        statusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isHighlight) {
                    Highlight updatedHighlight = snapshot.getValue(Highlight.class);
                    if (updatedHighlight != null && !isFinishing() && !isDestroyed()) {
                        status.setItems(updatedHighlight.getItems());
                        sortItems();
                        updateUI();
                    }
                } else {
                    Status updatedStatus = snapshot.getValue(Status.class);
                    if (updatedStatus != null && !isFinishing() && !isDestroyed()) {
                        status = updatedStatus;
                        isStatusLoadedFromDb = true;
                        sortItems();
                        updateUI();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        statusListenerRef.addValueEventListener(statusListener);
    }

    private void recordView() {
        if (currentUserId == null || status == null || statusItems.isEmpty() || currentIndex >= statusItems.size() || !isStatusLoadedFromDb) return;
        
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
            DatabaseReference viewsRef = getBaseRef()
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
        if (currentUserId == null || status == null || statusItems.isEmpty() || !isStatusLoadedFromDb) return;
        
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

        DatabaseReference likeRef = getBaseRef()
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

        // Only reload media if it's a different item
        if (currentlyShowingId == null || !currentlyShowingId.equals(item.getId())) {
            currentlyShowingId = item.getId();
            
            statusVideoView.stopPlayback();
            statusVideoView.setVisibility(View.GONE);
            statusImageView.setVisibility(View.GONE);
            statusTextView.setVisibility(View.GONE);
            videoLoadingProgress.setVisibility(View.GONE);

            if ("text".equals(item.getType())) {
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

            if (currentUserId == null || !currentUserId.equals(status.getUserId())) {
                recordView();
            }
        }

        if (item.getCaption() != null && !item.getCaption().isEmpty() && !"text".equals(item.getType())) {
            tvCaption.setVisibility(View.VISIBLE);
            tvCaption.setText(item.getCaption());
        } else {
            tvCaption.setVisibility(View.GONE);
        }

        if (currentUserId != null && currentUserId.equals(status.getUserId())) {
            int viewsCount = (item.getViews() != null) ? item.getViews().size() : 0;
            tvViewsCount.setText(String.valueOf(viewsCount));
            
            int likesCount = (item.getLikes() != null) ? item.getLikes().size() : 0;
            tvLikesCount.setText(String.valueOf(likesCount));
        } else {
            boolean isLiked = item.getLikes() != null && Boolean.TRUE.equals(item.getLikes().get(currentUserId));
            btnLike.setImageResource(isLiked ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
            btnLike.setColorFilter(isLiked ? 0xFFFF4444 : 0xFFFFFFFF); // Red if liked, white if not
        }

        if (highlightedItemIds.contains(item.getId())) {
            ivHighlightedStar.setVisibility(View.VISIBLE);
        } else {
            ivHighlightedStar.setVisibility(View.GONE);
        }
    }

    private void loadHighlightedIds() {
        FirebaseDatabase.getInstance().getReference("highlights").child(status.getUserId())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        highlightedItemIds.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            Highlight h = ds.getValue(Highlight.class);
                            if (h != null && h.getItems() != null) {
                                for (Status.StatusItem si : h.getItems()) {
                                    highlightedItemIds.add(si.getId());
                                }
                            }
                        }
                        if (!isFinishing() && !isDestroyed()) {
                            updateUI();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void loadMedia(Status.StatusItem item) {
        String url = item.getMediaUrl();
        
        if ("image".equals(item.getType())) {
            displayMedia(url, "image");
            return;
        }

        File localFile = getLocalFile(item);
        if (localFile.exists()) {
            displayMedia(localFile.getAbsolutePath(), item.getType());
        } else {
            videoLoadingProgress.setVisibility(View.VISIBLE);
            com.hamraj37.somechat.utils.GitHubStorage.downloadToFile(url, localFile, new com.hamraj37.somechat.utils.GitHubStorage.UploadCallback() {
                @Override
                public void onSuccess(String path) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed() && item.getId().equals(currentlyShowingId)) {
                            displayMedia(path, item.getType());
                        }
                    });
                }

                @Override
                public void onProgress(int progress) {}

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed() && item.getId().equals(currentlyShowingId)) {
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
            Glide.with(this)
                    .load(path)
                    .apply(new RequestOptions()
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(android.R.color.black)
                            .error(android.R.color.black))
                    .into(statusImageView);
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
