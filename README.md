# carplauncher: a personal focus fork of Olauncher

> **Modified version.** This is a personal fork of [Olauncher](https://github.com/tanujnotes/Olauncher)
> by tanujnotes, modified starting 2026-11-12 to add the focus features below. It stays under the
> same license (GNU GPLv3, see `LICENSE`); all upstream copyright notices are kept. It's sideloaded
> for personal use and isn't published to any store. The upstream README follows the divider.

All new code is in `app/src/main/java/app/olauncher/focus/` (logic and storage) plus a few
`ui/` files. Upstream files only get small hook points, so upstream changes merge cleanly.
**No permissions were added.** Everything is stored on the device: a Room database (`focus.db`)
for the launch log and the `app.olauncher.focus*` SharedPreferences files for settings.

## Features

All settings are in the **Focus** card in Settings (long-press the home screen).

### 1. Frequent apps row
Every launch made *through this launcher* is recorded locally (package, profile, time) and kept
for 30 days. Home shows a small row of your most-launched apps over a rolling window, leaving out
apps already in your home favorites and hidden apps. Ties go to the most recently launched app.

| Setting | Default | Options |
|---|---|---|
| Frequent apps on home | 4 | 0 (off) – 8 |
| Frequency window | 7 days | 1, 3, 7, 14, 30 days |

### 2. Faster app drawer
- **Usage-sorted:** the drawer lists your most-launched apps (30-day count) first and is
  alphabetical among equals. The pickers (set home app, swipe app…) and the hidden-apps list stay
  alphabetical.
- **Letter scrubber** on the drawer edge: tap or drag to show only apps starting with that letter
  (still in usage order). Tap the active letter again, or start typing, to clear it. The list isn't
  alphabetical, so the scrubber filters instead of jumping.
- **Fuzzy search**, from best to weakest match: exact → prefix → word prefix ("map" → Google *Map*s)
  → substring → word initials ("gm", "gmaps" → Google Maps) → letters in order ("tlgm" → Telegram,
  3+ characters). Within a tier, more-launched apps come first. Anything upstream's search matched
  still matches.
- **Launch when search has one match** (Settings → Focus, default **on**, same as upstream). Starting
  the query with a space still skips it once, as before.

### 3. Modes (contextual favorites)
Named modes (e.g. Work, Evening, Weekend), each with its own home apps and app count. The
existing favorites become the **Default** mode, and a new mode starts as a copy of the current
favorites. While a mode is active, edit its favorites the usual way (long-press a home app).

- **Switch manually:** once you have 2+ modes, a small label above the home apps shows the active mode.
  Tap it to cycle; long-press it to pick one. You can also use Settings → Focus → Modes → *mode* → Switch to.
- **Schedule** (optional, per mode): weekdays + start/end time. A window that ends at or before its
  start time runs past midnight and belongs to the day it starts (Fri 22:00–02:00 covers
  early Saturday). Equal start and end means all day. If windows overlap, the one that started
  most recently wins. Outside every window, Default is active.
- A **manual switch holds until the schedule next changes**, then the schedule takes over again.
- The schedule is applied whenever the home screen is shown, so there are no background alarms.

### 4. Friction for distracting apps
Flag an app in the drawer: long-press it, then tap **Flag** (tap **Unflag** to undo). Pinned
shortcuts follow their app's flag. When you open a flagged app **from this launcher**:

1. If you've hit the **daily launch limit**, the launch is refused until local midnight.
2. Otherwise a **countdown** runs before *Open* is enabled, and optionally you have to type
   **why you're opening it** (the answer isn't stored).

| Setting | Default | Options |
|---|---|---|
| Distracting apps | none | review/unflag the list |
| Pause before opening | 10 s | off, 5, 10, 20, 30, 60 s |
| Ask why | off | on/off |
| Daily launch limit | off | off, 1, 2, 3, 5, 10 |

**Scope:** this only covers launches made *from this launcher* (home favorites, frequent row, drawer,
swipe gestures, clock/date shortcuts). Notifications, links from other apps, the recents
screen and other launchers open the app directly. There's no accessibility service or overlay.

## Building

```sh
echo "sdk.dir=/path/to/android-sdk" > local.properties   # platform 36 + build-tools
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk (id app.olauncher.debug)
./gradlew testDebugUnitTest    # pure logic tests + Robolectric smoke tests
```

The debug build installs alongside upstream Olauncher (`applicationId` suffix `.debug`).

---

![Olauncher](https://repository-images.githubusercontent.com/278638069/db0acb80-661b-11eb-803e-926cae5dccb4)


# Olauncher | Minimal AF Launcher
AF stands for Ad-Free! :D

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
    alt="Get it on Play Store"
    height="80" align="middle">](https://play.google.com/store/apps/details?id=app.olauncher)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80" align="middle">](https://f-droid.org/packages/app.olauncher)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png"
    alt="Get it on IzzyOnDroid"
    height="54" hspace="13" align="middle">](https://apt.izzysoft.de/packages/app.olauncher)

#### Install using [Play Store,](https://play.google.com/store/apps/details?id=app.olauncher) [F-Droid,](https://f-droid.org/packages/app.olauncher) [IzzyOnDroid](https://apt.izzysoft.de/packages/app.olauncher) or the [latest APK.](https://github.com/tanujnotes/Olauncher/releases/)

To maintain the simplicity of the launcher, a few niche features are available but hidden.

Please check out the **[About](https://tanujnotes.substack.com/p/olauncher-minimal-af-launcher?utm_source=github)** page in the Olauncher settings for a complete list of features and **FAQs**.

##

License: [GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html)

Developer: [@tanujnotes](https://x.com/tanujnotes)

##

### Other apps from team Olauncher:

- [Pro Launcher](https://play.google.com/store/apps/details?id=app.prolauncher) - Pro version of Olauncher with extra features like widgets, weather, folders, etc.

- [Note to Self](https://play.google.com/store/apps/details?id=com.makenotetoself) - Free and [open source](https://github.com/jeerovan/ntsapp) notes app with chat like interface and end-to-end encryption.

- [Pentastic](https://play.google.com/store/apps/details?id=app.pentastic) - Free and [open source](https://github.com/tanujnotes/Pentastic). A minimal tasks, notes and reminders app.

- [Supernova](https://github.com/tanujnotes/Supernova) - Free and [open source](https://github.com/tanujnotes/Supernova) alternative of Nova Launcher.

##

Thank you!
