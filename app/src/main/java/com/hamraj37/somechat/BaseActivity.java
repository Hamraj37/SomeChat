package com.hamraj37.somechat;

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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.hamraj37.somechat.services.CallService;
import java.util.Locale;

public class BaseActivity extends AppCompatActivity {

    private BroadcastReceiver messageReceiver;
    private BroadcastReceiver callReceiver;
    private View callBar;
    private TextView callBarTimer;
    private android.os.Handler callTimerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable callTimerRunnable;

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
                if ("com.hamraj37.somechat.CALL_ENDED".equals(intent.getAction())) {
                    updateCallBar();
                    return;
                }

                String callerName = intent.getStringExtra("callerName");
                String callerId = intent.getStringExtra("callerId");
                boolean isVideo = intent.getBooleanExtra("isVideo", false);

                // If already in a call activity, don't start another one
                String className = BaseActivity.this.getClass().getSimpleName();
                if (className.equals("AudioCallActivity") || className.equals("VideoCallActivity")) return;

                // Automatically open correct Activity
                Intent callIntent = new Intent(BaseActivity.this, isVideo ? VideoCallActivity.class : AudioCallActivity.class);
                callIntent.putExtra("uid", callerId);
                callIntent.putExtra("displayName", callerName);
                callIntent.putExtra("isIncoming", true);
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(callIntent);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter msgFilter = new IntentFilter("com.hamraj37.somechat.NEW_MESSAGE");
        ContextCompat.registerReceiver(this, messageReceiver, msgFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        
        IntentFilter callFilter = new IntentFilter();
        callFilter.addAction("com.hamraj37.somechat.INCOMING_CALL");
        callFilter.addAction("com.hamraj37.somechat.CALL_ENDED");
        ContextCompat.registerReceiver(this, callReceiver, callFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        updateCallBar();
    }

    private void updateCallBar() {
        if (CallService.isCallActive) {
            showCallBar();
        } else {
            hideCallBar();
        }
    }

    private void showCallBar() {
        if (callBar == null) {
            ViewGroup root = findViewById(android.R.id.content);
            if (root != null) {
                callBar = LayoutInflater.from(this).inflate(R.layout.layout_call_bar, root, false);
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = android.view.Gravity.TOP;
                root.addView(callBar, lp);

                callBarTimer = callBar.findViewById(R.id.call_bar_timer);
                
                callBar.setOnClickListener(v -> {
                    Intent intent = new Intent(this, CallService.isActiveCallVideo ? VideoCallActivity.class : AudioCallActivity.class);
                    intent.putExtra("uid", CallService.activeCallId);
                    intent.putExtra("displayName", CallService.activeCallName);
                    intent.putExtra("photoUrl", CallService.activeCallAvatar);
                    intent.putExtra("isIncoming", false);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                });
            }
        }

        if (callBar != null) {
            callBar.setVisibility(View.VISIBLE);
            startCallBarTimer();
        }
    }

    private void hideCallBar() {
        if (callBar != null) {
            callBar.setVisibility(View.GONE);
        }
        stopCallBarTimer();
    }

    private void startCallBarTimer() {
        stopCallBarTimer();
        callTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (CallService.isCallActive && callBarTimer != null) {
                    long millis = System.currentTimeMillis() - CallService.callStartTime;
                    int seconds = (int) (millis / 1000);
                    int minutes = seconds / 60;
                    seconds = seconds % 60;
                    callBarTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
                    callTimerHandler.postDelayed(this, 1000);
                }
            }
        };
        callTimerHandler.post(callTimerRunnable);
    }

    private void stopCallBarTimer() {
        if (callTimerRunnable != null) {
            callTimerHandler.removeCallbacks(callTimerRunnable);
        }
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
