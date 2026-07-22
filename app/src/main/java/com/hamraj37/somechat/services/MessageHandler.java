package com.hamraj37.somechat.services;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.ChatActivity;
import com.hamraj37.somechat.GroupChatActivity;
import com.hamraj37.somechat.models.Message;
import com.hamraj37.somechat.utils.EncryptionManager;
import java.util.HashMap;
import java.util.Map;

public class MessageHandler {
    private final Context context;
    private final MessageListener listener;
    private ValueEventListener friendsListener;
    private ValueEventListener groupsListener;
    private final Map<String, ValueEventListener> messageListeners = new HashMap<>();
    private final Map<String, ValueEventListener> groupMessageListeners = new HashMap<>();
    private final Map<String, ValueEventListener> groupNameListeners = new HashMap<>();
    private final Map<String, String> userNames = new HashMap<>();
    private final Map<String, String> groupNames = new HashMap<>();

    public interface MessageListener {
        void onNewMessage(String id, String name, String text, boolean isGroup);
    }

    public MessageHandler(Context context, MessageListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void startListening() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        // Listen for friends
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

        // Listen for groups
        groupsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String groupId = ds.getKey();
                    if (groupId != null && !groupMessageListeners.containsKey(groupId)) {
                        observeGroupChat(uid, groupId);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        FirebaseDatabase.getInstance().getReference("users").child(uid).child("groups").addValueEventListener(groupsListener);
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
                            String type = message.getType();
                            if (type == null) type = "text";
                            String text = type.equals("text") ? 
                                    EncryptionManager.decrypt(message.getText(), context, false) : 
                                    type.substring(0,1).toUpperCase() + type.substring(1);
                            listener.onNewMessage(friendUid, name, text, false);
                        }
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        messageListeners.put(friendUid, chatListener);
        FirebaseDatabase.getInstance().getReference("chats").child(chatId).limitToLast(1).addValueEventListener(chatListener);
    }

    private void observeGroupChat(String myUid, String groupId) {
        ValueEventListener nameListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                String name = snapshot.child("groupName").getValue(String.class);
                if (name == null) name = snapshot.child("name").getValue(String.class);
                groupNames.put(groupId, name);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        groupNameListeners.put(groupId, nameListener);
        FirebaseDatabase.getInstance().getReference("groups").child(groupId).addValueEventListener(nameListener);

        ValueEventListener groupListener = new ValueEventListener() {
            private boolean isFirstLoad = true;
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFirstLoad) { isFirstLoad = false; return; }
                DataSnapshot lastMsgSnapshot = null;
                for (DataSnapshot child : snapshot.getChildren()) lastMsgSnapshot = child;

                if (lastMsgSnapshot != null) {
                    Message message = lastMsgSnapshot.getValue(Message.class);
                    if (message != null && !message.getSenderId().equals(myUid)) {
                        if (!groupId.equals(GroupChatActivity.openedGroupId)) {
                            String gName = groupNames.get(groupId);
                            String sName = message.getSenderName();
                            String type = message.getType();
                            if (type == null) type = "text";
                            
                            String text = type.equals("text") ? message.getText() : (type.substring(0,1).toUpperCase() + type.substring(1));
                            
                            // For system messages, don't include sender prefix
                            String displayTitle = gName != null ? gName : "Group Message";
                            String displayText = ("system".equals(message.getType()) || sName == null) ? text : (sName + ": " + text);
                            
                            listener.onNewMessage(groupId, displayTitle, displayText, true);
                        }
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        groupMessageListeners.put(groupId, groupListener);
        FirebaseDatabase.getInstance().getReference("groups").child(groupId).child("messages").limitToLast(1).addValueEventListener(groupListener);
    }

    public void stopListening() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        if (friendsListener != null) FirebaseDatabase.getInstance().getReference("friends").child(uid).removeEventListener(friendsListener);
        if (groupsListener != null) FirebaseDatabase.getInstance().getReference("users").child(uid).child("groups").removeEventListener(groupsListener);
        
        for (Map.Entry<String, ValueEventListener> entry : messageListeners.entrySet()) {
            String chatId = uid.compareTo(entry.getKey()) < 0 ? uid + "_" + entry.getKey() : entry.getKey() + "_" + uid;
            FirebaseDatabase.getInstance().getReference("chats").child(chatId).removeEventListener(entry.getValue());
        }

        for (Map.Entry<String, ValueEventListener> entry : groupMessageListeners.entrySet()) {
            FirebaseDatabase.getInstance().getReference("groups").child(entry.getKey()).child("messages").removeEventListener(entry.getValue());
        }

        for (Map.Entry<String, ValueEventListener> entry : groupNameListeners.entrySet()) {
            FirebaseDatabase.getInstance().getReference("groups").child(entry.getKey()).removeEventListener(entry.getValue());
        }
    }
}
