package com.hamraj37.somechat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
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
import com.hamraj37.somechat.models.Message;
import com.hamraj37.somechat.models.User;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AddMembersActivity extends BaseActivity {

    private String groupId;
    private TextView selectedCountText;
    private RecyclerView friendsRecycler;
    private ExtendedFloatingActionButton btnAdd;
    
    private MembersAdapter adapter;
    private List<User> friendList = new ArrayList<>();
    private Set<String> selectedMemberIds = new HashSet<>();
    private Set<String> existingMemberIds = new HashSet<>();
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_members);

        groupId = getIntent().getStringExtra("groupId");
        currentUserId = FirebaseAuth.getInstance().getUid();

        if (groupId == null) {
            finish();
            return;
        }

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        selectedCountText = findViewById(R.id.selected_count_text);
        friendsRecycler = findViewById(R.id.friends_recycler);
        btnAdd = findViewById(R.id.btn_add_confirm);

        setupRecyclerView();
        loadExistingMembersAndFriends();

        btnAdd.setOnClickListener(v -> addMembersToGroup());
    }

    private void setupRecyclerView() {
        adapter = new MembersAdapter();
        friendsRecycler.setLayoutManager(new LinearLayoutManager(this));
        friendsRecycler.setAdapter(adapter);
    }

    private void loadExistingMembersAndFriends() {
        // 1. Load existing members to filter them out
        FirebaseDatabase.getInstance().getReference("groups").child(groupId).child("members")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            existingMemberIds.add(ds.getKey());
                        }
                        loadFriends();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void loadFriends() {
        FirebaseDatabase.getInstance().getReference("friends").child(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> friendIds = new ArrayList<>();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String id = ds.getKey();
                            if (id != null && !existingMemberIds.contains(id)) {
                                friendIds.add(id);
                            }
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

    private void addMembersToGroup() {
        if (selectedMemberIds.isEmpty()) {
            Toast.makeText(this, "Select at least one member", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference groupMembersRef = FirebaseDatabase.getInstance().getReference("groups").child(groupId).child("members");
        DatabaseReference groupChatRef = FirebaseDatabase.getInstance().getReference("group_chats").child(groupId).child("messages");

        // Get current user name for system message
        FirebaseDatabase.getInstance().getReference("users").child(currentUserId).child("displayName")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String adminName = snapshot.getValue(String.class);
                        if (adminName == null) adminName = "Admin";

                        for (String userId : selectedMemberIds) {
                            // Add to group members
                            groupMembersRef.child(userId).setValue(true);
                            // Add to user groups list
                            FirebaseDatabase.getInstance().getReference("users").child(userId).child("groups").child(groupId).setValue(true);

                            // Send system message for each added user (or one combined message)
                            postSystemMessage(userId, adminName, groupChatRef);
                        }

                        Toast.makeText(AddMembersActivity.this, "Members added successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void postSystemMessage(String addedUserId, String adminName, DatabaseReference chatRef) {
        FirebaseDatabase.getInstance().getReference("users").child(addedUserId).child("displayName")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String addedName = snapshot.getValue(String.class);
                        if (addedName == null) addedName = "A member";

                        String messageId = chatRef.push().getKey();
                        if (messageId != null) {
                            Message msg = new Message();
                            msg.setMessageId(messageId);
                            msg.setSenderId("system");
                            msg.setType("system");
                            msg.setText(adminName + " added " + addedName);
                            msg.setTimestamp(System.currentTimeMillis());
                            chatRef.child(messageId).setValue(msg);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
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
        selectedCountText.setText("Selected: " + selectedMemberIds.size());
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
