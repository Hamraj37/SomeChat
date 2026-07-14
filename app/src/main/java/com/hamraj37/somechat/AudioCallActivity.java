package com.hamraj37.somechat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.hamraj37.somechat.utils.WebRTCClient;

import com.hamraj37.somechat.services.CallService;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AudioCallActivity extends BaseActivity {

    private String receiverId, receiverName, receiverAvatar;
    private String senderId;
    private boolean isIncoming;
    private DatabaseReference callRef;
    private ValueEventListener callListener;

    private TextView callStatus, callTimer;
    private FloatingActionButton btnAccept, btnDecline, btnMute, btnSpeaker;

    private WebRTCClient webRTCClient;
    private static final int PERMISSION_REQUEST_CODE = 102;
    private final List<String> addedIceCandidates = new ArrayList<>();

    private long startTime = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private boolean isMuted = false;
    private boolean isSpeakerOn = false;
    private boolean isConnected = false;
    private Ringtone ringtone;
    private PowerManager.WakeLock proximityWakeLock;
    private boolean isLogged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isNightMode);
        
        setContentView(R.layout.activity_audio_call);

        receiverId = getIntent().getStringExtra("uid");
        receiverName = getIntent().getStringExtra("displayName");
        receiverAvatar = getIntent().getStringExtra("photoUrl");
        isIncoming = getIntent().getBooleanExtra("isIncoming", false);
        senderId = FirebaseAuth.getInstance().getUid();

        initViews();
        checkPermissionsAndInit();
        setupAudioRouting();
        setupProximitySensor();
        if (isIncoming) {
            startRingtone();
        }
    }

    private void setupProximitySensor() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null && powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, getLocalClassName() + ":ProximityLock");
        }
    }

    private void updateProximitySensor(boolean enable) {
        if (proximityWakeLock == null) return;
        
        if (enable && !isSpeakerOn && isConnected) {
            if (!proximityWakeLock.isHeld()) {
                proximityWakeLock.acquire(10 * 60 * 1000L /*10 minutes*/);
            }
        } else {
            if (proximityWakeLock.isHeld()) {
                proximityWakeLock.release();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (isConnected) {
            minimizeCall();
        } else {
            endCall();
        }
    }

    private void minimizeCall() {
        CallService.isCallActive = true;
        CallService.activeCallId = receiverId;
        CallService.activeCallName = receiverName;
        CallService.activeCallAvatar = receiverAvatar;
        CallService.isActiveCallVideo = false;
        CallService.callStartTime = startTime;
        
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    private void setupAudioRouting() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(isSpeakerOn);
        }
    }

    private void checkPermissionsAndInit() {
        String[] permissions = {Manifest.permission.RECORD_AUDIO};

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            initWebRTC();
            setupFirebase();
        } else {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initWebRTC();
                setupFirebase();
            } else {
                Toast.makeText(this, "Microphone permission required for audio calling", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void initViews() {
        ImageView avatar = findViewById(R.id.call_avatar);
        TextView name = findViewById(R.id.call_name);
        callStatus = findViewById(R.id.call_status);
        callTimer = findViewById(R.id.call_timer);
        btnAccept = findViewById(R.id.btn_accept);
        btnDecline = findViewById(R.id.btn_decline);
        btnMute = findViewById(R.id.btn_mute);
        btnSpeaker = findViewById(R.id.btn_speaker);

        name.setText(receiverName);
        if (receiverAvatar != null && !receiverAvatar.isEmpty()) {
            Glide.with(this).load(receiverAvatar).circleCrop().into(avatar);
        }

        if (isIncoming) {
            callStatus.setText("Incoming Audio Call...");
            btnAccept.setVisibility(View.VISIBLE);
            findViewById(R.id.controls_container).setVisibility(View.GONE);
        } else {
            callStatus.setText("Calling...");
            btnAccept.setVisibility(View.GONE);
            findViewById(R.id.controls_container).setVisibility(View.VISIBLE);
        }

        btnAccept.setOnClickListener(v -> acceptCall());
        btnDecline.setOnClickListener(v -> endCall());
        btnMute.setOnClickListener(v -> toggleMute());
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
    }

    private void toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(isSpeakerOn);
        }
        btnSpeaker.setAlpha(isSpeakerOn ? 1.0f : 0.5f);
        updateProximitySensor(true);
    }

    private void toggleMute() {
        isMuted = !isMuted;
        if (webRTCClient != null) {
            webRTCClient.setAudioEnabled(!isMuted);
        }
        btnMute.setAlpha(isMuted ? 0.5f : 1.0f);
    }

    private void initWebRTC() {
        webRTCClient = new WebRTCClient(this, new WebRTCClient.Observer() {
            @Override
            public void onIceCandidate(IceCandidate candidate) {
                if (callRef != null) {
                    DatabaseReference candidatesRef = callRef.child("iceCandidates").push();
                    candidatesRef.child("candidate").setValue(candidate.sdp);
                    candidatesRef.child("sdpMid").setValue(candidate.sdpMid);
                    candidatesRef.child("sdpMLineIndex").setValue(candidate.sdpMLineIndex);
                }
            }

            @Override
            public void onTrack(RtpTransceiver transceiver) {}
        });

        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        webRTCClient.init(iceServers, false);
    }

    private void setupFirebase() {
        String signalingPath = isIncoming ? "calls/" + senderId + "/" + receiverId : "calls/" + receiverId + "/" + senderId;
        callRef = FirebaseDatabase.getInstance().getReference(signalingPath);

        if (!isIncoming) {
            callRef.child("status").setValue("calling");
            String name = FirebaseAuth.getInstance().getCurrentUser() != null ? 
                         FirebaseAuth.getInstance().getCurrentUser().getDisplayName() : "User";
            String avatar = FirebaseAuth.getInstance().getCurrentUser() != null && FirebaseAuth.getInstance().getCurrentUser().getPhotoUrl() != null ?
                           FirebaseAuth.getInstance().getCurrentUser().getPhotoUrl().toString() : "";
            callRef.child("callerName").setValue(name != null ? name : "User");
            callRef.child("callerAvatar").setValue(avatar);
            callRef.child("isVideo").setValue(false);

            webRTCClient.createOffer(new SimpleSdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    webRTCClient.setLocalDescription(sessionDescription, new SimpleSdpObserver() {
                        @Override
                        public void onSetSuccess() {
                            callRef.child("offer").child("sdp").setValue(sessionDescription.description);
                            callRef.child("offer").child("type").setValue(sessionDescription.type.canonicalForm());
                        }
                    });
                }
            });
        }

        callListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    if (isIncoming) finish();
                    return;
                }

                String status = snapshot.child("status").getValue(String.class);
                if ("accepted".equals(status)) {
                    stopRingtone();
                    isConnected = true;
                    updateProximitySensor(true);
                    callStatus.setText("Connected");
                    btnAccept.setVisibility(View.GONE);
                    findViewById(R.id.controls_container).setVisibility(View.VISIBLE);
                    startTimer();
                    
                    if (!isIncoming && snapshot.hasChild("answer")) {
                        String sdp = snapshot.child("answer/sdp").getValue(String.class);
                        if (sdp != null) {
                            SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
                            webRTCClient.setRemoteDescription(remoteSdp, new SimpleSdpObserver());
                        }
                    }
                } else if ("rejected".equals(status)) {
                    if (!isLogged) {
                        isLogged = true;
                        com.hamraj37.somechat.utils.CallLogManager.logCall(senderId, receiverId, receiverName, receiverAvatar, false, isIncoming, "rejected", 0);
                    }
                    Toast.makeText(AudioCallActivity.this, "Call Rejected", Toast.LENGTH_SHORT).show();
                    finish();
                } else if ("ended".equals(status)) {
                    if (!isLogged) {
                        isLogged = true;
                        long duration = startTime == 0 ? 0 : (System.currentTimeMillis() - startTime) / 1000;
                        com.hamraj37.somechat.utils.CallLogManager.logCall(senderId, receiverId, receiverName, receiverAvatar, false, isIncoming, "ended", duration);
                    }
                    Toast.makeText(AudioCallActivity.this, "Call Ended", Toast.LENGTH_SHORT).show();
                    finish();
                }

                if (isIncoming && snapshot.hasChild("offer") && !snapshot.hasChild("answer")) {
                    String sdp = snapshot.child("offer/sdp").getValue(String.class);
                    SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.OFFER, sdp);
                    webRTCClient.setRemoteDescription(remoteSdp, new SimpleSdpObserver());
                }

                if (snapshot.hasChild("iceCandidates")) {
                    for (DataSnapshot candidateSnapshot : snapshot.child("iceCandidates").getChildren()) {
                        String candidateId = candidateSnapshot.getKey();
                        if (candidateId != null && addedIceCandidates.contains(candidateId)) {
                            continue;
                        }

                        String sdp = candidateSnapshot.child("candidate").getValue(String.class);
                        String sdpMid = candidateSnapshot.child("sdpMid").getValue(String.class);
                        Object mLineIndexObj = candidateSnapshot.child("sdpMLineIndex").getValue();
                        int sdpMLineIndex = 0;
                        if (mLineIndexObj instanceof Long) {
                            sdpMLineIndex = ((Long) mLineIndexObj).intValue();
                        } else if (mLineIndexObj instanceof Integer) {
                            sdpMLineIndex = (Integer) mLineIndexObj;
                        }

                        if (sdp != null && sdpMid != null) {
                            if (webRTCClient != null) {
                                webRTCClient.addIceCandidate(new IceCandidate(sdpMid, sdpMLineIndex, sdp));
                                if (candidateId != null) {
                                    addedIceCandidates.add(candidateId);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        callRef.addValueEventListener(callListener);
    }

    private void startRingtone() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), notification);
            if (ringtone != null) {
                ringtone.setLooping(true);
                ringtone.play();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopRingtone() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
    }

    private void acceptCall() {
        stopRingtone();
        updateProximitySensor(true);
        callRef.child("status").setValue("accepted");
        startTimer();
        
        webRTCClient.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                webRTCClient.setLocalDescription(sessionDescription, new SimpleSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        callRef.child("answer").child("sdp").setValue(sessionDescription.description);
                        callRef.child("answer").child("type").setValue(sessionDescription.type.canonicalForm());
                    }
                });
            }
        });
    }

    private void endCall() {
        CallService.isCallActive = false;
        Intent endIntent = new Intent("com.hamraj37.somechat.CALL_ENDED");
        endIntent.setPackage(getPackageName());
        sendBroadcast(endIntent);

        stopRingtone();
        stopTimer();
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(false);
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
        if (callRef != null) {
            if (!isLogged) {
                isLogged = true;
                long duration = startTime == 0 ? 0 : (System.currentTimeMillis() - startTime) / 1000;
                String status;
                if (isConnected) {
                    status = "ended";
                } else {
                    status = isIncoming ? "rejected" : "cancelled";
                }
                com.hamraj37.somechat.utils.CallLogManager.logCall(senderId, receiverId, receiverName, receiverAvatar, false, isIncoming, status, duration);
            }

            callRef.child("status").setValue("ended");
            new Handler(Looper.getMainLooper()).postDelayed(() -> callRef.removeValue(), 1000);
        }
        if (webRTCClient != null) webRTCClient.close();
        finish();
    }

    @Override
    protected void onDestroy() {
        if (!CallService.isCallActive) {
            updateProximitySensor(false);
            stopRingtone();
            stopTimer();
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setSpeakerphoneOn(false);
                audioManager.setMode(AudioManager.MODE_NORMAL);
            }
            if (callRef != null && callListener != null) {
                callRef.removeEventListener(callListener);
            }
            if (webRTCClient != null) {
                webRTCClient.close();
                webRTCClient = null;
            }
        }
        super.onDestroy();
    }

    private void startTimer() {
        if (startTime != 0) return;
        startTime = System.currentTimeMillis();
        callTimer.setVisibility(View.VISIBLE);
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long millis = System.currentTimeMillis() - startTime;
                int seconds = (int) (millis / 1000);
                int minutes = seconds / 60;
                seconds = seconds % 60;
                callTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String s) {}
        @Override public void onSetFailure(String s) {}
    }
}
