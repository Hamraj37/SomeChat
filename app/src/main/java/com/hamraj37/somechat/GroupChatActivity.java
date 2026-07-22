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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.adapters.MessageAdapter;
import com.hamraj37.somechat.models.Message;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.IOException;

public class GroupChatActivity extends BaseActivity {

    private String groupId;
    private String groupName;
    private String groupAvatar;
    private String senderId;
    private String senderName;
    private String senderUsername;

    private RecyclerView chatRecycler;
    private MessageAdapter adapter;
    private List<Message> messageList = new ArrayList<>();
    private EditText messageInput;
    private com.google.android.material.floatingactionbutton.FloatingActionButton btnSend;
    private DatabaseReference groupChatRef;
    private ValueEventListener messageListener;
    private DatabaseReference groupDetailsRef;
    private ValueEventListener groupDetailsListener;
    private DatabaseReference membershipRef;
    private ValueEventListener membershipListener;
    private boolean isMember = false;
    public static String openedGroupId = null;

    private View replyLayout;
    private TextView replyName;
    private TextView replyText;
    private Message replyingToMessage = null;

    private View editLayout;
    private TextView editTextPreview;
    private Message editingMessage = null;

    private View groupInfoContainer;
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

    private ImageView btnAttachment;
    private View attachmentMenu;

    private android.media.MediaRecorder mediaRecorder;
    private String audioPath = null;
    private long recordStartTime = 0;
    private boolean isRecording = false;
    private android.net.Uri photoUri;

