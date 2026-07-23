# Implementation Plan - Client-to-Client FCM Push Notifications

The goal is to enable direct device-to-device push notifications using Firebase Cloud Messaging (FCM). This allows notifications to be received even when the app is closed, without requiring a persistent "Connected and ready" background icon.

## User Review Required

> [!CAUTION]
> **Security Warning**: This approach involves storing your **FCM Server Key** inside the app's source code (via `BuildConfig`). While convenient for a direct device-to-device setup without a backend server, it is **not secure** for a production app, as someone could potentially extract the key and send unauthorized notifications.
>
> For a production-ready app, these notifications should be triggered by **Firebase Cloud Functions** on the server side.

## Proposed Changes

### [app]

#### [MODIFY] [build.gradle](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/build.gradle)
- Add a new `buildConfigField` for `FCM_SERVER_KEY`. This will allow you to set the key in your `local.properties` file.

#### [NEW] [FcmNotificationSender.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/utils/FcmNotificationSender.java)
- Create a utility class that uses `OkHttp` to send a POST request to the FCM Legacy API.
- It will accept a target token, a title, a body, and a data payload (for handling calls and deep links).

#### [MODIFY] [ChatActivity.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/ChatActivity.java)
- In `sendMessage` and media upload success callbacks:
    - Fetch the receiver's `fcmToken` from Firebase RTDB.
    - Call `FcmNotificationSender` to trigger a push notification to the receiver.

#### [MODIFY] [GroupChatActivity.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/GroupChatActivity.java)
- In `sendMessage` and media upload success callbacks:
    - Fetch tokens for all members of the group (excluding the sender).
    - Send notifications to all member tokens.

#### [MODIFY] [AudioCallActivity.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/AudioCallActivity.java) and [VideoCallActivity.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/VideoCallActivity.java)
- When initiating a call (`status = "calling"`):
    - Fetch the receiver's `fcmToken`.
    - Send a "call" type notification payload so the receiver's phone can show the incoming call screen immediately.

## Verification Plan

### Manual Verification
1.  **Configure Key**: Add `FCM_SERVER_KEY=your_key_here` to your `local.properties`.
2.  **Test Message**: Send a message from Device A to Device B while Device B has the app swiped away. Verify Device B receives a notification.
3.  **Test Call**: Start a call from Device A to Device B while Device B is locked. Verify Device B shows the call notification/screen.
4.  **Verify No Icon**: Confirm there is no longer a persistent "Connected and ready" notification in the system tray.

### Next Steps for the User
1. Go to **Firebase Console** > **Project Settings** > **Cloud Messaging**.
2. Look for **Cloud Messaging API (Legacy)**. If it's disabled, enable it in the Google Cloud Console.
3. Copy the **Server Key**.
4. Add it to your `local.properties` file like this: `FCM_SERVER_KEY=AAAA...`
5. Sync Gradle and rebuild.
