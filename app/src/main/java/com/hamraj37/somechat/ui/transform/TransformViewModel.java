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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
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
    private final Map<String, String> nameCache = new HashMap<>();
    private final Map<String, String> photoCache = new HashMap<>();
    private final java.util.Set<String> observedFriends = new java.util.HashSet<>();
    private final java.util.Set<String> observedGroups = new java.util.HashSet<>();
    private final Map<String, List<ValueEventListener>> listenerMap = new HashMap<>();
    private final Map<String, List<Query>> queryMap = new HashMap<>();
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
        chatsMap.clear();
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
            
            // Seed memory cache
            if (entity.getName() != null && !entity.getName().contains("Loading")) {
                nameCache.put(entity.getUid(), entity.getName());
            }
            if (entity.getPhotoUrl() != null) {
                photoCache.put(entity.getUid(), entity.getPhotoUrl());
            }
        }
        mAllChats.setValue(items);
        filter(currentQuery);
    };

    @Override
    protected void onCleared() {
        super.onCleared();
        chatItemDao.getAllChatItems().removeObserver(roomObserver);
        stopAllObservers();
    }

    private void stopAllObservers() {
        for (String id : new ArrayList<>(observedFriends)) stopObserving(id);
        for (String id : new ArrayList<>(observedGroups)) stopObserving(id);
    }

    private synchronized void stopObserving(String id) {
        List<ValueEventListener> listeners = listenerMap.remove(id);
        List<Query> queries = queryMap.remove(id);
        if (listeners != null && queries != null) {
            for (int i = 0; i < listeners.size(); i++) {
                queries.get(i).removeEventListener(listeners.get(i));
            }
        }
        observedFriends.remove(id);
        observedGroups.remove(id);
    }

    private void addListener(String id, Query query, ValueEventListener listener) {
        List<ValueEventListener> listeners = listenerMap.computeIfAbsent(id, k -> new ArrayList<>());
        List<Query> queries = queryMap.computeIfAbsent(id, k -> new ArrayList<>());
        listeners.add(listener);
        queries.add(query);
        query.addValueEventListener(listener);
    }

    private void loadFriends() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) return;

        FirebaseDatabase.getInstance().getReference("friends").child(myUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
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

                        java.util.Iterator<String> iterator = observedFriends.iterator();
                        while (iterator.hasNext()) {
                            String observedId = iterator.next();
                            if (!currentFriends.contains(observedId)) {
                                stopObserving(observedId);
                                iterator = observedFriends.iterator(); // Refresh iterator after modification
                                AppDatabase.databaseWriteExecutor.execute(() -> chatItemDao.deleteByUid(observedId));
                            }
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void observeFriend(String myUid, String friendUid) {
        // Observe User Details
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(friendUid);
        addListener(friendUid, userRef, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) updateChatItem(friendUid, user, null, -1, false);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Observe Last Message
        String chatId = myUid.compareTo(friendUid) < 0 ? myUid + "_" + friendUid : friendUid + "_" + myUid;
        DatabaseReference lastMsgRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId);
        addListener(friendUid, lastMsgRef.limitToLast(1), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        Message message = ds.getValue(Message.class);
                        if (message != null) updateChatItem(friendUid, null, message, -1, false);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Observe Unread Count
        addListener(friendUid, lastMsgRef, new ValueEventListener() {
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
            @Override public void onCancelled(@NonNull DatabaseError error) {}
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
                        java.util.Iterator<String> iterator = observedGroups.iterator();
                        while (iterator.hasNext()) {
                            String observedId = iterator.next();
                            if (!currentGroups.contains(observedId)) {
                                stopObserving(observedId);
                                iterator = observedGroups.iterator(); // Refresh iterator
                                AppDatabase.databaseWriteExecutor.execute(() -> chatItemDao.deleteByUid(observedId));
                            }
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void observeGroup(String groupId) {
        DatabaseReference groupRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId);
        addListener(groupId, groupRef, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                com.hamraj37.somechat.models.Group group = snapshot.getValue(com.hamraj37.somechat.models.Group.class);
                String name = null, photo = null;
                if (group != null) {
                    name = group.getGroupName();
                    photo = group.getGroupAvatar();
                }
                if (name == null || name.isEmpty()) {
                    name = snapshot.child("name").getValue(String.class);
                    if (name == null) name = snapshot.child("groupName").getValue(String.class);
                }
                if (photo == null || photo.isEmpty()) {
                    photo = snapshot.child("avatar").getValue(String.class);
                    if (photo == null) photo = snapshot.child("groupAvatar").getValue(String.class);
                }
                updateGroupChatItem(groupId, name, photo, null);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        DatabaseReference msgRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId).child("messages");
        addListener(groupId, msgRef.limitToLast(1), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        Message message = ds.getValue(Message.class);
                        if (message != null) updateGroupChatItem(groupId, null, null, message);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private synchronized void updateGroupChatItem(String groupId, String groupName, String photo, Message lastMsg) {
        if (!observedGroups.contains(groupId)) return;
        
        ChatItem existing = chatsMap.get(groupId);
        String name = groupName;
        if (name == null || name.isEmpty()) {
            name = nameCache.get(groupId);
            if (name == null) {
                if ("somechat_ai".equals(groupId)) name = "SomeChat AI";
                else name = "Loading group...";
            }
        } else nameCache.put(groupId, name);

        String photoUrl = photo;
        if (photoUrl == null || photoUrl.isEmpty()) photoUrl = photoCache.get(groupId);
        else photoCache.put(groupId, photoUrl);

        String displayMsgText;
        String type;
        if (lastMsg != null) {
            type = lastMsg.getType();
            if (type == null) type = "text";
            String sender = lastMsg.getSenderId().equals(FirebaseAuth.getInstance().getUid()) ? "You" : lastMsg.getSenderName();
            displayMsgText = (sender != null ? sender + ": " : "") + lastMsg.getText();
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
        if (!observedFriends.contains(friendUid)) return;
        
        ChatItem existing = chatsMap.get(friendUid);
        String myUid = FirebaseAuth.getInstance().getUid();
        
        String name = null;
        if (user != null) {
            name = user.getDisplayName();
            if (name == null || name.isEmpty()) name = user.getUsername();
        }
        
        if (name == null || name.isEmpty()) {
            name = nameCache.get(friendUid);
            if (name == null) {
                if ("somechat_ai".equals(friendUid)) name = "SomeChat AI";
                else name = "Loading...";
            }
        } else nameCache.put(friendUid, name);
        
        String photoUrl = user != null ? user.getPhotoUrl() : photoCache.get(friendUid);
        if (user != null && user.getPhotoUrl() != null) photoCache.put(friendUid, user.getPhotoUrl());
        
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

    public LiveData<List<ChatItem>> getChats() { return mFilteredChats; }
    public LiveData<List<ChatItem>> getAllChats() { return mAllChats; }

    public void filter(String query) {
        this.currentQuery = query;
        if (query == null || query.isEmpty()) {
            mFilteredChats.setValue(mAllChats.getValue());
            return;
        }
        List<ChatItem> all = mAllChats.getValue();
        if (all == null) return;
        List<ChatItem> filtered = all.stream()
                .filter(chat -> (chat.getName() != null && chat.getName().toLowerCase().contains(query.toLowerCase())) ||
                                (chat.getLastMessage() != null && chat.getLastMessage().toLowerCase().contains(query.toLowerCase())))
                .collect(Collectors.toList());
        mFilteredChats.setValue(filtered);
    }
}
