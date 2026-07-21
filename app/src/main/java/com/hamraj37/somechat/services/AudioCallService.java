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
import com.hamraj37.somechat.R;

import java.util.HashSet;
import java.util.Set;

public class AudioCallService extends Service {

    private static final String CHANNEL_ID = "AudioCallChannel";
    private static final String SERVICE_CHANNEL_ID = "AudioCallServiceChannel";
    
    public static final String ACTION_ANSWER = "com.hamraj37.somechat.AUDIO_ACTION_ANSWER";
    public static final String ACTION_DECLINE = "com.hamraj37.somechat.AUDIO_ACTION_DECLINE";
    
    private CallHandler callHandler;
    private final Set<String> activeCallIds = new HashSet<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_ANSWER.equals(action)) {
                handleAnswer(intent);
            } else if (ACTION_DECLINE.equals(action)) {
                handleDecline(intent);
            }
        }

        createNotificationChannels();
        startForegroundNotification();

        if (callHandler == null) {
            setupCallHandler();
        }
        return START_STICKY;
    }

    private void setupCallHandler() {
        callHandler = new CallHandler(this, new CallHandler.CallListener() {
            @Override
            public void onIncomingCall(String id, String name, String avatar, boolean isVideo) {
                notifyIncomingCall(id, name, avatar);
                if (!CallState.isCallActive) {
                    setGlobalCallState(id, name, avatar);
                }
            }

            @Override
            public void onCallRemoved(String id) {
                removeCall(id);
            }
        });
        callHandler.startListening(false); // Filter for audio only
    }

    private void startForegroundNotification() {
        Notification notification = new NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Listening for incoming audio calls")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
        } else {
            startForeground(1, notification);
        }
    }

    private void notifyIncomingCall(String callerId, String name, String avatar) {
        activeCallIds.add(callerId);

        Intent broadcastIntent = new Intent("com.hamraj37.somechat.AUDIO_INCOMING_CALL");
        broadcastIntent.setPackage(getPackageName());
        broadcastIntent.putExtra("callerId", callerId);
        broadcastIntent.putExtra("callerName", name);
        broadcastIntent.putExtra("callerAvatar", avatar);
        broadcastIntent.putExtra("isVideo", false);
        sendBroadcast(broadcastIntent);

        showCallNotification(callerId, name, avatar);
    }

    private void removeCall(String callerId) {
        activeCallIds.remove(callerId);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(getNotificationId(callerId));

        if (activeCallIds.isEmpty()) {
            CallState.isCallActive = false;
            CallState.activeCallId = null;
            Intent endIntent = new Intent("com.hamraj37.somechat.AUDIO_CALL_ENDED");
            endIntent.setPackage(getPackageName());
            sendBroadcast(endIntent);
        }
    }

    private void setGlobalCallState(String id, String name, String avatar) {
        CallState.isCallActive = true;
        CallState.activeCallId = id;
        CallState.activeCallName = name;
        CallState.activeCallAvatar = avatar;
        CallState.isActiveCallVideo = false;
        CallState.isActiveCallIncoming = true;
    }

    private void handleAnswer(Intent intent) {
        String callerId = intent.getStringExtra("uid");
        
        Intent callIntent = new Intent(this, AudioCallActivity.class);
        callIntent.putExtras(intent.getExtras());
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(callIntent);

        if (callerId != null) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(getNotificationId(callerId));
        }
    }

    private void handleDecline(Intent intent) {
        String callerId = intent.getStringExtra("uid");
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid != null && callerId != null) {
            FirebaseDatabase.getInstance().getReference("calls").child(myUid).child(callerId).child("status").setValue("rejected");
        }
        removeCall(callerId);
    }

    private void showCallNotification(String callerId, String name, String avatar) {
        Intent intent = new Intent(this, AudioCallActivity.class);
        intent.putExtra("uid", callerId);
        intent.putExtra("displayName", name);
        intent.putExtra("photoUrl", avatar);
        intent.putExtra("isIncoming", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent fullScreenIntent = PendingIntent.getActivity(this, callerId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent answerIntent = new Intent(this, AudioCallService.class);
        answerIntent.setAction(ACTION_ANSWER);
        answerIntent.putExtras(intent.getExtras());
        PendingIntent answerPI = PendingIntent.getService(this, callerId.hashCode() + 1, answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent declineIntent = new Intent(this, AudioCallService.class);
        declineIntent.setAction(ACTION_DECLINE);
        declineIntent.putExtra("uid", callerId);
        PendingIntent declinePI = PendingIntent.getService(this, callerId.hashCode() + 2, declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Audio Call")
                .setContentText(name + " is calling...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenIntent, true)
                .addAction(android.R.drawable.ic_menu_call, "Answer", answerPI)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePI)
                .setOngoing(true)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(getNotificationId(callerId), notification);
    }

    private int getNotificationId(String callerId) {
        return Math.abs(callerId.hashCode());
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            NotificationChannel callChannel = new NotificationChannel(CHANNEL_ID, "Audio Calls", NotificationManager.IMPORTANCE_HIGH);
            callChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), 
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
            manager.createNotificationChannel(callChannel);

            manager.createNotificationChannel(new NotificationChannel(SERVICE_CHANNEL_ID, "Audio Call Service", NotificationManager.IMPORTANCE_MIN));
        }
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        if (CallState.isCallActive && CallState.activeCallId != null) {
            String myUid = FirebaseAuth.getInstance().getUid();
            if (myUid != null) {
                FirebaseDatabase.getInstance().getReference("calls").child(myUid).child(CallState.activeCallId).child("status").setValue("ended");
            }
        }
        stopSelf();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (callHandler != null) {
            callHandler.stopListening();
        }
        super.onDestroy();
    }
}
