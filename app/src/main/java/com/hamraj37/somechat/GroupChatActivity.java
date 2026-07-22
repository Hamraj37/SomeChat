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
        findViewById(R.id.btn_cancel_reply).setOnClickListener(v -> cancelReply());
        
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

        setupRecyclerView();
        setupInputListeners();
        setupAttachmentMenuListeners();

        initGroupChat();
        checkEncryptionStatus();

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

    private void checkEncryptionStatus() {
        if (com.hamraj37.somechat.utils.EncryptionManager.getMyPrivateKey(this) == null) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Encryption Required")
                    .setMessage("Your encryption keys are not set up. You won't be able to read or send secure messages until you initialize them.")
                    .setPositiveButton("Initialize Now", (dialog, which) -> {
                        String pubKey = com.hamraj37.somechat.utils.EncryptionManager.initKeys(this);
                        String privKey = com.hamraj37.somechat.utils.EncryptionManager.getMyPrivateKey(this);

                        if (senderId != null) {
                            java.util.Map<String, Object> keyMap = new java.util.HashMap<>();
                            keyMap.put("publicKey", pubKey);
                            keyMap.put("privateKey", privKey);
                            FirebaseDatabase.getInstance().getReference("users").child(senderId).updateChildren(keyMap);
                        }
                        
                        Toast.makeText(this, "Encryption initialized. Please restart the chat to see decrypted messages.", Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Later", null)
                    .show();
        }
    }

    private void initGroupChat() {
        groupChatRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId);
        openedGroupId = groupId;

        setupToolbar();
        checkMembership();
        loadGroupDetails();
        setupFirebase();
    }

    private void checkMembership() {
        membershipRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId).child("members").child(senderId);
        membershipListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                isMember = snapshot.exists();
                updateInputUI();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        membershipRef.addValueEventListener(membershipListener);
    }

    private void updateInputUI() {
        if (isMember) {
            inputArea.setVisibility(View.VISIBLE);
            findViewById(R.id.not_member_text).setVisibility(View.GONE);
        } else {
            inputArea.setVisibility(View.GONE);
            findViewById(R.id.not_member_text).setVisibility(View.VISIBLE);
        }
    }

    private void loadGroupDetails() {
        groupDetailsRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId);
        groupDetailsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    groupName = snapshot.child("name").getValue(String.class);
                    groupAvatar = snapshot.child("avatar").getValue(String.class);
                    updateToolbar();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        groupDetailsRef.addValueEventListener(groupDetailsListener);
    }

    private void updateToolbar() {
        TextView toolbarName = findViewById(R.id.toolbar_name);
        ImageView toolbarAvatar = findViewById(R.id.toolbar_avatar);
        toolbarName.setText(groupName);
        if (groupAvatar != null && !groupAvatar.isEmpty()) {
            Glide.with(this).load(groupAvatar).circleCrop().into(toolbarAvatar);
        }
    }

    private void checkForwardedMessage() {
        if (getIntent().hasExtra("forward_list")) {
            ArrayList<Message> list = (ArrayList<Message>) getIntent().getSerializableExtra("forward_list");
            if (list != null && !list.isEmpty()) {
                for (Message m : list) {
                    sendForwardedMessage(m);
                }
            }
            getIntent().removeExtra("forward_list");
        }
    }

    private void sendForwardedMessage(Message m) {
        String messageId = groupChatRef.child("messages").push().getKey();
        if (messageId == null) return;

        Message message = new Message();
        message.setMessageId(messageId);
        message.setSenderId(senderId);
        message.setReceiverId(groupId);
        message.setTimestamp(System.currentTimeMillis());
        message.setType(m.getType());
        message.setSenderName(senderName);
        message.setSenderUsername(senderUsername);
        message.setForwarded(true);
        message.setDuration(m.getDuration());

        if (m.getType() == null || "text".equals(m.getType())) {
            message.setText(m.getText());
        } else {
            message.setMediaUrl(m.getMediaUrl());
        }

        groupChatRef.child("messages").child(messageId).setValue(message);
    }

    private void checkPinnedScroll() {
        if (getIntent().hasExtra("scroll_to_message_id")) {
            String targetId = getIntent().getStringExtra("scroll_to_message_id");
            if (targetId != null) {
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
            android.util.Log.e("GroupChatActivity", "Error creating photo file", e);
        }
    }

    private void recordVideo() {
        try {
            File videoFile = File.createTempFile("VID_", ".mp4", getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES));
            cameraUri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", videoFile);
            takeVideoLauncher.launch(cameraUri);
        } catch (IOException e) {
            android.util.Log.e("GroupChatActivity", "Error creating video file", e);
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
                            if (diff > 150) {
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
        if (senderName == null || senderUsername == null) {
            Toast.makeText(this, "Please wait, loading user info...", Toast.LENGTH_SHORT).show();
            return;
        }
        String messageId = groupChatRef.child("messages").push().getKey();
        if (messageId == null) return;

        String fileName;
        String remoteFolder;
        if (type.equals("file")) {
            fileName = com.hamraj37.somechat.utils.FileUtils.getFileName(this, uri);
            remoteFolder = "group_documents";
        } else {
            String extension = type.equals("image") ? ".jpg" : ".mp4";
            fileName = messageId + extension;
            remoteFolder = type.equals("image") ? "group_images" : "group_videos";
        }

        String displayPreview = type.equals("file") ? fileName : type.substring(0,1).toUpperCase() + type.substring(1);
        Message pendingMsg = new Message(messageId, senderId, groupId, displayPreview, System.currentTimeMillis(), type, "local:" + uri.toString(), 0);
        pendingMsg.setSenderName(senderName);
        pendingMsg.setSenderUsername(senderUsername);
        messageList.add(pendingMsg);
        adapter.notifyItemInserted(messageList.size() - 1);
        chatRecycler.smoothScrollToPosition(messageList.size() - 1);

        try {
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

            com.hamraj37.somechat.utils.MediaUtils.saveMediaLocally(this, bytes, type, messageId, type.equals("file") ? fileName : null);

            javax.crypto.SecretKey fileKey = com.hamraj37.somechat.utils.EncryptionManager.generateAESKey();
            byte[] encryptedBytes = com.hamraj37.somechat.utils.EncryptionManager.encryptRaw(bytes, fileKey);
            String encodedFileKey = com.hamraj37.somechat.utils.EncryptionManager.encodeKey(fileKey);

            com.hamraj37.somechat.utils.GitHubStorage.uploadBytes(encryptedBytes, remoteFolder, fileName, new com.hamraj37.somechat.utils.GitHubStorage.UploadCallback() {
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

                            String displayPreviewFinal = type.equals("file") ? fileName : type.substring(0,1).toUpperCase() + type.substring(1);
                            Message message = new Message(messageId, senderId, groupId, displayPreviewFinal, System.currentTimeMillis(), type, mediaInfo, 0);
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
                        Toast.makeText(GroupChatActivity.this, "Failed to upload " + type, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
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
            final long fileSize = bytes.length;
            fis.close();

            com.hamraj37.somechat.utils.MediaUtils.saveMediaLocally(this, bytes, "voice", messageId);

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
                            mediaJson.put("s", fileSize);
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

    private void fetchSenderName() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseDatabase.getInstance().getReference("users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        senderName = snapshot.child("displayName").getValue(String.class);
                        senderUsername = snapshot.child("username").getValue(String.class);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        
        findViewById(R.id.group_info_container).setOnClickListener(v -> {
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
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecycler.setLayoutManager(layoutManager);
        chatRecycler.setAdapter(adapter);
    }

    private void setupFirebase() {
        messageListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
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

                        if (message.isPinned()) pinnedMessages.add(message);

                        messageList.add(message);
                    }
                }

                if (!pinnedMessages.isEmpty()) {
                    if (currentPinnedIndex >= pinnedMessages.size()) currentPinnedIndex = 0;
                    showPinnedMessage(pinnedMessages.get(currentPinnedIndex));
                } else {
                    pinnedMessageLayout.setVisibility(View.GONE);
                }

                for (Message pending : pendingUploads) {
                    boolean alreadyExists = false;
                    for (Message m : messageList) {
                        if (m.getMessageId() != null && m.getMessageId().equals(pending.getMessageId())) {
                            alreadyExists = true;
                            break;
                        }
                    }
                    if (!alreadyExists) messageList.add(pending);
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
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        groupChatRef.child("messages").limitToLast(50).addValueEventListener(messageListener);
        checkForwardedMessage();
    }

    private void showPinnedMessage(Message message) {
        pinnedMessageLayout.setVisibility(View.VISIBLE);
        if (pinnedMessages.size() > 1) {
            pinnedMessageTitle.setText("Pinned Message (" + (currentPinnedIndex + 1) + "/" + pinnedMessages.size() + ")");
        } else {
            pinnedMessageTitle.setText("Pinned Message");
        }
        pinnedMessageText.setText(message.getText());
        pinnedMessageLayout.setOnClickListener(v -> {
            for (int i = 0; i < messageList.size(); i++) {
                if (messageList.get(i).getMessageId().equals(message.getMessageId())) {
                    chatRecycler.smoothScrollToPosition(i + 1);
                    adapter.highlightMessage(message.getMessageId());
                    break;
                }
            }
            if (pinnedMessages.size() > 1) {
                currentPinnedIndex = (currentPinnedIndex + 1) % pinnedMessages.size();
                showPinnedMessage(pinnedMessages.get(currentPinnedIndex));
            }
        });
    }

    private void unpinCurrentMessage() {
        if (pinnedMessages.isEmpty()) return;
        Message current = pinnedMessages.get(currentPinnedIndex);
        groupChatRef.child("messages").child(current.getMessageId()).child("pinned").removeValue();
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

    private void showReplyLayout(Message message) {
        replyingToMessage = message;
        replyLayout.setVisibility(View.VISIBLE);
        replyName.setText(message.getSenderName());
        replyText.setText(message.getText());
        messageInput.requestFocus();
    }

    private void cancelReply() {
        replyingToMessage = null;
        replyLayout.setVisibility(View.GONE);
    }

    private void enterEditMode(Message message) {
        editingMessage = message;
        editLayout.setVisibility(View.VISIBLE);
        editTextPreview.setText(message.getText());
        messageInput.setText(message.getText());
        messageInput.requestFocus();
        btnSend.setImageResource(android.R.drawable.ic_menu_save);
    }

    private void cancelEdit() {
        editingMessage = null;
        editLayout.setVisibility(View.GONE);
        messageInput.setText("");
        btnSend.setImageResource(android.R.drawable.ic_btn_speak_now);
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
        String text = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        if (editingMessage != null) {
            groupChatRef.child("messages").child(editingMessage.getMessageId()).child("text").setValue(text);
            groupChatRef.child("messages").child(editingMessage.getMessageId()).child("edited").setValue(true);
            cancelEdit();
            return;
        }

        String messageId = groupChatRef.child("messages").push().getKey();
        if (messageId == null) return;

        Message message = new Message(messageId, senderId, groupId, text, System.currentTimeMillis());
        message.setSenderName(senderName);
        message.setSenderUsername(senderUsername);

        if (replyingToMessage != null) {
            message.setReplyToId(replyingToMessage.getMessageId());
            message.setReplyToName(replyingToMessage.getSenderName());
            message.setReplyToText(replyingToMessage.getText());
            cancelReply();
        }

        groupChatRef.child("messages").child(messageId).setValue(message);
        messageInput.setText("");
    }

    private void deleteSelectedMessages() {
        List<Message> selected = adapter.getSelectedMessages();
        for (Message m : selected) {
            groupChatRef.child("messages").child(m.getMessageId()).removeValue();
        }
        adapter.clearSelection();
    }

    private void copySelectedMessages() {
        List<Message> selected = adapter.getSelectedMessages();
        if (selected.size() == 1) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("message", selected.get(0).getText());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
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
        for (Message m : selected) {
            groupChatRef.child("messages").child(m.getMessageId()).child("pinned").setValue(true);
        }
        adapter.clearSelection();
    }

    private void editSelectedMessage() {
        List<Message> selected = adapter.getSelectedMessages();
        if (selected.size() == 1) {
            enterEditMode(selected.get(0));
            adapter.clearSelection();
        }
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
        updateLastSeen();
    }

    private void updateLastSeen() {
        if (senderId != null && groupId != null) {
            FirebaseDatabase.getInstance().getReference("users")
                    .child(senderId)
                    .child("groupLastSeen")
                    .child(groupId)
                    .setValue(System.currentTimeMillis());
        }
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
