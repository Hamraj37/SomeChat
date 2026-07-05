package com.samechat37;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.samechat37.utils.WebRTCClient;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CallActivity extends BaseActivity {

    private String receiverId, receiverName, receiverAvatar;
    private String senderId;
    private boolean isIncoming, isVideo;
    private DatabaseReference callRef;
    private ValueEventListener callListener;

    private TextView callStatus, callTimer;
    private FloatingActionButton btnAccept, btnDecline, btnMute, btnSwitchCamera, btnSpeaker;
    private SurfaceViewRenderer localVideoView, remoteVideoView;
    private View localVideoContainer;

    private WebRTCClient webRTCClient;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private final List<String> addedIceCandidates = new ArrayList<>();

    private long startTime = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private boolean isMuted = false;
    private boolean isSpeakerOn = false;
    private boolean isFrontCamera = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isNightMode);
        
        setContentView(R.layout.activity_call);

        receiverId = getIntent().getStringExtra("uid");
        receiverName = getIntent().getStringExtra("displayName");
        receiverAvatar = getIntent().getStringExtra("photoUrl");
        isIncoming = getIntent().getBooleanExtra("isIncoming", false);
        isVideo = getIntent().getBooleanExtra("isVideo", false);
        isSpeakerOn = isVideo; // Default to speaker on for video calls
        senderId = FirebaseAuth.getInstance().getUid();

        initViews();
        checkPermissionsAndInit();
        setupAudioRouting();
    }

    private void setupAudioRouting() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(isSpeakerOn);
        }
    }

    private void checkPermissionsAndInit() {
        String[] permissions = isVideo 
            ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}
            : new String[]{Manifest.permission.RECORD_AUDIO};

        boolean allGranted = true;
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
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
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initWebRTC();
                setupFirebase();
            } else {
                Toast.makeText(this, "Permissions required for calling", Toast.LENGTH_SHORT).show();
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
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        
        localVideoView = findViewById(R.id.local_video_view);
        remoteVideoView = findViewById(R.id.remote_video_view);
        localVideoContainer = findViewById(R.id.local_video_container);

        name.setText(receiverName);
        if (receiverAvatar != null && !receiverAvatar.isEmpty()) {
            Glide.with(this).load(receiverAvatar).into(avatar);
        }

        if (isIncoming) {
            callStatus.setText(isVideo ? "Incoming Video Call..." : "Incoming Audio Call...");
            btnAccept.setVisibility(View.VISIBLE);
            btnMute.setVisibility(View.GONE);
            btnSpeaker.setVisibility(View.GONE);
            btnSwitchCamera.setVisibility(View.GONE);
        } else {
            callStatus.setText(isVideo ? "Video Calling..." : "Calling...");
            btnAccept.setVisibility(View.GONE);
            btnMute.setVisibility(View.VISIBLE);
            btnSpeaker.setVisibility(View.VISIBLE);
            btnSpeaker.setAlpha(isSpeakerOn ? 1.0f : 0.5f);
            if (isVideo) {
                btnSwitchCamera.setVisibility(View.VISIBLE);
            }
        }

        btnAccept.setOnClickListener(v -> acceptCall());
        btnDecline.setOnClickListener(v -> endCall());
        btnMute.setOnClickListener(v -> toggleMute());
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        btnSwitchCamera.setOnClickListener(v -> toggleCamera());
    }

    private void toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(isSpeakerOn);
        }
        btnSpeaker.setAlpha(isSpeakerOn ? 1.0f : 0.5f);
        Toast.makeText(this, isSpeakerOn ? "Speaker On" : "Speaker Off", Toast.LENGTH_SHORT).show();
    }

    private void toggleMute() {
        isMuted = !isMuted;
        if (webRTCClient != null) {
            webRTCClient.setAudioEnabled(!isMuted);
        }
        btnMute.setImageResource(isMuted ? android.R.drawable.ic_lock_silent_mode_off : android.R.drawable.ic_lock_silent_mode);
        btnMute.setAlpha(isMuted ? 0.5f : 1.0f);
        Toast.makeText(this, isMuted ? "Muted" : "Unmuted", Toast.LENGTH_SHORT).show();
    }

    private void toggleCamera() {
        if (webRTCClient != null) {
            webRTCClient.switchCamera();
            isFrontCamera = !isFrontCamera;
            if (localVideoView != null) {
                localVideoView.setMirror(isFrontCamera);
            }
        }
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
            public void onTrack(RtpTransceiver transceiver) {
                if (transceiver != null && transceiver.getReceiver() != null && transceiver.getReceiver().track() instanceof VideoTrack) {
                    VideoTrack videoTrack = (VideoTrack) transceiver.getReceiver().track();
                    if (videoTrack != null) {
                        runOnUiThread(() -> {
                            if (remoteVideoView != null) {
                                remoteVideoView.setVisibility(View.VISIBLE);
                                videoTrack.addSink(remoteVideoView);
                            }
                            // Hide avatar when video starts
                            View avatar = findViewById(R.id.call_avatar);
                            View name = findViewById(R.id.call_name);
                            if (avatar != null) avatar.setVisibility(View.GONE);
                            if (name != null) name.setVisibility(View.GONE);
                        });
                    }
                }
            }
        });

        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        webRTCClient.init(iceServers, isVideo);

        if (isVideo && localVideoView != null && remoteVideoView != null) {
            try {
                localVideoView.init(webRTCClient.getEglContext(), null);
                remoteVideoView.init(webRTCClient.getEglContext(), null);
                
                localVideoView.setMirror(true);
                localVideoView.setZOrderMediaOverlay(true); // Bring local video to front
                remoteVideoView.setMirror(false);
                remoteVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                localVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);

                localVideoContainer.setVisibility(View.VISIBLE);
                webRTCClient.startLocalVideo(localVideoView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setupFirebase() {
        String signalingPath = isIncoming ? "calls/" + senderId + "/" + receiverId : "calls/" + receiverId + "/" + senderId;
        callRef = FirebaseDatabase.getInstance().getReference(signalingPath);

        if (!isIncoming) {
            callRef.child("status").setValue("calling");
            String name = FirebaseAuth.getInstance().getCurrentUser() != null ? 
                         FirebaseAuth.getInstance().getCurrentUser().getDisplayName() : "User";
            callRef.child("callerName").setValue(name != null ? name : "User");
            callRef.child("isVideo").setValue(isVideo);

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
                    callStatus.setText("Connected");
                    btnAccept.setVisibility(View.GONE);
                    btnMute.setVisibility(View.VISIBLE);
                    btnSpeaker.setVisibility(View.VISIBLE);
                    btnSpeaker.setAlpha(isSpeakerOn ? 1.0f : 0.5f);
                    if (isVideo) {
                        btnSwitchCamera.setVisibility(View.VISIBLE);
                    }
                    startTimer();
                    
                    if (!isIncoming && snapshot.hasChild("answer")) {
                        String sdp = snapshot.child("answer/sdp").getValue(String.class);
                        if (sdp != null) {
                            SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
                            webRTCClient.setRemoteDescription(remoteSdp, new SimpleSdpObserver());
                        }
                    }
                } else if ("ringing".equals(status)) {
                    if (!isIncoming) callStatus.setText("Ringing...");
                } else if ("rejected".equals(status) || "ended".equals(status)) {
                    Toast.makeText(CallActivity.this, "Call Ended", Toast.LENGTH_SHORT).show();
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

    private void acceptCall() {
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
        stopTimer();
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(false);
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
        if (callRef != null) {
            callRef.child("status").setValue("ended");
            new Handler(Looper.getMainLooper()).postDelayed(() -> callRef.removeValue(), 1000);
        }
        if (webRTCClient != null) webRTCClient.close();
        finish();
    }

    @Override
    protected void onDestroy() {
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
        if (localVideoView != null) {
            localVideoView.release();
            localVideoView = null;
        }
        if (remoteVideoView != null) {
            remoteVideoView.release();
            remoteVideoView = null;
        }
        super.onDestroy();
    }

    private void startTimer() {
        if (startTime != 0) return; // Already started
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
