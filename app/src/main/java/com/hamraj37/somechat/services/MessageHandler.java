package com.hamraj37.somechat.services;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.ChatActivity;
import com.hamraj37.somechat.models.Message;
import com.hamraj37.somechat.utils.EncryptionManager;
import java.util.HashMap;
import java.util.Map;

public class MessageHandler {
    private final Context context;
    private final MessageListener listener;
    private ValueEventListener friendsListener;
    private final Map<String, ValueEventListener> messageListeners = new HashMap<>();
    private final Map<String, String> userNames = new HashMap<>();

    public interface MessageListener {
        void onNewMessage(String senderId, String senderName, String text);
    }

    public MessageHandler(Context context, MessageListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void startListening() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        friendsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String friendUid = ds.getKey();
                    if (friendUid != null && !messageListeners.containsKey(friendUid)) {
                        observeChat(uid, friendUid);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        FirebaseDatabase.getInstance().getReference("friends").child(uid).addValueEventListener(friendsListener);
    }

    private void observeChat(String myUid, String friendUid) {
        FirebaseDatabase.getInstance().getReference("users").child(friendUid).child("displayName")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        userNames.put(friendUid, snapshot.getValue(String.class));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });

        String chatId = myUid.compareTo(friendUid) < 0 ? myUid + "_" + friendUid : friendUid + "_" + myUid;
        ValueEventListener chatListener = new ValueEventListener() {
            private boolean isFirstLoad = true;
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFirstLoad) { isFirstLoad = false; return; }
                DataSnapshot lastMsgSnapshot = null;
                for (DataSnapshot child : snapshot.getChildren()) lastMsgSnapshot = child;

                if (lastMsgSnapshot != null) {
                    Message message = lastMsgSnapshot.getValue(Message.class);
                    if (message != null && message.getReceiverId().equals(myUid) && !message.isSeen()) {
                        if (!friendUid.equals(ChatActivity.openedChatId)) {
                            String name = userNames.get(friendUid);
                            String text = message.getType().equals("text") ? 
                                    EncryptionManager.decrypt(message.getText(), context, false) : 
                                    message.getType().substring(0,1).toUpperCase() + message.getType().substring(1);
                            listener.onNewMessage(friendUid, name, text);
                        }
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        messageListeners.put(friendUid, chatListener);
        FirebaseDatabase.getInstance().getReference("chats").child(chatId).limitToLast(1).addValueEventListener(chatListener);
    }

    public void stopListening() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        if (friendsListener != null) FirebaseDatabase.getInstance().getReference("friends").child(uid).removeEventListener(friendsListener);
        for (Map.Entry<String, ValueEventListener> entry : messageListeners.entrySet()) {
            String chatId = uid.compareTo(entry.getKey()) < 0 ? uid + "_" + entry.getKey() : entry.getKey() + "_" + uid;
            FirebaseDatabase.getInstance().getReference("chats").child(chatId).removeEventListener(entry.getValue());
        }
    }
}
