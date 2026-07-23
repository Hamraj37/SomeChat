# Implementation Plan - Persist Encryption Keys Across Devices

The goal is to prevent the app from regenerating encryption keys when a user logs in on a new device or restarts the app. We will achieve this by caching keys locally in `SharedPreferences` and ensuring that the app fetches existing keys from Firebase instead of generating new ones if they already exist.

## User Review Required

> [!IMPORTANT]
> The private key is currently stored in plain text in Firebase Realtime Database. While this allows for easy multi-device sync, it is not a secure practice for end-to-end encryption. For a truly secure implementation, the private key should be encrypted with a user-derived password before being uploaded. However, to stay consistent with the current architecture and address the user's immediate request, we will focus on preventing unnecessary key regeneration.

## Proposed Changes

### [app]

#### [MODIFY] [EncryptionManager.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/utils/EncryptionManager.java)
- Update `loadKeys` and `saveKeys` to persist keys in `SharedPreferences`.
- Add a `loadKeysFromPrefs(Context context)` method to retrieve keys on app start.
- Ensure `initKeys` is only used for initial generation.

#### [MODIFY] [MainActivity.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/MainActivity.java)
- In `setupNavHeader`, call `EncryptionManager.loadKeysFromPrefs(this)` first.
- In `syncUserToDatabases`, check if `snapshot` already contains `publicKey` and `privateKey`. If they exist, use them instead of calling `initKeys`.

#### [MODIFY] [ChatActivity.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/ChatActivity.java) and [GroupChatActivity.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/GroupChatActivity.java)
- In `checkEncryptionStatus`, try to load keys from `SharedPreferences` first.
- If still missing, attempt to fetch them from Firebase RTDB automatically before prompting the user to "Initialize" (which triggers regeneration).

## Verification Plan

### Manual Verification
1.  **Fresh Login**: Log in on a device. Verify keys are generated and saved to Firebase.
2.  **App Restart**: Close and restart the app on the same device. Verify keys are loaded from `SharedPreferences` and no regeneration occurs.
3.  **New Device Login**: Log in on a second device. Verify that the app fetches the existing keys from Firebase and doesn't prompt to "Initialize".
4.  **Decryption Check**: Verify that messages sent from Device 1 can be decrypted on Device 2.
