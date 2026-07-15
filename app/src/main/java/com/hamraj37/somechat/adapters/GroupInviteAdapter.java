package com.hamraj37.somechat.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.R;
import com.hamraj37.somechat.models.Group;

import java.util.List;

public class GroupInviteAdapter extends RecyclerView.Adapter<GroupInviteAdapter.ViewHolder> {

    private final List<String> groupIds;
    private final OnInviteListener listener;

    public interface OnInviteListener {
        void onAccept(String groupId);
        void onDecline(String groupId);
    }

    public GroupInviteAdapter(List<String> groupIds, OnInviteListener listener) {
        this.groupIds = groupIds;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_invite, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String groupId = groupIds.get(position);
        holder.bind(groupId, listener);
    }

    @Override
    public int getItemCount() {
        return groupIds.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView avatar;
        TextView groupName;
        TextView senderName;
        View btnAccept, btnDecline;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.invite_avatar);
            groupName = itemView.findViewById(R.id.invite_group_name);
            senderName = itemView.findViewById(R.id.invite_sender_name);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnDecline = itemView.findViewById(R.id.btn_decline);
        }

        void bind(String groupId, OnInviteListener listener) {
            // 1. Fetch group details
            FirebaseDatabase.getInstance().getReference("groups").child(groupId)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            Group group = snapshot.getValue(Group.class);
                            if (group != null) {
                                groupName.setText(group.getGroupName());
                                if (group.getGroupAvatar() != null && !group.getGroupAvatar().isEmpty()) {
                                    Glide.with(itemView.getContext()).load(group.getGroupAvatar()).into(avatar);
                                } else {
                                    avatar.setImageResource(R.mipmap.ic_launcher_round);
                                }

                                // 2. Fetch inviter name
                                String adminId = group.getCreatedBy();
                                if (adminId != null) {
                                    FirebaseDatabase.getInstance().getReference("users").child(adminId).child("displayName")
                                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                                @Override
                                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                                    String name = snapshot.getValue(String.class);
                                                    if (name != null) {
                                                        senderName.setText(itemView.getContext().getString(R.string.invited_by, name));
                                                    }
                                                }
                                                @Override public void onCancelled(@NonNull DatabaseError error) {}
                                            });
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });

            btnAccept.setOnClickListener(v -> listener.onAccept(groupId));
            btnDecline.setOnClickListener(v -> listener.onDecline(groupId));
        }
    }
}
