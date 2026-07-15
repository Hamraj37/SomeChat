package com.hamraj37.somechat.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.hamraj37.somechat.BaseActivity;
import com.hamraj37.somechat.databinding.ActivitySettingsBinding;

import java.util.concurrent.Executor;

public class SettingsActivity extends BaseActivity {

    private ActivitySettingsBinding binding;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable Edge-to-Edge appearance logic
        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isNightMode);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE);

        setupBiometricLock();
        setupChatBackground();
    }

    private void setupChatBackground() {
        binding.btnChatBackground.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatBackgroundActivity.class);
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