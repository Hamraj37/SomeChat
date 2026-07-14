package com.hamraj37.somechat.utils;

import androidx.annotation.NonNull;
import com.google.firebase.database.FirebaseDatabase;
import com.hamraj37.somechat.models.CallLog;

public class CallLogManager {

    public static void logCall(String myUid, String otherUid, String otherName, String otherAvatar, boolean isVideo, boolean isIncoming, String status, long duration) {
        if (myUid == null || otherUid == null) return;

        // Duplicate prevention: check last 5 seconds for same user and type
        FirebaseDatabase.getInstance().getReference("call_logs").child(myUid)
                .orderByChild("timestamp")
                .limitToLast(1)
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (com.google.firebase.database.DataSnapshot ds : snapshot.getChildren()) {
                                CallLog lastLog = ds.getValue(CallLog.class);
                                if (lastLog != null && lastLog.getOtherUserId().equals(otherUid) &&
                                        lastLog.isVideo() == isVideo &&
                                        Math.abs(System.currentTimeMillis() - lastLog.getTimestamp()) < 5000) {
                                    // Potential duplicate, skip logging
                                    return;
                                }
                            }
                        }
                        
                        // Proceed with logging
                        performLogging(myUid, otherUid, otherName, otherAvatar, isVideo, isIncoming, status, duration);
                    }

                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                        performLogging(myUid, otherUid, otherName, otherAvatar, isVideo, isIncoming, status, duration);
                    }
                });
    }

    private static void performLogging(String myUid, String otherUid, String otherName, String otherAvatar, boolean isVideo, boolean isIncoming, String status, long duration) {
        String logId = FirebaseDatabase.getInstance().getReference("call_logs").child(myUid).push().getKey();
        if (logId == null) return;

        CallLog log = new CallLog(
                logId,
                isIncoming ? otherUid : myUid,
                isIncoming ? myUid : otherUid,
                otherUid,
                otherName,
                otherAvatar,
                isVideo,
                isIncoming,
                status,
                System.currentTimeMillis(),
                duration
        );

        FirebaseDatabase.getInstance().getReference("call_logs").child(myUid).child(logId).setValue(log);
    }
}