    private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> pickImageLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (uri != null) {
                            uploadMedia(uri, "image");
                        }
                    });

    private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> pickVideoLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (uri != null) {
                            uploadMedia(uri, "video");
                        }
                    });

    private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> takePhotoLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
                    success -> {
                        if (success && photoUri != null) {
                            uploadMedia(photoUri, "image");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        groupId = getIntent().getStringExtra("groupId");
        groupName = getIntent().getStringExtra("groupName");
        groupAvatar = getIntent().getStringExtra("groupAvatar");
        senderId = FirebaseAuth.getInstance().getUid();

        fetchSenderName();
        messageInput = findViewById(R.id.message_input);
        btnSend = findViewById(R.id.btn_send);
        replyLayout = findViewById(R.id.reply_layout);
        replyName = findViewById(R.id.reply_name);
        replyText = findViewById(R.id.reply_text);
        
        editLayout = findViewById(R.id.edit_layout);
        editTextPreview = findViewById(R.id.edit_text_preview);
        findViewById(R.id.btn_cancel_edit).setOnClickListener(v -> cancelEdit());

        groupInfoContainer = findViewById(R.id.group_info_container);
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

        btnAttachment = findViewById(R.id.btn_attachment);
        attachmentMenu = findViewById(R.id.attachment_menu);

        setupBackground();
        setupToolbar();
        setupRecyclerView();
        setupInputListeners();
        setupAttachmentMenuListeners();

        findViewById(R.id.btn_cancel_reply).setOnClickListener(v -> cancelReply());
        // Remove direct click listener for btn_send as it's now in setupInputListeners
        // findViewById(R.id.btn_send).setOnClickListener(v -> sendMessage());

        setupFirebase();
        checkMembership();
        listenForGroupDetails();

        findViewById(R.id.btn_reply_selected).setOnClickListener(v -> replyToSelectedMessage());
        findViewById(R.id.btn_pin_selected).setOnClickListener(v -> pinSelectedMessages());
        findViewById(R.id.btn_copy_selected).setOnClickListener(v -> copySelectedMessages());
        findViewById(R.id.btn_forward_selected).setOnClickListener(v -> forwardSelectedMessages());
        findViewById(R.id.btn_edit_selected).setOnClickListener(v -> editSelectedMessage());
        findViewById(R.id.btn_delete_selected).setOnClickListener(v -> deleteSelectedMessages());

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

    private void startRecording() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 200);
            return;
        }

        File cacheDir = getExternalCacheDir();
        if (cacheDir == null) cacheDir = getCacheDir();
        audioPath = cacheDir.getAbsolutePath() + "/temp_group_audio.3gp";
        
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
            android.util.Log.e("GroupChatActivity", "Recording failed", e);
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
                Toast.makeText(this, "Message too short", Toast.LENGTH_SHORT).show();
                return;
            }

            uploadVoiceMessage(audioPath, (int) duration);
        } catch (Exception e) {
            android.util.Log.e("GroupChatActivity", "Stop recording failed", e);
            recordingPreview.setVisibility(View.GONE);
            inputArea.setVisibility(View.VISIBLE);
        }
    }

    private void uploadVoiceMessage(String path, int duration) {
        if (senderName == null || senderUsername == null) {
            Toast.makeText(this, "Please wait, loading user info...", Toast.LENGTH_SHORT).show();
            return;
        }
        String messageId = groupChatRef.child("messages").push().getKey();
        if (messageId == null) return;

        // Add placeholder message locally
        Message pendingMsg = new Message(messageId, senderId, groupId, "Voice message", System.currentTimeMillis(), "voice", "local:" + path, duration);
        pendingMsg.setSenderName(senderName);
        pendingMsg.setSenderUsername(senderUsername);
        messageList.add(pendingMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        chatRecycler.smoothScrollToPosition(messageList.size() - 1);

        try {
            File file = new File(path);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            byte[] bytes = baos.toByteArray();
            fis.close();

            // Save copy locally
            com.hamraj37.somechat.utils.MediaUtils.saveMediaLocally(this, bytes, "voice", messageId);

            // 2. Encrypt bytes
            javax.crypto.SecretKey fileKey = com.hamraj37.somechat.utils.EncryptionManager.generateAESKey();
            byte[] encryptedBytes = com.hamraj37.somechat.utils.EncryptionManager.encryptRaw(bytes, fileKey);
            String encodedFileKey = com.hamraj37.somechat.utils.EncryptionManager.encodeKey(fileKey);

            String fileName = messageId + ".3gp";
            com.hamraj37.somechat.utils.GitHubStorage.uploadBytes(encryptedBytes, "group_voice_messages", fileName, new com.hamraj37.somechat.utils.GitHubStorage.UploadCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    runOnUiThread(() -> {
                        try {
                            org.json.JSONObject mediaJson = new org.json.JSONObject();
                            mediaJson.put("u", downloadUrl);
                            mediaJson.put("k", encodedFileKey);
                            String mediaInfo = mediaJson.toString();

                            Message message = new Message(messageId, senderId, groupId, "Voice message", System.currentTimeMillis(), "voice", mediaInfo, duration);
                            message.setSenderName(senderName);
                            message.setSenderUsername(senderUsername);
                            
                            if (replyingToMessage != null) {
                                message.setReplyToId(replyingToMessage.getMessageId());
                                message.setReplyToName(replyingToMessage.getSenderName());
                                message.setReplyToText(replyingToMessage.getText());
                                cancelReply();
                            }

                            groupChatRef.child("messages").child(messageId).setValue(message);

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
                        Toast.makeText(GroupChatActivity.this, "Failed to upload voice message", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupAttachmentMenuListeners() {
        findViewById(R.id.option_photo).setOnClickListener(v -> {
            toggleAttachmentMenu();
            pickImageLauncher.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                    .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });
        findViewById(R.id.option_video).setOnClickListener(v -> {
            toggleAttachmentMenu();
            pickVideoLauncher.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                    .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE)
                    .build());
        });
        findViewById(R.id.option_camera).setOnClickListener(v -> {
            toggleAttachmentMenu();
            openCamera();
        });
    }

    private void openCamera() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 201);
            return;
        }

        try {
            File photoFile = File.createTempFile("IMG_", ".jpg", getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES));
            photoUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
            takePhotoLauncher.launch(photoUri);
        } catch (IOException e) {
            android.util.Log.e("GroupChatActivity", "Error creating photo file", e);
        }
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

    private void uploadMedia(android.net.Uri uri, String type) {
        if (senderName == null || senderUsername == null) {
            Toast.makeText(this, "Please wait, loading user info...", Toast.LENGTH_SHORT).show();
            return;
        }
        String messageId = groupChatRef.child("messages").push().getKey();
        if (messageId == null) return;

        String extension = type.equals("image") ? ".jpg" : ".mp4";
        String fileName = messageId + extension;
        String remoteFolder = type.equals("image") ? "group_images" : "group_videos";

        // Add placeholder message locally
        Message pendingMsg = new Message(messageId, senderId, groupId, type.substring(0,1).toUpperCase() + type.substring(1), System.currentTimeMillis(), type, "local:" + uri.toString(), 0);
        pendingMsg.setSenderName(senderName);
        pendingMsg.setSenderUsername(senderUsername);
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
            inputStream.close();

            // Save a copy locally like WhatsApp
            com.hamraj37.somechat.utils.MediaUtils.saveMediaLocally(this, bytes, type, messageId);

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
                            String mediaInfo = mediaJson.toString();

                            Message message = new Message(messageId, senderId, groupId, type.substring(0,1).toUpperCase() + type.substring(1), System.currentTimeMillis(), type, mediaInfo, 0);
                            message.setSenderName(senderName);
                            message.setSenderUsername(senderUsername);
                            
                            if (replyingToMessage != null) {
                                message.setReplyToId(replyingToMessage.getMessageId());
                                message.setReplyToName(replyingToMessage.getSenderName());
                                message.setReplyToText(replyingToMessage.getText());
                                cancelReply();
                            }

                            groupChatRef.child("messages").child(messageId).setValue(message);
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
                        android.util.Log.e("GroupChatActivity", "Media Upload failed", e);
                        Toast.makeText(GroupChatActivity.this, "Failed to upload " + type, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            android.util.Log.e("GroupChatActivity", "File preparation failed", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 200) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted. Hold the button to record.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 201) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openCamera();
            }
        }
    }

    private void fetchSenderName() {
        FirebaseDatabase.getInstance().getReference("users").child(senderId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            senderName = snapshot.child("displayName").getValue(String.class);
                            senderUsername = snapshot.child("username").getValue(String.class);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void setupBackground() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
        String bgPath = prefs.getString("chat_background_path", null);
        ImageView bgImage = findViewById(R.id.chat_background_image);
        if (bgPath != null && bgImage != null) {
            if (bgPath.startsWith("res:")) {
                int resId = getResources().getIdentifier(bgPath.replace("res:", ""), "drawable", getPackageName());
                if (resId != 0) com.bumptech.glide.Glide.with(this).load(resId).into(bgImage);
            } else {
                java.io.File file = new java.io.File(bgPath);
                if (file.exists()) {
                    com.bumptech.glide.Glide.with(this)
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

        toolbarName.setText(groupName);
        if (groupAvatar != null && !groupAvatar.isEmpty()) {
            Glide.with(this).load(groupAvatar).circleCrop().into(toolbarAvatar);
        } else {
            toolbarAvatar.setImageResource(R.mipmap.ic_launcher_round);
        }

        groupInfoContainer.setOnClickListener(v -> {
            Intent intent = new Intent(this, GroupInfoActivity.class);
            intent.putExtra("groupId", groupId);
            startActivity(intent);
        });
    }

    private void setupRecyclerView() {
        chatRecycler = findViewById(R.id.chat_recycler);
        adapter = new MessageAdapter(messageList, true, new MessageAdapter.OnMessageClickListener() {
            @Override
            public void onReplyClick(String messageId) {
                for (int i = 0; i < messageList.size(); i++) {
                    if (messageList.get(i).getMessageId().equals(messageId)) {
                        chatRecycler.smoothScrollToPosition(i + 1);
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
            public void onMessageClick(Message message) {}

            @Override
            public void onSelectionChanged(int count) {
                updateSelectionUI(count);
            }

            @Override
            public void onReactionClick(Message message, String emoji) {
                groupChatRef.child("messages").child(message.getMessageId()).child("reactions").child(senderId).setValue(emoji);
            }
        });
        chatRecycler.setLayoutManager(new LinearLayoutManager(this));
        chatRecycler.setAdapter(adapter);
    }

    private void updateMembershipUI() {
        if (!isMember) {
            messageInput.setEnabled(false);
            messageInput.setHint("You can't send messages to this group");
            btnSend.setEnabled(false);
            btnAttachment.setEnabled(false);
            btnAttachment.setAlpha(0.5f);
            btnSend.setAlpha(0.5f);
            hideAttachmentMenu();
        } else {
            messageInput.setEnabled(true);
            messageInput.setHint("Type a message...");
            btnSend.setEnabled(true);
            btnAttachment.setEnabled(true);
            btnAttachment.setAlpha(1.0f);
            btnSend.setAlpha(1.0f);
        }
    }

    private void setupFirebase() {
        groupChatRef = FirebaseDatabase.getInstance().getReference("group_chats").child(groupId);
        messageListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isMember) return; // Extra safety
                messageList.clear();
                pinnedMessages.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Message message = ds.getValue(Message.class);
                    if (message != null) {
                        if (message.isPinned()) {
                            pinnedMessages.add(message);
                        }
                        messageList.add(message);
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

                adapter.notifyDataSetChanged();
                if (!messageList.isEmpty()) {
                    chatRecycler.scrollToPosition(adapter.getItemCount() - 1);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
    }

    private void listenForGroupDetails() {
        groupDetailsRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId);
        groupDetailsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                com.hamraj37.somechat.models.Group g = snapshot.getValue(com.hamraj37.somechat.models.Group.class);
                if (g != null) {
                    groupName = g.getGroupName();
                    groupAvatar = g.getGroupAvatar();
                    
                    TextView toolbarName = findViewById(R.id.toolbar_name);
                    ImageView toolbarAvatar = findViewById(R.id.toolbar_avatar);
                    
                    toolbarName.setText(groupName);
                    if (groupAvatar != null && !groupAvatar.isEmpty()) {
                        Glide.with(GroupChatActivity.this).load(groupAvatar).circleCrop().into(toolbarAvatar);
                    } else {
                        toolbarAvatar.setImageResource(R.mipmap.ic_launcher_round);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        groupDetailsRef.addValueEventListener(groupDetailsListener);
    }

    private void checkMembership() {
        membershipRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId).child("members").child(senderId);
        membershipListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean wasMember = isMember;
                isMember = snapshot.exists() && Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                updateMembershipUI();
                
                if (!isMember) {
                    if (groupChatRef != null && messageListener != null) {
                        groupChatRef.child("messages").removeEventListener(messageListener);
                    }
                } else if (!wasMember || messageList.isEmpty()) {
                    // Attach listener if it wasn't attached or if we need a fresh load
                    if (groupChatRef != null && messageListener != null) {
                        groupChatRef.child("messages").removeEventListener(messageListener); // Avoid duplicates
                        groupChatRef.child("messages").addValueEventListener(messageListener);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        membershipRef.addValueEventListener(membershipListener);
    }

    private void sendMessage() {
        if (senderName == null || senderUsername == null) {
            // Still loading user info
            Toast.makeText(this, "Please wait, loading user info...", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        if (editingMessage != null) {
            updateMessage(editingMessage, text);
            return;
        }

        String messageId = groupChatRef.child("messages").push().getKey();
        Message message = new Message(messageId, senderId, groupId, text, System.currentTimeMillis());
        message.setSenderName(senderName);
        message.setSenderUsername(senderUsername);

        if (replyingToMessage != null) {
            message.setReplyToId(replyingToMessage.getMessageId());
            message.setReplyToName(replyingToMessage.getSenderName());
            message.setReplyToText(replyingToMessage.getText());
            cancelReply();
        }

        if (messageId != null) {
            groupChatRef.child("messages").child(messageId).setValue(message);
            messageInput.setText("");
        }
    }

    private void cancelReply() {
        replyingToMessage = null;
        replyLayout.setVisibility(View.GONE);
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
            if (groupChatRef != null) {
                groupChatRef.child("messages").child(message.getMessageId()).child("reactions").child(senderId).setValue(emoji);
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

    private void updateSelectionUI(int count) {
        if (count > 0) {
            groupInfoContainer.setVisibility(View.GONE);
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

            findViewById(R.id.btn_reply_selected).setVisibility(isSingle ? View.VISIBLE : View.GONE);
            findViewById(R.id.btn_copy_selected).setVisibility(isSingle && isTextOnly ? View.VISIBLE : View.GONE);
            
            // Show edit only for single text message sent by me
            boolean isMyMessage = isSingle && selected.get(0).getSenderId().equals(senderId);
            findViewById(R.id.btn_edit_selected).setVisibility(isSingle && isTextOnly && isMyMessage ? View.VISIBLE : View.GONE);

            androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            toolbar.setNavigationOnClickListener(v -> adapter.clearSelection());
        } else {
            groupInfoContainer.setVisibility(View.VISIBLE);
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
            String text;
            boolean isMe = m.getSenderId().equals(senderId);
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

    private void showReplyLayout(Message message) {
        replyingToMessage = message;
        cancelEdit();
        replyLayout.setVisibility(View.VISIBLE);
        
        replyName.setText(message.getSenderName());
        
        String displayText;
        boolean isMe = message.getSenderId().equals(senderId);
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

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + selected.size() + " messages?")
                .setMessage("Delete these messages from this group for you?")
                .setPositiveButton("Delete for me", (dialog, which) -> {
                    for (Message m : selected) {
                        groupChatRef.child("messages").child(m.getMessageId()).removeValue();
                    }
                    adapter.clearSelection();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pinSelectedMessages() {
        List<Message> selected = adapter.getSelectedMessages();
        if (selected.isEmpty()) return;

        for (Message m : selected) {
            groupChatRef.child("messages").child(m.getMessageId()).child("pinned").setValue(true);
        }
        adapter.clearSelection();
        Toast.makeText(this, selected.size() > 1 ? "Messages pinned" : "Message pinned", Toast.LENGTH_SHORT).show();
    }

    private void unpinCurrentMessage() {
        if (pinnedMessages.isEmpty() || currentPinnedIndex >= pinnedMessages.size()) return;
        
        Message current = pinnedMessages.get(currentPinnedIndex);
        groupChatRef.child("messages").child(current.getMessageId()).child("pinned").setValue(false);
        Toast.makeText(this, "Message unpinned", Toast.LENGTH_SHORT).show();
    }

    private void showPinnedMessage(Message message) {
        pinnedMessageLayout.setVisibility(View.VISIBLE);
        
        if (pinnedMessages.size() > 1) {
            pinnedMessageTitle.setText("Pinned Message (" + (currentPinnedIndex + 1) + "/" + pinnedMessages.size() + ")");
        } else {
            pinnedMessageTitle.setText("Pinned Message");
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
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("text", newText); // Group chat doesn't seem to use E2EE for text based on sendMessage
        updates.put("edited", true);

        groupChatRef.child("messages").child(message.getMessageId()).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    cancelEdit();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update message", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        openedGroupId = groupId;
    }

    @Override
    protected void onPause() {
        super.onPause();
        openedGroupId = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (groupChatRef != null && messageListener != null) {
            groupChatRef.child("messages").removeEventListener(messageListener);
        }
        if (membershipRef != null && membershipListener != null) {
            membershipRef.removeEventListener(membershipListener);
        }
        if (groupDetailsRef != null && groupDetailsListener != null) {
            groupDetailsRef.removeEventListener(groupDetailsListener);
        }
    }
}
