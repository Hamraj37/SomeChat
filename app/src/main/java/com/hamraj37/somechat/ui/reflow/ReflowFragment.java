package com.hamraj37.somechat.ui.reflow;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.R;
import com.hamraj37.somechat.adapters.StatusAdapter;
import com.hamraj37.somechat.databinding.FragmentReflowBinding;
import com.hamraj37.somechat.models.Status;
import com.hamraj37.somechat.utils.GitHubStorage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReflowFragment extends Fragment {

    private FragmentReflowBinding binding;
    private StatusAdapter adapter;
    private List<Status> statusList = new ArrayList<>();
    private String currentUserId;
    private Status myStatus = null;

    private final androidx.activity.result.ActivityResultLauncher<String> pickStatusLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            uploadStatus(uri);
                        }
                    });

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentReflowBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        currentUserId = FirebaseAuth.getInstance().getUid();

        setupRecyclerView();
        loadStatuses();

        binding.myStatusContainer.setOnClickListener(v -> {
            if (myStatus != null) {
                Intent intent = new Intent(getContext(), com.hamraj37.somechat.ViewStatusActivity.class);
                intent.putExtra("status", myStatus);
                startActivity(intent);
            } else {
                pickStatusLauncher.launch("image/*");
            }
        });

        binding.fabAddStatus.setOnClickListener(v -> pickStatusLauncher.launch("image/*"));

        binding.fabTextStatus.setOnClickListener(v -> showTextStatusDialog());

        return root;
    }

    private void setupRecyclerView() {
        adapter = new StatusAdapter(statusList, status -> {
            Intent intent = new Intent(getContext(), com.hamraj37.somechat.ViewStatusActivity.class);
            intent.putExtra("status", status);
            startActivity(intent);
        });
        binding.statusRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.statusRecycler.setAdapter(adapter);
    }

    private void loadStatuses() {
        // 1. Get friends list first
        FirebaseDatabase.getInstance().getReference("friends").child(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot friendSnapshot) {
                        java.util.Set<String> friendIds = new java.util.HashSet<>();
                        for (DataSnapshot ds : friendSnapshot.getChildren()) {
                            friendIds.add(ds.getKey());
                        }
                        
                        // 2. Load statuses
                        DatabaseReference statusRef = FirebaseDatabase.getInstance().getReference("statuses");
                        statusRef.addValueEventListener(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                statusList.clear();
                                myStatus = null;
                                long yesterday = System.currentTimeMillis() - (24 * 60 * 60 * 1000);

                                for (DataSnapshot ds : snapshot.getChildren()) {
                                    Status status = ds.getValue(Status.class);
                                    if (status != null) {
                                        boolean isMe = status.getUserId().equals(currentUserId);
                                        boolean isFriend = friendIds.contains(status.getUserId());
                                        
                                        if (isMe || isFriend) {
                                            // Filter out old status items
                                            List<Status.StatusItem> validItems = new ArrayList<>();
                                            if (status.getItems() != null) {
                                                for (Status.StatusItem item : status.getItems()) {
                                                    if (item.getTimestamp() > yesterday) {
                                                        validItems.add(item);
                                                    }
                                                }
                                            }

                                            if (!validItems.isEmpty()) {
                                                status.setItems(validItems);
                                                if (isMe) {
                                                    myStatus = status;
                                                } else {
                                                    statusList.add(status);
                                                }
                                            }
                                        }
                                    }
                                }
                                updateMyStatusUI();
                                adapter.notifyDataSetChanged();
                                binding.noStatusText.setVisibility(statusList.isEmpty() ? View.VISIBLE : View.GONE);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void updateMyStatusUI() {
        if (myStatus != null && !myStatus.getItems().isEmpty()) {
            Status.StatusItem lastItem = myStatus.getItems().get(myStatus.getItems().size() - 1);
            Glide.with(this).load(lastItem.getMediaUrl()).circleCrop().into(binding.myStatusImage);
            
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            binding.myStatusTime.setText(sdf.format(new Date(lastItem.getTimestamp())));
            binding.addStatusIcon.setVisibility(View.GONE);
        } else {
            binding.myStatusImage.setImageResource(R.mipmap.ic_launcher_round);
            binding.myStatusTime.setText("Tap to add status update");
            binding.addStatusIcon.setVisibility(View.VISIBLE);
        }
    }

    private void showTextStatusDialog() {
        android.widget.EditText input = new android.widget.EditText(getContext());
        input.setHint("Type a status...");
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(getContext());
        container.addView(input);
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) input.getLayoutParams();
        lp.setMargins(padding, padding / 2, padding, padding / 2);
        input.setLayoutParams(lp);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
                .setTitle("Text Status")
                .setView(container)
                .setPositiveButton("Post", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (!android.text.TextUtils.isEmpty(text)) {
                        saveTextStatus(text);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveTextStatus(String text) {
        saveStatusToFirebase(null, "text", text);
    }

    private void uploadStatus(Uri uri) {
        Toast.makeText(getContext(), "Uploading status...", Toast.LENGTH_SHORT).show();
        try {
            java.io.InputStream is = getContext().getContentResolver().openInputStream(uri);
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            is.close();

            String fileName = currentUserId + "_" + System.currentTimeMillis() + ".jpg";
            GitHubStorage.uploadBytes(bytes, "statuses", fileName, new GitHubStorage.UploadCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    saveStatusToFirebase(downloadUrl, "image", "");
                }

                @Override
                public void onProgress(int progress) {}

                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(getContext(), "Failed to upload status", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveStatusToFirebase(String mediaUrl, String type, String caption) {
        DatabaseReference myStatusRef = FirebaseDatabase.getInstance().getReference("statuses").child(currentUserId);
        
        // 1. Get my info
        FirebaseDatabase.getInstance().getReference("users").child(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String name = snapshot.child("displayName").getValue(String.class);
                        String profile = snapshot.child("photoUrl").getValue(String.class);
                        
                        // 2. Prepare status item
                        Status.StatusItem newItem = new Status.StatusItem(
                                String.valueOf(System.currentTimeMillis()),
                                mediaUrl,
                                type,
                                System.currentTimeMillis(),
                                caption
                        );

                        // 3. Update or create status
                        if (myStatus == null) {
                            List<Status.StatusItem> items = new ArrayList<>();
                            items.add(newItem);
                            Status s = new Status(currentUserId, currentUserId, name, profile, System.currentTimeMillis(), items);
                            myStatusRef.setValue(s);
                        } else {
                            List<Status.StatusItem> items = myStatus.getItems();
                            if (items == null) items = new ArrayList<>();
                            items.add(newItem);
                            
                            java.util.Map<String, Object> updates = new java.util.HashMap<>();
                            updates.put("items", items);
                            updates.put("lastUpdated", System.currentTimeMillis());
                            myStatusRef.updateChildren(updates);
                        }
                        Toast.makeText(getContext(), "Status updated", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}