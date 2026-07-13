package com.hamraj37.somechat.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

public class PresenceManager {

    public static void setUserOnline() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
        DatabaseReference connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");

        connectedRef.addValueEventListener(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                boolean connected = snapshot.getValue(Boolean.class) != null && snapshot.getValue(Boolean.class);
                if (connected) {
                    Map<String, Object> onlineStatus = new HashMap<>();
                    onlineStatus.put("online", true);
                    onlineStatus.put("lastSeen", ServerValue.TIMESTAMP);

                    userRef.updateChildren(onlineStatus);

                    // When this device disconnects, update status to offline
                    userRef.child("online").onDisconnect().setValue(false);
                    userRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP);
                }
            }

            @Override
            public void onCancelled(com.google.firebase.database.DatabaseError error) {}
        });
    }

    public static void setUserOffline() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
        Map<String, Object> offlineStatus = new HashMap<>();
        offlineStatus.put("online", false);
        offlineStatus.put("lastSeen", ServerValue.TIMESTAMP);

        userRef.updateChildren(offlineStatus);
    }
}
