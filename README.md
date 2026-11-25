# Cakeday 🎂

An Android app that reads a family-tree GEDCOM (`.ged`) file and shows each day's family events in two panels:

- **Celebrate**: birthdays, wedding anniversaries and other events for people who are living, or calculated to be living.
- **Remember**: the same kinds of events, plus death and burial anniversaries, for people who are no longer living.

## Features

| | |
|---|---|
| **Import** | Settings → *Import .ged file*. Cakeday reads the file and keeps a private copy. **Your original file is never changed.** |
| **Day / Week / Month** | Bottom tabs. Month view shows a calendar with green (living) and purple (remembered) dots, and the two panels for the selected day. |
| **Home person** | Choose a home person (usually you). Every event and person then shows the relationship, e.g. *1st cousin once removed*, *Great-great-grandfather*, *Sister-in-law*, *Aunt (by marriage)*. |
| **Search** | Search by name, or by name and year ("John Smith 1920"). |
| **Edit anyone** | Name, sex, birth and death dates and places, living-status override, phone number, notes, marriage dates and divorce, plus your own yearly events. Edits are saved separately and layered over the tree, and you can revert each person or all of them. |
| **Google Calendar** | Tap the calendar icon on any event to add it as a yearly, all-day event. Google Calendar opens prefilled (or another calendar app if Google Calendar isn't installed). |
| **Contacts** | On a living person's page, use *Find in my contacts* to see matching contacts and call or text them. On a birthday or anniversary, the text comes prefilled with a greeting. |
| **Notifications** | One daily summary at a time you choose. Filters: living only / deceased only, by event type (birthdays, anniversaries, deaths, other life events, your own events), milestone years only (1, 5, 10, 25, 50…), by relationship (everyone / anyone related / blood relatives / close family), an advance reminder (1 day to 2 weeks ahead), and a *Send a test notification* button. |
| **Portrait only** | The screen does not rotate when the phone is turned sideways. |

### How "living" is worked out
A person counts as deceased if there is any death, burial, cremation or probate record (or a `DEAT Y` flag). Otherwise they count as living if their birth year is within the age limit you set (default 105). When a person has no birth date, it is estimated from their parents, children, spouses, siblings and marriage. You can override anyone from their Edit screen.

## Building

1. Open the project folder in **Android Studio** (Ladybug or newer) and let Gradle sync.
2. Plug in a phone with USB debugging enabled, or start an emulator, then press **Run ▶**.

From the command line: `./gradlew assembleDebug`. The APK is written to `app/build/outputs/apk/debug/`.

Unit tests for the parser, relationship, living-status and event logic: `./gradlew test`.

A small sample tree is in `app/src/test/resources/sample.ged` if you want to try the app before using your own file.

## Project layout

```
app/src/main/java/com/randaladams/cakeday/
  gedcom/GedcomParser.kt        GEDCOM 5.5 / 5.5.1 / 7 reader (UTF-8, UTF-16, ANSI)
  model/                        Person, Family, GedDate, edits, settings
  logic/LivingCalculator.kt     living / deceased, with birth-year estimates
  logic/RelationshipCalculator.kt  cousin / great-uncle / in-law names
  logic/EventEngine.kt          yearly events per day (Feb 29 → Feb 28 in non-leap years)
  logic/NotificationFilter.kt   applies the notification settings
  data/Repository.kt            import, private copy, edits overlay, state
  notify/Notifications.kt       WorkManager daily summary
  ui/                           Jetpack Compose screens
```

Tech: Kotlin, Jetpack Compose (Material 3), WorkManager. Minimum Android 8.0 (API 26).

## Permissions
- **Notifications**: only asked when you turn on the daily summary.
- **Contacts (read)**: only asked when you tap *Find in my contacts*.
- Calls, texts and calendar events go through your own phone, Messages and Calendar apps, so the app needs no permission for them.
