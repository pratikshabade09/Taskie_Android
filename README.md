# Taskie — Smart Voice-Enabled Productivity App

> "Think less, act faster."

Taskie is a modern Android productivity app with an integrated voice assistant, smart task intelligence, and a premium dark UI.

---

## Quick Start

### Requirements
| Tool | Minimum version |
|------|----------------|
| Android Studio | Hedgehog (2023.1.1) or newer |
| Android Gradle Plugin | 8.2.2 |
| Gradle | 8.4 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| Java | 17 |

### Steps
1. **Clone / unzip** this project.
2. Open in **Android Studio → File → Open** (select the `Taskie/` root folder).
3. Let Gradle sync complete.
4. Set `sdk.dir` in `local.properties` to your Android SDK path (Android Studio does this automatically).
5. Connect a device or start an emulator (API 26+).
6. Press **Run ▶**.

---

## Architecture

```
com.taskie.app/
├── data/
│   ├── model/          Task.java              — Room entity
│   ├── dao/            TaskDao.java           — LiveData queries
│   ├── database/       TaskDatabase.java      — Room singleton
│   └── repository/     TaskRepository.java    — Single source of truth
├── viewmodel/          TaskViewModel.java     — Shared ViewModel
├── voice/
│   ├── VoiceManager.java   — SpeechRecognizer + TextToSpeech
│   └── IntentParser.java   — Rule-based NLP
├── notification/
│   ├── NotificationHelper.java    — Channel + AlarmManager
│   ├── NotificationReceiver.java  — Broadcast actions (Done/Snooze)
│   ├── BootReceiver.java          — Re-schedule after reboot
│   └── DailySummaryWorker.java    — WorkManager 8 AM digest
├── ui/
│   ├── splash/         SplashActivity.java
│   ├── main/           MainActivity.java      — Bottom nav host
│   ├── dashboard/      DashboardFragment.java — Sectioned task list
│   │                   TaskAdapter.java
│   │                   SwipeActionCallback.java
│   ├── addedittask/    AddEditTaskActivity.java
│   ├── voice/          VoiceFragment.java
│   ├── analytics/      AnalyticsFragment.java
│   └── settings/       SettingsFragment.java
├── utils/
│   ├── DateUtils.java             — Formatting + range helpers
│   └── SmartSuggestionEngine.java — Pattern learning + streak
└── TaskieApplication.java         — App init + WorkManager schedule
```

---

## Feature Guide

### Voice Assistant
- Tap the **mic button** on the Voice tab.
- Speak naturally: *"Submit the report tomorrow at 3 PM"*
- IntentParser extracts: title, date, time, priority, tag.
- TTS reads back the confirmation.
- Tap **Add Task** to save or **Cancel** to discard.

**Supported voice patterns**
| What you say | What gets extracted |
|---|---|
| "Call John tomorrow at 6 PM" | title=Call John, date=tomorrow, time=18:00 |
| "Urgent meeting on Friday at 10 AM" | priority=High, day=Friday |
| "Buy groceries this weekend" | date=Saturday, tag=Personal |
| "Study for exam next Monday at 9 AM" | tag=Study, date=next Monday |
| "Pay electricity bill" | tag=Finance, priority=Medium |

### Task Sections
| Section | Condition |
|---|---|
| **Overdue** | `is_completed = 0 AND due_date < now` |
| **Today** | `is_completed = 0 AND due_date in today's range` |
| **Upcoming** | `is_completed = 0 AND due_date > today` |
| **Recently Completed** | Last 10 completed tasks |

### Swipe Gestures
- **Swipe right** → Complete (green background + check icon)
- **Swipe left** → Delete (red background + trash icon)
- Snackbar undo after completion

### Notifications
| Type | Channel | When |
|---|---|---|
| Task reminder | `taskie_reminders` | At `reminder_time` via AlarmManager |
| Overdue alert | `taskie_overdue` | Missed task |
| Daily summary | `taskie_summary` | 8 AM via WorkManager |

Notification actions: **Done** · **Snooze 30m** · **Reschedule +1h**

### Smart Intelligence
- **Priority auto-detect**: title words like "urgent", "asap", "deadline" → High
- **Time pattern learning**: records completion hour per tag in SharedPreferences
- **Streak tracking**: consecutive days with at least one completion
- **Insights**: "You usually do Study tasks at night"

---

## Permissions Required

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Voice recognition (requested at runtime) |
| `POST_NOTIFICATIONS` | Task reminders (requested at runtime, API 33+) |
| `SCHEDULE_EXACT_ALARM` | Precise reminder timing |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule alarms after reboot |

---

## Color Palette

| Token | Hex | Use |
|---|---|---|
| Background | `#0F172A` | Screen background |
| Card | `#1E293B` | Task cards, sections |
| Accent | `#14B8A6` | Primary actions, FAB, checkboxes |
| Text primary | `#F1F5F9` | Titles, body |
| Text secondary | `#94A3B8` | Subtitles, meta |
| Priority High | `#EF4444` | Red |
| Priority Medium | `#F59E0B` | Amber |
| Priority Low | `#22C55E` | Green |

---

## Extending Taskie

### Add a new tag
In `Task.java`, add a constant:
```java
public static final String TAG_TRAVEL = "Travel";
```
Then add the keyword mapping in `IntentParser.java`:
```java
put("flight",  Task.TAG_TRAVEL);
put("hotel",   Task.TAG_TRAVEL);
```

### Add cloud sync (future)
`TaskRepository` is the only data-access point. Swap the Room DAO calls for Firebase/Firestore calls here — the rest of the app doesn't need to change.

### Add ML priority detection
Replace `SmartSuggestionEngine.detectPriorityFromTitle()` with an on-device ML Kit text classification call — same return type (`int`), zero UI changes needed.

---

## Known Limitations / Future Work
- Voice recognition requires an active internet connection on most devices (Google's SpeechRecognizer uses cloud by default).
- Offline voice recognition can be enabled via `EXTRA_PREFER_OFFLINE` in `VoiceManager`.
- The launcher icon is a placeholder PNG — replace with a proper adaptive icon in production.
- `gradlew` is a stub — Android Studio will download the real Gradle distribution automatically.

---

## License
MIT — build freely, ship confidently.
