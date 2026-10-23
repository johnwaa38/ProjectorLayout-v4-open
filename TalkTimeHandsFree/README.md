# TalkTime Hands-Free: Android app

This is a genuine native Android application project (Java, minimum Android 8; package `com.talktime.handsfree`). It does not require a subscription, online account, or paid API. The record store is private and local. The speech recognizer installed on the phone may use the internet, depending on the provider and downloaded language models.

## Voice commands

After enabling "Hands-free listening" once while the app is on screen, keep a persistent microphone foreground-service notification. Say:

- `Hey TalkTime, clock me in.`
- `Hey TalkTime, set my start time to 1 PM.`
- `Hey TalkTime, clock me out.`
- `Hey TalkTime, I worked 8 AM to 4:30 PM with 30 minutes lunch.`
- `Hey TalkTime, how many hours this week?`
- `Hey TalkTime, stop listening.`

If you only say `Hey TalkTime`, the app answers "I'm listening" and accepts a follow-up command for 12 seconds. It gives a spoken confirmation after saving. AM/PM is required for a single-digit start time so it does not silently save the wrong time.

## Limitations (important)

Android restricts microphone services from starting in the background. A user must first open the app, give microphone permission and press Enable. An ongoing foreground microphone service may continue while the phone is locked, but Android may stop it due to power management or restrictions. It cannot reliably auto-restart from cold boot. No special Google Assistant integration is needed.

The built-in Android SpeechRecognizer API is **not intended for continuous listening**. This release experimentally restarts the recognizer after it finishes. On some phones or speech providers, repeated sessions fail. Battery and data use can be high. This is not a production-grade 24/7 offline wake-word engine. A reliable production implementation would use a dedicated on-device keyword spotting engine plus speech recognition only after the wake word, and needs a properly packaged model and Android hardware tests.

To stop, use the button or **Stop listening** in the persistent notification. A nonmatching conversation is ignored (not stored in shift records), but the device's speech recognition provider may process it. This app itself does not request the INTERNET permission.

The native database is separate from the earlier browser-based TalkTime version. The app supports CSV and JSON exports and the app's own JSON backup restore. Deleting the app wipes its private storage unless backed up.

## Build

Run with Android SDK 35, JDK 17 and Gradle 8.9:

```bash
gradle assembleDebug --no-daemon
```

Output: `app/build/outputs/apk/debug/app-debug.apk`, signed with Gradle's automatically generated debug key.

A GitHub Actions workflow in `.github/workflows/build-apk.yml` installs the necessary SDK and Gradle and builds the APK on Ubuntu. In a repository where this project is nested in `TalkTimeHandsFree`, its workdir is configured for that directory.

## Verification scope

- The pure-Java speech parser was compiled and exercised using the included `tests/CommandParserTest.java` with `javac` (no Android SDK necessary).
- The rest of the app requires Android SDK compilation and physical device testing, especially background service, microphone permissions, Bluetooth headsets, and lock-screen voice recognition.

## Verified October 22, 2026

Free GitHub Actions run `39810926649` compiled successfully using Java 17, Gradle 8.9 and Android SDK 35. The built, v2-signed debug APK (SHA-256 `284ab30e2e07ca9e20fe49d295cf3c96ba644b101e0a7a262bec7ce6e93dcea3`) was retrieved and archive-tested. The on-device microphone and screen-off behavior have **not** been tested on a physical Android phone, so always check the saved entry before relying on it for payroll.

Build source is currently on the separate branch `talktime-handsfree-build` of the user's public `ProjectorLayout-v4-open` GitHub repository. This is unrelated to ProjectorLayout and did not modify the `main` branch. The TalkTime app contains no personal timesheets or user information in its source.


## Voice launch with Google (2.1)
Say **Hey Google, open TalkTime** while the Android phone is unlocked. TalkTime starts a one-shot microphone session without another tap; follow up with **Set my start time to 1 PM**, **clock me in**, **clock me out**, or **how many hours this week**. Google itself handles the wake word. App Actions for direct commands from Google require Play Store publishing/Google review and are **not** enabled by sideloading this APK. Opening other apps from the lock screen generally requires unlocking. Android's speech provider may need Internet.

The original optional TalkTime continuous foreground microphone service remains available as a separate mode; the Google launch mode does not keep the microphone open between commands. If updating from a previous GitHub-generated debug APK fails because of different debug signing keys, **export your JSON backup before uninstalling**, then import it in the new version.

## 2.2 Daily evening hours review (October 22, 2026)

- Daily reminder is **enabled by default** and aims for **7:30 PM device local time**. Android can defer alarms to preserve battery, so 7:30 is approximate. A notification contains the recorded start, finish, breaks, and total, or asks for missing finish time if a shift is still open.
- Notification includes **Looks right** (saves confirmation only for a completed shift with no open shift) and **Review / correct** (opens TalkTime, speaks the question and listens for a reply when supported). Notifications require the user to allow Android notification permission, and can be disabled in the app.
- Supported voice replies: **Yes, that's right**, **I finished at 5 PM**, **Set my end time to 5 PM**, **Correct my hours to 8 AM to 4:30 PM** (replaces only when exactly one completed shift exists for the day). No ambiguous standalone times are silently accepted.
- This is **not 24/7 background voice recognition**. At 7:30 PM the app delivers a standard Android notification. It can speak the review and hear a reply after opening the app; Android may require unlocking the phone. It does not secretly open the microphone when an alarm fires. The notification may be delayed or suppressed by Do Not Disturb, disabled notifications, or battery restrictions.
- Android may refuse updates of a previously installed GitHub-built debug APK due to different debug signing keys. **Before uninstalling, export a JSON backup**. A new install erases old local records. This version uses the same on-device JSON backup format as 2.1.
- Source build: `gradle -p TalkTimeHandsFree assembleDebug --no-daemon` from the repo root, or `gradle assembleDebug` within `TalkTimeHandsFree`. Build verifies compilation only; physical Android voice, notifications, reboot, and screen-off behavior require device tests.

## 2.3: Spoken Google-to-TalkTime handoff

When Google opens TalkTime, wait until Google releases the screen; TalkTime now says **“TalkTime ready. Tell me your start or finish time.”** and begins one recognition session *after* speaking. Say **“I started at 1 PM”**, **“Clock me out”**, or **“I finished at 5 PM”**, **without repeating Hey Google**. This is a two-stage exchange, not a native direct Google Assistant command. It requires Android microphone permission, audio focus and an installed speech recognizer. Google App Actions direct-phrase forwarding would require approved Play Store publication. The 7:30 PM daily review remains in this version. Tests of app launch and microphone still need an actual Android phone.