# Implementation Plan - Call Progress UI (PiP, Call Bar & Dynamic Island)

The goal is to provide a seamless "Ongoing Call" experience. This includes:
1. **Video Call PiP**: Smooth transition to Picture-in-Picture when navigating away.
2. **In-App Call Bar**: A persistent bar at the top of the app for audio calls.
3. **Dynamic Island / Status Pill**: Support for the system-level call capsule in the status bar on modern Android devices.

## Proposed Changes

### [app]

#### [MODIFY] [activity_video_call.xml](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/res/layout/activity_video_call.xml)
- Add a "back" button (arrow) at the top left.
- Ensure proper spacing for the status bar.

#### [MODIFY] [VideoCallActivity.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/VideoCallActivity.java)
- Initialize the back button and set its click listener to `onBackPressed()`.
- Update `enterPipMode()` to use `setAutoEnterEnabled(true)` for Android 12+.
- Refine `onPictureInPictureModeChanged()` to keep the local video visible in PiP.

#### [MODIFY] [layout_call_bar.xml](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/res/layout/layout_call_bar.xml)
- Redesign to match the requested "Call Progress" UI:
    - Change background to a darker green/translucent style.
    - Horizontal layout: [Mic/Speaker Icon] [Name - Timer] [End Call Button].

#### [MODIFY] [BaseActivity.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/BaseActivity.java)
- Update `showCallBar()`:
    - Find and set up the new "End Call" button.
    - The button will send a broadcast (`com.hamraj37.somechat.END_CURRENT_CALL`).

#### [MODIFY] [activity_audio_call.xml](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/res/layout/activity_audio_call.xml)
- Add a "back" button (arrow) at the top left, similar to the video call layout.

#### [MODIFY] [AudioCallActivity.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/AudioCallActivity.java)
- Initialize the back button and set its click listener to `onBackPressed()`.
- Add a `BroadcastReceiver` for `END_CURRENT_CALL`.
- Update `minimizeCall()` to ensure `CallState` is fully populated.

#### [MODIFY] [MainService.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/services/MainService.java)
- **Dynamic Island Support**: Update `showCallNotification()` to use `NotificationCompat.CallStyle`.
    - Create a `Person` object for the caller.
    - Use `CallStyle.forOngoingCall` for active calls.
    - This will trigger the system status bar capsule (pill) on Android 11+ and various OEM skins (like RealmeUI, ColorOS, etc.).
- Ensure `setForegroundServiceType(FOREGROUND_SERVICE_TYPE_PHONE_CALL)` is used correctly for active calls.

## Verification Plan

### Manual Verification
1.  **Video Call PiP**:
    - Start video call -> Tap Back -> Verify PiP enters correctly.
2.  **Audio Call Bar**:
    - Start audio call -> Tap Back -> Verify the green Call Bar appears.
    - Tap "End Call" -> Verify call ends.
3.  **Dynamic Island (Status Pill)**:
    - Start call -> Go to home screen -> Verify the system status bar shows a green pill/bubble with the call status and timer.
