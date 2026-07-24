package com.hamraj37.somechat.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.hamraj37.somechat.AudioCallActivity;
import com.hamraj37.somechat.VideoCallActivity;
import com.hamraj37.somechat.ChatActivity;
import com.hamraj37.somechat.GroupChatActivity;
import com.hamraj37.somechat.R;

import java.util.HashSet;
import java.util.Set;

public class MainService extends Service {

    private static final String SILENT_CHANNEL_ID = "SilentServiceChannel";
    private static final String CALL_CHANNEL_ID = "CallChannel";
    private static final String MESSAGE_CHANNEL_ID = "MessageChannel";

    public static final String ACTION_ANSWER_AUDIO = "com.hamraj37.somechat.ACTION_ANSWER_AUDIO";
    public static final String ACTION_DECLINE_AUDIO = "com.hamraj37.somechat.ACTION_DECLINE_AUDIO";
    public static final String ACTION_ANSWER_VIDEO = "com.hamraj37.somechat.ACTION_ANSWER_VIDEO";
    public static final String ACTION_DECLINE_VIDEO = "com.hamraj37.somechat.ACTION_DECLINE_VIDEO";
    public static final String ACTION_UPDATE_CALL_NOTIFICATION = "com.hamraj37.somechat.ACTION_UPDATE_CALL_NOTIFICATION";

    private MessageHandler messageHandler;
    private CallHandler callHandler;
    
