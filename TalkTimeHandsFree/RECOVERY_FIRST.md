# TalkTime: recovery-first procedure

**DO NOT uninstall, clear application storage, or install a replacement APK until after an export or forensic copy.**

## Recover from the currently installed app

1. Open TalkTime on the phone you have been using. Look for **SAVED SHIFTS** and **Backup JSON**.
2. If **Backup JSON** exists, use it first to save `TalkTime-backup.json` to Downloads, then copy it off the phone. Do not use **RESTORE** on the existing app.
3. Open the exported JSON in a text editor. The native version uses `"format":"talktime-handsfree-2"` with an `entries` array and an `active` timestamp. The backup may contain dates, clock-in and clock-out times, and break minutes. Keep the original file unchanged.
4. If no shifts display, still export the JSON if possible; check whether `entries` is empty. If it is empty, that proves only that this version's database export is empty, not that every old version's storage or reminder history is empty.
5. If the phone was running the *browser-based* TalkTime version, its database is separate. Browser-origin local storage must be inspected separately.
6. If TalkTime merely created Google reminders, review the reminders' dates and times for reconstruction. Google reminders are not reliable proof of actual working hours.
7. If no backup button appears, determine the installed app version and package, preserve the phone, and obtain a copy of its private data only using a legitimate Android app backup or an authorized debuggable-app connection. Unrooted production apps usually do not expose their private storage to Android Studio Device Explorer.
8. Check calendar, sent messages, and employer records as possible independent corroboration.

## Source code recovery notes

Original branch: `talktime-handsfree-build` in `johnwaa38/ProjectorLayout-v4-open`.
Application ID: `com.talktime.handsfree`.
Legacy timesheet storage: Android SharedPreferences `talktime_native_timesheet_v2`, key `entries`; active shift key `started_at`.

## Repair branch change

Branch `talktime-storage-recovery-v1` adds a redundant `entries_mirror_v3`, a previous-state snapshot `entries_previous_v3`, verified commits and a storage diagnostics UI button. This is source-level safety work, **not** a verified on-phone fix, and it is **not** a recovery of existing device data. Preserve the original install while locating existing hours.

## Build and test

Open `TalkTimeHandsFree` in Android Studio, sync Gradle and run `assembleDebug`. Build uses Android SDK 35 and Java 17. The new APK may have a different signing certificate and may not install over the original; **never uninstall the original to make room for the new APK until its records have been safely exported**.

Before relying on the new app for payroll, test clock-in, clock-out, edit, review, app restart, phone reboot, CSV and JSON export/restore using a disposable test device or separate app installation. Confirm entries survive process kills, restarts, and power cycles. Test microphone recognition and Android permission behavior on real hardware.

**Test status:** Source patched on GitHub; build, device tests, and record recovery not yet verified.
