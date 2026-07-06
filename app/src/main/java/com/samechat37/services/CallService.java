package com.samechat37.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.samechat37.CallActivity;
import com.samechat37.ChatActivity;
import com.samechat37.MainActivity;
import com.samechat37.R;
import com.samechat37.models.Message;

import java.util.HashMap;
import java.util.Map;

public class CallService extends Service {

    private static final String CHANNEL_ID = "CallChannel";
    private static final String MESSAGE_CHANNEL_ID = "MessageChannel";
    private static final String SERVICE_CHANNEL_ID = "ServiceChannel";
    private ValueEventListener callListener;
    private ValueEventListener friendsListener;
    private final Map<String, ValueEventListener> messageListeners = new HashMap<>();
    private final Map<String, String> userNames = new HashMap<>();
    private boolean isListenersSetup = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannels();
        Notification notification = new NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
                .setContentTitle("SameChat is running")
                .setContentText("Listening for calls and messages...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
        } else {
            startForeground(1, notification);
        }

        if (!isListenersSetup) {
            setupCallListener();
            setupMessageListeners();
            isListenersSetup = true;
        }
        return START_STICKY;
    }

    private void setupMessageListeners() {
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

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        FirebaseDatabase.getInstance().getReference("friends").child(uid)
                .addValueEventListener(friendsListener);
    }

    private void setupCallListener() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        callListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    NotificationManager nm = getSystemService(NotificationManager.class);
                    if (nm != null) nm.cancel(2);
                    return;
                }
                boolean activeCall = false;
                for (DataSnapshot callerSnapshot : snapshot.getChildren()) {
                    String status = callerSnapshot.child("status").getValue(String.class);
                    if ("calling".equals(status)) {
                        activeCall = true;
                        String callerId = callerSnapshot.getKey();
                        String callerName = callerSnapshot.child("callerName").getValue(String.class);
                        Boolean isVideo = callerSnapshot.child("isVideo").getValue(Boolean.class);

                        // Mark as "ringing" so we don't trigger multiple notifications
                        callerSnapshot.getRef().child("status").setValue("ringing");

                        // Send In-App broadcast
                        Intent broadcastIntent = new Intent("com.samechat37.INCOMING_CALL");
                        broadcastIntent.setPackage(getPackageName());
                        broadcastIntent.putExtra("callerId", callerId);
                        broadcastIntent.putExtra("callerName", callerName);
                        broadcastIntent.putExtra("isVideo", isVideo != null && isVideo);
                        sendBroadcast(broadcastIntent);

                        showCallNotification(callerId, callerName, isVideo != null && isVideo);
                    } else if ("ringing".equals(status) || "accepted".equals(status)) {
                        activeCall = true;
                    }
                }
                if (!activeCall) {
                    // Call is no longer active (ended or cancelled)
                    Intent endIntent = new Intent("com.samechat37.CALL_ENDED");
                    endIntent.setPackage(getPackageName());
                    sendBroadcast(endIntent);

                    NotificationManager nm = getSystemService(NotificationManager.class);
                    if (nm != null) nm.cancel(2);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        FirebaseDatabase.getInstance().getReference("calls").child(uid)
                .addValueEventListener(callListener);
    }

    private void showCallNotification(String callerId, String callerName, boolean isVideo) {
        Intent intent = new Intent(this, CallActivity.class);
        intent.putExtra("uid", callerId);
        intent.putExtra("displayName", callerName != null ? callerName : "Unknown");
        intent.putExtra("isIncoming", true);
        intent.putExtra("isVideo", isVideo);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Incoming " + (isVideo ? "Video" : "Audio") + " Call")
                .setContentText(callerName + " is calling you")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .build();

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.notify(2, notification);
        }
    }

    private void observeChat(String myUid, String friendUid) {
        // First, get friend's name for notification
        FirebaseDatabase.getInstance().getReference("users").child(friendUid).child("displayName")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String name = snapshot.getValue(String.class);
                        userNames.put(friendUid, name != null ? name : "New Message");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        String chatId = myUid.compareTo(friendUid) < 0 ? myUid + "_" + friendUid : friendUid + "_" + myUid;
        ValueEventListener listener = new ValueEventListener() {
            private boolean isFirstLoad = true;

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFirstLoad) {
                    isFirstLoad = false;
                    return; // Skip initial load of old messages
                }

                // Get the last message
                DataSnapshot lastMsgSnapshot = null;
                for (DataSnapshot child : snapshot.getChildren()) {
                    lastMsgSnapshot = child;
                }

                if (lastMsgSnapshot != null) {
                    Message message = lastMsgSnapshot.getValue(Message.class);
                    if (message != null && message.getReceiverId().equals(myUid) && !message.isSeen()) {
                        // Only notify if this chat is not currently open
                        if (!friendUid.equals(ChatActivity.openedChatId)) {
                            String type = message.getType();
                            String notificationText;
                            
                            if (type == null || "text".equals(type)) {
                                notificationText = com.samechat37.utils.EncryptionManager.decrypt(message.getText(), CallService.this, false);
                            } else {
                                // Capitalize first letter (e.g. "image" -> "Image")
                                notificationText = type.substring(0, 1).toUpperCase() + type.substring(1);
                                if ("voice".equals(type)) notificationText = "Voice Message";
                            }
                            
                            showMessageNotification(friendUid, userNames.get(friendUid), notificationText);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        messageListeners.put(friendUid, listener);
        FirebaseDatabase.getInstance().getReference("chats").child(chatId).limitToLast(1)
                .addValueEventListener(listener);
    }

    private void showMessageNotification(String senderId, String senderName, String text) {
        if (senderId == null) return;
        String name = senderName != null ? senderName : "New Message";
        
        // Send In-App broadcast
        Intent broadcastIntent = new Intent("com.samechat37.NEW_MESSAGE");
        broadcastIntent.setPackage(getPackageName());
        broadcastIntent.putExtra("senderId", senderId);
        broadcastIntent.putExtra("senderName", name);
        broadcastIntent.putExtra("text", text);
        sendBroadcast(broadcastIntent);

        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("uid", senderId);
        intent.putExtra("displayName", name);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, senderId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
                .setContentTitle(name)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            // Ensure ID doesn't clash with foreground service (1)
            int notificationId = senderId.hashCode();
            if (notificationId == 1) notificationId = 1001;
            notificationManager.notify(notificationId, notification);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            NotificationChannel callChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Call Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            callChannel.setDescription("Used for incoming call notifications");
            manager.createNotificationChannel(callChannel);

            NotificationChannel msgChannel = new NotificationChannel(
                    MESSAGE_CHANNEL_ID,
                    "Message Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            msgChannel.setDescription("Used for new message notifications");
            manager.createNotificationChannel(msgChannel);

            NotificationChannel serviceChannel = new NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    "Background Service",
                    NotificationManager.IMPORTANCE_MIN
            );
            serviceChannel.setDescription("Required for SameChat to receive calls in the background");
            serviceChannel.setShowBadge(false);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            if (callListener != null) {
                FirebaseDatabase.getInstance().getReference("calls").child(uid)
                        .removeEventListener(callListener);
            }
            if (friendsListener != null) {
                FirebaseDatabase.getInstance().getReference("friends").child(uid)
                        .removeEventListener(friendsListener);
            }
            for (Map.Entry<String, ValueEventListener> entry : messageListeners.entrySet()) {
                String friendUid = entry.getKey();
                String chatId = uid.compareTo(friendUid) < 0 ? uid + "_" + friendUid : friendUid + "_" + uid;
                FirebaseDatabase.getInstance().getReference("chats").child(chatId)
                        .removeEventListener(entry.getValue());
            }
        }
    }
}
