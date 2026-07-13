package com.hamraj37.somechat.adapters;

import android.content.Intent;
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
import com.hamraj37.somechat.ProfileInfoActivity;
import com.hamraj37.somechat.R;
import com.hamraj37.somechat.models.User;

import java.util.List;

public class FriendRequestAdapter extends RecyclerView.Adapter<FriendRequestAdapter.ViewHolder> {

    private final List<String> requesterUids;
    private final OnRequestListener listener;

    public interface OnRequestListener {
        void onAccept(String uid);
        void onDecline(String uid);
    }

    public FriendRequestAdapter(List<String> requesterUids, OnRequestListener listener) {
        this.requesterUids = requesterUids;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String uid = requesterUids.get(position);
        holder.bind(uid, listener);
    }

    @Override
    public int getItemCount() {
        return requesterUids.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView avatar;
        TextView name;
        View btnAccept, btnDecline;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.request_avatar);
            name = itemView.findViewById(R.id.request_name);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnDecline = itemView.findViewById(R.id.btn_decline);
        }

        void bind(String uid, OnRequestListener listener) {
            // Fetch user details from RTDB
            FirebaseDatabase.getInstance().getReference("users").child(uid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            User user = snapshot.getValue(User.class);
                            if (user != null) {
                                name.setText(user.getDisplayName());
                                if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                                    Glide.with(itemView.getContext()).load(user.getPhotoUrl()).into(avatar);
                                } else {
                                    avatar.setImageResource(R.mipmap.ic_launcher_round);
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });

            View.OnClickListener openProfile = v -> {
                android.content.Intent intent = new android.content.Intent(itemView.getContext(), ProfileInfoActivity.class);
                intent.putExtra("uid", uid);
                itemView.getContext().startActivity(intent);
            };

            avatar.setOnClickListener(openProfile);
            name.setOnClickListener(openProfile);

            btnAccept.setOnClickListener(v -> listener.onAccept(uid));
            btnDecline.setOnClickListener(v -> listener.onDecline(uid));
        }
    }
}
