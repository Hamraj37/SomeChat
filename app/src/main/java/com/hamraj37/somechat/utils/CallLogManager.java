package com.hamraj37.somechat.utils;

import com.google.firebase.database.FirebaseDatabase;
import com.hamraj37.somechat.models.CallLog;

public class CallLogManager {

    public static void logCall(String myUid, String otherUid, String otherName, String otherAvatar, boolean isVideo, boolean isIncoming, String status, long duration) {
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
