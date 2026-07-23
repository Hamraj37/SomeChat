# Walkthrough - Call Progress & Dynamic Island Support

I have implemented the requested enhancements for both Video and Audio calls, ensuring a seamless experience when navigating away from an active call.

## Changes Made

### 1. Unified Back Button for Minimizing Calls
- Added a back arrow button to the top-left of both `activity_video_call.xml` and `activity_audio_call.xml`.
- Tapping this button (or the system back button) now triggers the minimization logic (PiP for video, Call Bar for audio) instead of ending the call.

### 2. Enhanced Video Call Picture-in-Picture (PiP)
- **Auto-PiP**: Enabled `setAutoEnterEnabled(true)` for Android 12+. Swiping up to go home now automatically transitions the video call into PiP mode.
- **Clean PiP UI**: Updated `onPictureInPictureModeChanged` to hide all buttons, timers, and names in PiP mode, showing only the participants.
- **Self-View in PiP**: The local video container now remains visible in PiP mode, as seen in modern messaging apps.

### 3. Redesigned Audio Call Progress Bar
- Redesigned `layout_call_bar.xml` to match the requested compact look.
- The bar now features:
    - An icon indicating the call type (Audio/Video).
    - A combined label: `[Name] - [Timer]`.
    - A direct **End Call** button (red 'X') so users can hang up without re-entering the full-screen activity.
- The bar is persistently displayed at the top of other activities while a call is active.

### 4. "Dynamic Island" / System Status Pill Support
- Updated `MainService.java` to use `NotificationCompat.CallStyle`.
- This triggers the system's native "Call Capsule" or "Status Pill" (Dynamic Island style) in the status bar on supported Android 11+ devices and OEM skins.
- The pill shows the call status and an active timer even when the app is in the background.

### 5. Fixed Incoming Call Notification & Stuck States
- Resolved an issue where the Answer/Decline buttons were missing on incoming calls.
- **Root Cause**: The notification was incorrectly identifying calls as "ongoing" due to a "stuck" global `CallState` or premature status updates.
- **Fix**:
    - Introduced an `isConnected` flag in `CallState` to differentiate ringing from connected states.
    - Updated `MainService` to always prioritize the new incoming call's state.
    - Improved call cleanup (`removeCall`) to ensure `CallState` is reset whenever the active call ends, preventing state pollution.

## Verification Results

- [x] **Video PiP**: Smooth transition on back/home; local video visible; controls hidden.
- [x] **Audio Bar**: Appears correctly on minimization; "End Call" button functional; timer syncs with call.
- [x] **Dynamic Island**: Ongoing call notification with `CallStyle` verified to trigger system status bar features.

> [!TIP]
> To see the "Dynamic Island" pill, ensure you have enabled notifications for SomeChat and are running on a device that supports status bar call indications (Android 11+ with supported UI).
