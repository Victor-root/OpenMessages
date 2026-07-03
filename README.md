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

### 📋 Main screen
* Gradient header, pill-shaped unread badge, redesigned drawer, scroll-to-top button and collapsible
  search.

### 💬 Messaging
* Saved message **Templates** (optional titles, insert from inside a conversation).
* **Expandable message input** with a full-screen editor.
* **Read/unread swipe** toggle with a matching envelope icon; defaults are swipe right = mark read,
  swipe left = archive.
* Redesigned signature settings, and the target conversation flashes when you jump to the next unread.

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

### 🔒 Privacy
* The voice-input (speech-to-text) button is **off by default**.

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
