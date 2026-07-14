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
import com.hamraj37.somechat.models.CallLog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CallLogAdapter extends RecyclerView.Adapter<CallLogAdapter.ViewHolder> {

    private List<CallLog> callLogs;
    private OnCallLogClickListener listener;

    public interface OnCallLogClickListener {
        void onCallLogClick(CallLog callLog);
    }

    public CallLogAdapter(List<CallLog> callLogs, OnCallLogClickListener listener) {
        this.callLogs = callLogs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_call_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CallLog log = callLogs.get(position);
        
        String name = log.getOtherUserName();
        if (name == null || name.isEmpty()) {
            holder.name.setText(R.string.unknown_user);
            // Attempt to fetch name from Firebase
            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users")
                    .child(log.getOtherUserId()).child("displayName")
                    .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                String dbName = snapshot.getValue(String.class);
                                if (dbName != null) {
                                    log.setOtherUserName(dbName);
                                    holder.name.setText(dbName);
                                }
                            }
                        }
                        @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
                    });
        } else {
            holder.name.setText(name);
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault());
        holder.time.setText(sdf.format(new Date(log.getTimestamp())));

        if (log.isIncoming()) {
            if ("missed".equals(log.getStatus()) || "rejected".equals(log.getStatus())) {
                holder.direction.setImageResource(android.R.drawable.sym_call_missed);
                holder.direction.setColorFilter(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), android.R.color.holo_red_dark));
            } else {
                holder.direction.setImageResource(android.R.drawable.sym_call_incoming);
                holder.direction.setColorFilter(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.whatsapp_green));
            }
        } else {
            holder.direction.setImageResource(android.R.drawable.sym_call_outgoing);
            holder.direction.setColorFilter(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.whatsapp_green));
        }

        if (log.isVideo()) {
            holder.callType.setImageResource(R.drawable.ic_video_call);
        } else {
            holder.callType.setImageResource(R.drawable.ic_audio_call);
        }

        if (log.getOtherUserAvatar() != null && !log.getOtherUserAvatar().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(log.getOtherUserAvatar())
                    .placeholder(R.mipmap.ic_launcher_round)
                    .circleCrop()
                    .into(holder.avatar);
        } else {
            holder.avatar.setImageResource(R.mipmap.ic_launcher_round);
            // Attempt to fetch avatar from Firebase
            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users")
                    .child(log.getOtherUserId()).child("photoUrl")
                    .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                String photoUrl = snapshot.getValue(String.class);
                                if (photoUrl != null && !photoUrl.isEmpty()) {
                                    log.setOtherUserAvatar(photoUrl);
                                    Glide.with(holder.itemView.getContext())
                                            .load(photoUrl)
                                            .placeholder(R.mipmap.ic_launcher_round)
                                            .circleCrop()
                                            .into(holder.avatar);
                                }
                            }
                        }
                        @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
                    });
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onCallLogClick(log);
        });
    }

    @Override
    public int getItemCount() {
        return callLogs.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar, direction, callType;
        TextView name, time;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.iv_avatar);
            direction = itemView.findViewById(R.id.iv_call_direction);
            callType = itemView.findViewById(R.id.iv_call_type);
            name = itemView.findViewById(R.id.tv_name);
            time = itemView.findViewById(R.id.tv_time);
        }
    }
}
