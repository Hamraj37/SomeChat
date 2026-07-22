package com.hamraj37.somechat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.models.Group;
import com.hamraj37.somechat.models.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CreateGroupActivity extends BaseActivity {

    private EditText groupNameInput;
    private ImageView groupAvatarPreview;
    private TextView selectedCountText;
    private ExtendedFloatingActionButton btnCreate;
    private RecyclerView friendsRecycler;
    
    private MembersAdapter adapter;
    private List<User> friendList = new ArrayList<>();
    private Set<String> selectedMemberIds = new HashSet<>();
    private String currentUserId;
    private Uri selectedAvatarUri;

    private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> pickImageLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (uri != null) {
                            selectedAvatarUri = uri;
                            Glide.with(this).load(uri).circleCrop().into(groupAvatarPreview);
                            groupAvatarPreview.setPadding(0, 0, 0, 0);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        currentUserId = FirebaseAuth.getInstance().getUid();

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        groupNameInput = findViewById(R.id.group_name_input);
        groupAvatarPreview = findViewById(R.id.group_avatar_preview);
        selectedCountText = findViewById(R.id.selected_count_text);
        btnCreate = findViewById(R.id.btn_create_group);
        friendsRecycler = findViewById(R.id.friends_recycler);

        findViewById(R.id.btn_select_avatar).setOnClickListener(v -> pickImageLauncher.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));

        setupRecyclerView();
        loadFriends();

        btnCreate.setOnClickListener(v -> createGroup());
    }

    private void setupRecyclerView() {
        adapter = new MembersAdapter();
        friendsRecycler.setLayoutManager(new LinearLayoutManager(this));
        friendsRecycler.setAdapter(adapter);
    }

    private void loadFriends() {
        DatabaseReference friendsRef = FirebaseDatabase.getInstance().getReference("friends").child(currentUserId);
        friendsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> friendIds = new ArrayList<>();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    friendIds.add(ds.getKey());
                }
                fetchFriendDetails(friendIds);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchFriendDetails(List<String> friendIds) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        for (String id : friendIds) {
            usersRef.child(id).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    User user = snapshot.getValue(User.class);
                    if (user != null) {
                        friendList.add(user);
                        adapter.notifyItemInserted(friendList.size() - 1);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }

    private void createGroup() {
        String groupName = groupNameInput.getText().toString().trim();
        if (TextUtils.isEmpty(groupName)) {
            Toast.makeText(this, "Please enter group name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedMemberIds.isEmpty()) {
            Toast.makeText(this, "Please select at least one member", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference groupsRef = FirebaseDatabase.getInstance().getReference("groups");
        String groupId = groupsRef.push().getKey();

        Map<String, Boolean> members = new HashMap<>();
        Map<String, Boolean> admins = new HashMap<>();
        members.put(currentUserId, true); // Admin
        admins.put(currentUserId, true);
        for (String id : selectedMemberIds) {
            members.put(id, true);
        }

        Group group = new Group(groupId, groupName, null, currentUserId, System.currentTimeMillis(), members, admins);

        if (selectedAvatarUri != null) {
            uploadAvatarAndCreateGroup(group);
        } else {
            saveGroupToFirebase(group);
        }
    }

    private void uploadAvatarAndCreateGroup(Group group) {
        // Logic to upload to GitHub or Firebase Storage
        // For now, let's just save it with null avatar or implement GitHub upload if available
        // Reusing GitHubStorage if possible
        try {
            java.io.InputStream is = getContentResolver().openInputStream(selectedAvatarUri);
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            is.close();

            com.hamraj37.somechat.utils.GitHubStorage.uploadBytes(bytes, "group_avatars", group.getGroupId() + ".jpg", new com.hamraj37.somechat.utils.GitHubStorage.UploadCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    group.setGroupAvatar(downloadUrl);
                    saveGroupToFirebase(group);
                }

                @Override
                public void onProgress(int progress) {}

                @Override
                public void onFailure(Exception e) {
                    saveGroupToFirebase(group); // Save anyway without avatar
                }
            });
        } catch (Exception e) {
            saveGroupToFirebase(group);
        }
    }

    private void saveGroupToFirebase(Group group) {
        DatabaseReference groupsRef = FirebaseDatabase.getInstance().getReference("groups");
        groupsRef.child(group.getGroupId()).setValue(group).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // 1. Add creator directly to their groups list
                FirebaseDatabase.getInstance().getReference("users").child(currentUserId)
                        .child("groups").child(group.getGroupId()).setValue(true);

                // 2. Send invitations to all other selected members
                DatabaseReference invitesRef = FirebaseDatabase.getInstance().getReference("groupInvites");
                DatabaseReference groupPendingRef = FirebaseDatabase.getInstance().getReference("groups")
                        .child(group.getGroupId()).child("pendingInvites");

                for (String userId : selectedMemberIds) {
                    if (!userId.equals(currentUserId)) {
                        groupPendingRef.child(userId).setValue(currentUserId);
                        invitesRef.child(userId).child(group.getGroupId()).setValue(currentUserId);
                    }
                }
                
                Toast.makeText(this, "Group created. Invitations sent.", Toast.LENGTH_SHORT).show();
                
                Intent intent = new Intent(this, GroupChatActivity.class);
                intent.putExtra("groupId", group.getGroupId());
                intent.putExtra("groupName", group.getGroupName());
                intent.putExtra("groupAvatar", group.getGroupAvatar());
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Failed to create group", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class MembersAdapter extends RecyclerView.Adapter<MemberViewHolder> {
        @NonNull
        @Override
        public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new MemberViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_selectable, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
            User user = friendList.get(position);
            holder.name.setText(user.getDisplayName());
            holder.username.setText("@" + user.getUsername());
            
            if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                Glide.with(holder.itemView).load(user.getPhotoUrl()).circleCrop().into(holder.avatar);
            } else {
                holder.avatar.setImageResource(R.mipmap.ic_launcher_round);
            }

            holder.checkBox.setChecked(selectedMemberIds.contains(user.getUid()));

            holder.itemView.setOnClickListener(v -> {
                if (selectedMemberIds.contains(user.getUid())) {
                    selectedMemberIds.remove(user.getUid());
                } else {
                    selectedMemberIds.add(user.getUid());
                }
                notifyItemChanged(position);
                updateSelectedCount();
            });
        }

        @Override
        public int getItemCount() {
            return friendList.size();
        }
    }

    private void updateSelectedCount() {
        selectedCountText.setText("Selected members: " + selectedMemberIds.size());
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView name, username;
        CheckBox checkBox;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.user_avatar);
            name = itemView.findViewById(R.id.user_display_name);
            username = itemView.findViewById(R.id.user_username);
            checkBox = itemView.findViewById(R.id.checkbox_member);
        }
    }
}
