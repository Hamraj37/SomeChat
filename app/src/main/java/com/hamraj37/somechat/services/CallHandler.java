package com.hamraj37.somechat.services;

import android.content.Context;
import android.content.Intent;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.HashSet;
import java.util.Set;

public class CallHandler {
    private final Context context;
    private final CallListener listener;
    private ValueEventListener firebaseListener;
    private final Set<String> activeCallIds = new HashSet<>();

    public interface CallListener {
        void onIncomingCall(String id, String name, String avatar, boolean isVideo);
        void onCallRemoved(String id);
    }

    public CallHandler(Context context, CallListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void startListening(Boolean filterVideo) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        DatabaseReference callsRef = FirebaseDatabase.getInstance().getReference("calls").child(uid);
        firebaseListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Set<String> currentCallers = new HashSet<>();
                for (DataSnapshot callerSnapshot : snapshot.getChildren()) {
                    String callerId = callerSnapshot.getKey();
                    if (callerId == null) continue;
                    
                    String status = callerSnapshot.child("status").getValue(String.class);
                    if (status == null) continue;

                    currentCallers.add(callerId);

                    if ("calling".equals(status)) {
                        Boolean isVideo = callerSnapshot.child("isVideo").getValue(Boolean.class);
                        boolean isVideoBool = isVideo != null && isVideo;

                        if (filterVideo != null && filterVideo != isVideoBool) continue;

                        callerSnapshot.getRef().child("status").setValue("ringing");
                        String name = callerSnapshot.child("callerName").getValue(String.class);
                        String avatar = callerSnapshot.child("callerAvatar").getValue(String.class);
                        
                        activeCallIds.add(callerId);
                        listener.onIncomingCall(callerId, name, avatar, isVideoBool);
                    } else if ("ended".equals(status) || "rejected".equals(status)) {
                        removeCall(callerId);
                    }
                }

                for (String id : new HashSet<>(activeCallIds)) {
                    if (!currentCallers.contains(id)) {
                        removeCall(id);
                    }
                }
            }

            @Override public void onCancelled(DatabaseError error) {}
        };
        callsRef.addValueEventListener(firebaseListener);
    }

    private void removeCall(String id) {
        if (activeCallIds.remove(id)) {
            listener.onCallRemoved(id);
        }
    }

    public void stopListening() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null && firebaseListener != null) {
            FirebaseDatabase.getInstance().getReference("calls").child(uid).removeEventListener(firebaseListener);
        }
    }
}
