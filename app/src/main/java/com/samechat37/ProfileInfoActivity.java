package com.samechat37;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.samechat37.databinding.ActivityProfileInfoBinding;

import java.util.HashMap;
import java.util.Map;

public class ProfileInfoActivity extends BaseActivity {

    private ActivityProfileInfoBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isNightMode);
        
        binding = ActivityProfileInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadUserProfile();
        setupClickListeners();
    }

    private void setupClickListeners() {
        binding.editNameButton.setOnClickListener(v -> showEditNameDialog());
        binding.editHandleButton.setOnClickListener(v -> showEditHandleDialog());
    }

    private void showEditNameDialog() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Display Name");

        final EditText input = new EditText(this);
        input.setText(binding.profileName.getText());
        input.setSelection(input.getText().length());
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                updateName(newName);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showEditHandleDialog() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Username");

        // Use a container for padding the EditText
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        final EditText input = new EditText(this);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);
        
        String currentHandleText = binding.profileHandle.getText().toString();
        String currentHandle = currentHandleText.startsWith("@") ? currentHandleText.substring(1) : currentHandleText;
        
        input.setText(currentHandle);
        input.setSelection(input.getText().length());
        input.setHint("Enter unique handle");
        builder.setView(container);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String rawInput = input.getText().toString().trim();
            // Sanitize: remove illegal RTDB characters (. $ # [ ] /)
            String newHandle = rawInput.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            
            if (newHandle.isEmpty()) {
                Toast.makeText(this, "Username cannot be empty or contain special characters", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (!newHandle.equals(currentHandle)) {
                checkAndUpdateHandle(newHandle, currentHandle);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void checkAndUpdateHandle(String newHandle, String oldHandle) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseDatabase.getInstance().getReference("usernames").child(newHandle).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DataSnapshot snapshot = task.getResult();
                // If it exists and belongs to someone else, it's taken
                if (snapshot != null && snapshot.exists() && !user.getUid().equals(snapshot.getValue(String.class))) {
                    Toast.makeText(this, "Username already taken", Toast.LENGTH_SHORT).show();
                } else {
                    updateHandle(newHandle, oldHandle);
                }
            } else {
                String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateHandle(String newHandle, String oldHandle) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put("/users/" + uid + "/username", newHandle);
        childUpdates.put("/usernames/" + newHandle, uid);
        
        // Only remove the old handle if it was actually set and is different
        if (oldHandle != null && !oldHandle.isEmpty() && !oldHandle.equals(newHandle)) {
            childUpdates.put("/usernames/" + oldHandle, null);
        }

        FirebaseDatabase.getInstance().getReference().updateChildren(childUpdates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Username updated", Toast.LENGTH_SHORT).show();
            } else {
                String error = task.getException() != null ? task.getException().getMessage() : "Update failed";
                Toast.makeText(this, "Update failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateName(String newName) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build();

        user.updateProfile(profileUpdates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("displayName").setValue(newName)
                        .addOnSuccessListener(aVoid -> Toast.makeText(ProfileInfoActivity.this, "Name updated successfully", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(ProfileInfoActivity.this, "Failed to update database", Toast.LENGTH_SHORT).show());
            } else {
                Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadUserProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String uid = user.getUid();
            
            binding.profileEmail.setText(user.getEmail());
            binding.profileUid.setText(uid);
            if (user.getDisplayName() != null) binding.profileName.setText(user.getDisplayName());
            if (user.getPhotoUrl() != null) {
                Glide.with(this).load(user.getPhotoUrl()).centerCrop().into(binding.profileImage);
            }

            FirebaseDatabase.getInstance().getReference("users").child(uid)
                    .addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (binding == null) return;
                            if (snapshot.exists()) {
                                String name = snapshot.child("displayName").getValue(String.class);
                                String handle = snapshot.child("username").getValue(String.class);
                                String photoUrl = snapshot.child("photoUrl").getValue(String.class);

                                if (name != null) binding.profileName.setText(name);
                                if (handle != null) binding.profileHandle.setText("@" + handle);
                                if (photoUrl != null) {
                                    Glide.with(ProfileInfoActivity.this)
                                            .load(photoUrl)
                                            .centerCrop()
                                            .into(binding.profileImage);
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
