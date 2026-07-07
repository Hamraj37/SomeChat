package com.samechat37;

import android.content.Intent;
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
import com.samechat37.adapters.MediaAdapter;
import com.samechat37.adapters.PinnedMessagesAdapter;
import com.samechat37.databinding.ActivityProfileInfoBinding;
import com.samechat37.models.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileInfoActivity extends BaseActivity {

    private ActivityProfileInfoBinding binding;
    private String targetUid;
    private boolean isOwnProfile;
    private List<Message> mediaList = new ArrayList<>();
    private MediaAdapter mediaAdapter;
    private List<Message> pinnedMessagesList = new ArrayList<>();
    private PinnedMessagesAdapter pinnedAdapter;
    private String otherUserName;

    private final androidx.activity.result.ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            uploadProfileImage(uri, false);
                        }
                    });

    private final androidx.activity.result.ActivityResultLauncher<String> pickCoverLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            uploadProfileImage(uri, true);
                        }
                    });

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
            binding.settingsButton.setVisibility(android.view.View.GONE);
            binding.editProfileImageFab.setVisibility(android.view.View.GONE);
            binding.editCoverImageFab.setVisibility(android.view.View.GONE);
            checkFriendshipStatus();
        } else {
            binding.editProfileImageFab.setVisibility(android.view.View.VISIBLE);
            binding.editCoverImageFab.setVisibility(android.view.View.VISIBLE);
        }

        loadUserProfile();
        setupClickListeners();
        setupLongClickListeners();

        if (!isOwnProfile) {
            setupMediaRecyclerView();
            setupPinnedMessagesRecyclerView();
            loadSharedMedia();
        } else {
            binding.mediaCard.setVisibility(android.view.View.GONE);
            binding.pinnedMessagesCard.setVisibility(android.view.View.GONE);
        }
    }

    private void setupPinnedMessagesRecyclerView() {
        String myUid = FirebaseAuth.getInstance().getUid();
        pinnedAdapter = new PinnedMessagesAdapter(this, pinnedMessagesList, myUid, otherUserName, message -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("uid", targetUid);
            intent.putExtra("displayName", otherUserName);
            intent.putExtra("scroll_to_message_id", message.getMessageId());
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
        binding.pinnedMessagesRecycler.setAdapter(pinnedAdapter);
    }

    private void setupMediaRecyclerView() {
        mediaAdapter = new MediaAdapter(this, mediaList, message -> {
            Intent intent = new Intent(this, FullMediaActivity.class);
            intent.putExtra("type", message.getType());
            intent.putExtra("message_id", message.getMessageId());
            
            String mediaUrl = message.getMediaUrl();
            if (mediaUrl != null && mediaUrl.startsWith("{")) {
                intent.putExtra("encrypted_info", mediaUrl);
            } else {
                intent.putExtra("url", mediaUrl);
            }
            
            startActivity(intent);
        });
        binding.mediaRecycler.setAdapter(mediaAdapter);
    }

    private void loadSharedMedia() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null || targetUid == null) return;

        String chatId = myUid.compareTo(targetUid) < 0
                ? myUid + "_" + targetUid
                : targetUid + "_" + myUid;

        FirebaseDatabase.getInstance().getReference("chats").child(chatId)
                .limitToLast(50)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (binding == null) return;
                        List<Message> newMediaList = new ArrayList<>();
                        List<Message> newPinnedList = new ArrayList<>();
                        
                        for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                            Message message = dataSnapshot.getValue(Message.class);
                            if (message != null && message.getType() != null) {
                                String type = message.getType();
                                if ("image".equalsIgnoreCase(type) || "video".equalsIgnoreCase(type)) {
                                    decryptMessageMedia(message);
                                    newMediaList.add(0, message);
                                }
                                if (message.isPinned()) {
                                    decryptPinnedMessage(message);
                                    newPinnedList.add(0, message);
                                }
                            }
                        }
                        
                        mediaList.clear();
                        mediaList.addAll(newMediaList);
                        mediaAdapter.notifyDataSetChanged();
                        binding.mediaCount.setText(String.valueOf(mediaList.size()));
                        
                        if (mediaList.isEmpty()) {
                            binding.noMediaText.setVisibility(android.view.View.VISIBLE);
                            binding.mediaRecycler.setVisibility(android.view.View.GONE);
                        } else {
                            binding.noMediaText.setVisibility(android.view.View.GONE);
                            binding.mediaRecycler.setVisibility(android.view.View.VISIBLE);
                        }

                        pinnedMessagesList.clear();
                        pinnedMessagesList.addAll(newPinnedList);
                        pinnedAdapter.notifyDataSetChanged();
                        if (pinnedMessagesList.isEmpty()) {
                            binding.noPinnedMessagesText.setVisibility(android.view.View.VISIBLE);
                            binding.pinnedMessagesRecycler.setVisibility(android.view.View.GONE);
                        } else {
                            binding.noPinnedMessagesText.setVisibility(android.view.View.GONE);
                            binding.pinnedMessagesRecycler.setVisibility(android.view.View.VISIBLE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void decryptPinnedMessage(Message message) {
        String text = message.getText();
        if (text == null) return;

        try {
            boolean isMe = message.getSenderId().equals(FirebaseAuth.getInstance().getUid());
            String decrypted = com.samechat37.utils.EncryptionManager.decrypt(text, this, isMe);
            if (decrypted != null) {
                message.setText(decrypted);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void decryptMessageMedia(Message message) {
        String mediaInfo = message.getMediaUrl();
        if (mediaInfo == null) return;

        try {
            boolean isMe = message.getSenderId().equals(FirebaseAuth.getInstance().getUid());
            String decrypted = com.samechat37.utils.EncryptionManager.decrypt(mediaInfo, this, isMe);
            if (decrypted != null) {
                message.setMediaUrl(decrypted);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupLongClickListeners() {
        binding.profileHandle.setOnLongClickListener(v -> {
            String handleText = binding.profileHandle.getText().toString();
            if (!handleText.isEmpty()) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Username", handleText);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Username copied to clipboard", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
    }

    private void checkFriendshipStatus() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null || targetUid == null) return;

        FirebaseDatabase.getInstance().getReference("friends")
                .child(myUid)
                .child(targetUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (binding == null) return;
                        if (snapshot.exists()) {
                            showUnfriendUI();
                        } else {
                            checkPendingRequest();
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void checkPendingRequest() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null || targetUid == null) return;

        FirebaseDatabase.getInstance().getReference("friendRequests")
                .child(targetUid)
                .child(myUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (binding == null) return;
                        if (snapshot.exists()) {
                            showPendingUI();
                        } else {
                            FirebaseDatabase.getInstance().getReference("friendRequests")
                                    .child(myUid)
                                    .child(targetUid)
                                    .addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                                            if (binding == null) return;
                                            if (snapshot.exists()) {
                                                showAcceptUI();
                                            } else {
                                                showAddFriendUI();
                                            }
                                        }
                                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                                    });
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void showUnfriendUI() {
        binding.friendActionButton.setVisibility(android.view.View.VISIBLE);
        binding.friendActionButton.setText(R.string.unfriend);
        binding.friendActionButton.setIconResource(android.R.drawable.ic_menu_delete);
        
        int colorError = com.google.android.material.color.MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError, android.graphics.Color.RED);
        int colorErrorContainer = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorErrorContainer, android.graphics.Color.RED);
        
        binding.friendActionButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorErrorContainer));
        binding.friendActionButton.setTextColor(colorError);
        binding.friendActionButton.setIconTint(android.content.res.ColorStateList.valueOf(colorError));
        binding.friendActionButton.setOnClickListener(v -> showUnfriendConfirmDialog());
    }

    private void showPendingUI() {
        binding.friendActionButton.setVisibility(android.view.View.VISIBLE);
        binding.friendActionButton.setText(R.string.cancel_request);
        binding.friendActionButton.setIconResource(android.R.drawable.ic_menu_close_clear_cancel);
        
        int colorOnSurface = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.GRAY);
        int colorSurface = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant, android.graphics.Color.LTGRAY);
        
        binding.friendActionButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorSurface));
        binding.friendActionButton.setTextColor(colorOnSurface);
        binding.friendActionButton.setIconTint(android.content.res.ColorStateList.valueOf(colorOnSurface));
        binding.friendActionButton.setOnClickListener(v -> cancelFriendRequest());
    }

    private void cancelFriendRequest() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null || targetUid == null) return;

        FirebaseDatabase.getInstance().getReference("friendRequests")
                .child(targetUid)
                .child(myUid)
                .removeValue()
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Friend request cancelled", Toast.LENGTH_SHORT).show());
    }

    private void showAddFriendUI() {
        binding.friendActionButton.setVisibility(android.view.View.VISIBLE);
        binding.friendActionButton.setText(R.string.add_friend);
        binding.friendActionButton.setIconResource(android.R.drawable.ic_input_add);
        
        int colorPrimary = com.google.android.material.color.MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, android.graphics.Color.BLUE);
        int colorOnPrimary = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary, android.graphics.Color.WHITE);
        
        binding.friendActionButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorPrimary));
        binding.friendActionButton.setTextColor(colorOnPrimary);
        binding.friendActionButton.setIconTint(android.content.res.ColorStateList.valueOf(colorOnPrimary));
        binding.friendActionButton.setOnClickListener(v -> sendFriendRequest());
    }

    private void showAcceptUI() {
        binding.friendActionButton.setVisibility(android.view.View.VISIBLE);
        binding.friendActionButton.setText("Accept Request");
        binding.friendActionButton.setIconResource(android.R.drawable.checkbox_on_background);
        
        int colorPrimary = com.google.android.material.color.MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, android.graphics.Color.BLUE);
        int colorOnPrimary = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary, android.graphics.Color.WHITE);
        
        binding.friendActionButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colorPrimary));
        binding.friendActionButton.setTextColor(colorOnPrimary);
        binding.friendActionButton.setIconTint(android.content.res.ColorStateList.valueOf(colorOnPrimary));
        binding.friendActionButton.setOnClickListener(v -> acceptFriendRequest());
    }

    private void sendFriendRequest() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null || targetUid == null) return;
        FirebaseDatabase.getInstance().getReference("friendRequests")
                .child(targetUid)
                .child(myUid)
                .setValue("pending")
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Friend request sent", Toast.LENGTH_SHORT).show());
    }

    private void acceptFriendRequest() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null || targetUid == null) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("/friends/" + myUid + "/" + targetUid, true);
        updates.put("/friends/" + targetUid + "/" + myUid, true);
        updates.put("/friendRequests/" + myUid + "/" + targetUid, null);
        FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Friend request accepted", Toast.LENGTH_SHORT).show());
    }

    private void setupClickListeners() {
        if (isOwnProfile) {
            binding.settingsButton.setOnClickListener(v -> showProfileSettingsDialog());
            binding.profileImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
            binding.editProfileImageFab.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
            binding.editCoverImageFab.setOnClickListener(v -> pickCoverLauncher.launch("image/*"));
        }
    }

    private void showProfileSettingsDialog() {
        String[] options = {"Change Name", "Change Username", "Change Profile Photo"};
        new AlertDialog.Builder(this)
                .setTitle("Profile Settings")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showEditNameDialog();
                            break;
                        case 1:
                            showEditHandleDialog();
                            break;
                        case 2:
                            pickImageLauncher.launch("image/*");
                            break;
                    }
                })
                .show();
    }

    private void uploadProfileImage(android.net.Uri uri, boolean isCover) {
        if (binding != null) {
            binding.uploadProgress.setVisibility(android.view.View.VISIBLE);
            binding.uploadProgress.setProgress(0);
        }
        Toast.makeText(this, isCover ? "Uploading cover photo..." : "Uploading profile photo...", Toast.LENGTH_SHORT).show();
        try {
            java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return;
            
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            byte[] bytes = baos.toByteArray();
            inputStream.close();

            String prefix = isCover ? "covers" : "avatars";
            String fileName = targetUid + "_" + System.currentTimeMillis() + ".jpg";
            com.samechat37.utils.GitHubStorage.uploadBytes(bytes, prefix, fileName, new com.samechat37.utils.GitHubStorage.UploadCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    runOnUiThread(() -> {
                        if (binding != null) {
                            binding.uploadProgress.setVisibility(android.view.View.GONE);
                        }
                        String node = isCover ? "coverUrl" : "photoUrl";
                        FirebaseDatabase.getInstance().getReference("users").child(targetUid).child(node).setValue(downloadUrl)
                                .addOnSuccessListener(aVoid -> {
                                    if (!isCover) {
                                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                                        if (user != null) {
                                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                                    .setPhotoUri(android.net.Uri.parse(downloadUrl))
                                                    .build();
                                            user.updateProfile(profileUpdates);
                                        }
                                    }
                                    Toast.makeText(ProfileInfoActivity.this, isCover ? "Cover photo updated" : "Profile photo updated", Toast.LENGTH_SHORT).show();
                                });
                    });
                }

                @Override
                public void onProgress(int progress) {
                    runOnUiThread(() -> {
                        if (binding != null) {
                            binding.uploadProgress.setProgress(progress);
                        }
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> {
                        if (binding != null) {
                            binding.uploadProgress.setVisibility(android.view.View.GONE);
                        }
                        Toast.makeText(ProfileInfoActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to read image", Toast.LENGTH_SHORT).show();
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
                                String coverUrl = snapshot.child("coverUrl").getValue(String.class);
                                String email = snapshot.child("email").getValue(String.class);
                                otherUserName = name;

                                if (name != null) binding.profileName.setText(name);
                                if (handle != null) binding.profileHandle.setText("@" + handle);
                                
                                if (isOwnProfile && email != null) {
                                    binding.profileEmailHeader.setVisibility(android.view.View.VISIBLE);
                                    binding.profileEmailHeader.setText(email);
                                } else {
                                    binding.profileEmailHeader.setVisibility(android.view.View.GONE);
                                }

                                if (photoUrl != null && !photoUrl.isEmpty()) {
                                    Glide.with(ProfileInfoActivity.this)
                                            .load(photoUrl)
                                            .centerCrop()
                                            .placeholder(R.mipmap.ic_launcher_round)
                                            .into(binding.profileImage);
                                }
                                if (coverUrl != null && !coverUrl.isEmpty()) {
                                    Glide.with(ProfileInfoActivity.this)
                                            .load(coverUrl)
                                            .centerCrop()
                                            .into(binding.coverImage);
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
