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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.hamraj37.somechat.services.CallState;
import java.util.Locale;

public class BaseActivity extends AppCompatActivity {

    protected static boolean isAppUnlocked = false;
    private static boolean isPromptShowing = false;
    private static int activeActivities = 0;
    private BroadcastReceiver messageReceiver;
    private BroadcastReceiver callReceiver;
    private View callBar;
    private TextView callBarTimer;
    private android.os.Handler callTimerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable callTimerRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable Edge-to-Edge: Draw under status and navigation bars
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        
        // Handle status bar icon colors based on theme
        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.core.view.WindowInsetsControllerCompat windowInsetsController = 
            new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        windowInsetsController.setAppearanceLightStatusBars(!isNightMode);
        windowInsetsController.setAppearanceLightNavigationBars(!isNightMode);

        messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String senderName = intent.getStringExtra("senderName");
                String text = intent.getStringExtra("text");
                String senderId = intent.getStringExtra("senderId");

                if (ChatActivity.openedChatId != null && ChatActivity.openedChatId.equals(senderId)) {
                    return;
                }
                
                if (GroupChatActivity.openedGroupId != null && GroupChatActivity.openedGroupId.equals(senderId)) {
                    return;
                }

                showInAppNotification(senderName, text, senderId);
            }
        };

        callReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("com.hamraj37.somechat.AUDIO_CALL_ENDED".equals(action) || "com.hamraj37.somechat.VIDEO_CALL_ENDED".equals(action)) {
                    updateCallBar();
                    return;
                }

                if (!"com.hamraj37.somechat.AUDIO_INCOMING_CALL".equals(action) && !"com.hamraj37.somechat.VIDEO_INCOMING_CALL".equals(action)) {
                    return;
                }

                // If already in a call activity, don't start another one
                String className = BaseActivity.this.getClass().getSimpleName();
                if (className.equals("AudioCallActivity") || className.equals("VideoCallActivity")) return;

                // We no longer automatically open the activity here to avoid interrupting the user.
                // The CallState shows a high-priority heads-up notification (floating window)
                // which allows the user to Answer or Decline via buttons, or tap to open full screen.
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        activeActivities++;
        checkAppLock();
    }

    @Override
    protected void onStop() {
        super.onStop();
        activeActivities--;
        if (activeActivities <= 0) {
            // App backgrounded, reset unlock state for next entry
            isAppUnlocked = false;
        }
    }

    protected void checkAppLock() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE);
        boolean isLockEnabled = prefs.getBoolean("biometric_lock", false);

        if (isLockEnabled && !isAppUnlocked && !isPromptShowing) {
            showBiometricPrompt();
        }
    }

    private void showBiometricPrompt() {
        isPromptShowing = true;
        java.util.concurrent.Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                isPromptShowing = false;
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    finishAffinity(); // Close all activities
                } else if (errorCode == BiometricPrompt.ERROR_CANCELED) {
                    // System-level cancellation, ignore to avoid annoying toast
                } else {
                    Toast.makeText(getApplicationContext(), "Lock error: " + errString, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                isPromptShowing = false;
                isAppUnlocked = true;
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                // Failed, but prompt stays up
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("SomeChat Locked")
                .setSubtitle("Authenticate to use SomeChat")
                .setNegativeButtonText("Exit")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter msgFilter = new IntentFilter("com.hamraj37.somechat.NEW_MESSAGE");
        ContextCompat.registerReceiver(this, messageReceiver, msgFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        
        IntentFilter callFilter = new IntentFilter();
        callFilter.addAction("com.hamraj37.somechat.AUDIO_INCOMING_CALL");
        callFilter.addAction("com.hamraj37.somechat.VIDEO_INCOMING_CALL");
        callFilter.addAction("com.hamraj37.somechat.AUDIO_CALL_ENDED");
        callFilter.addAction("com.hamraj37.somechat.VIDEO_CALL_ENDED");
        ContextCompat.registerReceiver(this, callReceiver, callFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        updateCallBar();
    }

    private void updateCallBar() {
        // Don't show call bar on call activities themselves
        String className = this.getClass().getSimpleName();
        if (className.equals("AudioCallActivity") || className.equals("VideoCallActivity")) {
            hideCallBar();
            return;
        }

        if (CallState.isCallActive) {
            showCallBar();
        } else {
            hideCallBar();
        }
    }

    private void showCallBar() {
        if (callBar == null) {
            // Prefer adding to AppBarLayout if it exists (so it shows "after header")
            View appBar = findViewById(R.id.main_app_bar);
            if (appBar instanceof ViewGroup) {
                ViewGroup appBarGroup = (ViewGroup) appBar;
                callBar = LayoutInflater.from(this).inflate(R.layout.layout_call_bar, appBarGroup, false);
                appBarGroup.addView(callBar);
                
                callBarTimer = callBar.findViewById(R.id.call_bar_timer);
                setupCallBarClickListener();
                startCallBarTimer();
                callBar.setVisibility(View.VISIBLE);
                return;
            }

            ViewGroup root = findViewById(android.R.id.content);
            if (root != null) {
                callBar = LayoutInflater.from(this).inflate(R.layout.layout_call_bar, root, false);
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = android.view.Gravity.TOP;
                root.addView(callBar, lp);

                callBarTimer = callBar.findViewById(R.id.call_bar_timer);
                setupCallBarClickListener();
            }
        }

        if (callBar != null) {
            callBar.setVisibility(View.VISIBLE);
            startCallBarTimer();
        }
    }

    private void setupCallBarClickListener() {
        if (callBar != null) {
            callBar.setOnClickListener(v -> {
                Intent intent = new Intent(this, CallState.isActiveCallVideo ? VideoCallActivity.class : AudioCallActivity.class);
                intent.putExtra("uid", CallState.activeCallId);
                intent.putExtra("displayName", CallState.activeCallName);
                intent.putExtra("photoUrl", CallState.activeCallAvatar);
                intent.putExtra("isIncoming", false);
                // REORDER_TO_FRONT brings the existing instance to the front if it exists
                // SINGLE_TOP ensures we don't create a new instance if it's already at the top
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
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
                if (CallState.isCallActive && callBarTimer != null) {
                    long millis = System.currentTimeMillis() - CallState.callStartTime;
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
