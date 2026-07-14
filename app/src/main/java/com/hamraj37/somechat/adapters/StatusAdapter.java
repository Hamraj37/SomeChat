package com.hamraj37.somechat.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.hamraj37.somechat.R;
import com.hamraj37.somechat.models.Status;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatusAdapter extends RecyclerView.Adapter<StatusAdapter.StatusViewHolder> {

    private List<Status> statusList;
    private OnStatusClickListener listener;

    public interface OnStatusClickListener {
        void onStatusClick(Status status);
    }

    public StatusAdapter(List<Status> statusList, OnStatusClickListener listener) {
        this.statusList = statusList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StatusViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new StatusViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_status, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull StatusViewHolder holder, int position) {
        Status status = statusList.get(position);
        holder.userName.setText(status.getUserName());
        holder.time.setText(formatTime(status.getLastUpdated()));

        if (status.getProfilePic() != null && !status.getProfilePic().isEmpty()) {
            Glide.with(holder.itemView).load(status.getProfilePic()).circleCrop().into(holder.userImage);
        } else {
            holder.userImage.setImageResource(R.mipmap.ic_launcher_round);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onStatusClick(status);
        });
    }

    @Override
    public int getItemCount() {
        return statusList.size();
    }

    private String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    static class StatusViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView userImage;
        TextView userName, time;

        public StatusViewHolder(@NonNull View itemView) {
            super(itemView);
            userImage = itemView.findViewById(R.id.status_user_image);
            userName = itemView.findViewById(R.id.status_user_name);
            time = itemView.findViewById(R.id.status_time);
        }
    }
}
