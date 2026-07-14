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
                pickStatusLauncher.launch("*/*");
            }
        });

        binding.fabAddStatus.setOnClickListener(v -> pickStatusLauncher.launch("*/*"));

        binding.fabTextStatus.setOnClickListener(v -> showTextStatusDialog());

        return root;
    }

    private void setupRecyclerView() {
        adapter = new StatusAdapter(statusList, currentUserId, status -> {
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
                                                // Create a copy of the status object for display in the list
                                                // so we don't modify the original one which might be needed in full
                                                Status displayStatus = new Status(
                                                        status.getUserId(),
                                                        status.getUserId(),
                                                        status.getUserName(),
                                                        status.getProfilePic(),
                                                        status.getLastUpdated(),
                                                        validItems
                                                );
                                                displayStatus.setStatusId(status.getStatusId());

                                                if (isMe) {
                                                    myStatus = displayStatus;
                                                } else {
                                                    statusList.add(displayStatus);
                                                }
                                            }
                                        }
                                    }
                                }

                                // Sort statusList: Unseen first, then by timestamp
                                statusList.sort((s1, s2) -> {
                                    boolean s1Unseen = hasUnseenItems(s1);
                                    boolean s2Unseen = hasUnseenItems(s2);

                                    if (s1Unseen != s2Unseen) {
                                        return s1Unseen ? -1 : 1;
                                    }
                                    return Long.compare(s2.getLastUpdated(), s1.getLastUpdated());
                                });

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

    private boolean hasUnseenItems(Status status) {
        if (status.getItems() == null) return false;
        for (Status.StatusItem item : status.getItems()) {
            if (item.getViews() == null || !item.getViews().containsKey(currentUserId)) {
                return true;
            }
        }
        return false;
    }

    private void updateMyStatusUI() {
        if (myStatus != null && !myStatus.getItems().isEmpty()) {
            List<Status.StatusItem> sortedItems = new ArrayList<>(myStatus.getItems());
            sortedItems.sort((i1, i2) -> Long.compare(i1.getTimestamp(), i2.getTimestamp()));
            
            Status.StatusItem lastItem = sortedItems.get(sortedItems.size() - 1);
            if (getContext() != null) {
                Glide.with(this).load(lastItem.getMediaUrl()).circleCrop().into(binding.myStatusImage);
            }
            
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            binding.myStatusTime.setText(sdf.format(new Date(lastItem.getTimestamp())));
            binding.addStatusIcon.setVisibility(View.GONE);

            // Set ring color based on unseen
            if (hasUnseenItems(myStatus)) {
                binding.myStatusImage.setStrokeColorResource(R.color.whatsapp_green);
                binding.myStatusImage.setStrokeWidth(4);
            } else {
                binding.myStatusImage.setStrokeColorResource(android.R.color.darker_gray);
                binding.myStatusImage.setStrokeWidth(1);
            }
        } else {
            binding.myStatusImage.setImageResource(R.mipmap.ic_launcher_round);
            binding.myStatusTime.setText("Tap to add status update");
            binding.addStatusIcon.setVisibility(View.VISIBLE);
            binding.myStatusImage.setStrokeWidth(0);
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
        String mimeType = getContext().getContentResolver().getType(uri);
        boolean isVideo = mimeType != null && mimeType.startsWith("video");
        
        if (isVideo) {
            try (android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever()) {
                retriever.setDataSource(getContext(), uri);
                String time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (time != null) {
                    long durationMs = Long.parseLong(time);
                    if (durationMs > 30000) { // 30 seconds
                        Toast.makeText(getContext(), "Video must be 30 seconds or shorter", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        String type = isVideo ? "video" : "image";
        String extension = isVideo ? ".mp4" : ".jpg";

        com.google.android.material.progressindicator.LinearProgressIndicator progressIndicator = new com.google.android.material.progressindicator.LinearProgressIndicator(getContext());
        progressIndicator.setIndeterminate(false);
        progressIndicator.setMax(100);
        
        androidx.appcompat.app.AlertDialog progressDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
                .setTitle("Uploading " + type)
                .setMessage("Please wait...")
                .setView(progressIndicator)
                .setCancelable(false)
                .show();

        try {
            java.io.InputStream is = getContext().getContentResolver().openInputStream(uri);
            if (is == null) {
                progressDialog.dismiss();
                return;
            }
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            is.close();

            String fileName = currentUserId + "_" + System.currentTimeMillis() + extension;
            GitHubStorage.uploadBytes(bytes, "statuses", fileName, new GitHubStorage.UploadCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    progressDialog.dismiss();
                    saveStatusToFirebase(downloadUrl, type, "");
                }

                @Override
                public void onProgress(int progress) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progressIndicator.setProgress(progress);
                            progressDialog.setMessage("Uploading: " + progress + "%");
                        });
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    progressDialog.dismiss();
                    Toast.makeText(getContext(), "Failed to upload status", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            progressDialog.dismiss();
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