package com.samechat37;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
    private String targetUid;
    private boolean isOwnProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isNightMode);
        
        binding = ActivityProfileInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String myUid = FirebaseAuth.getInstance().getUid();
        targetUid = getIntent().getStringExtra("uid");
        if (targetUid == null) targetUid = myUid;
        
        isOwnProfile = targetUid != null && targetUid.equals(myUid);

        if (!isOwnProfile) {
            binding.editNameButton.setVisibility(android.view.View.GONE);
            binding.editHandleButton.setVisibility(android.view.View.GONE);
            checkFriendStatus();
        }

        loadUserProfile();
        setupClickListeners();
    }

    private void checkFriendStatus() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null || targetUid == null) return;

        FirebaseDatabase.getInstance().getReference("friends")
                .child(myUid)
                .child(targetUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (binding == null || isFinishing() || isDestroyed()) return;
                        if (snapshot.exists()) {
                            binding.unfriendButton.setVisibility(android.view.View.VISIBLE);
                        } else {
                            binding.unfriendButton.setVisibility(android.view.View.GONE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void setupClickListeners() {
        if (isOwnProfile) {
            binding.editNameButton.setOnClickListener(v -> showEditNameDialog());
            binding.editHandleButton.setOnClickListener(v -> showEditHandleDialog());
        } else {
            binding.unfriendButton.setOnClickListener(v -> showUnfriendConfirmDialog());
        }
    }

    private void showUnfriendConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Unfriend User")
                .setMessage("Are you sure you want to remove this user from your friends list?")
                .setPositiveButton("Unfriend", (dialog, which) -> unfriendUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void unfriendUser() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null || targetUid == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("/friends/" + myUid + "/" + targetUid, null);
        updates.put("/friends/" + targetUid + "/" + myUid, null);

        FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "User removed from friends", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to unfriend", Toast.LENGTH_SHORT).show());
    }

    private void showEditNameDialog() {
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Username");

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
        FirebaseDatabase.getInstance().getReference("usernames").child(newHandle).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DataSnapshot snapshot = task.getResult();
                if (snapshot != null && snapshot.exists() && !targetUid.equals(snapshot.getValue(String.class))) {
                    Toast.makeText(this, "Username already taken", Toast.LENGTH_SHORT).show();
                } else {
                    updateHandle(newHandle, oldHandle);
                }
            } else {
                Toast.makeText(this, "Error checking username uniqueness", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateHandle(String newHandle, String oldHandle) {
        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put("/users/" + targetUid + "/username", newHandle);
        childUpdates.put("/usernames/" + newHandle, targetUid);
        
        if (oldHandle != null && !oldHandle.isEmpty() && !oldHandle.equals(newHandle)) {
            childUpdates.put("/usernames/" + oldHandle, null);
        }

        FirebaseDatabase.getInstance().getReference().updateChildren(childUpdates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Username updated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show();
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
                FirebaseDatabase.getInstance().getReference("users").child(targetUid).child("displayName").setValue(newName)
                        .addOnSuccessListener(aVoid -> Toast.makeText(ProfileInfoActivity.this, "Name updated successfully", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadUserProfile() {
        if (targetUid != null) {
            FirebaseDatabase.getInstance().getReference("users").child(targetUid)
                    .addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (binding == null || isFinishing() || isDestroyed()) return;
                            if (snapshot.exists()) {
                                String name = snapshot.child("displayName").getValue(String.class);
                                String handle = snapshot.child("username").getValue(String.class);
                                String photoUrl = snapshot.child("photoUrl").getValue(String.class);
                                String email = snapshot.child("email").getValue(String.class);

                                if (name != null) binding.profileName.setText(name);
                                if (handle != null) binding.profileHandle.setText("@" + handle);
                                if (email != null) {
                                    binding.profileEmailHeader.setText(email);
                                }
                                if (photoUrl != null && !photoUrl.isEmpty()) {
                                    Glide.with(ProfileInfoActivity.this)
                                            .load(photoUrl)
                                            .centerCrop()
                                            .placeholder(R.mipmap.ic_launcher_round)
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
