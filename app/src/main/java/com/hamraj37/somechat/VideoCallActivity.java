package com.hamraj37.somechat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import com.hamraj37.somechat.services.CallState;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Rational;
import android.view.MotionEvent;
import android.view.WindowManager;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.webrtc.CameraVideoCapturer;
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

public class VideoCallActivity extends BaseActivity {

    private String receiverId, receiverName, receiverAvatar;
    private String senderId;
    private boolean isIncoming;
    private DatabaseReference callRef;
    private ValueEventListener callListener;

    private TextView callStatus, callTimer;
    private FloatingActionButton btnAccept, btnDecline, btnMute, btnSwitchCamera, btnSpeaker;
    private SurfaceViewRenderer localVideoView, remoteVideoView;
    private View localVideoContainer, videoPlaceholder;

    private WebRTCClient webRTCClient;
    private static final int PERMISSION_REQUEST_CODE = 101;
    private final List<String> addedIceCandidates = new ArrayList<>();

    private long startTime = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private boolean isMuted = false;
    private boolean isSpeakerOn = true; // Always default to speaker for video
    private boolean isFrontCamera = true;
    private boolean isConnected = false;
    private boolean isRemoteDescriptionSet = false;
    private boolean isFlashOn = false;
    private float originalBrightness = -1f;
    private float dX, dY;
    private FloatingActionButton btnFlash;
    private View screenLightOverlay;
    private boolean isUiVisible = true;
    private int statusBarHeight = 0;
    private Ringtone ringtone;
    private boolean isLogged = false;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_video_call);

        receiverId = getIntent().getStringExtra("uid");
        receiverName = getIntent().getStringExtra("displayName");
        receiverAvatar = getIntent().getStringExtra("photoUrl");
        isIncoming = getIntent().getBooleanExtra("isIncoming", false);
        senderId = FirebaseAuth.getInstance().getUid();

        initViews();

        if (CallState.isCallActive && receiverId != null && receiverId.equals(CallState.activeCallId)) {
            // Resuming existing call state
            startTime = CallState.callStartTime;
            if (startTime != 0) {
                isConnected = true;
                callStatus.setText("Connected");
                btnAccept.setVisibility(View.GONE);
                findViewById(R.id.controls_container).setVisibility(View.VISIBLE);
                startTimer();
            }
        }

        checkPermissionsAndInit();
        setupAudioRouting();
        if (isIncoming && !isConnected) {
            startRingtone();
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.video_call_root), (v, insets) -> {
            statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            
            // Adjust initial top margin of local video container to avoid status bar
            int margin = (int) (16 * getResources().getDisplayMetrics().density);
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = 
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) localVideoContainer.getLayoutParams();
            lp.topMargin = statusBarHeight + margin;
            localVideoContainer.setLayoutParams(lp);

            return insets;
        });
    }

    private void setupAudioRouting() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(isSpeakerOn);
        }
    }

    private void checkPermissionsAndInit() {
        String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA};

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
                Toast.makeText(this, "Permissions required for video calling", Toast.LENGTH_SHORT).show();
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
        btnFlash = findViewById(R.id.btn_flash);
        screenLightOverlay = findViewById(R.id.screen_light_overlay);
        
        localVideoView = findViewById(R.id.local_video_view);
        remoteVideoView = findViewById(R.id.remote_video_view);
        localVideoContainer = findViewById(R.id.local_video_container);
        videoPlaceholder = findViewById(R.id.video_placeholder);

        name.setText(receiverName);
        if (receiverAvatar != null && !receiverAvatar.isEmpty()) {
            Glide.with(this).load(receiverAvatar).circleCrop().into(avatar);
        }

        if (isIncoming) {
            callStatus.setText("Incoming Video Call...");
            btnAccept.setVisibility(View.VISIBLE);
            findViewById(R.id.controls_container).setVisibility(View.GONE);
        } else {
            callStatus.setText("Video Calling...");
            btnAccept.setVisibility(View.GONE);
            findViewById(R.id.controls_container).setVisibility(View.VISIBLE);
        }

        btnAccept.setOnClickListener(v -> acceptCall());
        btnDecline.setOnClickListener(v -> endCall());
        btnMute.setOnClickListener(v -> toggleMute());
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        btnSwitchCamera.setOnClickListener(v -> toggleCamera());
        btnFlash.setOnClickListener(v -> toggleFlash());

        setupDraggableLocalVideo();
        setupClickToHideUi();
    }

    private void setupClickToHideUi() {
        View.OnClickListener toggleUiListener = v -> {
            isUiVisible = !isUiVisible;
            int visibility = isUiVisible ? View.VISIBLE : View.GONE;
            
            findViewById(R.id.controls_container).setVisibility(visibility);
            btnDecline.setVisibility(visibility);
            if (isIncoming && !isConnected) {
                btnAccept.setVisibility(visibility);
            }
            callStatus.setVisibility(visibility);
            if (isConnected) {
                callTimer.setVisibility(visibility);
            }
            
            if (remoteVideoView.getVisibility() != View.VISIBLE) {
                findViewById(R.id.call_avatar).setVisibility(visibility);
                findViewById(R.id.call_name).setVisibility(visibility);
            }
        };

        findViewById(R.id.video_call_root).setOnClickListener(toggleUiListener);
        remoteVideoView.setOnClickListener(toggleUiListener);
    }

    private void setupDraggableLocalVideo() {
        localVideoContainer.setOnTouchListener((v, event) -> {
            View parent = (View) v.getParent();
            int parentWidth = parent.getWidth();
            int parentHeight = parent.getHeight();
            int margin = (int) (16 * getResources().getDisplayMetrics().density);

            int topLimit = statusBarHeight + margin;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dX = v.getX() - event.getRawX();
                    dY = v.getY() - event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float newX = event.getRawX() + dX;
                    float newY = event.getRawY() + dY;

                    // Keep within parent bounds during drag
                    newX = Math.max(0, Math.min(newX, parentWidth - v.getWidth()));
                    newY = Math.max(statusBarHeight, Math.min(newY, parentHeight - v.getHeight()));

                    v.setX(newX);
                    v.setY(newY);
                    break;
                case MotionEvent.ACTION_UP:
                    float finalX = v.getX();
                    float finalY = v.getY();

                    // Snap to nearest corner
                    float destX = (finalX + v.getWidth() / 2f > parentWidth / 2f) 
                            ? parentWidth - v.getWidth() - margin 
                            : margin;
                    
                    // Vertical snapping
                    float destY = finalY;
                    if (finalY < topLimit) destY = topLimit;
                    if (finalY > parentHeight - v.getHeight() - margin) destY = parentHeight - v.getHeight() - margin;

                    v.animate()
                            .x(destX)
                            .y(destY)
                            .setDuration(300)
                            .start();

                    v.performClick();
                    break;
                default:
                    return false;
            }
            return true;
        });
    }

    private void toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(isSpeakerOn);
        }
        btnSpeaker.setAlpha(isSpeakerOn ? 1.0f : 0.5f);
    }

    private void toggleMute() {
        isMuted = !isMuted;
        if (webRTCClient != null) {
            webRTCClient.setAudioEnabled(!isMuted);
        }
        btnMute.setAlpha(isMuted ? 0.5f : 1.0f);
    }

    private void toggleCamera() {
        if (webRTCClient != null) {
            if (isFlashOn) {
                toggleFlash(); // Turn off flash before switching
            }
            // Switch to the opposite of current (e.g., if front, switch to back)
            webRTCClient.switchCamera(!isFrontCamera, new CameraVideoCapturer.CameraSwitchHandler() {
                @Override
                public void onCameraSwitchDone(boolean isFront) {
                    isFrontCamera = isFront;
                    runOnUiThread(() -> {
                        if (localVideoView != null) {
                            localVideoView.setMirror(isFront);
                        }
                    });
                }

                @Override
                public void onCameraSwitchError(String s) {
                    runOnUiThread(() -> Toast.makeText(VideoCallActivity.this, "Camera switch error: " + s, Toast.LENGTH_SHORT).show());
                }
            });
        }
    }

    private void toggleFlash() {
        isFlashOn = !isFlashOn;
        if (isFrontCamera) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            if (isFlashOn) {
                if (originalBrightness == -1f) originalBrightness = lp.screenBrightness;
                lp.screenBrightness = 1.0f;
                screenLightOverlay.setVisibility(View.VISIBLE);
            } else {
                lp.screenBrightness = originalBrightness;
                screenLightOverlay.setVisibility(View.GONE);
            }
            getWindow().setAttributes(lp);
        } else {
            try {
                CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                if (camManager != null) {
                    boolean flashToggled = false;
                    String[] cameraIds = camManager.getCameraIdList();
                    for (String id : cameraIds) {
                        CameraCharacteristics characteristics = camManager.getCameraCharacteristics(id);
                        Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                        
                        if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK && hasFlash != null && hasFlash) {
                            try {
                                camManager.setTorchMode(id, isFlashOn);
                                flashToggled = true;
                                break;
                            } catch (Exception inner) {
                                // If this ID is busy (e.g. WebRTC using it), log and try next
                                inner.printStackTrace();
                            }
                        }
                    }
                    
                    if (!flashToggled && isFlashOn) {
                        Toast.makeText(this, "Flash unavailable while camera is active", Toast.LENGTH_SHORT).show();
                        isFlashOn = false;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                isFlashOn = false;
                Toast.makeText(this, "Flash error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        btnFlash.setImageResource(isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        btnFlash.setAlpha(isFlashOn ? 1.0f : 0.5f);
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
                            if (videoPlaceholder != null) {
                                videoPlaceholder.setVisibility(View.GONE);
                            }
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
        webRTCClient.init(iceServers, true);

        if (localVideoView != null && remoteVideoView != null) {
            try {
                localVideoView.init(webRTCClient.getEglContext(), null);
                remoteVideoView.init(webRTCClient.getEglContext(), null);
                
                localVideoView.setMirror(isFrontCamera);
                localVideoView.setZOrderMediaOverlay(true);
                remoteVideoView.setMirror(false);
                remoteVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
                localVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);

                webRTCClient.startLocalVideo(localVideoView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void setupFirebase() {
        String signalingPath = isIncoming ? "calls/" + senderId + "/" + receiverId : "calls/" + receiverId + "/" + senderId;
        callRef = FirebaseDatabase.getInstance().getReference(signalingPath);

        if (!isIncoming && !isConnected) {
            callRef.child("status").setValue("calling");

            // Mark as active call so if swiped away, it can be ended
            CallState.isCallActive = true;
            CallState.activeCallId = receiverId;
            CallState.activeCallName = receiverName;
            CallState.activeCallAvatar = receiverAvatar;
            CallState.isActiveCallVideo = true;
            CallState.isActiveCallIncoming = false;

            String name = FirebaseAuth.getInstance().getCurrentUser() != null ? 
                         FirebaseAuth.getInstance().getCurrentUser().getDisplayName() : "User";
            String avatar = FirebaseAuth.getInstance().getCurrentUser() != null && FirebaseAuth.getInstance().getCurrentUser().getPhotoUrl() != null ?
                           FirebaseAuth.getInstance().getCurrentUser().getPhotoUrl().toString() : "";
            callRef.child("callerName").setValue(name != null ? name : "User");
            callRef.child("callerAvatar").setValue(avatar);
            callRef.child("isVideo").setValue(true);

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
                    CallState.isCallActive = true;
                    CallState.activeCallId = receiverId;
                    CallState.activeCallName = receiverName;
                    CallState.activeCallAvatar = receiverAvatar;
                    CallState.isActiveCallVideo = true;
                    CallState.isActiveCallIncoming = isIncoming;
                    CallState.callStartTime = startTime;

                    callStatus.setText("Connected");
                    btnAccept.setVisibility(View.GONE);
                    findViewById(R.id.controls_container).setVisibility(View.VISIBLE);
                    startTimer();
                    
                    if (!isIncoming && snapshot.hasChild("answer") && !isRemoteDescriptionSet) {
                        String sdp = snapshot.child("answer/sdp").getValue(String.class);
                        if (sdp != null) {
                            isRemoteDescriptionSet = true;
                            SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
                            webRTCClient.setRemoteDescription(remoteSdp, new SimpleSdpObserver());
                        }
                    }
                } else if ("rejected".equals(status)) {
                    CallState.reset();
                    Intent endIntent = new Intent("com.hamraj37.somechat.VIDEO_CALL_ENDED");
                    endIntent.setPackage(getPackageName());
                    sendBroadcast(endIntent);
                    if (!isLogged) {
                        isLogged = true;
                        com.hamraj37.somechat.utils.CallLogManager.logCall(senderId, receiverId, receiverName, receiverAvatar, true, isIncoming, "rejected", 0);
                    }
                    Toast.makeText(VideoCallActivity.this, "Call Rejected", Toast.LENGTH_SHORT).show();
                    finish();
                } else if ("ended".equals(status)) {
                    CallState.reset();
                    Intent endIntent = new Intent("com.hamraj37.somechat.VIDEO_CALL_ENDED");
                    endIntent.setPackage(getPackageName());
                    sendBroadcast(endIntent);
                    if (!isLogged) {
                        isLogged = true;
                        long duration = startTime == 0 ? 0 : (System.currentTimeMillis() - startTime) / 1000;
                        com.hamraj37.somechat.utils.CallLogManager.logCall(senderId, receiverId, receiverName, receiverAvatar, true, isIncoming, "ended", duration);
                    }
                    Toast.makeText(VideoCallActivity.this, "Call Ended", Toast.LENGTH_SHORT).show();
                    finish();
                }

                if (isIncoming && snapshot.hasChild("offer") && !snapshot.hasChild("answer") && !isRemoteDescriptionSet) {
                    String sdp = snapshot.child("offer/sdp").getValue(String.class);
                    if (sdp != null) {
                        isRemoteDescriptionSet = true;
                        SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.OFFER, sdp);
                        webRTCClient.setRemoteDescription(remoteSdp, new SimpleSdpObserver());
                    }
                }

                if (snapshot.hasChild("iceCandidates") && isRemoteDescriptionSet) {
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
        CallState.reset();
        Intent endIntent = new Intent("com.hamraj37.somechat.VIDEO_CALL_ENDED");
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
                com.hamraj37.somechat.utils.CallLogManager.logCall(senderId, receiverId, receiverName, receiverAvatar, true, isIncoming, status, duration);
            }

            callRef.child("status").setValue("ended");
            new Handler(Looper.getMainLooper()).postDelayed(() -> callRef.removeValue(), 1000);
        }
        if (webRTCClient != null) webRTCClient.close();
        finish();
    }

    @Override
    protected void onDestroy() {
        stopRingtone();
        if (isFlashOn) {
            toggleFlash(); // Ensure flash is off and brightness restored
        }
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
        if (timerRunnable != null) return;
        if (startTime == 0) startTime = System.currentTimeMillis();
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
            timerRunnable = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (isConnected) {
            enterPipMode();
        } else {
            // If calling or incoming, back press should decline/cancel
            endCall();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isConnected) {
            enterPipMode();
        }
    }

    private void enterPipMode() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(9, 16))
                    .build();
            enterPictureInPictureMode(params);
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            // Hide UI elements in PiP mode
            findViewById(R.id.controls_container).setVisibility(View.GONE);
            btnDecline.setVisibility(View.GONE);
            btnAccept.setVisibility(View.GONE);
            callStatus.setVisibility(View.GONE);
            callTimer.setVisibility(View.GONE);
            findViewById(R.id.call_avatar).setVisibility(View.GONE);
            findViewById(R.id.call_name).setVisibility(View.GONE);
            if (videoPlaceholder != null) videoPlaceholder.setVisibility(View.GONE);
            localVideoContainer.setVisibility(View.GONE);
        } else {
            // Show UI elements when returning from PiP mode
            findViewById(R.id.controls_container).setVisibility(View.VISIBLE);
            btnDecline.setVisibility(View.VISIBLE);
            if (isIncoming && !isConnected) {
                btnAccept.setVisibility(View.VISIBLE);
            }
            callStatus.setVisibility(View.VISIBLE);
            if (isConnected) {
                callTimer.setVisibility(View.VISIBLE);
            }
            
            // Only show avatar/name if video isn't active
            if (remoteVideoView.getVisibility() != View.VISIBLE) {
                findViewById(R.id.call_avatar).setVisibility(View.VISIBLE);
                findViewById(R.id.call_name).setVisibility(View.VISIBLE);
                if (videoPlaceholder != null) videoPlaceholder.setVisibility(View.VISIBLE);
            }
            localVideoContainer.setVisibility(View.VISIBLE);
        }
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String s) {}
        @Override public void onSetFailure(String s) {}
    }
}
