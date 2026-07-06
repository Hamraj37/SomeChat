package com.samechat37.ui.transform;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.samechat37.ChatActivity;
import com.samechat37.R;
import com.samechat37.adapters.FriendRequestAdapter;
import com.samechat37.databinding.FragmentTransformBinding;
import com.samechat37.databinding.ItemTransformBinding;

import java.util.ArrayList;
import java.util.List;

public class TransformFragment extends Fragment {

    private FragmentTransformBinding binding;
    private TransformViewModel transformViewModel;
    private FriendRequestAdapter requestAdapter;
    private final List<String> requestList = new ArrayList<>();

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        // Use activity-scoped ViewModel so MainActivity can send search queries to it
        transformViewModel =
                new ViewModelProvider(requireActivity()).get(TransformViewModel.class);

        binding = FragmentTransformBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        RecyclerView recyclerView = binding.recyclerviewTransform;
        ListAdapter<ChatItem, TransformViewHolder> adapter = new TransformAdapter();
        recyclerView.setAdapter(adapter);
        transformViewModel.getChats().observe(getViewLifecycleOwner(), adapter::submitList);

        setupFriendRequests();

        return root;
    }

    private void setupFriendRequests() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) return;

        requestAdapter = new FriendRequestAdapter(requestList, new FriendRequestAdapter.OnRequestListener() {
            @Override
            public void onAccept(String requesterUid) {
                acceptFriendRequest(myUid, requesterUid);
            }

            @Override
            public void onDecline(String requesterUid) {
                declineFriendRequest(myUid, requesterUid);
            }
        });

        binding.requestsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.requestsRecycler.setAdapter(requestAdapter);

        FirebaseDatabase.getInstance().getReference("friendRequests").child(myUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        requestList.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            requestList.add(ds.getKey());
                        }
                        
                        if (requestList.isEmpty()) {
                            binding.friendRequestsContainer.setVisibility(View.GONE);
                        } else {
                            binding.friendRequestsContainer.setVisibility(View.VISIBLE);
                        }
                        requestAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void acceptFriendRequest(String myUid, String requesterUid) {
        // 1. Add to both users' friends list
        FirebaseDatabase.getInstance().getReference("friends").child(myUid).child(requesterUid).setValue(true)
                .addOnSuccessListener(aVoid -> {
                    FirebaseDatabase.getInstance().getReference("friends").child(requesterUid).child(myUid).setValue(true);
                    
                    // 2. Remove from requests
                    FirebaseDatabase.getInstance().getReference("friendRequests").child(myUid).child(requesterUid).removeValue();
                });
    }

    private void declineFriendRequest(String myUid, String requesterUid) {
        FirebaseDatabase.getInstance().getReference("friendRequests").child(myUid).child(requesterUid).removeValue();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class TransformAdapter extends ListAdapter<ChatItem, TransformViewHolder> {

        protected TransformAdapter() {
            super(new DiffUtil.ItemCallback<ChatItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull ChatItem oldItem, @NonNull ChatItem newItem) {
                    return oldItem.getName().equals(newItem.getName());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ChatItem oldItem, @NonNull ChatItem newItem) {
                    return oldItem.getLastMessage().equals(newItem.getLastMessage()) &&
                           oldItem.getTime().equals(newItem.getTime()) &&
                           oldItem.isOnline() == newItem.isOnline() &&
                           oldItem.getTimestamp() == newItem.getTimestamp() &&
                           oldItem.getUnreadCount() == newItem.getUnreadCount();
                }
            });
        }

        @NonNull
        @Override
        public TransformViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemTransformBinding binding = ItemTransformBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new TransformViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull TransformViewHolder holder, int position) {
            ChatItem chatItem = getItem(position);
            holder.textViewName.setText(chatItem.getName());
            holder.textViewLastMessage.setText(chatItem.getLastMessage());
            holder.textViewTime.setText(chatItem.getTime());
            
            holder.onlineIndicator.setVisibility(chatItem.isOnline() ? View.VISIBLE : View.GONE);
            
            if (chatItem.getUnreadCount() > 0) {
                holder.unreadCountBadge.setVisibility(View.VISIBLE);
                holder.unreadCountBadge.setText(String.valueOf(chatItem.getUnreadCount()));
            } else {
                holder.unreadCountBadge.setVisibility(View.GONE);
            }

            if (chatItem.getPhotoUrl() != null && !chatItem.getPhotoUrl().isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(chatItem.getPhotoUrl())
                        .circleCrop()
                        .placeholder(R.mipmap.ic_launcher_round)
                        .into(holder.imageView);
            } else {
                holder.imageView.setImageResource(R.mipmap.ic_launcher_round);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(holder.itemView.getContext(), ChatActivity.class);
                intent.putExtra("uid", chatItem.getUid());
                intent.putExtra("displayName", chatItem.getName());
                intent.putExtra("photoUrl", chatItem.getPhotoUrl());
                holder.itemView.getContext().startActivity(intent);
            });
        }
    }

    private static class TransformViewHolder extends RecyclerView.ViewHolder {

        private final ImageView imageView;
        private final TextView textViewName;
        private final TextView textViewLastMessage;
        private final TextView textViewTime;
        private final View onlineIndicator;
        private final TextView unreadCountBadge;

        public TransformViewHolder(ItemTransformBinding binding) {
            super(binding.getRoot());
            imageView = binding.imageViewItemTransform;
            textViewName = binding.textViewItemTransform;
            textViewLastMessage = binding.textViewLastMessage;
            textViewTime = binding.textViewTime;
            onlineIndicator = binding.onlineIndicator;
            unreadCountBadge = binding.unreadCountBadge;
        }
    }
}