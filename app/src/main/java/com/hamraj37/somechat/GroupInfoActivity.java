package com.hamraj37.somechat;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.adapters.UserAdapter;
import com.hamraj37.somechat.models.Group;
import com.hamraj37.somechat.models.User;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GroupInfoActivity extends BaseActivity {

    private String groupId;
    private String currentUserId;
    private Group group;
    
    private ImageView groupAvatarExpanded;
    private TextView groupNameTitle;
    private TextView groupCreationInfo;
    private RecyclerView membersRecycler;
    private UserAdapter adapter;
    private List<User> membersList = new ArrayList<>();

    private final androidx.activity.result.ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            uploadGroupPhoto(uri);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_info);

        groupId = getIntent().getStringExtra("groupId");
        currentUserId = FirebaseAuth.getInstance().getUid();

        if (groupId == null) {
            finish();
            return;
        }

        groupAvatarExpanded = findViewById(R.id.group_avatar_expanded);
        groupNameTitle = findViewById(R.id.group_name_title);
        groupCreationInfo = findViewById(R.id.group_creation_info);
        membersRecycler = findViewById(R.id.members_recycler);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        setupRecyclerView();
        loadGroupDetails();

        findViewById(R.id.btn_leave_group).setOnClickListener(v -> showLeaveGroupDialog());
        findViewById(R.id.btn_edit_group_name).setOnClickListener(v -> showEditGroupNameDialog());
        findViewById(R.id.btn_add_members).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddMembersActivity.class);
            intent.putExtra("groupId", groupId);
            startActivity(intent);
        });

        findViewById(R.id.btn_change_group_photo).setOnClickListener(v -> pickImageLauncher.launch("image/*"));
    }

    private void setupRecyclerView() {
        adapter = new UserAdapter(membersList, new UserAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(User user) {
                if (user.getUid().equals(currentUserId)) return;

                if (group != null && isAdmin(currentUserId)) {
                    // Admin clicking on a member
                    showAdminMemberOptions(user);
                } else {
                    // Regular member clicking on another member
                    Intent intent = new Intent(GroupInfoActivity.this, ProfileInfoActivity.class);
                    intent.putExtra("uid", user.getUid());
                    startActivity(intent);
                }
            }

            @Override
            public void onNewGroupClick() {}
        });
        membersRecycler.setLayoutManager(new LinearLayoutManager(this));
        membersRecycler.setAdapter(adapter);
    }

    private boolean isAdmin(String userId) {
        if (group == null) return false;
        if (userId.equals(group.getCreatedBy())) return true;
        return group.getAdmins() != null && Boolean.TRUE.equals(group.getAdmins().get(userId));
    }

    private void showAdminMemberOptions(User member) {
        boolean isSelectedMemberAdmin = isAdmin(member.getUid());
        boolean isCreator = member.getUid().equals(group.getCreatedBy());

        List<String> options = new ArrayList<>();
        options.add("View Profile");
        
        if (!isCreator) {
            if (isSelectedMemberAdmin) {
                options.add("Dismiss as Admin");
            } else {
                options.add("Make Group Admin");
            }
            options.add("Remove from Group");
        }

        String[] optionsArray = options.toArray(new String[0]);

        new MaterialAlertDialogBuilder(this)
                .setTitle(member.getDisplayName())
                .setItems(optionsArray, (dialog, which) -> {
                    String selectedOption = optionsArray[which];
                    if (selectedOption.equals("View Profile")) {
                        Intent intent = new Intent(GroupInfoActivity.this, ProfileInfoActivity.class);
                        intent.putExtra("uid", member.getUid());
                        startActivity(intent);
                    } else if (selectedOption.equals("Make Group Admin")) {
                        toggleAdminStatus(member, true);
                    } else if (selectedOption.equals("Dismiss as Admin")) {
                        toggleAdminStatus(member, false);
                    } else if (selectedOption.equals("Remove from Group")) {
                        showRemoveMemberDialog(member);
                    }
                })
                .show();
    }

    private void toggleAdminStatus(User member, boolean makeAdmin) {
        if (group == null) return;
        
        if (!makeAdmin && member.getUid().equals(group.getCreatedBy())) {
            Toast.makeText(this, "The group owner must remain an admin", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference adminRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId).child("admins").child(member.getUid());
        if (makeAdmin) {
            adminRef.setValue(true).addOnSuccessListener(aVoid -> {
                postAdminChangeSystemMessage(member, true);
                Toast.makeText(this, member.getDisplayName() + " is now an admin", Toast.LENGTH_SHORT).show();
            });
        } else {
            adminRef.removeValue().addOnSuccessListener(aVoid -> {
                postAdminChangeSystemMessage(member, false);
                Toast.makeText(this, member.getDisplayName() + " is no longer an admin", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void postAdminChangeSystemMessage(User member, boolean madeAdmin) {
        FirebaseDatabase.getInstance().getReference("users").child(currentUserId).child("displayName")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String adminName = snapshot.getValue(String.class);
                        if (adminName == null) adminName = "Admin";

                        String messageId = FirebaseDatabase.getInstance().getReference("group_chats").child(groupId).child("messages").push().getKey();
                        if (messageId != null) {
                            com.hamraj37.somechat.models.Message systemMsg = new com.hamraj37.somechat.models.Message();
                            systemMsg.setMessageId(messageId);
                            systemMsg.setSenderId("system");
                            systemMsg.setType("system");
                            systemMsg.setText(adminName + (madeAdmin ? " made " : " removed ") + member.getDisplayName() + (madeAdmin ? " a group admin" : " as group admin"));
                            systemMsg.setTimestamp(System.currentTimeMillis());
                            FirebaseDatabase.getInstance().getReference("group_chats").child(groupId).child("messages").child(messageId).setValue(systemMsg);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void showRemoveMemberDialog(User member) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove member")
                .setMessage("Remove " + member.getDisplayName() + " from the group?")
                .setPositiveButton("Remove", (dialog, which) -> removeMember(member))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeMember(User member) {
        if (group == null) return;
        
        if (member.getUid().equals(group.getCreatedBy())) {
            Toast.makeText(this, "The group owner cannot be removed", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference groupMemberRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId).child("members").child(member.getUid());
        DatabaseReference userGroupRef = FirebaseDatabase.getInstance().getReference("users").child(member.getUid()).child("groups").child(groupId);

        groupMemberRef.removeValue().addOnSuccessListener(aVoid -> {
            // Also remove from admins list if they were an admin
            FirebaseDatabase.getInstance().getReference("groups").child(groupId).child("admins").child(member.getUid()).removeValue();

            userGroupRef.removeValue();
            postRemovalSystemMessage(member);
            Toast.makeText(this, member.getDisplayName() + " removed", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to remove member", Toast.LENGTH_SHORT).show();
        });
    }

    private void postRemovalSystemMessage(User member) {
        FirebaseDatabase.getInstance().getReference("users").child(currentUserId).child("displayName")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String adminName = snapshot.getValue(String.class);
                        if (adminName == null) adminName = "Admin";

                        String messageId = FirebaseDatabase.getInstance().getReference("group_chats").child(groupId).child("messages").push().getKey();
                        if (messageId != null) {
                            com.hamraj37.somechat.models.Message systemMsg = new com.hamraj37.somechat.models.Message();
                            systemMsg.setMessageId(messageId);
                            systemMsg.setSenderId("system");
                            systemMsg.setType("system");
                            systemMsg.setText(adminName + " removed " + member.getDisplayName());
                            systemMsg.setTimestamp(System.currentTimeMillis());
                            FirebaseDatabase.getInstance().getReference("group_chats").child(groupId).child("messages").child(messageId).setValue(systemMsg);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void loadGroupDetails() {
        FirebaseDatabase.getInstance().getReference("groups").child(groupId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        group = snapshot.getValue(Group.class);
                        if (group != null) {
                            java.util.Map<String, Boolean> admins = new java.util.HashMap<>();
                            if (group.getAdmins() != null) {
                                admins.putAll(group.getAdmins());
                            }
                            // Always ensure creator is in the admin map for the badge
                            if (group.getCreatedBy() != null) {
                                admins.put(group.getCreatedBy(), true);
                            }
                            adapter.setAdminsMap(admins);
                            updateUI();
                            loadMembers();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void updateUI() {
        if (group == null || isFinishing() || isDestroyed()) return;
        
        groupNameTitle.setText(group.getGroupName());
        if (group.getGroupAvatar() != null && !group.getGroupAvatar().isEmpty()) {
            Glide.with(this).load(group.getGroupAvatar()).placeholder(R.mipmap.ic_launcher_round).into(groupAvatarExpanded);
        } else {
            groupAvatarExpanded.setImageResource(R.mipmap.ic_launcher_round);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        String dateStr = sdf.format(new Date(group.getCreatedAt()));
        
        if (group.getCreatedBy() != null) {
            // Fetch creator name
            FirebaseDatabase.getInstance().getReference("users").child(group.getCreatedBy()).child("displayName")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            String creatorName = snapshot.getValue(String.class);
                            if (creatorName == null) creatorName = "Unknown";
                            groupCreationInfo.setText("Created by " + (group.getCreatedBy().equals(currentUserId) ? "You" : creatorName) + " on " + dateStr);
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                    });
        } else {
            groupCreationInfo.setText("Created on " + dateStr);
        }

        if (isAdmin(currentUserId)) {
            findViewById(R.id.btn_add_members).setVisibility(View.VISIBLE);
            findViewById(R.id.btn_change_group_photo).setVisibility(View.VISIBLE);
            findViewById(R.id.btn_edit_group_name).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.btn_add_members).setVisibility(View.GONE);
            findViewById(R.id.btn_change_group_photo).setVisibility(View.GONE);
            findViewById(R.id.btn_edit_group_name).setVisibility(View.GONE);
        }
    }

    private void showEditGroupNameDialog() {
        if (group == null) return;

        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(group.getGroupName());
        input.setSelection(group.getGroupName().length());
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.addView(input);
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) input.getLayoutParams();
        lp.leftMargin = padding;
        lp.rightMargin = padding;
        input.setLayoutParams(lp);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit Group Name")
                .setView(container)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(newName)) {
                        updateGroupName(newName);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateGroupName(String newName) {
        FirebaseDatabase.getInstance().getReference("groups").child(groupId).child("groupName").setValue(newName)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Group name updated", Toast.LENGTH_SHORT).show();
                    
                    // Post a system message about the name change
                    postGroupNameChangeSystemMessage(newName);
                });
    }

    private void postGroupNameChangeSystemMessage(String newName) {
        FirebaseDatabase.getInstance().getReference("users").child(currentUserId).child("displayName")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String myName = snapshot.getValue(String.class);
                        if (myName == null) myName = "Admin";
                        
                        String messageId = FirebaseDatabase.getInstance().getReference("group_chats").child(groupId).child("messages").push().getKey();
                        if (messageId != null) {
                            com.hamraj37.somechat.models.Message systemMsg = new com.hamraj37.somechat.models.Message();
                            systemMsg.setMessageId(messageId);
                            systemMsg.setSenderId("system");
                            systemMsg.setType("system");
                            systemMsg.setText(myName + " changed the group name to \"" + newName + "\"");
                            systemMsg.setTimestamp(System.currentTimeMillis());
                            FirebaseDatabase.getInstance().getReference("group_chats").child(groupId).child("messages").child(messageId).setValue(systemMsg);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void uploadGroupPhoto(android.net.Uri uri) {
        Toast.makeText(this, "Updating group photo...", Toast.LENGTH_SHORT).show();
        try {
            java.io.InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;
            byte[] bytes = new byte[is.available()];
            int read = is.read(bytes);
            is.close();

            if (read <= 0) return;

            String fileName = groupId + "_" + System.currentTimeMillis() + ".jpg";
            com.hamraj37.somechat.utils.GitHubStorage.uploadBytes(bytes, "group_avatars", fileName, new com.hamraj37.somechat.utils.GitHubStorage.UploadCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    FirebaseDatabase.getInstance().getReference("groups").child(groupId).child("groupAvatar").setValue(downloadUrl)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(GroupInfoActivity.this, "Group photo updated", Toast.LENGTH_SHORT).show();
                            });
                }

                @Override
                public void onProgress(int progress) {}

                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(GroupInfoActivity.this, "Failed to update group photo", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Error preparing image", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadMembers() {
        if (group.getMembers() == null) return;

        membersList.clear();
        final int total = group.getMembers().size();
        final int[] count = {0};

        for (String uid : group.getMembers().keySet()) {
            FirebaseDatabase.getInstance().getReference("users").child(uid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            User user = snapshot.getValue(User.class);
                            if (user != null) {
                                membersList.add(user);
                            }
                            count[0]++;
                            if (count[0] == total) {
                                // Sort: Creator first, then other admins, then others alphabetically
                                membersList.sort((u1, u2) -> {
                                    boolean isCreator1 = u1.getUid().equals(group.getCreatedBy());
                                    boolean isCreator2 = u2.getUid().equals(group.getCreatedBy());
                                    if (isCreator1) return -1;
                                    if (isCreator2) return 1;

                                    boolean isAdmin1 = isAdmin(u1.getUid());
                                    boolean isAdmin2 = isAdmin(u2.getUid());

                                    if (isAdmin1 && !isAdmin2) return -1;
                                    if (!isAdmin1 && isAdmin2) return 1;

                                    String name1 = u1.getDisplayName() != null ? u1.getDisplayName() : "";
                                    String name2 = u2.getDisplayName() != null ? u2.getDisplayName() : "";
                                    return name1.compareToIgnoreCase(name2);
                                });

                                adapter.notifyDataSetChanged();
                                ((TextView) findViewById(R.id.members_count_label)).setText("Members (" + membersList.size() + ")");
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            count[0]++;
                        }
                    });
        }
    }

    private void showLeaveGroupDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Leave Group")
                .setMessage("Are you sure you want to leave this group?")
                .setPositiveButton("Leave", (dialog, which) -> leaveGroup())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void leaveGroup() {
        if (group == null) return;

        // 1. Get my name for the system message
        FirebaseDatabase.getInstance().getReference("users").child(currentUserId).child("displayName")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String myName = snapshot.getValue(String.class);
                        if (myName == null) myName = "A member";
                        
                        // 2. Post system message
                        String messageId = FirebaseDatabase.getInstance().getReference("group_chats").child(groupId).child("messages").push().getKey();
                        if (messageId != null) {
                            com.hamraj37.somechat.models.Message systemMsg = new com.hamraj37.somechat.models.Message();
                            systemMsg.setMessageId(messageId);
                            systemMsg.setSenderId("system");
                            systemMsg.setType("system");
                            systemMsg.setText(myName + " left the group");
                            systemMsg.setTimestamp(System.currentTimeMillis());
                            FirebaseDatabase.getInstance().getReference("group_chats").child(groupId).child("messages").child(messageId).setValue(systemMsg);
                        }

                        // 3. Remove from members list
                        DatabaseReference groupMembersRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId).child("members").child(currentUserId);
                        DatabaseReference userGroupsRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserId).child("groups").child(groupId);

                        groupMembersRef.removeValue().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                userGroupsRef.removeValue();
                                
                                // Remove from local database immediately
                                com.hamraj37.somechat.db.AppDatabase.databaseWriteExecutor.execute(() -> {
                                    com.hamraj37.somechat.db.AppDatabase.getDatabase(GroupInfoActivity.this).chatItemDao().deleteByUid(groupId);
                                });

                                // 4. If I was admin and there are other members, maybe assign new admin?
                                // For now, if no members left, delete the group metadata
                                if (group.getMembers() != null && group.getMembers().size() <= 1) {
                                    FirebaseDatabase.getInstance().getReference("groups").child(groupId).removeValue();
                                    // Optionally delete messages too
                                    FirebaseDatabase.getInstance().getReference("group_chats").child(groupId).removeValue();
                                }

                                Toast.makeText(GroupInfoActivity.this, "Left group", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(GroupInfoActivity.this, MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(GroupInfoActivity.this, "Failed to leave group", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }
}
