<p align="center"><strong>📱 Screenshots</strong></p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/94f53cd8-d569-4cec-b7a0-c38e97471f28" width="250" alt="OpenMessages mobile home screen"/>
  <img src="https://github.com/user-attachments/assets/65b431b9-7fef-42a4-b139-50ef798c25cf" width="250" alt="OpenMessages mobile home screen"/>
  <img src="https://github.com/user-attachments/assets/70d8da96-81df-4ea2-8b5c-dc7e3a40324b" width="250" alt="OpenMessages mobile home screen"/>
</p>

## ✨ What's different from upstream

Open Messages is a rebrand of QUIK (itself a fork of QKSMS), package `io.openmessages`, with UX,
theming and privacy improvements layered on top. The highlights:

### 🛡️ Privacy-first spam & phishing blocking
* On-device blocking in two clear sections: **Integrated** (no third-party app) and **External apps**
  (optionally delegate to Call Control, Should I Answer? or Call Blocker).
* Opt-in **anti-phishing** source: messages whose links point to known phishing sites.
* **"Suspected spam"**: borderline senders stay in your inbox with a discreet label and an
  in-conversation **Approve / Block** banner, or go straight to the blocked list. The **allowlist**
  permanently approves a sender.
* **Offline-first**: no network connection on its own. The phishing list downloads once when you
  enable it (from [The Block List Project](https://github.com/blocklistproject/Lists)); all matching
  then happens on your device.

### 🎨 Theming & appearance
* Tabbed theme picker with **live in-app updates**: theme, dark mode and text size crossfade instead
  of flashing, no restart. **Per-conversation colors** use the same picker.
* **Pure-black (AMOLED) mode** for OLED screens.
* Dedicated **App icon** tab: color the launcher icon independently or have it follow the theme; it
  swaps instantly and keeps the splash and recent-apps colors in sync. New Tabler-style launcher icon.
* **Material 3 switches**, themed rounded dialogs, a themed unread badge, refreshed drawer and
  blocking/notification-row icons, and system-bar icons kept legible on every color.
* **Themed circular send and mic buttons**, and a redesigned attachment picker as a themed bottom
  sheet.
* **Optional contact avatar** in conversations: turn it off to give message bubbles more width.
* Optional **edge-to-edge** mode (off by default): content draws behind the transparent system bars,
  with a lighter nav-bar veil in light theme and a status-bar scrim that fades in as the main list
  scrolls behind it.

### 📋 Main screen
* Gradient header, pill-shaped unread badge, redesigned drawer, scroll-to-top button and collapsible
  search.

### 💬 Messaging
* Saved message **Templates** (optional titles, insert from inside a conversation).
* **Expandable message input** with a full-screen editor.
* **Read/unread swipe** toggle with a matching envelope icon; defaults are swipe right = mark read,
  swipe left = archive.
* Redesigned signature settings, and the target conversation flashes when you jump to the next unread.
* **Configurable conversation header**: optionally hide the country code prefix in the title, and
  add a quick-action button next to the call icon (Archive, Mark unread, Block, Delete, or none).
* **Optional sound on send**: a short confirmation tone, with a choice of 5 tones, including a
  brighter one that cuts through background noise, previewed live before you pick.

### 💾 Backup & restore
* **Full local backup and restore**, on-device with no cloud or account. Pick what to include:
  **messages (SMS, MMS and attachments)**, **settings** (including your templates), **blocking
  rules**, **scheduled messages** and the **archived / pinned** state of conversations.
* Saved to **Documents/OpenMessages** or a **custom folder**, as a dated folder or a single **.zip**;
  restore from either.
* **Automatic backups** (off, daily, weekly or a custom interval), plus a built-in **manager** to
  rename or delete backups from inside the app.
* MMS attachments are **streamed to separate files** to stay light on memory, and restoring
  **scheduled messages** re-arms their alarms.

### 🔔 Notifications
* Notifications show **"Messages"** as the app name in the notification panel.
* **Copy codes from notifications**: an authentication or one-time-code SMS gets a **Copy code**
  action on its notification, so you paste the code without opening the app. Detection is keyword-based
  (English and French) to avoid grabbing unrelated numbers. On by default, with a toggle in
  Notification settings.

### 🔒 Privacy
* The voice-input (speech-to-text) button is **off by default**.

---

## 🗺️ Roadmap

* **Migrate the local database from Realm to Room.** Realm Java, which this app still runs on, was
  deprecated by MongoDB in September 2024 and no longer receives fixes; a 16 KB
  device page-size crash already had to be worked around for that reason. Room is Google's actively
  maintained equivalent, ships in every modern Android app (including on F-Droid), needs no Google
  account or Play Services to run, and removes this dependency risk for good. This isn't urgent (the
  current setup works and has no known issue left open), but it's the direction this project is
  headed: a sizeable, dedicated migration, not something to rush.

---

## 🎯 OpenMessages will simply be a place where I can

* 🔧 implement changes upstream does not want
* 🐛 fix issues I personally encounter
* 📱 improve the Android user experience
* 🤖 experiment freely with modern development workflows
* 🌱 maintain a version aligned with my own vision of open source

---

## 🚀 Why this fork exists

OpenMessages started from a few small improvements I wanted while using Open Messages as my daily SMS
app.

Like many developers today, I use AI tools as part of my workflow, *not to blindly generate code*,
but to understand codebases faster, test ideas, and work more efficiently while still **reviewing
everything myself**.

My first instinct was to contribute upstream. But after submitting a small contribution, I realized
some projects are still more focused on ***how*** code is written than on the **actual quality of the
contribution itself**. I disagree with that approach: good code should be judged on the final result,
maintainability, and whether the contributor understands what they are shipping, not on whether
modern tools were involved. So instead of stopping there, I decided to maintain my own fork.

---

## 💭 Philosophy

**Open source is freedom.**

Sometimes that means contributing upstream.
Sometimes that means building your own path.
