package com.hamraj37.somechat.utils;

import android.content.Context;
import org.webrtc.*;
import org.webrtc.audio.JavaAudioDeviceModule;
import java.util.ArrayList;
import java.util.List;

public class WebRTCClient {
    private final PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private AudioTrack localAudioTrack;
    private VideoTrack localVideoTrack;
    private VideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private final Context context;
    private final Observer observer;
    private final EglBase rootEglBase = EglBase.create();
    private String frontCameraName;
    private String backCameraName;

    public interface Observer {
        void onIceCandidate(IceCandidate candidate);
        void onTrack(RtpTransceiver transceiver);
    }

    public WebRTCClient(Context context, Observer observer) {
        this.context = context;
        this.observer = observer;

        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        
        VideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(), true, true);
        VideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());

        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(JavaAudioDeviceModule.builder(context).createAudioDeviceModule())
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
    }

    public EglBase.Context getEglContext() {
        return rootEglBase.getEglBaseContext();
    }

    public void init(List<PeerConnection.IceServer> iceServers, boolean isVideoCall) {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                observer.onIceCandidate(iceCandidate);
            }

            @Override
            public void onTrack(RtpTransceiver transceiver) {
                observer.onTrack(transceiver);
            }

            @Override public void onSignalingChange(PeerConnection.SignalingState signalingState) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {}
            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}
            @Override public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}
            @Override public void onAddStream(MediaStream mediaStream) {}
            @Override public void onRemoveStream(MediaStream mediaStream) {}
            @Override public void onDataChannel(DataChannel dataChannel) {}
            @Override public void onRenegotiationNeeded() {}
            @Override public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}
        });

        // Setup local audio
        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource);
        peerConnection.addTrack(localAudioTrack);

        if (isVideoCall) {
            setupVideoTrack();
        }
    }

    private void setupVideoTrack() {
        videoCapturer = createVideoCapturer();
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
        VideoSource videoSource = factory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        videoCapturer.startCapture(1280, 720, 30);

        localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource);
        peerConnection.addTrack(localVideoTrack);
    }

    private VideoCapturer createVideoCapturer() {
        CameraEnumerator enumerator = new Camera2Enumerator(context);
        final String[] deviceNames = enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                if (frontCameraName == null) frontCameraName = deviceName;
            } else if (enumerator.isBackFacing(deviceName)) {
                if (backCameraName == null) backCameraName = deviceName;
            }
        }

        if (frontCameraName != null) {
            return enumerator.createCapturer(frontCameraName, null);
        } else if (backCameraName != null) {
            return enumerator.createCapturer(backCameraName, null);
        }
        return enumerator.createCapturer(deviceNames[0], null);
    }

    public void startLocalVideo(SurfaceViewRenderer renderer) {
        if (localVideoTrack != null) {
            localVideoTrack.addSink(renderer);
        }
    }

    public void createOffer(SdpObserver sdpObserver) {
        if (peerConnection != null) {
            MediaConstraints constraints = new MediaConstraints();
            peerConnection.createOffer(sdpObserver, constraints);
        }
    }

    public void createAnswer(SdpObserver sdpObserver) {
        if (peerConnection != null) {
            MediaConstraints constraints = new MediaConstraints();
            peerConnection.createAnswer(sdpObserver, constraints);
        }
    }

    public void setRemoteDescription(SessionDescription sdp, SdpObserver sdpObserver) {
        if (peerConnection != null) {
            peerConnection.setRemoteDescription(sdpObserver, sdp);
        }
    }

    public void setLocalDescription(SessionDescription sdp, SdpObserver sdpObserver) {
        if (peerConnection != null) {
            peerConnection.setLocalDescription(sdpObserver, sdp);
        }
    }

    public void addIceCandidate(IceCandidate candidate) {
        if (peerConnection != null) {
            peerConnection.addIceCandidate(candidate);
        }
    }

    public void setAudioEnabled(boolean enabled) {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(enabled);
        }
    }

    public void setVideoEnabled(boolean enabled) {
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(enabled);
        }
    }

    public void switchCamera(boolean toFront, CameraVideoCapturer.CameraSwitchHandler handler) {
        if (videoCapturer instanceof CameraVideoCapturer) {
            CameraVideoCapturer cameraCapturer = (CameraVideoCapturer) videoCapturer;
            String targetName = toFront ? frontCameraName : backCameraName;
            
            if (targetName != null) {
                cameraCapturer.switchCamera(handler, targetName);
            } else {
                // Fallback to cycling if specific name isn't found
                cameraCapturer.switchCamera(handler);
            }
        }
    }

    private boolean isClosed = false;

    public void close() {
        if (isClosed) return;
        isClosed = true;

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (Exception e) {
                e.printStackTrace();
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection.dispose();
            peerConnection = null;
        }
        if (factory != null) {
            factory.dispose();
        }
        rootEglBase.release();
    }
}
