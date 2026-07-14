package com.hamraj37.somechat.ui.transform;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.db.AppDatabase;
import com.hamraj37.somechat.db.ChatItemDao;
import com.hamraj37.somechat.models.ChatItemEntity;
import com.hamraj37.somechat.models.Message;
import com.hamraj37.somechat.models.User;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class TransformViewModel extends AndroidViewModel {

    private final MutableLiveData<List<ChatItem>> mAllChats = new MutableLiveData<>();
    private final MutableLiveData<List<ChatItem>> mFilteredChats = new MutableLiveData<>();
    private final Map<String, ChatItem> chatsMap = new HashMap<>();
    private final java.util.Set<String> observedFriends = new java.util.HashSet<>();
    private final java.util.Set<String> observedGroups = new java.util.HashSet<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
    private final ChatItemDao chatItemDao;
    private String currentQuery = "";

    public TransformViewModel(@NonNull Application application) {
        super(application);
        
        AppDatabase db = AppDatabase.getDatabase(application);
        chatItemDao = db.chatItemDao();
        
        // Observe local data
        chatItemDao.getAllChatItems().observeForever(roomObserver);

        loadFriends();
        loadGroups();
    }

    private final Observer<List<ChatItemEntity>> roomObserver = entities -> {
        List<ChatItem> items = new ArrayList<>();
        for (ChatItemEntity entity : entities) {
            ChatItem item = new ChatItem(
                    entity.getName(),
                    entity.getLastMessage(),
                    entity.getTime(),
                    0,
                    entity.getUid(),
                    entity.getPhotoUrl(),
                    entity.isOnline(),
                    entity.getTimestamp(),
                    entity.getUnreadCount(),
                    entity.getLastMessageType(),
                    entity.isGroup()
            );
            items.add(item);
            chatsMap.put(item.getUid(), item);
        }
        mAllChats.setValue(items);
        filter(currentQuery);
    };

    @Override
    protected void onCleared() {
        super.onCleared();
        chatItemDao.getAllChatItems().removeObserver(roomObserver);
    }

    private void loadFriends() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) return;

        FirebaseDatabase.getInstance().getReference("friends").child(myUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            mAllChats.setValue(new ArrayList<>());
                            mFilteredChats.setValue(new ArrayList<>());
                            // Clear local database if no friends (and potentially no groups handled elsewhere)
                            // But usually, we only want to remove specific ones
                        }

                        java.util.Set<String> currentFriends = new java.util.HashSet<>();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String friendUid = ds.getKey();
                            if (friendUid != null) {
                                currentFriends.add(friendUid);
                                if (!observedFriends.contains(friendUid)) {
                                    observedFriends.add(friendUid);
                                    observeFriend(myUid, friendUid);
                                }
                            }
                        }

                        // Remove friends that are no longer in the friends list
                        java.util.Iterator<String> iterator = observedFriends.iterator();
                        while (iterator.hasNext()) {
                            String observedId = iterator.next();
                            if (!currentFriends.contains(observedId)) {
                                iterator.remove();
                                AppDatabase.databaseWriteExecutor.execute(() -> chatItemDao.deleteByUid(observedId));
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void observeFriend(String myUid, String friendUid) {
        // Observe User Details (Name, Photo, Online Status)
        FirebaseDatabase.getInstance().getReference("users").child(friendUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        User user = snapshot.getValue(User.class);
                        if (user != null) {
                            updateChatItem(friendUid, user, null, -1, false);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        // Observe Last Message
        String chatId = myUid.compareTo(friendUid) < 0 
                ? myUid + "_" + friendUid 
                : friendUid + "_" + myUid;

        FirebaseDatabase.getInstance().getReference("chats").child(chatId)
                .limitToLast(1)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                Message message = ds.getValue(Message.class);
                                if (message != null) {
                                    updateChatItem(friendUid, null, message, -1, false);
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        // Observe Unread Count
        FirebaseDatabase.getInstance().getReference("chats").child(chatId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int count = 0;
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            Message m = ds.getValue(Message.class);
                            if (m != null && !m.isSeen() && myUid.equals(m.getReceiverId())) {
                                count++;
                            }
                        }
                        updateChatItem(friendUid, null, null, count, false);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void loadGroups() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) return;

        FirebaseDatabase.getInstance().getReference("users").child(myUid).child("groups")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        java.util.Set<String> currentGroups = new java.util.HashSet<>();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String groupId = ds.getKey();
                            if (groupId != null) {
                                currentGroups.add(groupId);
                                if (!observedGroups.contains(groupId)) {
                                    observedGroups.add(groupId);
                                    observeGroup(groupId);
                                }
                            }
                        }

                        // Remove groups that are no longer in the user's groups list
                        java.util.Iterator<String> iterator = observedGroups.iterator();
                        while (iterator.hasNext()) {
                            String observedId = iterator.next();
                            if (!currentGroups.contains(observedId)) {
                                iterator.remove();
                                // Note: In a real app we might want to stop specific listeners, 
                                // but for now we'll just remove it from the local DB so it disappears from UI
                                AppDatabase.databaseWriteExecutor.execute(() -> chatItemDao.deleteByUid(observedId));
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void observeGroup(String groupId) {
        FirebaseDatabase.getInstance().getReference("groups").child(groupId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        com.hamraj37.somechat.models.Group group = snapshot.getValue(com.hamraj37.somechat.models.Group.class);
                        if (group != null) {
                            updateGroupChatItem(groupId, group, null);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });

        FirebaseDatabase.getInstance().getReference("group_chats").child(groupId).child("messages")
                .limitToLast(1)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                Message message = ds.getValue(Message.class);
                                if (message != null) {
                                    updateGroupChatItem(groupId, null, message);
                                }
                            }
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private synchronized void updateGroupChatItem(String groupId, com.hamraj37.somechat.models.Group group, Message lastMsg) {
        ChatItem existing = chatsMap.get(groupId);
        String name = group != null ? group.getGroupName() : (existing != null ? existing.getName() : "Loading group...");
        String photoUrl = group != null ? group.getGroupAvatar() : (existing != null ? existing.getPhotoUrl() : null);
        
        String displayMsgText;
        String type;
        if (lastMsg != null) {
            type = lastMsg.getType();
            if (type == null) type = "text";
            String sender = lastMsg.getSenderId().equals(FirebaseAuth.getInstance().getUid()) ? "You" : lastMsg.getSenderName();
            String rawText = lastMsg.getText(); // Group messages might not be encrypted in the same way or at all yet
            displayMsgText = sender + ": " + rawText;
        } else {
            displayMsgText = existing != null ? existing.getLastMessage() : "No messages yet";
            type = existing != null ? existing.getLastMessageType() : "text";
        }

        String time = lastMsg != null ? formatTime(lastMsg.getTimestamp()) : (existing != null ? existing.getTime() : "");
        long timestamp = lastMsg != null ? lastMsg.getTimestamp() : (existing != null ? existing.getTimestamp() : 0);

        ChatItemEntity entity = new ChatItemEntity(groupId, name, displayMsgText, time, photoUrl, false, timestamp, 0, type, true);
        AppDatabase.databaseWriteExecutor.execute(() -> chatItemDao.insert(entity));
    }

    private synchronized void updateChatItem(String friendUid, User user, Message lastMsg, int unreadCount, boolean isGroup) {
        ChatItem existing = chatsMap.get(friendUid);
        String myUid = FirebaseAuth.getInstance().getUid();
        
        String name = user != null ? user.getDisplayName() : (existing != null ? existing.getName() : "Loading...");
        String photoUrl = user != null ? user.getPhotoUrl() : (existing != null ? existing.getPhotoUrl() : null);
        boolean online = user != null ? user.isOnline() : (existing != null && existing.isOnline());
        
        String rawMsgText;
        String type;
        
        if (lastMsg != null) {
            type = lastMsg.getType();
            if (type == null) type = "text";
            
            boolean isSender = myUid != null && myUid.equals(lastMsg.getSenderId());
            rawMsgText = com.hamraj37.somechat.utils.EncryptionManager.decrypt(lastMsg.getText(), getApplication(), isSender);
        } else {
            rawMsgText = existing != null ? getRawMessage(existing.getLastMessage()) : "No messages yet";
            type = existing != null ? existing.getLastMessageType() : "text";
        }
        
        String displayMsgText = rawMsgText;
        
        if (lastMsg != null && myUid != null && myUid.equals(lastMsg.getSenderId())) {
            displayMsgText = "You: " + rawMsgText;
        } else if (lastMsg == null && existing != null && existing.getLastMessage().startsWith("You: ")) {
            displayMsgText = existing.getLastMessage();
        }

        String time = lastMsg != null ? formatTime(lastMsg.getTimestamp()) : (existing != null ? existing.getTime() : "");
        long timestamp = lastMsg != null ? lastMsg.getTimestamp() : (existing != null ? existing.getTimestamp() : 0);
        int finalUnreadCount = unreadCount != -1 ? unreadCount : (existing != null ? existing.getUnreadCount() : 0);

        // Save to local DB
        ChatItemEntity entity = new ChatItemEntity(friendUid, name, displayMsgText, time, photoUrl, online, timestamp, finalUnreadCount, type, isGroup);
        AppDatabase.databaseWriteExecutor.execute(() -> chatItemDao.insert(entity));
    }

    private String getRawMessage(String displayMsg) {
        if (displayMsg != null && displayMsg.startsWith("You: ")) {
            return displayMsg.substring(5);
        }
        return displayMsg;
    }

    private String formatTime(long timestamp) {
        Date date = new Date(timestamp);
        Date now = new Date();
        
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        if (dayFormat.format(date).equals(dayFormat.format(now))) {
            return timeFormat.format(date);
        } else {
            return dateFormat.format(date);
        }
    }

    public LiveData<List<ChatItem>> getChats() {
        return mFilteredChats;
    }

    public LiveData<List<ChatItem>> getAllChats() {
        return mAllChats;
    }

    public void filter(String query) {
        this.currentQuery = query;
        if (query == null || query.isEmpty()) {
            mFilteredChats.setValue(mAllChats.getValue());
            return;
        }

        List<ChatItem> all = mAllChats.getValue();
        if (all == null) return;

        List<ChatItem> filtered = all.stream()
                .filter(chat -> chat.getName().toLowerCase().contains(query.toLowerCase()) ||
                                chat.getLastMessage().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
        mFilteredChats.setValue(filtered);
    }
}