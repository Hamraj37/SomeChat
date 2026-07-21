package com.hamraj37.somechat.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.hamraj37.somechat.ChatActivity;
import com.hamraj37.somechat.R;

public class MessageService extends Service {

    private static final String MESSAGE_CHANNEL_ID = "MessageChannel";
    private static final String SERVICE_CHANNEL_ID = "MessageServiceChannel";
    
    private MessageHandler messageHandler;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannels();
        startForegroundNotification();

        if (messageHandler == null) {
            setupMessageHandler();
        }
        return START_STICKY;
    }

    private void setupMessageHandler() {
        messageHandler = new MessageHandler(this, new MessageHandler.MessageListener() {
            @Override
            public void onNewMessage(String senderId, String senderName, String text) {
                showMessageNotification(senderId, senderName, text);
            }
        });
        messageHandler.startListening();
    }

    private void startForegroundNotification() {
        Notification notification = new NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Listening for messages...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();

        startForeground(2, notification);
    }

    private void showMessageNotification(String senderId, String senderName, String text) {
        String name = senderName != null ? senderName : "New Message";
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("uid", senderId);
        intent.putExtra("displayName", name);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(this, senderId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

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
            manager.createNotificationChannel(new NotificationChannel(MESSAGE_CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH));
            manager.createNotificationChannel(new NotificationChannel(SERVICE_CHANNEL_ID, "Message Service", NotificationManager.IMPORTANCE_MIN));
        }
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (messageHandler != null) {
            messageHandler.stopListening();
        }
        super.onDestroy();
    }
}
