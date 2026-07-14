package com.hamraj37.somechat.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.hamraj37.somechat.R;
import com.hamraj37.somechat.models.User;

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_ACTION = 1;
    private List<User> userList;
    private final OnUserClickListener listener;
    private boolean showActions = false;
    private java.util.Map<String, Boolean> adminsMap = null;

    public interface OnUserClickListener {
        void onUserClick(User user);
        void onNewGroupClick();
    }

    public UserAdapter(List<User> userList, OnUserClickListener listener) {
        this.userList = userList;
        this.listener = listener;
    }

    public void setShowActions(boolean show) {
        this.showActions = show;
        notifyDataSetChanged();
    }

    public void setAdminsMap(java.util.Map<String, Boolean> adminsMap) {
        this.adminsMap = adminsMap;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (showActions && position == 0) return VIEW_TYPE_ACTION;
        return VIEW_TYPE_USER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ACTION) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
            return new ActionViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ActionViewHolder) {
            ActionViewHolder actionHolder = (ActionViewHolder) holder;
            actionHolder.displayName.setText("New Group");
            actionHolder.username.setVisibility(View.GONE);
            actionHolder.email.setVisibility(View.GONE);
            actionHolder.avatar.setImageResource(android.R.drawable.ic_menu_add);
            actionHolder.avatar.setBackgroundResource(R.drawable.online_indicator_bg);
            actionHolder.avatar.setPadding(12, 12, 12, 12);
            actionHolder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onNewGroupClick();
            });
            return;
        }

        UserViewHolder userHolder = (UserViewHolder) holder;
        User user = userList.get(showActions ? position - 1 : position);
        userHolder.displayName.setText(user.getDisplayName());
        
        if (userHolder.adminBadge != null) {
            if (adminsMap != null && Boolean.TRUE.equals(adminsMap.get(user.getUid()))) {
                userHolder.adminBadge.setVisibility(View.VISIBLE);
            } else {
                userHolder.adminBadge.setVisibility(View.GONE);
            }
        }
        
        String myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (user.getUid() != null && user.getUid().equals(myUid)) {
            userHolder.email.setText(user.getEmail());
            userHolder.email.setVisibility(View.VISIBLE);
        } else {
            userHolder.email.setVisibility(View.GONE);
        }
        
        if (user.getUsername() != null && !user.getUsername().isEmpty()) {
            userHolder.username.setText("@" + user.getUsername());
            userHolder.username.setVisibility(View.VISIBLE);
        } else {
            userHolder.username.setVisibility(View.GONE);
        }

        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
            Glide.with(userHolder.itemView.getContext())
                    .load(user.getPhotoUrl())
                    .circleCrop()
                    .placeholder(R.mipmap.ic_launcher_round)
                    .into(userHolder.avatar);
        } else {
            userHolder.avatar.setImageResource(R.mipmap.ic_launcher_round);
        }

        userHolder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUserClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return userList.size() + (showActions ? 1 : 0);
    }

    public void updateList(List<User> newList) {
        this.userList = newList;
        notifyDataSetChanged();
    }

    static class ActionViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView displayName;
        TextView username;
        TextView email;

        public ActionViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.user_avatar);
            displayName = itemView.findViewById(R.id.user_display_name);
            username = itemView.findViewById(R.id.user_username);
            email = itemView.findViewById(R.id.user_email);
        }
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView displayName;
        TextView username;
        TextView email;
        TextView adminBadge;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.user_avatar);
            displayName = itemView.findViewById(R.id.user_display_name);
            username = itemView.findViewById(R.id.user_username);
            email = itemView.findViewById(R.id.user_email);
            adminBadge = itemView.findViewById(R.id.admin_badge);
        }
    }
}