    private final Set<String> activeCallIds = new HashSet<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            handleAction(intent);
        }

        createNotificationChannels();
        startForegroundNotification();

        if (messageHandler == null) setupMessageHandler();
        if (callHandler == null) setupCallHandler();

        return START_STICKY;
    }

    private void handleAction(Intent intent) {
        String action = intent.getAction();
        String callerId = intent.getStringExtra("uid");

        if (ACTION_ANSWER_AUDIO.equals(action) || ACTION_ANSWER_VIDEO.equals(action)) {
            CallState.isConnected = true;
            Class<?> activityClass = ACTION_ANSWER_VIDEO.equals(action) ? VideoCallActivity.class : AudioCallActivity.class;
            Intent callIntent = new Intent(this, activityClass);
            callIntent.putExtras(intent.getExtras());
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(callIntent);
            
            // Update to ongoing style
            showCallNotification(callerId, intent.getStringExtra("displayName"), intent.getStringExtra("photoUrl"), ACTION_ANSWER_VIDEO.equals(action));
        } else if (ACTION_DECLINE_AUDIO.equals(action) || ACTION_DECLINE_VIDEO.equals(action)) {
            String myUid = FirebaseAuth.getInstance().getUid();
            if (myUid != null && callerId != null) {
                FirebaseDatabase.getInstance().getReference("calls").child(myUid).child(callerId).child("status").setValue("rejected");
            }
            removeCall(callerId, ACTION_DECLINE_VIDEO.equals(action));
        } else if (ACTION_UPDATE_CALL_NOTIFICATION.equals(action)) {
            showCallNotification(callerId, intent.getStringExtra("displayName"), intent.getStringExtra("photoUrl"), intent.getBooleanExtra("isVideo", false));
        }
    }

    private void setupMessageHandler() {
        messageHandler = new MessageHandler(this, (id, name, text, isGroup) -> showMessageNotification(id, name, text, isGroup));
        messageHandler.startListening();
    }

    private void setupCallHandler() {
        callHandler = new CallHandler(this, new CallHandler.CallListener() {
            @Override
            public void onIncomingCall(String id, String name, String avatar, boolean isVideo) {
                notifyIncomingCall(id, name, avatar, isVideo);
            }
            @Override
            public void onCallRemoved(String id) {
                removeCall(id, CallState.isActiveCallVideo);
            }
        });
        callHandler.startListening(null);
    }

    private void startForegroundNotification() {
        Intent mainIntent = new Intent(this, com.hamraj37.somechat.MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, SILENT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("SomeChat is active")
                .setContentText("Start a conversation")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(100, notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL | 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(100, notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
        } else {
            startForeground(100, notification);
        }
    }

    private void notifyIncomingCall(String id, String name, String avatar, boolean isVideo) {
        activeCallIds.add(id);
        
        // Always update CallState for an incoming call to ensure it shows correctly
        CallState.isCallActive = true;
        CallState.activeCallId = id;
        CallState.activeCallName = name;
        CallState.activeCallAvatar = avatar;
        CallState.isActiveCallVideo = isVideo;
        CallState.isActiveCallIncoming = true;
        CallState.isConnected = false; // It's not connected yet, it's ringing

        Intent broadcastIntent = new Intent(isVideo ? "com.hamraj37.somechat.VIDEO_INCOMING_CALL" : "com.hamraj37.somechat.AUDIO_INCOMING_CALL");
        broadcastIntent.setPackage(getPackageName());
        broadcastIntent.putExtra("callerId", id);
        broadcastIntent.putExtra("callerName", name);
        broadcastIntent.putExtra("callerAvatar", avatar);
        broadcastIntent.putExtra("isVideo", isVideo);
        sendBroadcast(broadcastIntent);

        showCallNotification(id, name, avatar, isVideo);
    }

    private void removeCall(String id, boolean isVideo) {
        activeCallIds.remove(id);
        cancelNotification(id);

        if (id.equals(CallState.activeCallId) || activeCallIds.isEmpty()) {
            CallState.reset();
            Intent endIntent = new Intent(isVideo ? "com.hamraj37.somechat.VIDEO_CALL_ENDED" : "com.hamraj37.somechat.AUDIO_CALL_ENDED");
            endIntent.setPackage(getPackageName());
            sendBroadcast(endIntent);
        }
    }

    private void showCallNotification(String id, String name, String avatar, boolean isVideo) {
        if (id == null) return;
        
        Intent intent = new Intent(this, isVideo ? VideoCallActivity.class : AudioCallActivity.class);
        intent.putExtra("uid", id);
        intent.putExtra("displayName", name);
        intent.putExtra("photoUrl", avatar);
        // If it's already connected, it's not "incoming" in the sense of showing the answer UI
        intent.putExtra("isIncoming", CallState.isActiveCallIncoming && !CallState.isConnected); 
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent fullScreenPI = PendingIntent.getActivity(this, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent declineIntent = new Intent(this, MainService.class);
        declineIntent.setAction(isVideo ? ACTION_DECLINE_VIDEO : ACTION_DECLINE_AUDIO);
        declineIntent.putExtra("uid", id);
        PendingIntent declinePI = PendingIntent.getService(this, id.hashCode() + 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Person caller = new Person.Builder()
                .setName(name)
                .setImportant(true)
                .build();

        NotificationCompat.CallStyle callStyle;
        // We use ongoing style if the call is connected OR if we are the one who started it
        boolean isOngoing = (CallState.isCallActive && CallState.activeCallId != null && CallState.activeCallId.equals(id)) 
                && (!CallState.isActiveCallIncoming || CallState.isConnected);

        if (isOngoing) {
            callStyle = NotificationCompat.CallStyle.forOngoingCall(caller, declinePI);
        } else {
            Intent answerIntent = new Intent(this, MainService.class);
            answerIntent.setAction(isVideo ? ACTION_ANSWER_VIDEO : ACTION_ANSWER_AUDIO);
            answerIntent.putExtras(intent.getExtras());
            PendingIntent answerPI = PendingIntent.getService(this, id.hashCode() + 1, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            
            callStyle = NotificationCompat.CallStyle.forIncomingCall(caller, declinePI, answerPI);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPI, true)
                .setStyle(callStyle)
                .setOngoing(true);

        if (isOngoing && CallState.callStartTime != 0) {
            builder.setWhen(CallState.callStartTime)
                   .setShowWhen(true)
                   .setUsesChronometer(true);
        }

        Notification notification = builder.build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(getNotificationId(id), notification);
    }

    private void showMessageNotification(String id, String name, String text, boolean isGroup) {
        String title = name != null ? name : "New Message";
        Intent intent;
        if (isGroup) {
            intent = new Intent(this, GroupChatActivity.class);
            intent.putExtra("groupId", id);
            intent.putExtra("groupName", name);
        } else {
            intent = new Intent(this, ChatActivity.class);
            intent.putExtra("uid", id);
            intent.putExtra("displayName", name);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(this, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
                .setContentTitle(title).setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi).setAutoCancel(true).build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(id.hashCode(), notification);
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;
            
            // Channel for the persistent background process (Hidden/Minimized)
            NotificationChannel silentChannel = new NotificationChannel(SILENT_CHANNEL_ID, "Background Sync", NotificationManager.IMPORTANCE_MIN);
            silentChannel.setShowBadge(false);
            silentChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            manager.createNotificationChannel(silentChannel);

            manager.createNotificationChannel(new NotificationChannel(MESSAGE_CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH));
            
            NotificationChannel callChannel = new NotificationChannel(CALL_CHANNEL_ID, "Calls", NotificationManager.IMPORTANCE_HIGH);
            callChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
            manager.createNotificationChannel(callChannel);
        }
    }

    private void cancelNotification(String id) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(getNotificationId(id));
    }

    private int getNotificationId(String id) { return Math.abs(id.hashCode()); }

    @Override public void onTaskRemoved(Intent rootIntent) {
        // We no longer stop the service when the task is removed.
        // This allows notifications to continue being received.
        // Super call is enough to notify the system.
        super.onTaskRemoved(rootIntent);
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (messageHandler != null) messageHandler.stopListening();
        if (callHandler != null) callHandler.stopListening();
        
        // If the service is being destroyed by the system (not by logout), attempt to restart
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            Intent broadcastIntent = new Intent("com.hamraj37.somechat.RESTART_SERVICE");
            broadcastIntent.setPackage(getPackageName());
            sendBroadcast(broadcastIntent);
        }
        
        super.onDestroy();
    }
}
