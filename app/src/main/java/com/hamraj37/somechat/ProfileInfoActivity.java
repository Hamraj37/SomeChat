package com.hamraj37.somechat;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.adapters.HighlightAdapter;
import com.hamraj37.somechat.adapters.MediaAdapter;
import com.hamraj37.somechat.adapters.PinnedMessagesAdapter;
import com.hamraj37.somechat.adapters.UserAdapter;
import com.hamraj37.somechat.databinding.ActivityProfileInfoBinding;
import com.hamraj37.somechat.models.Highlight;
import com.hamraj37.somechat.models.Message;
import com.hamraj37.somechat.models.Status;
import com.hamraj37.somechat.models.User;

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
    private List<Highlight> highlightList = new ArrayList<>();
    private HighlightAdapter highlightAdapter;
    private String currentPhotoUrl;
    private List<User> friendsList = new ArrayList<>();
    private UserAdapter friendsAdapter;

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
        
        binding = ActivityProfileInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String myUid = FirebaseAuth.getInstance().getUid();
        targetUid = getIntent().getStringExtra("uid");
        if (targetUid == null) targetUid = myUid;
        
        isOwnProfile = targetUid != null && targetUid.equals(myUid);

        if (!isOwnProfile) {
            binding.settingsButton.setVisibility(View.GONE);
            binding.qrCodeButton.setVisibility(View.GONE);
            binding.editProfileImageFab.setVisibility(View.GONE);
            binding.editCoverImageFab.setVisibility(View.GONE);
            binding.callActionsContainer.setVisibility(View.VISIBLE);
            binding.friendsCard.setVisibility(View.GONE);
            checkFriendshipStatus();
        } else {
            binding.editProfileImageFab.setVisibility(View.VISIBLE);
            binding.editCoverImageFab.setVisibility(View.VISIBLE);
            binding.qrCodeButton.setVisibility(View.VISIBLE);
            binding.callActionsContainer.setVisibility(View.GONE);
            binding.friendsCard.setVisibility(View.VISIBLE);
            setupFriendsRecyclerView();
            loadFriends();
        }

        loadUserProfile();
        setupClickListeners();
        setupLongClickListeners();
        setupHighlightsRecyclerView();
        
        binding.qrCodeButton.setOnClickListener(v -> showQRDialog());

        if (!isOwnProfile) {
            setupMediaRecyclerView();
            setupPinnedMessagesRecyclerView();
            loadSharedMedia();
        } else {
            binding.mediaCard.setVisibility(View.GONE);
            binding.pinnedMessagesCard.setVisibility(View.GONE);
        }
    }

    private void setupFriendsRecyclerView() {
        friendsAdapter = new UserAdapter(friendsList, new UserAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(User user) {
                Intent intent = new Intent(ProfileInfoActivity.this, ProfileInfoActivity.class);
                intent.putExtra("uid", user.getUid());
                startActivity(intent);
            }

            @Override
            public void onRemoveClick(User user) {
                new MaterialAlertDialogBuilder(ProfileInfoActivity.this)
                        .setTitle("Unfriend " + user.getDisplayName() + "?")
                        .setMessage("Are you sure you want to remove this user from your friends?")
                        .setPositiveButton("Unfriend", (dialog, which) -> {
                            String myUid = FirebaseAuth.getInstance().getUid();
                            if (myUid != null && user.getUid() != null) {
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("/friends/" + myUid + "/" + user.getUid(), null);
                                updates.put("/friends/" + user.getUid() + "/" + myUid, null);
                                FirebaseDatabase.getInstance().getReference().updateChildren(updates);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onNewGroupClick() {}
        });
        friendsAdapter.setShowRemoveButton(true);
        binding.friendsRecycler.setAdapter(friendsAdapter);
    }

    private void loadFriends() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) return;

        FirebaseDatabase.getInstance().getReference("friends").child(myUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> friendUids = new ArrayList<>();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            friendUids.add(ds.getKey());
                        }
                        fetchFriendDetails(friendUids);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void fetchFriendDetails(List<String> uids) {
        if (uids.isEmpty()) {
            friendsList.clear();
            friendsAdapter.notifyDataSetChanged();
            binding.friendsCount.setText("0");
            binding.noFriendsText.setVisibility(View.VISIBLE);
            binding.friendsRecycler.setVisibility(View.GONE);
            return;
        }

        binding.noFriendsText.setVisibility(View.GONE);
        binding.friendsRecycler.setVisibility(View.VISIBLE);
        
        List<User> newFriends = new ArrayList<>();
        final int total = uids.size();
        final int[] count = {0};

        for (String uid : uids) {
            FirebaseDatabase.getInstance().getReference("users").child(uid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            User user = snapshot.getValue(User.class);
                            if (user != null) {
                                user.setUid(snapshot.getKey());
                                newFriends.add(user);
                            }
                            count[0]++;
                            if (count[0] == total) {
                                friendsList.clear();
                                friendsList.addAll(newFriends);
                                friendsAdapter.notifyDataSetChanged();
                                binding.friendsCount.setText(String.valueOf(friendsList.size()));
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            count[0]++;
                            if (count[0] == total) {
                                friendsList.clear();
                                friendsList.addAll(newFriends);
                                friendsAdapter.notifyDataSetChanged();
                                binding.friendsCount.setText(String.valueOf(friendsList.size()));
                            }
                        }
                    });
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

    private void setupHighlightsRecyclerView() {
        highlightAdapter = new HighlightAdapter(highlightList, new HighlightAdapter.OnHighlightClickListener() {
            @Override
            public void onHighlightClick(Highlight highlight) {
                // Create a temporary Status object to reuse ViewStatusActivity
                Status status = new Status();
                status.setUserId(highlight.getUserId());
                status.setUserName(binding.profileName.getText().toString());
                status.setItems(highlight.getItems());
                status.setStatusId(highlight.getHighlightId());
                
                Intent intent = new Intent(ProfileInfoActivity.this, ViewStatusActivity.class);
                intent.putExtra("status", status);
                intent.putExtra("is_highlight", true);
                startActivity(intent);
            }

            @Override
            public void onHighlightLongClick(Highlight highlight) {
                if (isOwnProfile) {
                    new MaterialAlertDialogBuilder(ProfileInfoActivity.this)
                            .setTitle("Delete Highlight")
                            .setMessage("Are you sure you want to delete this highlight?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                FirebaseDatabase.getInstance().getReference("highlights")
                                        .child(targetUid)
                                        .child(highlight.getHighlightId())
                                        .removeValue();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            }
        });
        binding.highlightsRecycler.setAdapter(highlightAdapter);
        loadHighlights();
    }

    private void loadHighlights() {
        FirebaseDatabase.getInstance().getReference("highlights").child(targetUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        highlightList.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            Highlight h = ds.getValue(Highlight.class);
                            if (h != null) highlightList.add(h);
                        }
                        highlightAdapter.notifyDataSetChanged();
                        binding.highlightsRecycler.setVisibility(highlightList.isEmpty() ? View.GONE : View.VISIBLE);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
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
                            binding.noMediaText.setVisibility(View.VISIBLE);
                            binding.mediaRecycler.setVisibility(View.GONE);
                        } else {
                            binding.noMediaText.setVisibility(View.GONE);
                            binding.mediaRecycler.setVisibility(View.VISIBLE);
                        }

                        pinnedMessagesList.clear();
                        pinnedMessagesList.addAll(newPinnedList);
                        pinnedAdapter.notifyDataSetChanged();
                        if (pinnedMessagesList.isEmpty()) {
                            binding.noPinnedMessagesText.setVisibility(View.VISIBLE);
                            binding.pinnedMessagesRecycler.setVisibility(View.GONE);
                        } else {
                            binding.noPinnedMessagesText.setVisibility(View.GONE);
                            binding.pinnedMessagesRecycler.setVisibility(View.VISIBLE);
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
            String decrypted = com.hamraj37.somechat.utils.EncryptionManager.decrypt(text, this, isMe);
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
            String decrypted = com.hamraj37.somechat.utils.EncryptionManager.decrypt(mediaInfo, this, isMe);
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
        binding.friendActionButton.setVisibility(View.VISIBLE);
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
        binding.friendActionButton.setVisibility(View.VISIBLE);
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
        binding.friendActionButton.setVisibility(View.VISIBLE);
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
        binding.friendActionButton.setVisibility(View.VISIBLE);
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
        } else {
            binding.audioCallButton.setOnClickListener(v -> startCall(false));
            binding.videoCallButton.setOnClickListener(v -> startCall(true));
        }
    }

    private void startCall(boolean isVideo) {
        Intent intent = new Intent(this, isVideo ? VideoCallActivity.class : AudioCallActivity.class);
        intent.putExtra("uid", targetUid);
        intent.putExtra("displayName", otherUserName);
        intent.putExtra("photoUrl", currentPhotoUrl);
        intent.putExtra("isIncoming", false);
        startActivity(intent);
    }

    private void showQRDialog() {
        Intent intent = new Intent(this, QRCodeActivity.class);
        intent.putExtra("name", binding.profileName.getText().toString());
        intent.putExtra("uid", targetUid);
        intent.putExtra("photoUrl", currentPhotoUrl);
        startActivity(intent);
    }

    private void showProfileSettingsDialog() {
        String[] options = {getString(R.string.edit_name), getString(R.string.edit_handle), getString(R.string.change_bio), "Edit Websites", getString(R.string.change_profile_photo)};
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
                            showEditBioDialog();
                            break;
                        case 3:
                            showEditWebsitesDialog();
                            break;
                        case 4:
                            pickImageLauncher.launch("image/*");
                            break;
                    }
                })
                .show();
    }

    private void uploadProfileImage(android.net.Uri uri, boolean isCover) {
        if (binding != null) {
            binding.uploadProgress.setVisibility(View.VISIBLE);
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
            com.hamraj37.somechat.utils.GitHubStorage.uploadBytes(bytes, prefix, fileName, new com.hamraj37.somechat.utils.GitHubStorage.UploadCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    runOnUiThread(() -> {
                        if (binding != null) {
                            binding.uploadProgress.setVisibility(View.GONE);
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
                            binding.uploadProgress.setVisibility(View.GONE);
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

    private void showEditBioDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Bio");

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        final EditText input = new EditText(this);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input);
        
        String currentBio = binding.profileBio.getText().toString();
        String tapToAdd = getString(R.string.tap_to_add_bio);
        String noBioYet = getString(R.string.no_bio_yet);
        if (currentBio.equals(tapToAdd) || currentBio.equals(noBioYet)) {
            currentBio = "";
        }
        
        input.setText(currentBio);
        input.setSelection(input.getText().length());
        input.setHint("What's on your mind?");
        builder.setView(container);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newBio = input.getText().toString().trim();
            updateBio(newBio);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showEditWebsitesDialog() {
        FirebaseDatabase.getInstance().getReference("users").child(targetUid).child("websites")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<User.Website> currentWebsites = new ArrayList<>();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            Object value = ds.getValue();
                            if (value instanceof String) {
                                String url = (String) value;
                                currentWebsites.add(new User.Website(url, url));
                            } else {
                                User.Website w = ds.getValue(User.Website.class);
                                if (w != null) currentWebsites.add(w);
                            }
                        }

                        LinearLayout container = new LinearLayout(ProfileInfoActivity.this);
                        container.setOrientation(LinearLayout.VERTICAL);
                        int padding = (int) (16 * getResources().getDisplayMetrics().density);
                        container.setPadding(padding, padding, padding, padding);

                        List<EditText> urlInputs = new ArrayList<>();
                        
                        for (int i = 0; i < 5; i++) {
                            EditText urlInput = new EditText(ProfileInfoActivity.this);
                            urlInput.setHint("Website URL " + (i + 1));
                            
                            if (i < currentWebsites.size()) {
                                urlInput.setText(currentWebsites.get(i).getUrl());
                            }
                            
                            container.addView(urlInput);
                            urlInputs.add(urlInput);
                        }

                        android.widget.ScrollView scrollView = new android.widget.ScrollView(ProfileInfoActivity.this);
                        scrollView.addView(container);

                        new AlertDialog.Builder(ProfileInfoActivity.this)
                                .setTitle("Edit Websites (Max 5)")
                                .setView(scrollView)
                                .setPositiveButton("Save", (dialog, which) -> {
                                    List<String> urls = new ArrayList<>();
                                    for (EditText input : urlInputs) {
                                        String url = input.getText().toString().trim();
                                        if (!url.isEmpty()) {
                                            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                                url = "https://" + url;
                                            }
                                            urls.add(url);
                                        }
                                    }
                                    saveWebsitesWithAutoTitles(urls);
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void saveWebsitesWithAutoTitles(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            updateWebsites(new ArrayList<>());
            return;
        }

        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Updating Websites")
                .setMessage("Fetching titles...")
                .setCancelable(false)
                .show();

        new Thread(() -> {
            List<User.Website> newWebsites = new ArrayList<>();
            for (String url : urls) {
                String title = "";
                String faviconUrl = null;
                try {
                    String host = new java.net.URL(url).getHost();
                    faviconUrl = "https://www.google.com/s2/favicons?sz=64&domain=" + host;
                    
                    Document doc = Jsoup.connect(url).timeout(5000).get();
                    title = doc.title();
                    
                    // If fetched title is just the domain or empty, use a clean version of host
                    String cleanHost = host.startsWith("www.") ? host.substring(4) : host;
                    if (title.isEmpty() || title.equalsIgnoreCase(host) || title.equalsIgnoreCase(cleanHost)) {
                        int dotIndex = cleanHost.indexOf('.');
                        title = dotIndex > 0 ? cleanHost.substring(0, dotIndex) : cleanHost;
                        title = title.substring(0, 1).toUpperCase() + title.substring(1);
                    }
                } catch (Exception e) {
                    try {
                        String host = new java.net.URL(url).getHost();
                        if (host.startsWith("www.")) host = host.substring(4);
                        int dotIndex = host.indexOf('.');
                        title = dotIndex > 0 ? host.substring(0, dotIndex) : host;
                        title = title.substring(0, 1).toUpperCase() + title.substring(1);
                    } catch (Exception ignored) {
                        title = url;
                    }
                }
                
                newWebsites.add(new User.Website(title, url, faviconUrl));
            }

            runOnUiThread(() -> {
                progressDialog.dismiss();
                updateWebsites(newWebsites);
            });
        }).start();
    }

    private void updateWebsites(List<User.Website> websites) {
        FirebaseDatabase.getInstance().getReference("users").child(targetUid).child("websites").setValue(websites)
                .addOnSuccessListener(aVoid -> Toast.makeText(ProfileInfoActivity.this, "Websites updated", Toast.LENGTH_SHORT).show());
    }

    private void updateBio(String newBio) {
        FirebaseDatabase.getInstance().getReference("users").child(targetUid).child("bio").setValue(newBio)
                .addOnSuccessListener(aVoid -> Toast.makeText(ProfileInfoActivity.this, "Bio updated", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(ProfileInfoActivity.this, "Failed to update bio", Toast.LENGTH_SHORT).show());
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
                                String bio = snapshot.child("bio").getValue(String.class);
                                currentPhotoUrl = photoUrl;
                                otherUserName = name;

                                if (name != null) binding.profileName.setText(name);
                                if (handle != null) binding.profileHandle.setText("@" + handle);
                                
                                if (bio != null && !bio.isEmpty()) {
                                    binding.profileBio.setText(bio);
                                    binding.profileBio.setAlpha(0.9f);
                                } else {
                                    if (isOwnProfile) {
                                        binding.profileBio.setText(R.string.tap_to_add_bio);
                                        binding.profileBio.setAlpha(0.5f);
                                    } else {
                                        binding.profileBio.setText(R.string.no_bio_yet);
                                        binding.profileBio.setAlpha(0.5f);
                                    }
                                }

                                if (isOwnProfile) {
                                    binding.profileBio.setOnClickListener(v -> showEditBioDialog());
                                }

                                if (isOwnProfile && email != null) {
                                    binding.profileEmailHeader.setVisibility(View.VISIBLE);
                                    binding.profileEmailHeader.setText(email);
                                } else {
                                    binding.profileEmailHeader.setVisibility(View.GONE);
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

                                List<User.Website> websites = new ArrayList<>();
                                DataSnapshot websitesSnapshot = snapshot.child("websites");
                                for (DataSnapshot ds : websitesSnapshot.getChildren()) {
                                    Object value = ds.getValue();
                                    if (value instanceof String) {
                                        // Legacy data: handle it as a Website with the URL as title too
                                        String url = (String) value;
                                        websites.add(new User.Website(url, url));
                                    } else {
                                        User.Website w = ds.getValue(User.Website.class);
                                        if (w != null) websites.add(w);
                                    }
                                }
                                displayWebsites(websites);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });
        }
    }

    private void displayWebsites(List<User.Website> websites) {
        binding.websitesContainer.removeAllViews();
        if (websites == null || websites.isEmpty()) {
            binding.websitesContainer.setVisibility(View.GONE);
            return;
        }

        binding.websitesContainer.setVisibility(View.VISIBLE);
        for (User.Website website : websites) {
            com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, 0, 0, (int)(8 * getResources().getDisplayMetrics().density));
            card.setLayoutParams(cardLp);
            card.setRadius((int)(16 * getResources().getDisplayMetrics().density));
            card.setCardElevation(0);
            card.setStrokeWidth((int)(1 * getResources().getDisplayMetrics().density));
            card.setStrokeColor(androidx.core.content.ContextCompat.getColor(this, R.color.glass_border));
            card.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.glass_white));
            card.setClickable(true);
            card.setFocusable(true);

            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
            itemLayout.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
            int padding = (int)(12 * getResources().getDisplayMetrics().density);
            itemLayout.setPadding(padding, padding, padding, padding);

            ImageView favicon = new ImageView(this);
            int iconSize = (int)(24 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(iconSize, iconSize);
            lpIcon.setMarginEnd((int)(12 * getResources().getDisplayMetrics().density));
            favicon.setLayoutParams(lpIcon);

            String favUrl = website.getFaviconUrl();
            if (favUrl == null || favUrl.isEmpty()) {
                try {
                    String domain = new java.net.URL(website.getUrl()).getHost();
                    favUrl = "https://www.google.com/s2/favicons?sz=64&domain=" + domain;
                } catch (Exception e) {
                    favicon.setImageResource(android.R.drawable.ic_menu_share);
                }
            }

            if (favUrl != null) {
                Glide.with(this)
                        .load(favUrl)
                        .placeholder(android.R.drawable.ic_menu_share)
                        .error(android.R.drawable.ic_menu_share)
                        .into(favicon);
            }

            TextView textView = new TextView(this);
            textView.setText(website.getTitle());
            textView.setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK));
            androidx.core.widget.TextViewCompat.setTextAppearance(textView, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textView.setSingleLine(true);

            itemLayout.addView(favicon);
            itemLayout.addView(textView);
            card.addView(itemLayout);

            card.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(website.getUrl()));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
                }
            });
            binding.websitesContainer.addView(card);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
