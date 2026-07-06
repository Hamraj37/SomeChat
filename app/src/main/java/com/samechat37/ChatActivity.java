package com.samechat37;

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
import com.samechat37.adapters.MessageAdapter;
import com.samechat37.models.Message;
import com.samechat37.utils.PresenceManager;

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
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isNightMode);
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
    }

    private void initChat() {
        receiverId = getIntent().getStringExtra("uid");
        receiverName = getIntent().getStringExtra("displayName");
        receiverAvatar = getIntent().getStringExtra("photoUrl");
        senderId = FirebaseAuth.getInstance().getUid();

        openedChatId = receiverId;

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
        if (getIntent().hasExtra("forward_content")) {
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
            String myPubKey = com.samechat37.utils.EncryptionManager.getMyPublicKey(this);
            if (receiverPublicKey != null && myPubKey != null) {
                try {
                    // Content is decrypted JSON info from forwardMessage()
                    encryptedMediaInfo = com.samechat37.utils.EncryptionManager.encrypt(content, receiverPublicKey, myPubKey);
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
            android.util.Log.e("ChatActivity", "Error creating photo file", e);
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

        String extension = type.equals("image") ? ".jpg" : ".mp4";
        String fileName = messageId + extension;
        String remoteFolder = type.equals("image") ? "images" : "videos";

        // Add placeholder message locally
        Message pendingMsg = new Message(messageId, senderId, receiverId, type.substring(0,1).toUpperCase() + type.substring(1), System.currentTimeMillis(), type, "local:" + uri.toString(), 0);
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
            com.samechat37.utils.MediaUtils.saveMediaLocally(this, bytes, type, messageId);

            // 2. Encrypt bytes
            javax.crypto.SecretKey fileKey = com.samechat37.utils.EncryptionManager.generateAESKey();
            byte[] encryptedBytes = com.samechat37.utils.EncryptionManager.encryptRaw(bytes, fileKey);
            String encodedFileKey = com.samechat37.utils.EncryptionManager.encodeKey(fileKey);

            // 3. Upload encrypted bytes
            com.samechat37.utils.GitHubStorage.uploadBytes(encryptedBytes, remoteFolder, fileName, new com.samechat37.utils.GitHubStorage.UploadCallback() {
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

                            String finalContent = mediaInfo;
                            String myPubKey = com.samechat37.utils.EncryptionManager.initKeys(ChatActivity.this);
                            if (receiverPublicKey != null && myPubKey != null) {
                                finalContent = com.samechat37.utils.EncryptionManager.encrypt(mediaInfo, receiverPublicKey, myPubKey);
                            }

                            Message message = new Message(messageId, senderId, receiverId, type.substring(0,1).toUpperCase() + type.substring(1), System.currentTimeMillis(), type, finalContent, 0);
                            
                            if (replyingToMessage != null) {
                                message.setReplyToId(replyingToMessage.getMessageId());
                                boolean isMe = replyingToMessage.getSenderId().equals(senderId);
                                message.setReplyToName(isMe ? "You" : receiverName);
                                String replyDisplayText;
                                if (replyingToMessage.getType() == null || "text".equals(replyingToMessage.getType())) {
                                    replyDisplayText = com.samechat37.utils.EncryptionManager.decrypt(replyingToMessage.getText(), ChatActivity.this, isMe);
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
            android.widget.Toast.makeText(this, "Recording...", android.widget.Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            android.util.Log.e("ChatActivity", "Recording failed", e);
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
                android.widget.Toast.makeText(this, "Message too short", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            uploadVoiceMessage(audioPath, (int) duration);
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Stop recording failed", e);
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
            fis.close();

            // Save copy locally
            com.samechat37.utils.MediaUtils.saveMediaLocally(this, bytes, "voice", messageId);

            // 2. Encrypt bytes
            javax.crypto.SecretKey fileKey = com.samechat37.utils.EncryptionManager.generateAESKey();
            byte[] encryptedBytes = com.samechat37.utils.EncryptionManager.encryptRaw(bytes, fileKey);
            String encodedFileKey = com.samechat37.utils.EncryptionManager.encodeKey(fileKey);

            String fileName = messageId + ".3gp";
            com.samechat37.utils.GitHubStorage.uploadBytes(encryptedBytes, "voice_messages", fileName, new com.samechat37.utils.GitHubStorage.UploadCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    runOnUiThread(() -> {
                        adapter.removeUploadProgress(messageId);

                        try {
                            org.json.JSONObject mediaJson = new org.json.JSONObject();
                            mediaJson.put("u", downloadUrl);
                            mediaJson.put("k", encodedFileKey);
                            String mediaInfo = mediaJson.toString();

                            String finalContent = mediaInfo;
                            String myPubKey = com.samechat37.utils.EncryptionManager.getMyPublicKey(ChatActivity.this);
                            if (receiverPublicKey != null && myPubKey != null) {
                                finalContent = com.samechat37.utils.EncryptionManager.encrypt(mediaInfo, receiverPublicKey, myPubKey);
                            }

                            Message message = new Message(messageId, senderId, receiverId, "Voice message", System.currentTimeMillis(), "voice", finalContent, duration);
                            
                            if (replyingToMessage != null) {
                                message.setReplyToId(replyingToMessage.getMessageId());
                                boolean isMe = replyingToMessage.getSenderId().equals(senderId);
                                message.setReplyToName(isMe ? "You" : receiverName);
                                String replyDisplayText;
                                if (replyingToMessage.getType() == null || "text".equals(replyingToMessage.getType())) {
                                    replyDisplayText = com.samechat37.utils.EncryptionManager.decrypt(replyingToMessage.getText(), ChatActivity.this, isMe);
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
        Intent intent = new Intent(this, CallActivity.class);
        intent.putExtra("uid", receiverId);
        intent.putExtra("displayName", receiverName);
        intent.putExtra("photoUrl", receiverAvatar);
        intent.putExtra("isIncoming", false);
        intent.putExtra("isVideo", isVideo);
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
                showPopupMenu(message, view);
            }
        });
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecycler.setLayoutManager(layoutManager);
        chatRecycler.setAdapter(adapter);

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

    private void showPopupMenu(Message message, View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenu().add("Copy Text");
        popup.getMenu().add("Reply");
        
        boolean isLocal = message.getMediaUrl() != null && message.getMediaUrl().startsWith("local:");
        if (!isLocal) {
            popup.getMenu().add("Forward");
        }

        popup.getMenu().add("Delete");

        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            switch (title) {
                case "Copy Text":
                    copyMessage(message);
                    return true;
                case "Reply":
                    showReplyLayout(message);
                    return true;
                case "Forward":
                    forwardMessage(message);
                    return true;
                case "Delete":
                    deleteMessage(message);
                    return true;
            }
            return false;
        });
        popup.show();
    }

    private void copyMessage(Message message) {
        String textToCopy;
        boolean isMe = message.getSenderId().equals(senderId);
        if (message.getType() == null || message.getType().equals("text")) {
            textToCopy = com.samechat37.utils.EncryptionManager.decrypt(message.getText(), this, isMe);
        } else {
            textToCopy = message.getType();
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("message", textToCopy);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void forwardMessage(Message message) {
        // Simple forward: send message content to SearchUserActivity to pick a recipient
        Intent intent = new Intent(this, SearchUserActivity.class);
        intent.putExtra("forward_message", true);
        
        boolean isMe = message.getSenderId().equals(senderId);
        String content;
        if (message.getType() == null || message.getType().equals("text")) {
            content = com.samechat37.utils.EncryptionManager.decrypt(message.getText(), this, isMe);
        } else {
            content = com.samechat37.utils.EncryptionManager.decrypt(message.getMediaUrl(), this, isMe);
        }
        
        intent.putExtra("content", content);
        intent.putExtra("type", message.getType());
        intent.putExtra("duration", message.getDuration());
        startActivity(intent);
        Toast.makeText(this, "Select a user to forward to", Toast.LENGTH_SHORT).show();
    }

    private void deleteMessage(Message message) {
        if (message.getSenderId().equals(senderId)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Message")
                    .setMessage("Are you sure you want to delete this message?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        chatRef.child(message.getMessageId()).removeValue();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            Toast.makeText(this, "You can only delete your own messages", Toast.LENGTH_SHORT).show();
        }
    }

    private void showReplyLayout(Message message) {
        replyingToMessage = message;
        replyLayout.setVisibility(View.VISIBLE);
        
        boolean isMe = message.getSenderId().equals(senderId);
        replyName.setText(isMe ? "You" : receiverName);
        
        String displayText;
        if (message.getType() == null || "text".equals(message.getType())) {
            displayText = com.samechat37.utils.EncryptionManager.decrypt(message.getText(), this, isMe);
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

                messageList.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Message message = dataSnapshot.getValue(Message.class);
                    if (message != null) {
                        messageList.add(message);
                        // Mark as seen if we are the receiver
                        if (message.getReceiverId().equals(senderId) && !message.isSeen()) {
                            dataSnapshot.getRef().child("seen").setValue(true);
                        }
                    }
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
                    chatRecycler.scrollToPosition(messageList.size());
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
        DatabaseReference requestRef = FirebaseDatabase.getInstance().getReference("friendRequests")
                .child(receiverId)
                .child(senderId);

        requestRef.setValue("pending").addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                android.widget.Toast.makeText(this, "Friend request sent!", android.widget.Toast.LENGTH_SHORT).show();
                // Lock the UI after sending request
                findViewById(R.id.btn_send).setEnabled(false);
                messageInput.setHint("Waiting for approval...");
                messageInput.setEnabled(false);
            }
        });
    }

    private void sendMessage() {
        if (!isFriend) {
            android.widget.Toast.makeText(this, "You must be friends to send messages", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        String text = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        String messageId = chatRef.push().getKey();
        
        String encryptedText = text;
        String myPubKey = com.samechat37.utils.EncryptionManager.getMyPublicKey(this);
        if (receiverPublicKey != null && myPubKey != null) {
            encryptedText = com.samechat37.utils.EncryptionManager.encrypt(text, receiverPublicKey, myPubKey);
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
                replyDisplayText = com.samechat37.utils.EncryptionManager.decrypt(replyingToMessage.getText(), this, isMe);
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
