# Implementation Plan - Fix Group Profile Image in Group Info

The goal is to ensure the group profile image displays correctly in the `GroupInfoActivity`, even for groups created before the field name transition.

## Proposed Changes

### [app]

#### [MODIFY] [GroupInfoActivity.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/GroupInfoActivity.java)
- In `loadGroupDetails`, add logic to manually check for legacy field names (`groupName` and `groupAvatar`) if the automatic Firebase mapping (`getValue(Group.class)`) results in null values.
- This mirrors the robust loading logic already implemented in `GroupChatActivity`.

#### [MODIFY] [TransformViewModel.java](file:///C:/Users/Administrator/AndroidStudioProjects/SameChat/app/src/main/java/com/hamraj37/somechat/ui/transform/TransformViewModel.java)
- Refine the metadata listener to prioritize the new field names (`name`, `avatar`) over the legacy ones (`groupName`, `groupAvatar`).

## Verification Plan

### Manual Verification
1.  **Open Group Info**: Navigate to the "Support Group" info screen. Verify the group profile image is now visible instead of the default logo.
2.  **Verify New Groups**: Create a new group and verify its image shows correctly in `GroupInfoActivity`.
3.  **Update Image**: Change the image from the `GroupInfoActivity` and ensure it persists and remains visible.
