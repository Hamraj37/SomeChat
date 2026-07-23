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
    private final Map<String, String> nameCache = new HashMap<>();
    private final Map<String, String> photoCache = new HashMap<>();
    private final Map<String, String> messageCache = new HashMap<>();
    private final Map<String, String> typeCache = new HashMap<>();
    private final Map<String, String> timeCache = new HashMap<>();
    private final Map<String, Long> timestampCache = new HashMap<>();
    private final Map<String, Boolean> onlineCache = new HashMap<>();
    private final Map<String, Integer> unreadCache = new HashMap<>();
    private final Map<String, Long> groupLastSeenCache = new HashMap<>();
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
            
            // Seed memory cache
            String id = entity.getUid();
            if (entity.getName() != null && !entity.getName().contains("Loading")) {
                nameCache.put(id, entity.getName());
            }
            if (entity.getPhotoUrl() != null) {
                photoCache.put(id, entity.getPhotoUrl());
            }
            if (entity.getLastMessage() != null && !entity.getLastMessage().equals("No messages yet")) {
                messageCache.put(id, entity.getLastMessage());
            }
            if (entity.getLastMessageType() != null) {
                typeCache.put(id, entity.getLastMessageType());
            }
            if (entity.getTime() != null) {
                timeCache.put(id, entity.getTime());
            }
            timestampCache.put(id, entity.getTimestamp());
            onlineCache.put(id, entity.isOnline());
            unreadCache.put(id, entity.getUnreadCount());
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

                        List<String> toRemove = new ArrayList<>();
                        for (String id : observedFriends) {
                            if (!currentFriends.contains(id)) toRemove.add(id);
                        }
                        for (String id : toRemove) {
                            stopObserving(id);
                            AppDatabase.databaseWriteExecutor.execute(() -> chatItemDao.deleteByUid(id));
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
                        List<String> toRemove = new ArrayList<>();
                        for (String id : observedGroups) {
                            if (!currentGroups.contains(id)) toRemove.add(id);
                        }
                        for (String id : toRemove) {
                            stopObserving(id);
                            AppDatabase.databaseWriteExecutor.execute(() -> chatItemDao.deleteByUid(id));
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void observeGroup(String groupId) {
        String myUid = FirebaseAuth.getInstance().getUid();
        DatabaseReference groupRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId);
        
        // Listen only to name and avatar to avoid triggering on every message
        ValueEventListener metadataListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                
                String name = null, photo = null;
                // Prioritize new field names 'name' and 'avatar'
                if (snapshot.hasChild("name")) name = snapshot.child("name").getValue(String.class);
                else if (snapshot.hasChild("groupName")) name = snapshot.child("groupName").getValue(String.class);
                
                if (snapshot.hasChild("avatar")) photo = snapshot.child("avatar").getValue(String.class);
                else if (snapshot.hasChild("groupAvatar")) photo = snapshot.child("groupAvatar").getValue(String.class);
                
                updateGroupChatItem(groupId, name, photo, null, -1);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        addListener(groupId, groupRef, metadataListener);

        DatabaseReference msgRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId).child("messages");
        addListener(groupId, msgRef.limitToLast(1), new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        Message message = ds.getValue(Message.class);
                        if (message != null) {
                            updateGroupChatItem(groupId, null, null, message, -1);
                            refreshGroupUnreadCount(groupId, msgRef);
                        }
                    }
                } else {
                    // No messages yet
                    updateGroupChatItem(groupId, null, null, null, 0);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        if (myUid != null) {
            DatabaseReference lastSeenRef = FirebaseDatabase.getInstance().getReference("users").child(myUid).child("groupLastSeen").child(groupId);
            addListener(groupId, lastSeenRef, new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Long lastSeen = snapshot.getValue(Long.class);
                    groupLastSeenCache.put(groupId, lastSeen != null ? lastSeen : 0L);
                    refreshGroupUnreadCount(groupId, msgRef);
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }

    private void refreshGroupUnreadCount(String groupId, DatabaseReference msgRef) {
        String myUid = FirebaseAuth.getInstance().getUid();
        Long lastSeen = groupLastSeenCache.get(groupId);
        if (lastSeen == null) lastSeen = 0L;

        msgRef.orderByChild("timestamp").startAfter(lastSeen.doubleValue()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int count = 0;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Message m = ds.getValue(Message.class);
                    if (m != null && !m.getSenderId().equals(myUid)) {
                        count++;
                    }
                }
                updateGroupChatItem(groupId, null, null, null, count);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private synchronized void updateGroupChatItem(String groupId, String groupName, String photo, Message lastMsg, int unreadCount) {
        if (!observedGroups.contains(groupId)) return;
        
        // 1. Update Name and Photo
        String name = groupName;
        if (name == null || name.isEmpty()) {
            name = nameCache.get(groupId);
            if (name == null) {
                if ("somechat_ai".equals(groupId)) name = "SomeChat AI";
                else name = "Loading group...";
            }
        } else nameCache.put(groupId, name);

        String photoUrl = photo;
        if (photoUrl == null || photoUrl.isEmpty()) {
            photoUrl = photoCache.get(groupId);
        } else photoCache.put(groupId, photoUrl);

        // 2. Update Message
        String displayMsgText;
        String type;
        String time;
        long timestamp;

        if (lastMsg != null) {
            type = lastMsg.getType();
            if (type == null) type = "text";
            
            String sender;
            if ("system".equals(lastMsg.getSenderId())) {
                sender = null; // No prefix for system messages
            } else {
                String sName = lastMsg.getSenderName();
                if (sName == null || sName.isEmpty()) sName = "Member";
                sender = lastMsg.getSenderId().equals(FirebaseAuth.getInstance().getUid()) ? "You" : sName;
            }
            
            String msgText = lastMsg.getText();
            if (msgText == null) msgText = "";
            
            // Decrypt if it's a text message (others like 'image', 'file' usually have non-encrypted preview text)
            if ("text".equals(type)) {
                msgText = com.hamraj37.somechat.utils.EncryptionManager.decrypt(msgText, getApplication(), lastMsg.getSenderId().equals(FirebaseAuth.getInstance().getUid()));
            } else if ("image".equals(type)) {
                msgText = "Photo";
            } else if ("voice".equals(type)) {
                msgText = "Voice message";
            }
            
            displayMsgText = (sender != null ? sender + ": " : "") + msgText;
            time = formatTime(lastMsg.getTimestamp());
            timestamp = lastMsg.getTimestamp();
            
            // Update Caches
            messageCache.put(groupId, displayMsgText);
            typeCache.put(groupId, type);
            timeCache.put(groupId, time);
            timestampCache.put(groupId, timestamp);
        } else {
            // Metadata update: check cache first
            displayMsgText = messageCache.getOrDefault(groupId, "No messages yet");
            if (displayMsgText == null) displayMsgText = "No messages yet";
            
            type = typeCache.getOrDefault(groupId, "text");
            if (type == null) type = "text";
            
            time = timeCache.getOrDefault(groupId, "");
            if (time == null) time = "";
            
            Long ts = timestampCache.get(groupId);
            timestamp = (ts != null) ? ts : 0L;
        }

        int finalUnreadCount;
        if (unreadCount != -1) {
            finalUnreadCount = unreadCount;
            unreadCache.put(groupId, finalUnreadCount);
        } else {
            Integer u = unreadCache.get(groupId);
            finalUnreadCount = (u != null) ? u : 0;
        }

        ChatItemEntity entity = new ChatItemEntity(groupId, name, displayMsgText, time, photoUrl, false, timestamp, finalUnreadCount, type, true);
        AppDatabase.databaseWriteExecutor.execute(() -> chatItemDao.insert(entity));
    }

    private synchronized void updateChatItem(String friendUid, User user, Message lastMsg, int unreadCount, boolean isGroup) {
        if (!observedFriends.contains(friendUid)) return;
        
        String myUid = FirebaseAuth.getInstance().getUid();
        
        // 1. Update Name and Online Status
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
        
        boolean online;
        if (user != null) {
            online = user.isOnline();
            onlineCache.put(friendUid, online);
        } else {
            Boolean o = onlineCache.get(friendUid);
            online = (o != null) ? o : false;
        }
        
        // 2. Update Message
        String displayMsgText;
        String type;
        String time;
        long timestamp;

        if (lastMsg != null) {
            type = lastMsg.getType();
            if (type == null) type = "text";
            
            boolean isSender = myUid != null && myUid.equals(lastMsg.getSenderId());
            String msgText = lastMsg.getText();
            if (msgText == null) msgText = "";

            if ("text".equals(type)) {
                msgText = com.hamraj37.somechat.utils.EncryptionManager.decrypt(msgText, getApplication(), isSender);
            } else if ("image".equals(type)) {
                msgText = "Photo";
            } else if ("voice".equals(type)) {
                msgText = "Voice message";
            }

            if (isSender) {
                displayMsgText = "You: " + msgText;
            } else {
                displayMsgText = msgText;
            }
            
            time = formatTime(lastMsg.getTimestamp());
            timestamp = lastMsg.getTimestamp();

            // Update Caches
            messageCache.put(friendUid, displayMsgText);
            typeCache.put(friendUid, type);
            timeCache.put(friendUid, time);
            timestampCache.put(friendUid, timestamp);
        } else {
            // Metadata update: check cache
            displayMsgText = messageCache.getOrDefault(friendUid, "No messages yet");
            if (displayMsgText == null) displayMsgText = "No messages yet";
            
            type = typeCache.getOrDefault(friendUid, "text");
            if (type == null) type = "text";
            
            time = timeCache.getOrDefault(friendUid, "");
            if (time == null) time = "";
            
            Long ts = timestampCache.get(friendUid);
            timestamp = (ts != null) ? ts : 0L;
        }
        
        int finalUnreadCount;
        if (unreadCount != -1) {
            finalUnreadCount = unreadCount;
            unreadCache.put(friendUid, finalUnreadCount);
        } else {
            Integer u = unreadCache.get(friendUid);
            finalUnreadCount = (u != null) ? u : 0;
        }

        ChatItemEntity entity = new ChatItemEntity(friendUid, name, displayMsgText, time, photoUrl, online, timestamp, finalUnreadCount, type, isGroup);
        AppDatabase.databaseWriteExecutor.execute(() -> chatItemDao.insert(entity));
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
