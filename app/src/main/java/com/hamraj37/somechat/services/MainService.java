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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.hamraj37.somechat.AudioCallActivity;
import com.hamraj37.somechat.VideoCallActivity;
import com.hamraj37.somechat.ChatActivity;
import com.hamraj37.somechat.R;

import java.util.HashSet;
import java.util.Set;

public class MainService extends Service {

    private static final String SERVICE_CHANNEL_ID = "SomeChatServiceChannel";
    private static final String CALL_CHANNEL_ID = "CallChannel";
    private static final String MESSAGE_CHANNEL_ID = "MessageChannel";

    public static final String ACTION_ANSWER_AUDIO = "com.hamraj37.somechat.ACTION_ANSWER_AUDIO";
    public static final String ACTION_DECLINE_AUDIO = "com.hamraj37.somechat.ACTION_DECLINE_AUDIO";
    public static final String ACTION_ANSWER_VIDEO = "com.hamraj37.somechat.ACTION_ANSWER_VIDEO";
    public static final String ACTION_DECLINE_VIDEO = "com.hamraj37.somechat.ACTION_DECLINE_VIDEO";

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
            Class<?> activityClass = ACTION_ANSWER_VIDEO.equals(action) ? VideoCallActivity.class : AudioCallActivity.class;
            Intent callIntent = new Intent(this, activityClass);
            callIntent.putExtras(intent.getExtras());
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(callIntent);
            cancelNotification(callerId);
        } else if (ACTION_DECLINE_AUDIO.equals(action) || ACTION_DECLINE_VIDEO.equals(action)) {
            String myUid = FirebaseAuth.getInstance().getUid();
            if (myUid != null && callerId != null) {
                FirebaseDatabase.getInstance().getReference("calls").child(myUid).child(callerId).child("status").setValue("rejected");
            }
            removeCall(callerId, ACTION_DECLINE_VIDEO.equals(action));
        }
    }

    private void setupMessageHandler() {
        messageHandler = new MessageHandler(this, (senderId, senderName, text) -> showMessageNotification(senderId, senderName, text));
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
        Notification notification = new NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Connected and ready")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(100, notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL | 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA | 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(100, notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL | 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
        } else {
            startForeground(100, notification);
        }
    }

    private void notifyIncomingCall(String id, String name, String avatar, boolean isVideo) {
        activeCallIds.add(id);
        
        if (!CallState.isCallActive) {
            CallState.isCallActive = true;
            CallState.activeCallId = id;
            CallState.activeCallName = name;
            CallState.activeCallAvatar = avatar;
            CallState.isActiveCallVideo = isVideo;
            CallState.isActiveCallIncoming = true;
        }

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

        if (activeCallIds.isEmpty()) {
            CallState.reset();
            Intent endIntent = new Intent(isVideo ? "com.hamraj37.somechat.VIDEO_CALL_ENDED" : "com.hamraj37.somechat.AUDIO_CALL_ENDED");
            endIntent.setPackage(getPackageName());
            sendBroadcast(endIntent);
        }
    }

    private void showCallNotification(String id, String name, String avatar, boolean isVideo) {
        Intent intent = new Intent(this, isVideo ? VideoCallActivity.class : AudioCallActivity.class);
        intent.putExtra("uid", id);
        intent.putExtra("displayName", name);
        intent.putExtra("photoUrl", avatar);
        intent.putExtra("isIncoming", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent fullScreenPI = PendingIntent.getActivity(this, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent answerIntent = new Intent(this, MainService.class);
        answerIntent.setAction(isVideo ? ACTION_ANSWER_VIDEO : ACTION_ANSWER_AUDIO);
        answerIntent.putExtras(intent.getExtras());
        PendingIntent answerPI = PendingIntent.getService(this, id.hashCode() + 1, answerIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent declineIntent = new Intent(this, MainService.class);
        declineIntent.setAction(isVideo ? ACTION_DECLINE_VIDEO : ACTION_DECLINE_AUDIO);
        declineIntent.putExtra("uid", id);
        PendingIntent declinePI = PendingIntent.getService(this, id.hashCode() + 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CALL_CHANNEL_ID)
                .setContentTitle(isVideo ? "Video Call" : "Audio Call")
                .setContentText(name + " is calling...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPI, true)
                .addAction(isVideo ? R.drawable.ic_camera_black_24dp : android.R.drawable.ic_menu_call, "Answer", answerPI)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePI)
                .setOngoing(true).setAutoCancel(true).build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(getNotificationId(id), notification);
    }

    private void showMessageNotification(String senderId, String senderName, String text) {
        String name = senderName != null ? senderName : "New Message";
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("uid", senderId);
        intent.putExtra("displayName", name);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(this, senderId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
                .setContentTitle(name).setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi).setAutoCancel(true).build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(senderId.hashCode(), notification);
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;
            
            manager.createNotificationChannel(new NotificationChannel(SERVICE_CHANNEL_ID, "Service Status", NotificationManager.IMPORTANCE_MIN));
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
        if (CallState.isCallActive && CallState.activeCallId != null) {
            String myUid = FirebaseAuth.getInstance().getUid();
            if (myUid != null) FirebaseDatabase.getInstance().getReference("calls").child(myUid).child(CallState.activeCallId).child("status").setValue("ended");
        }
        stopSelf();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (messageHandler != null) messageHandler.stopListening();
        if (callHandler != null) callHandler.stopListening();
        super.onDestroy();
    }
}
