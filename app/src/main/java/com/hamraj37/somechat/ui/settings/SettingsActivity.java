package com.hamraj37.somechat.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.hamraj37.somechat.BaseActivity;
import com.hamraj37.somechat.R;
import com.hamraj37.somechat.databinding.ActivitySettingsBinding;

import java.util.concurrent.Executor;

public class SettingsActivity extends BaseActivity {

    private ActivitySettingsBinding binding;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE);

        setupBiometricLock();
        setupEncryptionStatus();
        setupChatBackground();
        setupRestoreTheme();
    }

    private void setupEncryptionStatus() {
        boolean isSecured = com.hamraj37.somechat.utils.EncryptionManager.hasKeys();

        binding.textEncryptionStatus.setText(isSecured ? R.string.secured : R.string.not_secured);
        binding.textEncryptionStatus.setTextColor(isSecured ?
                ContextCompat.getColor(this, android.R.color.holo_green_dark) : 
                ContextCompat.getColor(this, android.R.color.holo_red_dark));

        binding.btnEncryptionSettings.setOnClickListener(v -> {
            if (isSecured) {
                showViewKeysDialog();
            } else {
                showEncryptionSetupDialog();
            }
        });
    }

    private void showViewKeysDialog() {
        String pubKey = com.hamraj37.somechat.utils.EncryptionManager.getMyPublicKey(this);
        String privKey = com.hamraj37.somechat.utils.EncryptionManager.getMyPrivateKey(this);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_view_keys, null);
        TextView textPubKey = dialogView.findViewById(R.id.text_public_key);
        TextView textPrivKey = dialogView.findViewById(R.id.text_private_key);
        
        textPubKey.setText(pubKey);
        textPrivKey.setText(privKey);

        View.OnClickListener copyListener = view -> {
            String textToCopy = (view.getId() == R.id.btn_copy_public) ? pubKey : privKey;
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                android.content.ClipData clip = android.content.ClipData.newPlainText("Encryption Key", textToCopy);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Key copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        };

        dialogView.findViewById(R.id.btn_copy_public).setOnClickListener(copyListener);
        dialogView.findViewById(R.id.btn_copy_private).setOnClickListener(copyListener);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Your Encryption Keys")
                .setView(dialogView)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void showEncryptionSetupDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setup_encryption)
                .setMessage("Your encryption keys are not set up. Initialize them now to secure your messages?")
                .setPositiveButton("Initialize", (dialog, which) -> {
                    String pubKey = com.hamraj37.somechat.utils.EncryptionManager.initKeys(this);
                    String privKey = com.hamraj37.somechat.utils.EncryptionManager.getMyPrivateKey(this);

                    com.google.firebase.auth.FirebaseUser user = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        java.util.Map<String, Object> keyMap = new java.util.HashMap<>();
                        keyMap.put("publicKey", pubKey);
                        keyMap.put("privateKey", privKey);
                        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users")
                                .child(user.getUid()).updateChildren(keyMap);
                    }
                    
                    setupEncryptionStatus(); // Refresh UI
                    Toast.makeText(this, "Encryption initialized!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setupRestoreTheme() {
        binding.btnRestoreTheme.setOnClickListener(v -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restore_default_theme)
                .setMessage("This will reset your chat background and bubble colors. Are you sure?")
                .setPositiveButton("Restore", (dialog, which) -> {
                    prefs.edit()
                            .remove("chat_background_path")
                            .remove("theme_sent_color")
                            .remove("theme_received_color")
                            .remove("theme_sent_text_color")
                            .remove("theme_received_text_color")
                            .apply();

                    // Delete custom background file if exists
                    java.io.File file = new java.io.File(getFilesDir(), "chat_background.jpg");
                    if (file.exists()) {
                        if (!file.delete()) {
                            android.util.Log.e("SettingsActivity", "Could not delete background file");
                        }
                    }

                    Toast.makeText(this, "Theme restored to default", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show());
    }

    private void setupChatBackground() {
        binding.btnChatBackground.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatBackgroundActivity.class);
            startActivity(intent);
        });
        
        binding.btnCreateTheme.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateThemeActivity.class);
            startActivity(intent);
        });
    }

    private void setupBiometricLock() {
        boolean isLockEnabled = prefs.getBoolean("biometric_lock", false);
        
        // Remove listener to prevent recursive calls during initial set
        binding.switchBiometricLock.setOnCheckedChangeListener(null);
        binding.switchBiometricLock.setChecked(isLockEnabled);

        binding.switchBiometricLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                verifyAndEnableBiometric();
            } else {
                prefs.edit().putBoolean("biometric_lock", false).apply();
            }
        });
    }

    private void verifyAndEnableBiometric() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            handleBiometricError(canAuthenticate);
            binding.switchBiometricLock.setChecked(false);
            return;
        }

        // Verify with a prompt before enabling
        Executor executor = ContextCompat.getMainExecutor(this);
        androidx.biometric.BiometricPrompt biometricPrompt = new androidx.biometric.BiometricPrompt(this,
                executor, new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                runOnUiThread(() -> {
                    binding.switchBiometricLock.setChecked(false);
                    Toast.makeText(SettingsActivity.this, "Activation failed: " + errString, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                runOnUiThread(() -> {
                    prefs.edit().putBoolean("biometric_lock", true).apply();
                    Toast.makeText(SettingsActivity.this, "Biometric lock enabled", Toast.LENGTH_SHORT).show();
                });
            }
        });

        androidx.biometric.BiometricPrompt.PromptInfo promptInfo = new androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable App Lock")
                .setSubtitle("Confirm your biometric to enable lock")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void handleBiometricError(int errorCode) {
        String msg;
        switch (errorCode) {
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                msg = "No biometric hardware found";
                break;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                msg = "Biometric hardware unavailable";
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                msg = "No biometrics enrolled on this device";
                break;
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
                msg = "Security update required for biometrics";
                break;
            default:
                msg = "Biometric authentication is not supported";
                break;
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}