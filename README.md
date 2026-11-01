# NYXNOTE

Offline-first personal organizer for Android, built with Kotlin, Jetpack Compose and a private SQLite database.

## Included in the current implementation
- Today dashboard and task checklist with completion
- Goals with progress tracking
- Notes list and text search
- Focus / Pomodoro timer with recorded sessions
- Productivity summary
- Learning tracker
- Habit list
- Personal journal
- Idea laboratory
- Clock screen
- Basic offline helper for counts and daily planning
- Accent color selection (cyan, violet, green)
- GitHub Actions debug APK build

The app does not request the INTERNET permission. Personal records are stored locally on the device.

## Build
GitHub Actions runs `gradle :app:assembleDebug` on pushes to `main`. The APK artifact is named `NYXNOTE-debug-apk`.

## Important
This is the initial functional app foundation, not a claim that every advanced item in the full brief is finished. Biometric vault, speech-to-text, recurring reminders/alarms, habit streak calendar, XP/achievements, note tags/links/templates/trash, full backup/restore, home-screen widget, and a real on-device AI model still need implementation and testing on an Android device.
