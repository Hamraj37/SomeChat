package com.samechat37;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

public class BaseActivity extends AppCompatActivity {

    private BroadcastReceiver messageReceiver;
    private BroadcastReceiver callReceiver;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String senderName = intent.getStringExtra("senderName");
                String text = intent.getStringExtra("text");
                String senderId = intent.getStringExtra("senderId");

                if (ChatActivity.openedChatId != null && ChatActivity.openedChatId.equals(senderId)) {
                    return;
                }

                showInAppNotification(senderName, text, senderId);
            }
        };

        callReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.samechat37.CALL_ENDED".equals(intent.getAction())) {
                    // Call ended logic if needed (e.g. if we had a dialog)
                    return;
                }

                String callerName = intent.getStringExtra("callerName");
                String callerId = intent.getStringExtra("callerId");
                boolean isVideo = intent.getBooleanExtra("isVideo", false);

                // If already in a call activity, don't start another one
                if (BaseActivity.this.getClass().getSimpleName().equals("CallActivity")) return;

                // Automatically open CallActivity
                Intent callIntent = new Intent(BaseActivity.this, CallActivity.class);
                callIntent.putExtra("uid", callerId);
                callIntent.putExtra("displayName", callerName);
                callIntent.putExtra("isIncoming", true);
                callIntent.putExtra("isVideo", isVideo);
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(callIntent);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter msgFilter = new IntentFilter("com.samechat37.NEW_MESSAGE");
        ContextCompat.registerReceiver(this, messageReceiver, msgFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        
        IntentFilter callFilter = new IntentFilter();
        callFilter.addAction("com.samechat37.INCOMING_CALL");
        callFilter.addAction("com.samechat37.CALL_ENDED");
        ContextCompat.registerReceiver(this, callReceiver, callFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(messageReceiver);
        unregisterReceiver(callReceiver);
    }

    private void showInAppNotification(String name, String text, String senderId) {
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content),
                name + ": " + text, Snackbar.LENGTH_LONG);
        snackbar.setAction("Reply", v -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("uid", senderId);
            intent.putExtra("displayName", name);
            startActivity(intent);
        });
        snackbar.show();
    }
}
