package com.hamraj37.somechat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.adapters.UserAdapter;
import com.hamraj37.somechat.models.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StatusInsightsBottomSheet extends BottomSheetDialogFragment {

    private Map<String, Long> views;
    private Map<String, Boolean> likes;
    private List<User> userList = new ArrayList<>();
    private UserAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyText;
    private int currentTab = 0; // 0 for Views, 1 for Likes

    public static StatusInsightsBottomSheet newInstance(Map<String, Long> views, Map<String, Boolean> likes) {
        StatusInsightsBottomSheet fragment = new StatusInsightsBottomSheet();
        fragment.views = views;
        fragment.likes = likes;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_status_insights, container, false);

        TabLayout tabLayout = view.findViewById(R.id.insight_tabs);
        recyclerView = view.findViewById(R.id.insights_recycler);
        emptyText = view.findViewById(R.id.empty_insight_text);

        adapter = new UserAdapter(userList, new UserAdapter.OnUserClickListener() {
            @Override public void onUserClick(User user) {}
            @Override public void onNewGroupClick() {}
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                loadUsers();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadUsers();
        return view;
    }

    private void loadUsers() {
        userList.clear();
        adapter.notifyDataSetChanged();
        
        Set<String> uids = (currentTab == 0) ? (views != null ? views.keySet() : null) : (likes != null ? likes.keySet() : null);
        
        if (uids == null || uids.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText(currentTab == 0 ? "No views yet" : "No likes yet");
            return;
        }

        emptyText.setVisibility(View.GONE);
        final int total = uids.size();
        final int[] count = {0};

        for (String uid : uids) {
            FirebaseDatabase.getInstance().getReference("users").child(uid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            User user = snapshot.getValue(User.class);
                            if (user != null) {
                                userList.add(user);
                            }
                            count[0]++;
                            if (count[0] == total) {
                                adapter.notifyDataSetChanged();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            count[0]++;
                        }
                    });
        }
    }
}
