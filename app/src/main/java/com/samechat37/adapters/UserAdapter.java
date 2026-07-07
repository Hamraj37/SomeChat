package com.samechat37.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.samechat37.R;
import com.samechat37.models.User;

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private List<User> userList;
    private final OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(User user);
    }

    public UserAdapter(List<User> userList, OnUserClickListener listener) {
        this.userList = userList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = userList.get(position);
        holder.displayName.setText(user.getDisplayName());
        
        String myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (user.getUid() != null && user.getUid().equals(myUid)) {
            holder.email.setText(user.getEmail());
            holder.email.setVisibility(View.VISIBLE);
        } else {
            holder.email.setVisibility(View.GONE);
        }
        
        if (user.getUsername() != null && !user.getUsername().isEmpty()) {
            holder.username.setText("@" + user.getUsername());
            holder.username.setVisibility(View.VISIBLE);
        } else {
            holder.username.setVisibility(View.GONE);
        }

        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(user.getPhotoUrl())
                    .circleCrop()
                    .placeholder(R.mipmap.ic_launcher_round)
                    .into(holder.avatar);
        } else {
            holder.avatar.setImageResource(R.mipmap.ic_launcher_round);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUserClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public void updateList(List<User> newList) {
        this.userList = newList;
        notifyDataSetChanged();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView displayName;
        TextView username;
        TextView email;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.user_avatar);
            displayName = itemView.findViewById(R.id.user_display_name);
            username = itemView.findViewById(R.id.user_username);
            email = itemView.findViewById(R.id.user_email);
        }
    }
}
