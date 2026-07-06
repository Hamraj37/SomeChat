package com.samechat37;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends BaseActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isNightMode);

        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();

        final EditText emailEditText = findViewById(R.id.email);
        final EditText passwordEditText = findViewById(R.id.password);
        final EditText confirmPasswordEditText = findViewById(R.id.confirm_password);
        final Button registerButton = findViewById(R.id.register);
        final Button backToLoginButton = findViewById(R.id.back_to_login);

        registerButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
            String confirmPassword = confirmPasswordEditText.getText().toString().trim();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(RegisterActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirmPassword)) {
                Toast.makeText(RegisterActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.length() < 6) {
                Toast.makeText(RegisterActivity.this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            registerButton.setEnabled(false);
            performRegistration(email, password, registerButton);
        });

        backToLoginButton.setOnClickListener(v -> finish());
    }

    private void performRegistration(String email, String password, Button registerButton) {
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    String userId = mAuth.getCurrentUser().getUid();
                    
                    // Generate a default display name and unique username from email
                    String baseHandle = email.split("@")[0];
                    
                    findAndCreateUser(userId, baseHandle, email, registerButton);
                } else {
                    registerButton.setEnabled(true);
                    String errorMessage = task.getException() != null ? task.getException().getMessage() : "Registration failed";
                    Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            });
    }

    private void findAndCreateUser(String uid, String baseHandle, String email, Button registerButton) {
        // Sanitize baseHandle
        String sanitizedBase = baseHandle.replaceAll("[^a-zA-Z0-9]", "");
        if (sanitizedBase.isEmpty()) sanitizedBase = "user";

        checkUsernameUniqueness(sanitizedBase, 0, uid, uniqueHandle -> {
            String publicKey = com.samechat37.utils.EncryptionManager.initKeys(this);
            Map<String, Object> user = new HashMap<>();
            user.put("uid", uid);
            user.put("email", email);
            user.put("username", uniqueHandle);
            user.put("displayName", baseHandle);
            user.put("searchName", baseHandle.toLowerCase());
            user.put("publicKey", publicKey);

            FirebaseDatabase.getInstance().getReference("users").child(uid).setValue(user)
                .addOnSuccessListener(aVoid -> {
                    FirebaseDatabase.getInstance().getReference("usernames").child(uniqueHandle.toLowerCase()).setValue(uid)
                        .addOnSuccessListener(aVoid2 -> {
                            Toast.makeText(RegisterActivity.this, "Registration successful", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                })
                .addOnFailureListener(e -> {
                    registerButton.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, "Failed to save user data", Toast.LENGTH_SHORT).show();
                });
        });
    }

    private interface OnUsernameResolvedListener {
        void onResolved(String username);
    }

    private void checkUsernameUniqueness(String baseHandle, int suffix, String uid, OnUsernameResolvedListener listener) {
        String currentHandle = suffix == 0 ? baseHandle : baseHandle + suffix;
        FirebaseDatabase.getInstance().getReference("usernames").child(currentHandle.toLowerCase())
                .get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().exists() && !uid.equals(task.getResult().getValue(String.class))) {
                            checkUsernameUniqueness(baseHandle, suffix + 1, uid, listener);
                        } else {
                            listener.onResolved(currentHandle);
                        }
                    } else {
                        listener.onResolved(currentHandle);
                    }
                });
    }
}
