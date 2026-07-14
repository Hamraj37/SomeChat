package com.hamraj37.somechat.ui.slideshow;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.adapters.CallLogAdapter;
import com.hamraj37.somechat.databinding.FragmentSlideshowBinding;
import com.hamraj37.somechat.models.CallLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SlideshowFragment extends Fragment {

    private FragmentSlideshowBinding binding;
    private List<CallLog> callLogs = new ArrayList<>();
    private CallLogAdapter adapter;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSlideshowBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        setupRecyclerView();
        loadCallLogs();

        return root;
    }

    private void setupRecyclerView() {
        adapter = new CallLogAdapter(callLogs, log -> {
            Class<?> activityClass = log.isVideo() ? com.hamraj37.somechat.VideoCallActivity.class : com.hamraj37.somechat.AudioCallActivity.class;
            Intent intent = new Intent(getContext(), activityClass);
            intent.putExtra("uid", log.getOtherUserId());
            intent.putExtra("displayName", log.getOtherUserName());
            intent.putExtra("photoUrl", log.getOtherUserAvatar());
            intent.putExtra("isIncoming", false);
            startActivity(intent);
        });
        binding.rvCallLogs.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvCallLogs.setAdapter(adapter);
    }

    private void loadCallLogs() {
        String myUid = FirebaseAuth.getInstance().getUid();
        if (myUid == null) return;

        FirebaseDatabase.getInstance().getReference("call_logs").child(myUid)
                .orderByChild("timestamp")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (binding == null) return;
                        callLogs.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            CallLog log = ds.getValue(CallLog.class);
                            if (log != null) {
                                callLogs.add(log);
                            }
                        }
                        Collections.reverse(callLogs);
                        adapter.notifyDataSetChanged();
                        binding.tvEmptyCalls.setVisibility(callLogs.isEmpty() ? View.VISIBLE : View.GONE);
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
