package com.hamraj37.somechat.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.hamraj37.somechat.AudioCallActivity;
import com.hamraj37.somechat.ChatActivity;
import com.hamraj37.somechat.GroupChatActivity;
import com.hamraj37.somechat.MainActivity;
import com.hamraj37.somechat.R;
import com.hamraj37.somechat.VideoCallActivity;

import java.util.Map;

public class SomeChatMessagingService extends FirebaseMessagingService {

    private static final String CALL_CHANNEL_ID = "CallChannel";
    private static final String MESSAGE_CHANNEL_ID = "MessageChannel";

    public static final String ACTION_ANSWER_AUDIO = "com.hamraj37.somechat.ACTION_ANSWER_AUDIO";
    public static final String ACTION_DECLINE_AUDIO = "com.hamraj37.somechat.ACTION_DECLINE_AUDIO";
    public static final String ACTION_ANSWER_VIDEO = "com.hamraj37.somechat.ACTION_ANSWER_VIDEO";
    public static final String ACTION_DECLINE_VIDEO = "com.hamraj37.somechat.ACTION_DECLINE_VIDEO";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();
        if (data.isEmpty()) return;

        String type = data.get("type");
        if ("call".equals(type)) {
            handleCallMessage(data);
        } else if ("message".equals(type)) {
            handleChatMessage(data);
        }
    }

    private void handleCallMessage(Map<String, String> data) {
        String id = data.get("callerId");
        String name = data.get("callerName");
        String avatar = data.get("callerAvatar");
        boolean isVideo = Boolean.parseBoolean(data.get("isVideo"));

        if (id == null) return;

        showCallNotification(id, name, avatar, isVideo);
    }

    private void handleChatMessage(Map<String, String> data) {
        String id = data.get("senderId");
        String name = data.get("senderName");
        String text = data.get("text");
        boolean isGroup = Boolean.parseBoolean(data.get("isGroup"));
        String groupId = data.get("groupId");

        if (id == null) return;

        // Don't show notification if user is already in the chat
        if (!isGroup && id.equals(ChatActivity.openedChatId)) return;
        if (isGroup && groupId != null && groupId.equals(GroupChatActivity.openedGroupId)) return;

        showMessageNotification(isGroup ? groupId : id, name, text, isGroup);
    }

    private void showCallNotification(String id, String name, String avatar, boolean isVideo) {
        Intent intent = new Intent(this, isVideo ? VideoCallActivity.class : AudioCallActivity.class);
        intent.putExtra("uid", id);
        intent.putExtra("displayName", name);
        intent.putExtra("photoUrl", avatar);
        intent.putExtra("isIncoming", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent fullScreenPI = PendingIntent.getActivity(this, id.hashCode(), intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // For simplicity, handle actions via MainService for now or create a receiver
        Intent answerIntent = new Intent(this, MainService.class);
        answerIntent.setAction(isVideo ? ACTION_ANSWER_VIDEO : ACTION_ANSWER_AUDIO);
        answerIntent.putExtras(intent.getExtras());
        PendingIntent answerPI = PendingIntent.getService(this, id.hashCode() + 1, answerIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent declineIntent = new Intent(this, MainService.class);
        declineIntent.setAction(isVideo ? ACTION_DECLINE_VIDEO : ACTION_DECLINE_AUDIO);
        declineIntent.putExtra("uid", id);
        PendingIntent declinePI = PendingIntent.getService(this, id.hashCode() + 2, declineIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Person caller = new Person.Builder()
                .setName(name)
                .setImportant(true)
                .build();

        NotificationCompat.CallStyle callStyle = NotificationCompat.CallStyle.forIncomingCall(caller, declinePI, answerPI);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CALL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPI, true)
                .setStyle(callStyle)
                .setOngoing(true);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannels(nm);
        if (nm != null) nm.notify(Math.abs(id.hashCode()), builder.build());
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

        PendingIntent pi = PendingIntent.getActivity(this, id.hashCode(), intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannels(nm);
        if (nm != null) nm.notify(id.hashCode(), notification);
    }

    private void createNotificationChannels(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            if (manager.getNotificationChannel(MESSAGE_CHANNEL_ID) == null) {
                manager.createNotificationChannel(new NotificationChannel(MESSAGE_CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH));
            }
            if (manager.getNotificationChannel(CALL_CHANNEL_ID) == null) {
                NotificationChannel callChannel = new NotificationChannel(CALL_CHANNEL_ID, "Calls", NotificationManager.IMPORTANCE_HIGH);
                callChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), 
                        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
                manager.createNotificationChannel(callChannel);
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseDatabase.getInstance().getReference("users").child(uid).child("fcmToken").setValue(token);
        }
    }
}
