package com.samechat37;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
    private DatabaseReference chatRef;
    private boolean isFriend = false;
    private ValueEventListener messageListener;
    public static String openedChatId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isNightMode);
        setContentView(R.layout.activity_chat);

        receiverId = getIntent().getStringExtra("uid");
        receiverName = getIntent().getStringExtra("displayName");
        receiverAvatar = getIntent().getStringExtra("photoUrl");
        senderId = FirebaseAuth.getInstance().getUid();

        // Set openedChatId for notification filtering
        openedChatId = receiverId;

        setupToolbar();
        setupRecyclerView();
        checkFriendStatus();

        messageInput = findViewById(R.id.message_input);
        findViewById(R.id.btn_send).setOnClickListener(v -> sendMessage());
        findViewById(R.id.btn_audio_call).setOnClickListener(v -> startCall(false));
        findViewById(R.id.btn_video_call).setOnClickListener(v -> startCall(true));

        setupPresenceListener();
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

    private void setupPresenceListener() {
        TextView toolbarStatus = findViewById(R.id.toolbar_status);
        FirebaseDatabase.getInstance().getReference("users").child(receiverId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Boolean isOnline = snapshot.child("online").getValue(Boolean.class);
                            Long lastSeen = snapshot.child("lastSeen").getValue(Long.class);

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
                });
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
        }
    }

    private void setupRecyclerView() {
        chatRecycler = findViewById(R.id.chat_recycler);
        adapter = new MessageAdapter(messageList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecycler.setLayoutManager(layoutManager);
        chatRecycler.setAdapter(adapter);
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
                adapter.notifyDataSetChanged();
                if (!messageList.isEmpty()) {
                    chatRecycler.smoothScrollToPosition(messageList.size() - 1);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        chatRef.addValueEventListener(messageListener);
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
        Message message = new Message(messageId, senderId, receiverId, text, System.currentTimeMillis());

        if (messageId != null) {
            chatRef.child(messageId).setValue(message);
            messageInput.setText("");
        }
    }
}
