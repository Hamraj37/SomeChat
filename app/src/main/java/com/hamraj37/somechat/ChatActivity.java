package com.hamraj37.somechat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.adapters.MessageAdapter;
import com.hamraj37.somechat.models.Message;
import com.hamraj37.somechat.utils.PresenceManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends BaseActivity {

    private String receiverId;
    private String receiverName;
    private String receiverAvatar;
    private String senderId;

    private RecyclerView chatRecycler;
    private MessageAdapter adapter;
    private List<Message> messageList = new ArrayList<>();
    private EditText messageInput;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnSend;
    private ImageView btnAttachment;
    private View attachmentMenu;
    private DatabaseReference chatRef;
    private boolean isFriend = false;
    private ValueEventListener messageListener;
    private ValueEventListener presenceListener;
    private DatabaseReference presenceRef;
    public static String openedChatId = null;
    private String receiverPublicKey = null;
    private int messageLimit = 50;

    private View replyLayout;
    private TextView replyName;
    private TextView replyText;
    private Message replyingToMessage = null;

    private View editLayout;
    private TextView editTextPreview;
    private Message editingMessage = null;

    private View userInfoContainer;
    private View callButtonsContainer;
    private View selectionActionsContainer;
    private TextView selectionCountText;

    private View pinnedMessageLayout;
    private TextView pinnedMessageTitle;
    private TextView pinnedMessageText;
    private List<Message> pinnedMessages = new ArrayList<>();
    private int currentPinnedIndex = 0;

    private View inputArea;
    private View recordingPreview;
    private android.widget.Chronometer recordingTimer;
    private View recordingDot;
    private float startX;
    private boolean isCanceled = false;

    private android.media.MediaRecorder mediaRecorder;
    private String audioPath = null;
    private long recordStartTime = 0;
    private boolean isRecording = false;
    private android.net.Uri cameraUri;

    private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> pickMediaLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (uri != null) {
                            String mimeType = getContentResolver().getType(uri);
                            String type = (mimeType != null && mimeType.startsWith("video")) ? "video" : "image";
                            uploadMedia(uri, type);
                        }
                    });

    private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> takePhotoLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
                    success -> {
                        if (success && cameraUri != null) {
                            uploadMedia(cameraUri, "image");
                        }
                    });

    private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> takeVideoLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.CaptureVideo(),
                    success -> {
                        if (success && cameraUri != null) {
                            uploadMedia(cameraUri, "video");
                        }
                    });

    private final androidx.activity.result.ActivityResultLauncher<String[]> pickDocumentLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) {
                            uploadMedia(uri, "file");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        setupRecyclerView();
        
        messageInput = findViewById(R.id.message_input);
        btnSend = findViewById(R.id.btn_send);
        btnAttachment = findViewById(R.id.btn_attachment);
        attachmentMenu = findViewById(R.id.attachment_menu);
        
        replyLayout = findViewById(R.id.reply_layout);
        replyName = findViewById(R.id.reply_name);
        replyText = findViewById(R.id.reply_text);
        findViewById(R.id.btn_cancel_reply).setOnClickListener(v -> cancelReply());

        editLayout = findViewById(R.id.edit_layout);
        editTextPreview = findViewById(R.id.edit_text_preview);
        findViewById(R.id.btn_cancel_edit).setOnClickListener(v -> cancelEdit());

        userInfoContainer = findViewById(R.id.user_info_container);
        callButtonsContainer = findViewById(R.id.call_buttons_container);
        selectionActionsContainer = findViewById(R.id.selection_actions_container);
        selectionCountText = findViewById(R.id.selection_count);

        pinnedMessageLayout = findViewById(R.id.pinned_message_layout);
        pinnedMessageTitle = findViewById(R.id.pinned_message_title);
        pinnedMessageText = findViewById(R.id.pinned_message_text);
        findViewById(R.id.btn_unpin).setOnClickListener(v -> unpinCurrentMessage());

        inputArea = findViewById(R.id.input_area);
        recordingPreview = findViewById(R.id.recording_preview);
        recordingTimer = findViewById(R.id.recording_timer);
        recordingDot = findViewById(R.id.recording_dot);
        findViewById(R.id.btn_delete_recording).setOnClickListener(v -> cancelRecording());

        findViewById(R.id.btn_copy_selected).setOnClickListener(v -> copySelectedMessages());
        findViewById(R.id.btn_reply_selected).setOnClickListener(v -> replyToSelectedMessage());
        findViewById(R.id.btn_forward_selected).setOnClickListener(v -> forwardSelectedMessages());
        findViewById(R.id.btn_delete_selected).setOnClickListener(v -> deleteSelectedMessages());
        findViewById(R.id.btn_pin_selected).setOnClickListener(v -> pinSelectedMessages());
        findViewById(R.id.btn_edit_selected).setOnClickListener(v -> editSelectedMessage());
        
        checkPinnedScroll();

        replyLayout.setOnClickListener(v -> {
            if (replyingToMessage != null) {
                String targetId = replyingToMessage.getMessageId();
                for (int i = 0; i < messageList.size(); i++) {
                    if (messageList.get(i).getMessageId().equals(targetId)) {
                        chatRecycler.smoothScrollToPosition(i + 1);
                        adapter.highlightMessage(targetId);
                        break;
                    }
                }
            }
        });

        setupInputListeners();
        setupAttachmentMenuListeners();

        findViewById(R.id.btn_audio_call).setOnClickListener(v -> startCall(false));
        findViewById(R.id.btn_video_call).setOnClickListener(v -> startCall(true));

        initChat();

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (adapter != null && adapter.isSelectionMode()) {
                    adapter.clearSelection();
                } else if (attachmentMenu != null && attachmentMenu.getVisibility() == View.VISIBLE) {
                    hideAttachmentMenu();
                } else if (replyingToMessage != null) {
                    cancelReply();
                } else if (editingMessage != null) {
                    cancelEdit();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void initChat() {
        receiverId = getIntent().getStringExtra("uid");
        receiverName = getIntent().getStringExtra("displayName");
        receiverAvatar = getIntent().getStringExtra("photoUrl");
        senderId = FirebaseAuth.getInstance().getUid();

        openedChatId = receiverId;

        setupBackground();
        setupToolbar();
        
        if (chatRef != null && messageListener != null) {
            chatRef.removeEventListener(messageListener);
        }
        
        messageList.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
        
        checkFriendStatus();
        setupPresenceListener();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        initChat();
    }

    private void checkForwardedMessage() {
        if (getIntent().hasExtra("forward_list")) {
            ArrayList<Message> list = (ArrayList<Message>) getIntent().getSerializableExtra("forward_list");
            if (list != null && !list.isEmpty()) {
                forwardBatch(list);
            }
            getIntent().removeExtra("forward_list");
        } else if (getIntent().hasExtra("forward_content")) {
            String content = getIntent().getStringExtra("forward_content");
            String type = getIntent().getStringExtra("forward_type");
            if (type == null) type = "text";
            
            final String finalType = type;
            // Wait for receiverPublicKey to be loaded if it's not yet
            if (receiverPublicKey == null) {
                FirebaseDatabase.getInstance().getReference("users").child(receiverId).child("publicKey")
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                receiverPublicKey = snapshot.getValue(String.class);
                                sendForwardedMessage(content, finalType);
                            }
                            @Override public void onCancelled(@NonNull DatabaseError error) {}
                        });
            } else {
                sendForwardedMessage(content, type);
            }
        }
    }

    private void checkPinnedScroll() {
        if (getIntent().hasExtra("scroll_to_message_id")) {
            String targetId = getIntent().getStringExtra("scroll_to_message_id");
            if (targetId != null) {
                // We need to wait for messages to load
                chatRecycler.postDelayed(() -> {
                    for (int i = 0; i < messageList.size(); i++) {
                        if (messageList.get(i).getMessageId().equals(targetId)) {
                            chatRecycler.smoothScrollToPosition(i + 1);
                            adapter.highlightMessage(targetId);
                            break;
                        }
                    }
                }, 500);
            }
            getIntent().removeExtra("scroll_to_message_id");
        }
    }

    private void forwardBatch(ArrayList<Message> list) {
        if (receiverPublicKey == null) {
            FirebaseDatabase.getInstance().getReference("users").child(receiverId).child("publicKey")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            receiverPublicKey = snapshot.getValue(String.class);
                            for (Message m : list) {
                                sendForwardedMessage(m);
                            }
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                    });
        } else {
            for (Message m : list) {
                sendForwardedMessage(m);
            }
        }
    }

    private void sendForwardedMessage(Message m) {
        String type = m.getType();
        String content = (type == null || "text".equals(type)) ? m.getText() : m.getMediaUrl();
        if (content == null) return;

        if ("text".equals(type) || type == null) {
            String encryptedText = content;
            String myPubKey = com.hamraj37.somechat.utils.EncryptionManager.getMyPublicKey(this);
            if (receiverPublicKey != null && myPubKey != null) {
                encryptedText = com.hamraj37.somechat.utils.EncryptionManager.encrypt(content, receiverPublicKey, myPubKey);
            }
            
            String messageId = chatRef.push().getKey();
            Message message = new Message(messageId, senderId, receiverId, encryptedText, System.currentTimeMillis());
            message.setForwarded(true);
            if (messageId != null) {
                chatRef.child(messageId).setValue(message);
            }
        } else {
            String encryptedMediaInfo = content;
            String myPubKey = com.hamraj37.somechat.utils.EncryptionManager.getMyPublicKey(this);
            if (receiverPublicKey != null && myPubKey != null) {
                try {
                    encryptedMediaInfo = com.hamraj37.somechat.utils.EncryptionManager.encrypt(content, receiverPublicKey, myPubKey);
                } catch (Exception e) { e.printStackTrace(); }
            }

            String messageId = chatRef.push().getKey();
            Message message = new Message(messageId, senderId, receiverId, type.substring(0,1).toUpperCase() + type.substring(1), System.currentTimeMillis(), type, encryptedMediaInfo, m.getDuration());
            message.setForwarded(true);
            if (messageId != null) {
                chatRef.child(messageId).setValue(message);
            }
        }
    }

    private void sendForwardedMessage(String content, String type) {
        if (content == null) return;
        
        int duration = getIntent().getIntExtra("forward_duration", 0);

        if ("text".equals(type) || type == null) {
            messageInput.setText(content);
            sendMessage();
        } else {
            // For media, content is likely the mediaInfo JSON (u and k)
            // We need to re-encrypt it for the new receiver
            String encryptedMediaInfo = content;
            String myPubKey = com.hamraj37.somechat.utils.EncryptionManager.getMyPublicKey(this);
            if (receiverPublicKey != null && myPubKey != null) {
                try {
                    // Content is decrypted JSON info from forwardMessage()
                    encryptedMediaInfo = com.hamraj37.somechat.utils.EncryptionManager.encrypt(content, receiverPublicKey, myPubKey);
                } catch (Exception e) { e.printStackTrace(); }
            }

            String messageId = chatRef.push().getKey();
            Message message = new Message(messageId, senderId, receiverId, type.substring(0,1).toUpperCase() + type.substring(1), System.currentTimeMillis(), type, encryptedMediaInfo, duration);
            message.setForwarded(true);
            if (messageId != null) {
                chatRef.child(messageId).setValue(message);
            }
        }
        getIntent().removeExtra("forward_content");
        getIntent().removeExtra("forward_type");
        getIntent().removeExtra("forward_duration");
    }

    private void setupAttachmentMenuListeners() {
        findViewById(R.id.option_media).setOnClickListener(v -> {
            toggleAttachmentMenu();
            pickMediaLauncher.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                    .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                    .build());
        });
        findViewById(R.id.option_camera).setOnClickListener(v -> {
            toggleAttachmentMenu();
            openCamera();
        });
        findViewById(R.id.option_document).setOnClickListener(v -> {
            toggleAttachmentMenu();
            pickDocumentLauncher.launch(new String[]{"*/*"});
        });
    }

    private void openCamera() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 201);
            return;
        }

        String[] options = {"Take Photo", "Record Video"};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Camera")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        takePhoto();
                    } else {
                        recordVideo();
                    }
                })
                .show();
    }

    private void takePhoto() {
        try {
            File photoFile = File.createTempFile("IMG_", ".jpg", getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES));
            cameraUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
            takePhotoLauncher.launch(cameraUri);
        } catch (IOException e) {
            android.util.Log.e("ChatActivity", "Error creating photo file", e);
        }
    }

    private void recordVideo() {
        try {
            File videoFile = File.createTempFile("VID_", ".mp4", getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES));
            cameraUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", videoFile);
            takeVideoLauncher.launch(cameraUri);
        } catch (IOException e) {
            android.util.Log.e("ChatActivity", "Error creating video file", e);
        }
    }

    private void setupInputListeners() {
        btnAttachment.setOnClickListener(v -> toggleAttachmentMenu());

        messageInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().trim().isEmpty()) {
                    btnSend.setImageResource(editingMessage != null ? android.R.drawable.ic_menu_close_clear_cancel : android.R.drawable.ic_btn_speak_now);
                } else {
                    btnSend.setImageResource(editingMessage != null ? android.R.drawable.ic_menu_save : android.R.drawable.ic_menu_send);
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        btnSend.setOnTouchListener((v, event) -> {
            String text = messageInput.getText().toString().trim();
            if (text.isEmpty() && editingMessage == null) {
                // Voice message mode
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.performClick();
                        startX = event.getRawX();
                        isCanceled = false;
                        startRecording();
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (isRecording && !isCanceled) {
                            float diff = startX - event.getRawX();
                            if (diff > 150) { // Threshold for swipe to cancel
                                cancelRecording();
                            }
                        }
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (isRecording) {
                            if (!isCanceled) {
                                stopRecordingAndSend();
                            }
                        }
                        return true;
                }
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                v.performClick();
                if (editingMessage != null && text.isEmpty()) {
                    cancelEdit();
                } else {
                    sendMessage();
                }
                return true;
            }
            return false;
        });
    }

    private void toggleAttachmentMenu() {
        if (attachmentMenu.getVisibility() == View.VISIBLE) {
            attachmentMenu.animate()
                    .alpha(0f)
                    .translationY(attachmentMenu.getHeight())
                    .setDuration(200)
                    .withEndAction(() -> attachmentMenu.setVisibility(View.GONE))
                    .start();
        } else {
            attachmentMenu.setVisibility(View.VISIBLE);
            attachmentMenu.setAlpha(0f);
            attachmentMenu.setTranslationY(100f);
            attachmentMenu.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200)
                    .start();
        }
    }

    private void uploadMedia(android.net.Uri uri, String type) {
        String messageId = chatRef.push().getKey();
        if (messageId == null) return;

        String fileName;
        String remoteFolder;
        if (type.equals("file")) {
            fileName = com.hamraj37.somechat.utils.FileUtils.getFileName(this, uri);
            remoteFolder = "documents";
        } else {
            String extension = type.equals("image") ? ".jpg" : ".mp4";
            fileName = messageId + extension;
            remoteFolder = type.equals("image") ? "images" : "videos";
        }

        String displayPreview = type.equals("file") ? fileName : type.substring(0,1).toUpperCase() + type.substring(1);
        // Add placeholder message locally
        Message pendingMsg = new Message(messageId, senderId, receiverId, displayPreview, System.currentTimeMillis(), type, "local:" + uri.toString(), 0);
        messageList.add(pendingMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        chatRecycler.smoothScrollToPosition(messageList.size() - 1);

        try {
            // 1. Read file to bytes
            java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return;
            
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            byte[] bytes = baos.toByteArray();
            final long fileSize = bytes.length;
            inputStream.close();

            // Save a copy locally like WhatsApp
            com.hamraj37.somechat.utils.MediaUtils.saveMediaLocally(this, bytes, type, messageId, type.equals("file") ? fileName : null);

            // 2. Encrypt bytes
            javax.crypto.SecretKey fileKey = com.hamraj37.somechat.utils.EncryptionManager.generateAESKey();
            byte[] encryptedBytes = com.hamraj37.somechat.utils.EncryptionManager.encryptRaw(bytes, fileKey);
            String encodedFileKey = com.hamraj37.somechat.utils.EncryptionManager.encodeKey(fileKey);

            // 3. Upload encrypted bytes
            com.hamraj37.somechat.utils.GitHubStorage.uploadBytes(encryptedBytes, remoteFolder, fileName, new com.hamraj37.somechat.utils.GitHubStorage.UploadCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    runOnUiThread(() -> {
                        adapter.removeUploadProgress(messageId);
                        
                        // Combine URL and FileKey into a JSON
                        try {
                            org.json.JSONObject mediaJson = new org.json.JSONObject();
                            mediaJson.put("u", downloadUrl);
                            mediaJson.put("k", encodedFileKey);
                            mediaJson.put("s", fileSize);
                            String mediaInfo = mediaJson.toString();

                            String finalContent = mediaInfo;
                            String myPubKey = com.hamraj37.somechat.utils.EncryptionManager.initKeys(ChatActivity.this);
                            if (receiverPublicKey != null && myPubKey != null) {
                                finalContent = com.hamraj37.somechat.utils.EncryptionManager.encrypt(mediaInfo, receiverPublicKey, myPubKey);
                            }

                            String displayPreview = type.equals("file") ? fileName : type.substring(0,1).toUpperCase() + type.substring(1);
                            Message message = new Message(messageId, senderId, receiverId, displayPreview, System.currentTimeMillis(), type, finalContent, 0);
                            
                            if (replyingToMessage != null) {
                                message.setReplyToId(replyingToMessage.getMessageId());
                                boolean isMe = replyingToMessage.getSenderId().equals(senderId);
                                message.setReplyToName(isMe ? "You" : receiverName);
                                String replyDisplayText;
                                if (replyingToMessage.getType() == null || "text".equals(replyingToMessage.getType())) {
                                    replyDisplayText = com.hamraj37.somechat.utils.EncryptionManager.decrypt(replyingToMessage.getText(), ChatActivity.this, isMe);
                                } else {
                                    String rType = replyingToMessage.getType();
                                    replyDisplayText = rType.substring(0, 1).toUpperCase() + rType.substring(1);
                                }
                                message.setReplyToText(replyDisplayText);
                                cancelReply();
                            }

                            chatRef.child(messageId).setValue(message);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }

                @Override
                public void onProgress(int progress) {
                    runOnUiThread(() -> adapter.updateUploadProgress(messageId, progress));
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> {
                        adapter.removeUploadProgress(messageId);
                        messageList.remove(pendingMsg);
                        adapter.notifyDataSetChanged();
                        android.util.Log.e("ChatActivity", "GitHub Media Upload failed", e);
                        android.widget.Toast.makeText(ChatActivity.this, "Failed to upload " + type, android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "File preparation failed", e);
        }
    }

    private void startRecording() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 200);
            return;
        }

        File cacheDir = getExternalCacheDir();
        if (cacheDir == null) cacheDir = getCacheDir();
        audioPath = cacheDir.getAbsolutePath() + "/temp_audio.3gp";
        
        mediaRecorder = new android.media.MediaRecorder();
        mediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(audioPath);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            recordStartTime = System.currentTimeMillis();
            
            inputArea.setVisibility(View.GONE);
            recordingPreview.setVisibility(View.VISIBLE);
            recordingTimer.setBase(android.os.SystemClock.elapsedRealtime());
            recordingTimer.start();
            startBlinkingAnimation();
            
        } catch (IOException e) {
            android.util.Log.e("ChatActivity", "Recording failed", e);
        }
    }

    private void startBlinkingAnimation() {
        android.view.animation.Animation anim = new android.view.animation.AlphaAnimation(1.0f, 0.0f);
        anim.setDuration(500);
        anim.setRepeatMode(android.view.animation.Animation.REVERSE);
        anim.setRepeatCount(android.view.animation.Animation.INFINITE);
        recordingDot.startAnimation(anim);
    }

    private void cancelRecording() {
        if (!isRecording) return;
        isCanceled = true;
        
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            
            File file = new File(audioPath);
            if (file.exists()) file.delete();
            
            recordingTimer.stop();
            recordingDot.clearAnimation();
            recordingPreview.setVisibility(View.GONE);
            inputArea.setVisibility(View.VISIBLE);
            
            Toast.makeText(this, "Recording canceled", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopRecordingAndSend() {
        if (!isRecording || mediaRecorder == null) return;

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;

            recordingTimer.stop();
            recordingDot.clearAnimation();
            recordingPreview.setVisibility(View.GONE);
            inputArea.setVisibility(View.VISIBLE);

            long duration = (System.currentTimeMillis() - recordStartTime) / 1000;
            if (duration < 1) {
                File tempFile = new File(audioPath);
                if (tempFile.exists()) tempFile.delete();
                android.widget.Toast.makeText(this, "Message too short", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            uploadVoiceMessage(audioPath, (int) duration);
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Stop recording failed", e);
            recordingPreview.setVisibility(View.GONE);
            inputArea.setVisibility(View.VISIBLE);
        }
    }

    private void uploadVoiceMessage(String path, int duration) {
        String messageId = chatRef.push().getKey();
        if (messageId == null) return;

        // Add placeholder message locally
        Message pendingMsg = new Message(messageId, senderId, receiverId, "Voice message", System.currentTimeMillis(), "voice", "local:" + path, duration);
        messageList.add(pendingMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        chatRecycler.smoothScrollToPosition(messageList.size() - 1);

        try {
            // 1. Read file to bytes
            File file = new File(path);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            byte[] bytes = baos.toByteArray();
            final long fileSize = bytes.length;
            fis.close();

            // Save copy locally
            com.hamraj37.somechat.utils.MediaUtils.saveMediaLocally(this, bytes, "voice", messageId);

            // 2. Encrypt bytes
            javax.crypto.SecretKey fileKey = com.hamraj37.somechat.utils.EncryptionManager.generateAESKey();
            byte[] encryptedBytes = com.hamraj37.somechat.utils.EncryptionManager.encryptRaw(bytes, fileKey);
            String encodedFileKey = com.hamraj37.somechat.utils.EncryptionManager.encodeKey(fileKey);

            String fileName = messageId + ".3gp";
            com.hamraj37.somechat.utils.GitHubStorage.uploadBytes(encryptedBytes, "voice_messages", fileName, new com.hamraj37.somechat.utils.GitHubStorage.UploadCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    runOnUiThread(() -> {
                        adapter.removeUploadProgress(messageId);

                        try {
                            org.json.JSONObject mediaJson = new org.json.JSONObject();
                            mediaJson.put("u", downloadUrl);
                            mediaJson.put("k", encodedFileKey);
                            mediaJson.put("s", fileSize);
                            String mediaInfo = mediaJson.toString();

                            String finalContent = mediaInfo;
                            String myPubKey = com.hamraj37.somechat.utils.EncryptionManager.getMyPublicKey(ChatActivity.this);
                            if (receiverPublicKey != null && myPubKey != null) {
                                finalContent = com.hamraj37.somechat.utils.EncryptionManager.encrypt(mediaInfo, receiverPublicKey, myPubKey);
                            }

                            Message message = new Message(messageId, senderId, receiverId, "Voice message", System.currentTimeMillis(), "voice", finalContent, duration);
                            
                            if (replyingToMessage != null) {
                                message.setReplyToId(replyingToMessage.getMessageId());
                                boolean isMe = replyingToMessage.getSenderId().equals(senderId);
                                message.setReplyToName(isMe ? "You" : receiverName);
                                String replyDisplayText;
                                if (replyingToMessage.getType() == null || "text".equals(replyingToMessage.getType())) {
                                    replyDisplayText = com.hamraj37.somechat.utils.EncryptionManager.decrypt(replyingToMessage.getText(), ChatActivity.this, isMe);
                                } else {
                                    String rType = replyingToMessage.getType();
                                    replyDisplayText = rType.substring(0, 1).toUpperCase() + rType.substring(1);
                                }
                                message.setReplyToText(replyDisplayText);
                                cancelReply();
                            }

                            chatRef.child(messageId).setValue(message);

                            File tempFile = new File(path);
                            if (tempFile.exists()) tempFile.delete();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }

                @Override
                public void onProgress(int progress) {
                    runOnUiThread(() -> adapter.updateUploadProgress(messageId, progress));
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> {
                        adapter.removeUploadProgress(messageId);
                        messageList.remove(pendingMsg);
                        adapter.notifyDataSetChanged();
                        android.util.Log.e("ChatActivity", "GitHub Upload failed", e);
                        android.widget.Toast.makeText(ChatActivity.this, "Failed to upload audio to GitHub", android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startCall(boolean isVideo) {
        Intent intent = new Intent(this, isVideo ? VideoCallActivity.class : AudioCallActivity.class);
        intent.putExtra("uid", receiverId);
        intent.putExtra("displayName", receiverName);
        intent.putExtra("photoUrl", receiverAvatar);
        intent.putExtra("isIncoming", false);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 200) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.widget.Toast.makeText(this, "Permission granted. Hold the button to record.", android.widget.Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 201) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openCamera();
            }
        }
    }

    private void setupPresenceListener() {
        if (presenceRef != null && presenceListener != null) {
            presenceRef.removeEventListener(presenceListener);
        }

        TextView toolbarStatus = findViewById(R.id.toolbar_status);
        presenceRef = FirebaseDatabase.getInstance().getReference("users").child(receiverId);
        presenceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean isOnline = snapshot.child("online").getValue(Boolean.class);
                    Long lastSeen = snapshot.child("lastSeen").getValue(Long.class);
                    receiverPublicKey = snapshot.child("publicKey").getValue(String.class);

                    if (isOnline != null && isOnline) {
                        toolbarStatus.setText("Online");
                        toolbarStatus.setVisibility(android.view.View.VISIBLE);
                    } else if (lastSeen != null) {
                        // Simple last seen formatting
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.getDefault());
                        toolbarStatus.setText("Last seen: " + sdf.format(new java.util.Date(lastSeen)));
                        toolbarStatus.setVisibility(android.view.View.VISIBLE);
                    } else {
                        toolbarStatus.setVisibility(android.view.View.GONE);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        presenceRef.addValueEventListener(presenceListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        openedChatId = receiverId;
        PresenceManager.setUserOnline();
    }

    @Override
    protected void onPause() {
        super.onPause();
        openedChatId = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        openedChatId = null;
        if (chatRef != null && messageListener != null) {
            chatRef.removeEventListener(messageListener);
        }
        if (presenceRef != null && presenceListener != null) {
            presenceRef.removeEventListener(presenceListener);
        }
    }

    private void updateSelectionUI(int count) {
        if (count > 0) {
            userInfoContainer.setVisibility(View.GONE);
            callButtonsContainer.setVisibility(View.GONE);
            selectionCountText.setVisibility(View.VISIBLE);
            selectionActionsContainer.setVisibility(View.VISIBLE);
            selectionCountText.setText(String.valueOf(count));
            
            List<Message> selected = adapter.getSelectedMessages();
            boolean isSingle = count == 1;
            boolean isTextOnly = true;
            for (Message m : selected) {
                if (m.getType() != null && !m.getType().equals("text")) {
                    isTextOnly = false;
                    break;
                }
            }

            // Show/hide reply based on count
            findViewById(R.id.btn_reply_selected).setVisibility(isSingle ? View.VISIBLE : View.GONE);
            // Hide copy for media and multi-select (as requested)
            findViewById(R.id.btn_copy_selected).setVisibility(isSingle && isTextOnly ? View.VISIBLE : View.GONE);
            
            // Show edit only for single text message sent by me
            boolean isMyMessage = isSingle && selected.get(0).getSenderId().equals(senderId);
            findViewById(R.id.btn_edit_selected).setVisibility(isSingle && isTextOnly && isMyMessage ? View.VISIBLE : View.GONE);

            androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material); 
            toolbar.setNavigationOnClickListener(v -> adapter.clearSelection());
        } else {
            userInfoContainer.setVisibility(View.VISIBLE);
            callButtonsContainer.setVisibility(View.VISIBLE);
            selectionCountText.setVisibility(View.GONE);
            selectionActionsContainer.setVisibility(View.GONE);
            
            androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void copySelectedMessages() {
        List<Message> selected = adapter.getSelectedMessages();
        if (selected.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selected.size(); i++) {
            Message m = selected.get(i);
            boolean isMe = m.getSenderId().equals(senderId);
            String text;
            if (m.getType() == null || m.getType().equals("text")) {
                text = com.hamraj37.somechat.utils.EncryptionManager.decrypt(m.getText(), this, isMe);
            } else {
                text = m.getType();
            }
            sb.append(text);
            if (i < selected.size() - 1) sb.append("\n");
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("messages", sb.toString());
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
        adapter.clearSelection();
    }

    private void replyToSelectedMessage() {
        List<Message> selected = adapter.getSelectedMessages();
        if (selected.size() == 1) {
            showReplyLayout(selected.get(0));
            adapter.clearSelection();
        }
    }

    private void pinSelectedMessages() {
        List<Message> selected = adapter.getSelectedMessages();
        if (selected.isEmpty()) return;

        for (Message m : selected) {
            chatRef.child(m.getMessageId()).child("pinned").setValue(true);
        }
        adapter.clearSelection();
        Toast.makeText(this, selected.size() > 1 ? "Messages pinned" : "Message pinned", Toast.LENGTH_SHORT).show();
    }

    private void unpinCurrentMessage() {
        if (pinnedMessages.isEmpty() || currentPinnedIndex >= pinnedMessages.size()) return;
        
        Message current = pinnedMessages.get(currentPinnedIndex);
        chatRef.child(current.getMessageId()).child("pinned").setValue(false);
        Toast.makeText(this, "Message unpinned", Toast.LENGTH_SHORT).show();
    }

    private void unpinAllMessages() {
        chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Message m = ds.getValue(Message.class);
                    if (m != null && m.isPinned()) {
                        ds.getRef().child("pinned").setValue(false);
                    }
                }
                Toast.makeText(ChatActivity.this, "Messages unpinned", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void forwardSelectedMessages() {
        List<Message> selected = adapter.getSelectedMessages();
        if (selected.isEmpty()) return;

        ArrayList<Message> forwardList = new ArrayList<>();
        for (Message m : selected) {
            forwardList.add(prepareMessageForForward(m));
        }

        Intent intent = new Intent(this, SearchUserActivity.class);
        intent.putExtra("forward_message", true);
        intent.putExtra("forward_list", forwardList);
        startActivity(intent);
        
        adapter.clearSelection();
        Toast.makeText(this, "Select a user to forward to", Toast.LENGTH_SHORT).show();
    }

    private Message prepareMessageForForward(Message message) {
        Message f = new Message();
        f.setType(message.getType());
        f.setDuration(message.getDuration());
        boolean isMe = message.getSenderId().equals(senderId);
        if (message.getType() == null || "text".equals(message.getType())) {
            f.setText(com.hamraj37.somechat.utils.EncryptionManager.decrypt(message.getText(), this, isMe));
        } else {
            f.setMediaUrl(com.hamraj37.somechat.utils.EncryptionManager.decrypt(message.getMediaUrl(), this, isMe));
        }
        f.setForwarded(true);
        return f;
    }

    private void deleteSelectedMessages() {
        List<Message> selected = adapter.getSelectedMessages();
        if (selected.isEmpty()) return;

        if (selected.size() == 1) {
            deleteMessage(selected.get(0));
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Delete " + selected.size() + " messages?")
                    .setPositiveButton("Delete for me", (dialog, which) -> {
                        for (Message m : selected) {
                            boolean isMeSender = m.getSenderId().equals(senderId);
                            if (isMeSender) {
                                chatRef.child(m.getMessageId()).child("deletedBySender").setValue(true);
                            } else {
                                chatRef.child(m.getMessageId()).child("deletedByReceiver").setValue(true);
                            }
                        }
                        adapter.clearSelection();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }



    private void setupBackground() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        String bgPath = prefs.getString("chat_background_path", null);
        ImageView bgImage = findViewById(R.id.chat_background_image);
        if (bgPath != null && bgImage != null) {
            if (bgPath.startsWith("res:")) {
                int resId = getResources().getIdentifier(bgPath.replace("res:", ""), "drawable", getPackageName());
                if (resId != 0) Glide.with(this).load(resId).into(bgImage);
            } else {
                File file = new File(bgPath);
                if (file.exists()) {
                    Glide.with(this)
                            .load(file)
                            .signature(new com.bumptech.glide.signature.ObjectKey(file.lastModified()))
                            .into(bgImage);
                }
            }
        }
    }

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView toolbarName = findViewById(R.id.toolbar_name);
        ImageView toolbarAvatar = findViewById(R.id.toolbar_avatar);

        toolbarName.setText(receiverName);
        if (receiverAvatar != null && !receiverAvatar.isEmpty()) {
            Glide.with(this).load(receiverAvatar).circleCrop().into(toolbarAvatar);
        } else {
            toolbarAvatar.setImageResource(R.mipmap.ic_launcher_round);
        }

        findViewById(R.id.user_info_container).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileInfoActivity.class);
            intent.putExtra("uid", receiverId);
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        chatRecycler = findViewById(R.id.chat_recycler);
        adapter = new MessageAdapter(messageList, new MessageAdapter.OnMessageClickListener() {
            @Override
            public void onReplyClick(String messageId) {
                for (int i = 0; i < messageList.size(); i++) {
                    if (messageList.get(i).getMessageId().equals(messageId)) {
                        chatRecycler.smoothScrollToPosition(i + 1); // +1 for header
                        adapter.highlightMessage(messageId);
                        break;
                    }
                }
            }

            @Override
            public void onMessageLongClick(Message message, View view) {
                adapter.toggleSelection(message.getMessageId());
                showReactionPicker(message, view);
            }

            @Override
            public void onMessageClick(Message message) {
                // Handle message click (e.g. open image) - already handled for media in adapter
            }

            @Override
            public void onSelectionChanged(int count) {
                updateSelectionUI(count);
            }

            @Override
            public void onReactionClick(Message message, String emoji) {
                if (chatRef != null) {
                    chatRef.child(message.getMessageId()).child("reactions").child(senderId).setValue(emoji);
                }
            }
        });
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecycler.setLayoutManager(layoutManager);
        chatRecycler.setAdapter(adapter);

        chatRecycler.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom) {
                chatRecycler.postDelayed(() -> {
                    if (adapter.getItemCount() > 0) {
                        chatRecycler.scrollToPosition(adapter.getItemCount() - 1);
                    }
                }, 100);
            }
        });

        androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback itemTouchHelperCallback = new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                if (direction == androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
                    int pos = viewHolder.getBindingAdapterPosition();
                    if (pos > 0) {
                        showReplyLayout(messageList.get(pos - 1));
                    }
                    adapter.notifyItemChanged(pos);
                }
            }

            @Override
            public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (dX > 200) dX = 200;
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        new androidx.recyclerview.widget.ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(chatRecycler);

        chatRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    hideAttachmentMenu();
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy < 0) { // Scrolling up
                    LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (lm != null && lm.findFirstVisibleItemPosition() <= 5) {
                        loadMoreMessages();
                    }
                }
            }
        });

        chatRecycler.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                if (attachmentMenu != null && attachmentMenu.getVisibility() == View.VISIBLE) {
                    v.performClick();
                    hideAttachmentMenu();
                }
            }
            return false;
        });
    }

    private void forwardMessage(Message message) {
        ArrayList<Message> forwardList = new ArrayList<>();
        forwardList.add(prepareMessageForForward(message));

        Intent intent = new Intent(this, SearchUserActivity.class);
        intent.putExtra("forward_message", true);
        intent.putExtra("forward_list", forwardList);
        startActivity(intent);
        Toast.makeText(this, "Select a user to forward to", Toast.LENGTH_SHORT).show();
    }

    private void deleteMessage(Message message) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_delete_message, null);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        TextView btnDeleteForEveryone = dialogView.findViewById(R.id.btn_delete_for_everyone);
        TextView btnDeleteForMe = dialogView.findViewById(R.id.btn_delete_for_me);
        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        com.google.android.material.checkbox.MaterialCheckBox cbDeleteMedia = dialogView.findViewById(R.id.cb_delete_media);

        boolean isMe = message.getSenderId().equals(senderId);
        boolean hasMedia = message.getType() != null && !message.getType().equals("text");

        if (isMe) {
            btnDeleteForEveryone.setVisibility(View.VISIBLE);
        }

        if (hasMedia) {
            cbDeleteMedia.setVisibility(View.VISIBLE);
        }

        btnDeleteForEveryone.setOnClickListener(v -> {
            if (cbDeleteMedia.isChecked() && hasMedia) {
                deleteLocalMedia(message);
            }
            chatRef.child(message.getMessageId()).removeValue();
            dialog.dismiss();
        });

        btnDeleteForMe.setOnClickListener(v -> {
            if (cbDeleteMedia.isChecked() && hasMedia) {
                deleteLocalMedia(message);
            }
            
            boolean isMeSender = message.getSenderId().equals(senderId);
            if (isMeSender) {
                chatRef.child(message.getMessageId()).child("deletedBySender").setValue(true);
            } else {
                chatRef.child(message.getMessageId()).child("deletedByReceiver").setValue(true);
            }
            
            // Clean up if both deleted
            chatRef.child(message.getMessageId()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Message m = snapshot.getValue(Message.class);
                    if (m != null && m.isDeletedBySender() && m.isDeletedByReceiver()) {
                        chatRef.child(m.getMessageId()).removeValue();
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });

            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void deleteLocalMedia(Message message) {
        try {
            java.io.File file = com.hamraj37.somechat.utils.MediaUtils.getLocalFileForMedia(this, message.getType(), message.getMessageId());
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showReplyLayout(Message message) {
        replyingToMessage = message;
        replyLayout.setVisibility(View.VISIBLE);
        
        boolean isMe = message.getSenderId().equals(senderId);
        replyName.setText(isMe ? "You" : receiverName);
        
        String displayText;
        if (message.getType() == null || "text".equals(message.getType())) {
            displayText = com.hamraj37.somechat.utils.EncryptionManager.decrypt(message.getText(), this, isMe);
        } else {
            String type = message.getType();
            displayText = type.substring(0, 1).toUpperCase() + type.substring(1);
        }
        replyText.setText(displayText);
        
        messageInput.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(messageInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void cancelReply() {
        replyingToMessage = null;
        replyLayout.setVisibility(View.GONE);
    }

    private void showPinnedMessage(Message message) {
        pinnedMessageLayout.setVisibility(View.VISIBLE);
        
        if (pinnedMessages.size() > 1) {
            pinnedMessageTitle.setText(getString(R.string.pinned_message_count, currentPinnedIndex + 1, pinnedMessages.size()));
        } else {
            pinnedMessageTitle.setText(R.string.pinned_message);
        }

        boolean isMe = message.getSenderId().equals(senderId);
        
        String displayText;
        if (message.getType() == null || "text".equals(message.getType())) {
            displayText = com.hamraj37.somechat.utils.EncryptionManager.decrypt(message.getText(), this, isMe);
        } else {
            String type = message.getType();
            displayText = type.substring(0, 1).toUpperCase() + type.substring(1);
        }
        pinnedMessageText.setText(displayText);
        
        pinnedMessageLayout.setOnClickListener(v -> {
            // Scroll to current pinned message
            for (int i = 0; i < messageList.size(); i++) {
                if (messageList.get(i).getMessageId().equals(message.getMessageId())) {
                    chatRecycler.smoothScrollToPosition(i + 1);
                    adapter.highlightMessage(message.getMessageId());
                    break;
                }
            }
            
            // Cycle to next one for the next view
            if (pinnedMessages.size() > 1) {
                currentPinnedIndex = (currentPinnedIndex + 1) % pinnedMessages.size();
                showPinnedMessage(pinnedMessages.get(currentPinnedIndex));
            }
        });
    }

    private void loadMoreMessages() {
        messageLimit += 50;
        if (chatRef != null && messageListener != null) {
            chatRef.removeEventListener(messageListener);
            chatRef.limitToLast(messageLimit).addValueEventListener(messageListener);
        }
    }

    private void hideAttachmentMenu() {
        if (attachmentMenu != null && attachmentMenu.getVisibility() == View.VISIBLE) {
            attachmentMenu.animate()
                    .alpha(0f)
                    .translationY(attachmentMenu.getHeight())
                    .setDuration(200)
                    .withEndAction(() -> attachmentMenu.setVisibility(View.GONE))
                    .start();
        }
    }

    private void setupFirebase() {
        // Create a unique chat ID by sorting UIDs
        String chatId = senderId.compareTo(receiverId) < 0 
                ? senderId + "_" + receiverId 
                : receiverId + "_" + senderId;

        chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId);
        messageListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Keep track of pending uploads
                List<Message> pendingUploads = new ArrayList<>();
                for (Message m : messageList) {
                    if (m.getMediaUrl() != null && m.getMediaUrl().startsWith("local:")) {
                        pendingUploads.add(m);
                    }
                }

                int previousCount = messageList.size();
                messageList.clear();
                pinnedMessages.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Message message = dataSnapshot.getValue(Message.class);
                    if (message != null) {
                        boolean isMeSender = message.getSenderId().equals(senderId);
                        if ((isMeSender && message.isDeletedBySender()) || (!isMeSender && message.isDeletedByReceiver())) {
                            continue;
                        }

                        if (message.isPinned()) {
                            pinnedMessages.add(message);
                        }

                        messageList.add(message);
                        // Mark as seen if we are the receiver
                        if (message.getReceiverId().equals(senderId) && !message.isSeen()) {
                            dataSnapshot.getRef().child("seen").setValue(true);
                        }
                    }
                }

                if (!pinnedMessages.isEmpty()) {
                    if (currentPinnedIndex >= pinnedMessages.size()) {
                        currentPinnedIndex = 0;
                    }
                    showPinnedMessage(pinnedMessages.get(currentPinnedIndex));
                } else {
                    pinnedMessageLayout.setVisibility(View.GONE);
                }
                
                // Re-add pending uploads only if they are not already in the list (e.g. upload just finished)
                for (Message pending : pendingUploads) {
                    boolean alreadyExists = false;
                    for (Message m : messageList) {
                        if (m.getMessageId() != null && m.getMessageId().equals(pending.getMessageId())) {
                            alreadyExists = true;
                            break;
                        }
                    }
                    if (!alreadyExists) {
                        messageList.add(pending);
                    }
                }

                adapter.notifyDataSetChanged();
                if (!messageList.isEmpty()) {
                    LinearLayoutManager lm = (LinearLayoutManager) chatRecycler.getLayoutManager();
                    boolean isAtBottom = lm != null && lm.findLastVisibleItemPosition() >= previousCount - 1;
                    
                    if (isAtBottom || previousCount == 0) {
                        chatRecycler.post(() -> chatRecycler.scrollToPosition(adapter.getItemCount() - 1));
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        chatRef.limitToLast(messageLimit).addValueEventListener(messageListener);
        checkForwardedMessage();
    }

    private void checkFriendStatus() {
        FirebaseDatabase.getInstance().getReference("friends")
                .child(senderId)
                .child(receiverId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            isFriend = true;
                            setupFirebase();
                        } else {
                            // Check if a request is already pending
                            FirebaseDatabase.getInstance().getReference("friendRequests")
                                    .child(receiverId)
                                    .child(senderId)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot requestSnapshot) {
                                            if (!requestSnapshot.exists()) {
                                                showFriendRequestDialog();
                                            } else {
                                                android.widget.Toast.makeText(ChatActivity.this, "Friend request is pending. You can't message yet.", android.widget.Toast.LENGTH_LONG).show();
                                                // Optional: disable input
                                                findViewById(R.id.btn_send).setEnabled(false);
                                                messageInput.setHint("Waiting for friend request approval...");
                                                messageInput.setEnabled(false);
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {}
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void showFriendRequestDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Add Friend")
                .setMessage(receiverName + " is not in your friend list. Would you like to send a friend request?")
                .setPositiveButton("Send Request", (dialog, which) -> sendFriendRequest())
                .setNegativeButton("Cancel", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void sendFriendRequest() {
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("/friendRequests/" + receiverId + "/" + senderId, "pending");
        updates.put("/sentFriendRequests/" + senderId + "/" + receiverId, "pending");

        FirebaseDatabase.getInstance().getReference().updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                android.widget.Toast.makeText(this, "Friend request sent!", android.widget.Toast.LENGTH_SHORT).show();
                // Lock the UI after sending request
                findViewById(R.id.btn_send).setEnabled(false);
                messageInput.setHint("Waiting for approval...");
                messageInput.setEnabled(false);
            }
        });
    }

    private void showReactionPicker(Message message, View anchorView) {
        View reactionView = getLayoutInflater().inflate(R.layout.layout_reactions, (ViewGroup) anchorView.getParent(), false);
        android.widget.PopupWindow popupWindow = new android.widget.PopupWindow(reactionView, 
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        
        popupWindow.setElevation(16);
        popupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

        int[] location = new int[2];
        anchorView.getLocationOnScreen(location);
        
        // Position it above the bubble
        reactionView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int x = location[0] + (anchorView.getWidth() / 2) - (reactionView.getMeasuredWidth() / 2);
        int y = location[1] - reactionView.getMeasuredHeight() - 10;
        
        popupWindow.showAtLocation(anchorView, android.view.Gravity.NO_GRAVITY, x, y);

        View.OnClickListener clickListener = v -> {
            String emoji = ((TextView) v).getText().toString();
            if (chatRef != null) {
                chatRef.child(message.getMessageId()).child("reactions").child(senderId).setValue(emoji);
            }
            popupWindow.dismiss();
        };

        reactionView.findViewById(R.id.react_like).setOnClickListener(clickListener);
        reactionView.findViewById(R.id.react_love).setOnClickListener(clickListener);
        reactionView.findViewById(R.id.react_haha).setOnClickListener(clickListener);
        reactionView.findViewById(R.id.react_wow).setOnClickListener(clickListener);
        reactionView.findViewById(R.id.react_sad).setOnClickListener(clickListener);
        reactionView.findViewById(R.id.react_pray).setOnClickListener(clickListener);
    }

    private void editSelectedMessage() {
        List<Message> selected = adapter.getSelectedMessages();
        if (selected.size() == 1) {
            Message m = selected.get(0);
            if (m.getSenderId().equals(senderId) && (m.getType() == null || m.getType().equals("text"))) {
                enterEditMode(m);
                adapter.clearSelection();
            }
        }
    }

    private void enterEditMode(Message message) {
        editingMessage = message;
        cancelReply(); // Can't edit and reply at the same time
        
        editLayout.setVisibility(View.VISIBLE);
        String decryptedText = com.hamraj37.somechat.utils.EncryptionManager.decrypt(message.getText(), this, true);
        editTextPreview.setText(decryptedText);
        
        messageInput.setText(decryptedText);
        messageInput.requestFocus();
        messageInput.setSelection(messageInput.getText().length());
        
        btnSend.setImageResource(android.R.drawable.ic_menu_save); // Change icon to save/check
        
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(messageInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void cancelEdit() {
        editingMessage = null;
        editLayout.setVisibility(View.GONE);
        messageInput.setText("");
        btnSend.setImageResource(android.R.drawable.ic_btn_speak_now);
    }

    private void updateMessage(Message message, String newText) {
        String encryptedText = newText;
        String myPubKey = com.hamraj37.somechat.utils.EncryptionManager.getMyPublicKey(this);
        if (receiverPublicKey != null && myPubKey != null) {
            encryptedText = com.hamraj37.somechat.utils.EncryptionManager.encrypt(newText, receiverPublicKey, myPubKey);
        }

        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("text", encryptedText);
        updates.put("edited", true);

        chatRef.child(message.getMessageId()).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    cancelEdit();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update message", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            if (attachmentMenu != null && attachmentMenu.getVisibility() == View.VISIBLE) {
                android.graphics.Rect menuRect = new android.graphics.Rect();
                attachmentMenu.getGlobalVisibleRect(menuRect);
                
                android.graphics.Rect btnRect = new android.graphics.Rect();
                btnAttachment.getGlobalVisibleRect(btnRect);
                
                if (!menuRect.contains((int) ev.getRawX(), (int) ev.getRawY()) && 
                    !btnRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    hideAttachmentMenu();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void sendMessage() {
        if (!isFriend) {
            android.widget.Toast.makeText(this, "You must be friends to send messages", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        String text = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        if (editingMessage != null) {
            updateMessage(editingMessage, text);
            return;
        }

        String messageId = chatRef.push().getKey();
        
        String encryptedText = text;
        String myPubKey = com.hamraj37.somechat.utils.EncryptionManager.getMyPublicKey(this);
        if (receiverPublicKey != null && myPubKey != null) {
            encryptedText = com.hamraj37.somechat.utils.EncryptionManager.encrypt(text, receiverPublicKey, myPubKey);
        }
        
        Message message = new Message(messageId, senderId, receiverId, encryptedText, System.currentTimeMillis());
        
        if (getIntent().hasExtra("forward_content")) {
            message.setForwarded(true);
        }
        
        if (replyingToMessage != null) {
            message.setReplyToId(replyingToMessage.getMessageId());
            boolean isMe = replyingToMessage.getSenderId().equals(senderId);
            message.setReplyToName(isMe ? "You" : receiverName);
            
            String replyDisplayText;
            if (replyingToMessage.getType() == null || "text".equals(replyingToMessage.getType())) {
                replyDisplayText = com.hamraj37.somechat.utils.EncryptionManager.decrypt(replyingToMessage.getText(), this, isMe);
            } else {
                String type = replyingToMessage.getType();
                replyDisplayText = type.substring(0, 1).toUpperCase() + type.substring(1);
            }
            message.setReplyToText(replyDisplayText);
            cancelReply();
        }

        if (messageId != null) {
            chatRef.child(messageId).setValue(message);
            messageInput.setText("");
        }
    }
}
