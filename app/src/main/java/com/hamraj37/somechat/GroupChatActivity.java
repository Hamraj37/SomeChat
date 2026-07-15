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

    private View groupInfoContainer;
    private View selectionActionsContainer;
    private TextView selectionCountText;

    private ImageView btnAttachment;
    private View attachmentMenu;

    private android.media.MediaRecorder mediaRecorder;
    private String audioPath = null;
    private long recordStartTime = 0;
    private boolean isRecording = false;
    private android.net.Uri photoUri;

    private final androidx.activity.result.ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            uploadMedia(uri, "image");
                        }
                    });

    private final androidx.activity.result.ActivityResultLauncher<String> pickVideoLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
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
        
        groupInfoContainer = findViewById(R.id.group_info_container);
        selectionActionsContainer = findViewById(R.id.selection_actions_container);
        selectionCountText = findViewById(R.id.selection_count);

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

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (adapter != null && adapter.isSelectionMode()) {
                    adapter.clearSelection();
                } else if (attachmentMenu != null && attachmentMenu.getVisibility() == View.VISIBLE) {
                    hideAttachmentMenu();
                } else if (replyingToMessage != null) {
                    cancelReply();
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
                    btnSend.setImageResource(android.R.drawable.ic_btn_speak_now);
                } else {
                    btnSend.setImageResource(android.R.drawable.ic_menu_send);
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        btnSend.setOnTouchListener((v, event) -> {
            String text = messageInput.getText().toString().trim();
            if (text.isEmpty()) {
                // Voice message mode
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.performClick();
                        startRecording();
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        stopRecordingAndSend();
                        return true;
                }
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                v.performClick();
                sendMessage();
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
            Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            android.util.Log.e("GroupChatActivity", "Recording failed", e);
        }
    }

    private void stopRecordingAndSend() {
        if (!isRecording || mediaRecorder == null) return;

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;

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
            pickImageLauncher.launch("image/*");
        });
        findViewById(R.id.option_video).setOnClickListener(v -> {
            toggleAttachmentMenu();
            pickVideoLauncher.launch("video/*");
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
                    com.bumptech.glide.Glide.with(this).load(file).into(bgImage);
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
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Message message = ds.getValue(Message.class);
                    if (message != null) {
                        messageList.add(message);
                    }
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
        } else {
            groupInfoContainer.setVisibility(View.VISIBLE);
            selectionCountText.setVisibility(View.GONE);
            selectionActionsContainer.setVisibility(View.GONE);
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
