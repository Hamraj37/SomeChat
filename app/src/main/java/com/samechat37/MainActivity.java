package com.samechat37;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.appcompat.app.AppCompatActivity;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.samechat37.databinding.ActivityMainBinding;
import com.samechat37.ui.transform.TransformViewModel;
import com.samechat37.utils.PresenceManager;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends BaseActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private TransformViewModel transformViewModel;
    private ActivityMainBinding binding;
    private boolean isSyncing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isNightMode);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        transformViewModel = new ViewModelProvider(this).get(TransformViewModel.class);

        setupCustomToolbar();
        setupBackPressedHandling();
        
        if (binding.appBarMain.contentMain.fab != null) {
            binding.appBarMain.contentMain.fab.setOnClickListener(view -> {
                Intent intent = new Intent(this, SearchUserActivity.class);
                startActivity(intent);
            });
        }
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        assert navHostFragment != null;
        NavController navController = navHostFragment.getNavController();

        BottomNavigationView bottomNavigationView = binding.appBarMain.contentMain.bottomNavView;
        if (bottomNavigationView != null) {
            mAppBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.nav_chats, R.id.nav_status, R.id.nav_calls)
                    .build();
            NavigationUI.setupWithNavController(bottomNavigationView, navController);
        }

        setupNavHeader();
        setupUnreadCountBadge();
        PresenceManager.setUserOnline();
        checkAndRequestPermissions();
        startCallService();
    }

    private void setupUnreadCountBadge() {
        BottomNavigationView bottomNavigationView = binding.appBarMain.contentMain.bottomNavView;
        if (bottomNavigationView == null) return;

        transformViewModel.getAllChats().observe(this, chatItems -> {
            int totalUnread = 0;
            if (chatItems != null) {
                for (com.samechat37.ui.transform.ChatItem item : chatItems) {
                    totalUnread += item.getUnreadCount();
                }
            }

            com.google.android.material.badge.BadgeDrawable badge = bottomNavigationView.getOrCreateBadge(R.id.nav_chats);
            if (totalUnread > 0) {
                badge.setVisible(true);
                badge.setNumber(totalUnread);
                badge.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.whatsapp_light_green));
                badge.setBadgeTextColor(android.graphics.Color.WHITE);
            } else {
                badge.setVisible(false);
            }
        });
    }

    private void checkAndRequestPermissions() {
        java.util.List<String> permissionsNeeded = new java.util.ArrayList<>();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.MANAGE_OWN_CALLS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.MANAGE_OWN_CALLS);
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.FOREGROUND_SERVICE_PHONE_CALL) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.FOREGROUND_SERVICE_PHONE_CALL);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), 101);
        } else {
            // Service will be started via FCM when needed
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            // Permissions handled, service will be started via FCM when needed
        }
    }

    private void startCallService() {
        Intent serviceIntent = new Intent(this, com.samechat37.services.CallService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void setupNavHeader() {
        NavigationView navigationView = null;
        try {
            navigationView = findViewById(R.id.nav_view);
        } catch (Exception ignored) {}

        if (navigationView != null && navigationView.getHeaderCount() > 0) {
            View headerView = navigationView.getHeaderView(0);
            headerView.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProfileInfoActivity.class);
                startActivity(intent);
            });
            TextView navUsername = headerView.findViewById(R.id.nav_header_title);
            TextView navHandle = headerView.findViewById(R.id.nav_header_username);
            TextView navEmail = headerView.findViewById(R.id.nav_header_subtitle);
            ImageView navImage = headerView.findViewById(R.id.nav_header_imageView);

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                String name = user.getDisplayName();
                String email = user.getEmail();

                if (name != null) navUsername.setText(name);
                if (email != null) navEmail.setText(email);

                if (user.getPhotoUrl() != null) {
                    Glide.with(this).load(user.getPhotoUrl()).into(navImage);
                }

                // Fetch real-time details from RTDB
                FirebaseDatabase.getInstance().getReference("users").child(user.getUid())
                        .addValueEventListener(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (snapshot.exists()) {
                                    String dbName = snapshot.child("displayName").getValue(String.class);
                                    String dbHandle = snapshot.child("username").getValue(String.class);
                                    String dbPhoto = snapshot.child("photoUrl").getValue(String.class);

                                    if (dbName != null) navUsername.setText(dbName);
                                    if (dbHandle != null) {
                                        navHandle.setText("@" + dbHandle);
                                        navHandle.setVisibility(View.VISIBLE);
                                    } else {
                                        navHandle.setVisibility(View.GONE);
                                        // Handle missing username: generate and save one
                                        if (!isSyncing) {
                                            String baseHandle = (email != null && email.contains("@")) ? email.split("@")[0] : "user";
                                            findAndSyncUniqueUsername(user, baseHandle);
                                        }
                                    }
                                    if (dbPhoto != null) {
                                        Glide.with(MainActivity.this).load(dbPhoto).into(navImage);
                                    }
                                } else {
                                    // User doc doesn't exist, create it
                                    if (!isSyncing) {
                                        String baseHandle = (email != null && email.contains("@")) ? email.split("@")[0] : "user";
                                        findAndSyncUniqueUsername(user, baseHandle);
                                    }
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
            }
        }
    }

    private void findAndSyncUniqueUsername(FirebaseUser user, String baseHandle) {
        isSyncing = true;
        // Sanitize baseHandle: remove characters not allowed in RTDB keys (., $, #, [, ], /)
        String sanitizedHandle = baseHandle.replaceAll("[^a-zA-Z0-9]", "");
        if (sanitizedHandle.isEmpty()) sanitizedHandle = "user";

        checkUsernameUniqueness(sanitizedHandle, 0, uniqueHandle -> {
            syncUserToDatabases(user, uniqueHandle);
        });
    }

    private interface OnUsernameResolvedListener {
        void onResolved(String username);
    }

    private void checkUsernameUniqueness(String baseHandle, int suffix, OnUsernameResolvedListener listener) {
        String currentHandle = suffix == 0 ? baseHandle : baseHandle + suffix;
        FirebaseDatabase.getInstance().getReference("usernames").child(currentHandle.toLowerCase())
                .get().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DataSnapshot snapshot = task.getResult();
                        if (snapshot.exists() && !FirebaseAuth.getInstance().getUid().equals(snapshot.getValue(String.class))) {
                            checkUsernameUniqueness(baseHandle, suffix + 1, listener);
                        } else {
                            listener.onResolved(currentHandle);
                        }
                    } else {
                        listener.onResolved(currentHandle);
                    }
                });
    }

    private void syncUserToDatabases(FirebaseUser firebaseUser, String handle) {
        String uid = firebaseUser.getUid();
        String email = firebaseUser.getEmail();
        String displayName = firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : handle;
        String photoUrl = firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : null;

        FirebaseDatabase rtdb = FirebaseDatabase.getInstance();
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid", uid);
        userData.put("email", email);
        userData.put("username", handle);
        userData.put("displayName", displayName);
        userData.put("photoUrl", photoUrl);

        rtdb.getReference("users").child(uid).updateChildren(userData).addOnCompleteListener(task -> {
            isSyncing = false;
        });
        rtdb.getReference("usernames").child(handle.toLowerCase()).setValue(uid);
    }

    private void setupCustomToolbar() {
        // Handle Search (Sub-header)
        binding.appBarMain.searchViewSubHeader.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                transformViewModel.filter(newText);
                return true;
            }
        });

        // Handle More (3-dot)
        binding.appBarMain.actionMoreCustom.setOnClickListener(v -> {
            androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, v);
            popup.getMenuInflater().inflate(R.menu.overflow, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.nav_settings) {
                    NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
                    navController.navigate(R.id.nav_settings);
                    return true;
                } else if (item.getItemId() == R.id.nav_profile) {
                    Intent intent = new Intent(this, ProfileInfoActivity.class);
                    startActivity(intent);
                    return true;
                } else if (item.getItemId() == R.id.action_logout) {
                    logout();
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    private void logout() {
        stopService(new Intent(this, com.samechat37.services.CallService.class));
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupBackPressedHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                SearchView searchView = binding.appBarMain.searchViewSubHeader;
                if (!searchView.getQuery().toString().isEmpty()) {
                    searchView.setQuery("", false);
                    searchView.clearFocus();
                } else if (searchView.hasFocus()) {
                    searchView.clearFocus();
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
