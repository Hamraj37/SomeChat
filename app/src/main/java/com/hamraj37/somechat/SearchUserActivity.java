package com.hamraj37.somechat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.adapters.UserAdapter;
import com.hamraj37.somechat.models.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchUserActivity extends BaseActivity {

    private EditText searchInput;
    private RecyclerView searchResultsRecycler;
    private TextView emptyView;
    private View emptyStateContainer;
    private UserAdapter adapter;
    private List<User> userList = new ArrayList<>();
    private Map<String, User> userMap = new HashMap<>(); // To avoid duplicates
    private boolean isForwardMode = false;
    private List<String> friendIds = new ArrayList<>();

    private final java.util.Set<String> friendIdsSet = new java.util.HashSet<>();
    private boolean friendsLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isNightMode);
        setContentView(R.layout.activity_search_user);

        isForwardMode = getIntent().getBooleanExtra("forward_message", false);

        searchInput = findViewById(R.id.search_input);
        searchResultsRecycler = findViewById(R.id.search_results_recycler);
        emptyView = findViewById(R.id.empty_view);
        emptyStateContainer = findViewById(R.id.empty_state_container);

        setupRecyclerView();
        setupSearchListener();

        loadFriendsData();
        if (isForwardMode) {
            searchInput.setHint("Search friends...");
        }

        // Handle back navigation
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadFriendsData() {
        String myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (myUid == null) return;

        adapter.setShowActions(!isForwardMode);
        emptyView.setText("Loading friends...");
        emptyStateContainer.setVisibility(View.VISIBLE);

        FirebaseDatabase.getInstance().getReference("friends").child(myUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        friendIdsSet.clear();
                        friendIds.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String friendId = ds.getKey();
                            if (friendId != null) {
                                friendIdsSet.add(friendId);
                                friendIds.add(friendId);
                            }
                        }
                        
                        boolean wasLoaded = friendsLoaded;
                        friendsLoaded = true;

                        if (!wasLoaded) {
                            if (friendIds.isEmpty()) {
                                if (isForwardMode) {
                                    emptyView.setText("No friends found. Search for users to add them.");
                                } else {
                                    emptyView.setText("Start typing to search for users");
                                }
                            } else {
                                fetchFriendDetails();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void fetchFriendDetails() {
        userMap.clear();
        if (friendIds.isEmpty()) {
            updateUI("");
            return;
        }
        final int total = friendIds.size();
        final int[] loaded = {0};

        for (String uid : friendIds) {
            FirebaseDatabase.getInstance().getReference("users").child(uid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            User user = snapshot.getValue(User.class);
                            if (user != null) {
                                userMap.put(user.getUid(), user);
                            }
                            loaded[0]++;
                            if (loaded[0] == total) {
                                updateUI("");
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            loaded[0]++;
                            if (loaded[0] == total) {
                                updateUI("");
                            }
                        }
                    });
        }
    }

    private void setupRecyclerView() {
        adapter = new UserAdapter(userList, new UserAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(User user) {
                String myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
                
                if (user.getUid().equals(myUid) || !friendIdsSet.contains(user.getUid())) {
                    Intent intent = new Intent(SearchUserActivity.this, ProfileInfoActivity.class);
                    intent.putExtra("uid", user.getUid());
                    startActivity(intent);
                    return;
                }

                Intent intent = new Intent(SearchUserActivity.this, ChatActivity.class);
                intent.putExtra("uid", user.getUid());
                intent.putExtra("displayName", user.getDisplayName());
                intent.putExtra("photoUrl", user.getPhotoUrl());
                
                if (isForwardMode) {
                    if (getIntent().hasExtra("forward_list")) {
                        intent.putExtra("forward_list", getIntent().getSerializableExtra("forward_list"));
                    } else {
                        intent.putExtra("forward_content", getIntent().getStringExtra("content"));
                        intent.putExtra("forward_type", getIntent().getStringExtra("type"));
                        intent.putExtra("forward_duration", getIntent().getIntExtra("duration", 0));
                    }
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                }
                
                startActivity(intent);
                if (isForwardMode) {
                    finish();
                }
            }

            @Override
            public void onNewGroupClick() {
                // TODO: Handle new group creation
                Toast.makeText(SearchUserActivity.this, "Create New Group", Toast.LENGTH_SHORT).show();
            }
        });
        searchResultsRecycler.setLayoutManager(new LinearLayoutManager(this));
        searchResultsRecycler.setAdapter(adapter);
    }

    private void setupSearchListener() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (!query.isEmpty()) {
                    searchUsers(query);
                } else {
                    adapter.setShowActions(!isForwardMode);
                    fetchFriendDetails();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void searchUsers(String queryText) {
        adapter.setShowActions(false);
        userMap.clear();
        String lowerQuery = queryText.toLowerCase();

        if (queryText.startsWith("@") && queryText.length() > 1) {
            // Strip @ and search specifically by username
            String usernameQuery = lowerQuery.substring(1);
            performQuery("username", usernameQuery, queryText);
            return;
        }

        // Query variations to handle case-sensitivity for existing users
        // 1. By searchName (Case-insensitive for new users)
        performQuery("searchName", lowerQuery, queryText);

        // 2. By Display Name (Original case)
        performQuery("displayName", queryText, queryText);

        // 3. By Display Name (Capitalized - common for names like "Hamraj")
        if (!queryText.isEmpty()) {
            String capitalized = queryText.substring(0, 1).toUpperCase() + 
                                (queryText.length() > 1 ? queryText.substring(1).toLowerCase() : "");
            if (!capitalized.equals(queryText)) {
                performQuery("displayName", capitalized, queryText);
            }
        }

        // 4. By Username and Email (usually lowercase)
        performQuery("username", lowerQuery, queryText);
        performQuery("email", lowerQuery, queryText);
    }

    private void performQuery(String field, String value, String originalInput) {
        String myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        Query query = FirebaseDatabase.getInstance().getReference("users")
                .orderByChild(field)
                .startAt(value)
                .endAt(value + "\uf8ff")
                .limitToFirst(20);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    User user = dataSnapshot.getValue(User.class);
                    if (user != null && user.getUid() != null) {
                        // Don't show current user in search results
                        if (user.getUid().equals(myUid)) continue;

                        if (isForwardMode) {
                            if (friendIdsSet.contains(user.getUid())) {
                                userMap.put(user.getUid(), user);
                            }
                        } else {
                            userMap.put(user.getUid(), user);
                        }
                    }
                }
                updateUI(originalInput);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private synchronized void updateUI(String currentInput) {
        // Double check if input has changed while query was running
        if (!searchInput.getText().toString().trim().equals(currentInput)) return;

        userList.clear();
        userList.addAll(userMap.values());

        // Sort results: prioritize exact matches and prefix matches
        String lowerQuery = currentInput.toLowerCase();
        userList.sort((u1, u2) -> {
            int score1 = calculateMatchScore(u1, lowerQuery);
            int score2 = calculateMatchScore(u2, lowerQuery);

            if (score1 != score2) return score2 - score1; // Higher score comes first

            // If scores are equal, sort alphabetically by display name
            String name1 = u1.getDisplayName() != null ? u1.getDisplayName() : "";
            String name2 = u2.getDisplayName() != null ? u2.getDisplayName() : "";
            return name1.compareToIgnoreCase(name2);
        });

        adapter.updateList(userList);

        if (userList.isEmpty()) {
            emptyStateContainer.setVisibility(View.VISIBLE);
            if (currentInput.isEmpty()) {
                emptyView.setText("Start typing to search for users");
            } else {
                emptyView.setText("No users found matching \"" + currentInput + "\"");
            }
        } else {
            emptyStateContainer.setVisibility(View.GONE);
        }
    }

    private int calculateMatchScore(User user, String lowerQuery) {
        String dName = user.getDisplayName() != null ? user.getDisplayName().toLowerCase() : "";
        String uName = user.getUsername() != null ? user.getUsername().toLowerCase() : "";
        String email = user.getEmail() != null ? user.getEmail().toLowerCase() : "";

        String effectiveQuery = lowerQuery;
        if (lowerQuery.startsWith("@")) {
            effectiveQuery = lowerQuery.substring(1);
        }

        // Exact matches get the highest score
        if (dName.equals(effectiveQuery) || uName.equals(effectiveQuery)) return 100;

        // Prefix matches get a high score
        if (dName.startsWith(effectiveQuery) || uName.startsWith(effectiveQuery)) return 50;
        
        // Email prefix matches
        if (email.startsWith(effectiveQuery)) return 40;

        // Contains (partial matches)
        if (dName.contains(effectiveQuery) || uName.contains(effectiveQuery)) return 10;

        return 0;
    }
}
