# VOZ — Privacy Policy & Data Handling Guarantee

**Effective Date:** October 2026  
**Product:** VOZ Android Communication Utility

## 1. Core Privacy Principle: Zero Network Transmission
VOZ is engineered to be **offline-by-default and offline-by-design**.
- **No `INTERNET` Permission:** VOZ does not declare `android.permission.INTERNET` in `AndroidManifest.xml`. It is technically incapable of uploading your contacts, call history, notes, or phone numbers to any remote server.
- **No Advertising or Analytics SDKs:** VOZ contains zero ad networks, telemetry trackers, crash-reporting clouds, or third-party profiling libraries.
- **No Mandatory Accounts:** VOZ requires no sign-up, phone number verification, or cloud profile.

## 2. How Permissions Are Used
VOZ requests Android runtime permissions strictly in context when you invoke a feature that requires them:
1. **`READ_CONTACTS`:** Used locally on `Dispatchers.IO` to allow fast T9 keypad matching, English/Bangla name search, and caller identification against your device's saved contacts. VOZ never silently creates, edits, or deletes system contacts.
2. **`READ_CALL_LOG` & `WRITE_CALL_LOG`:** Used locally to display your chronological call timeline, group consecutive calls, highlight missed calls, and allow user-confirmed deletion of call records.
3. **`CALL_PHONE` & `READ_PHONE_STATE`:** Used to place calls via Android's `TelecomManager` and detect available SIM subscriptions on dual-SIM devices so you can choose which SIM to use.
4. **`RoleManager.ROLE_DIALER` & `RoleManager.ROLE_CALL_SCREENING`:** Enables VOZ to present the in-call screen (`VozInCallService`) and automatically reject incoming calls from numbers on your local blocklist (`VozCallScreeningService`).
5. **`POST_NOTIFICATIONS` & `USE_FULL_SCREEN_INTENT`:** Used to present incoming and active call notifications according to Android lock-screen policies.

## 3. Local Data Storage & Backup Rules
- **VOZ Call Assist Database (`voz_call_assist.db`):** Stores your optional contact notes, custom labels, pinned favorites, call-back reminders, and blocked numbers locally in an on-device SQLite (Room) database.
- **Cloud Backup Exclusion:** Both `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` explicitly exclude `voz_call_assist.db` from unencrypted cloud backups.
- **User Control:** You can edit or delete any note, reminder, or blocked number at any time inside the app, or clear all app data via Android System Settings.
