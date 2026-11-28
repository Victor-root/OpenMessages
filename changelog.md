# 📋 Changelog

## 🚀 v2.0.0 (not yet released)

A redesign and a rebuild. The whole app has a new look, sending and opening conversations are much faster, and under the hood the app moved to today's Android: new database, new build tools, Android 16 as its target, and no Google build plugins left.

### ➕ Added
- 🗂️ **Conversation menu in a floating card:** the ⋮ button opens the conversation's options right under it (contact, media, rename, notifications, mark unread, archive, block, delete) instead of a separate details page.
- 🖼️ **"See all media" page** gathering the photos and videos of a conversation in a grid.
- 📅 **New scheduling dialog:** times at hand (in 1 hour, this evening, tomorrow morning or afternoon) or any day on a calendar, in the conversation's colour, instead of the system date and time pickers.
- 🕐 **Time and sending state inside each bubble**, as Signal shows them, with day headings only when the day changes.
- 🏢 **Contact's company** shown after the name in the conversation list and under the name in a conversation.
- 📶 **SIM marker** in the conversation list on phones with two SIMs, and SIM icons in the colour Android gives each SIM.
- 🔔 **Unread count badge** on each conversation of the list.
- 👆 **Optional swipe to open the side menu** from the left edge of the conversation list.
- 🔤 **Contacts grouped by initial**, with a letter rail to jump through them.
- 🎙️ **Voice recorder as a bottom sheet** with a new record button.
- 📎 **Attach any file** through the system file picker.
- 🎞️ **Videos shrunk to fit an MMS**, with a warning when one is too long to be sent.
- 🌍 **Translations completed** in every language the app ships, from Arabic to Vietnamese, including Malayalam and Kurdish which had none.

### 🔄 Changed
- 🎨 **A new look throughout:** flat toolbars, rounded blocks for settings and lists, a drawer redrawn, a deeper colour palette, Material 3 dialogs and switches.
- 💬 **Conversation list laid out like Google Messages:** flat rows from edge to edge, bigger rounded avatars, the time above the unread count.
- 🌈 **Each conversation wears its own colour** on every screen opened from it: settings icons, switches, dialog buttons, scheduling, notifications.
- ↩️ **Back button as a slim chevron**, narrow in a conversation so its name keeps the room.
- ✨ **New messages rise into the conversation** from the composer, and the send and record buttons swap with a smooth animation.
- 👆 **Selected messages tinted in the conversation's colour** across the whole row, where the old grey barely showed.
- 📱 **Side menu over the status and navigation bars**, with or without edge-to-edge.
- 🧾 **Bottom sheets run on into the navigation bar** in their own colour instead of over a grey band.

### ⚡ Faster
- 📤 **A sent SMS appears the moment you tap send**, before the system has even finished writing it.
- 📜 **Long conversations open at once:** only the newest messages are loaded, older ones as you scroll up.
- 🏠 **The conversation list no longer stalls** on every change when there are hundreds of conversations.
- ⌨️ **Typing a long message no longer lags** at each new line.

### 🐛 Fixed
- 🧹 **Deduplication no longer deletes** distinct outgoing MMS or unsent texts.
- ↪️ **Forwarding keeps the pictures** and no longer adds the signature twice.
- 💾 **Restoring a backup no longer duplicates** content filters or scheduled messages.
- 📝 **Drafts survive a message sync.**
- 🎨 **Choosing a conversation's colour** no longer shows the app icon tab, and conversations not yet holding a message no longer share one colour.
- 🔢 **"1 message"** instead of "1 messages" in search results.
- 🔔 **Replying from a notification** now dismisses it.
- 📱 **The screen keeps clear of side system bars and camera cut-outs**, and the keyboard no longer takes the whole screen held sideways.
- 🔤 **Missing accessibility descriptions** added on several buttons.

### 🗑️ Removed
- 💬 **The quick reply popup** on Android 10 and later, where the system no longer lets it show reliably.
- 💳 **The leftovers of the Plus upgrade**, billing and referral code from the original app.

### 🔧 Under the hood
- 🗄️ **The app's data moved from Realm to Room.** Existing data is carried over on the first launch of v2.
- 🤖 **Targets Android 16 (API 36)**, built with Gradle 9, AGP 9, Kotlin 2.4 and RxJava 3.
- 🚫 **No more Firebase or Google Services build plugins**, and the Lato font ships inside the app instead of being downloaded.
- 🧩 **Libraries updated** to their newest versions, and the MMS code trimmed to what the app actually runs.

## 🚀 v1.0.1 (2026-08-19)

A bug-fix release. Every issue below is marked with where it comes from. Most are pre-existing bugs from the original codebase rather than regressions introduced during the Open Messages rebrand: they were already present in the code this project forked from, and only ever showed up on certain phones or with certain settings, which is why they went unnoticed until now. The two that came in with v1.0.0 say so.

### 🔄 Changed
- 📏 **The maximum MMS size now defaults to "Automatic"**, meaning whatever limit the carrier itself reports, instead of a fixed 300 KB guessed on its behalf. Where a carrier reports no limit, 300 KB is still what's used, so nothing is lost by asking first. Anyone who has already picked a size keeps it.

### 🐛 Fixed
- 📥 **(inherited from upstream) Conversations imported from another SMS app** no longer land in the inbox if they were archived there before switching to Open Messages.
- 📷 **(inherited from upstream) MMS (photo/video) sends failing instantly** on some phones, where the app mistook a missing SIM preference for subscription "0" and tried to send over a SIM that doesn't exist.
- 📇 **(inherited from upstream) Recipients possibly resolving to the wrong number** on phones whose system address book doesn't lay out its columns in the same order Android's reference implementation does.
- 📏 **(inherited from upstream) MMS sends failing when the "Automatic" size setting was used** and the carrier reported no known limit, which the app mistook for a limit of zero.
- 🗂️ **(inherited from upstream) The navigation drawer getting stuck reopening itself** right after closing it, during the first sync after installing.
- 💬 **(inherited from upstream) A conversation started with a photo staying empty** and missing from the conversation list until the app was left and reopened, because the message was filed under a different conversation from the one on screen.
- 🔁 **(inherited from upstream) Retrying a failed MMS** resending it untouched instead of rebuilding it, so a message that failed for its size failed again at the same size.
- 🐢 **(inherited from upstream) Several photos in one message taking a long time to appear**, each having been encoded at full size only to be discarded, then shrunk by fixed steps that overshot.
- 🖼️ **(inherited from upstream) Photos in MMS coming out far smaller than they needed to be.** The app asked for a sensible image quality and the request was ignored, so every photo was saved at maximum quality and had to give up most of its pixels to fit the size limit.
- 👤 **(inherited from upstream) Conversation shortcuts and share targets showing a coloured initial** in place of the contact's photo.
- 💥 **(inherited from upstream) The app crashing on leaving the conversation details screen**, where the spacing of a row being removed was worked out from a position that row no longer had. The same fault was found and fixed in eleven other lists, where it would have been reached by tapping a row at the moment its list changed.
- 🔤 **(introduced in v1.0.0, not inherited) A message holding several photos reading "Picture" untranslated** in the conversation list. Search results, the home-screen widget and notifications never translated it at all.
- 📎 **(introduced in v1.0.0, not inherited) The attachment menu opening in mid-screen** when the keyboard was up, left where the keyboard had been instead of settling at the bottom of the screen.

## 🚀 v1.0.0 (2026-08-13)

First public release of Open Messages. Everything below is relative to the app this project started from.

### ➕ Added
- 💾 **Multi-category backup and restore:** back up SMS, MMS with attachments, app settings, blocking rules, conversation states (archived, pinned, blocked, custom name, flagged) and scheduled messages. Saved to Documents/OpenMessages by default, a custom folder, or a single .zip, with configurable automatic backups and an in-app manager to rename or delete existing backups.
- 📅 **Scheduled messages shown in the conversation:** a message you've scheduled now appears at the bottom of its conversation, faded and captioned with its send time, instead of only being reachable from a separate screen.
- 🛡️ **Integrated anti-spam blocking:** a phishing-link blocklist, an allowlist, and content filters, built into the app rather than left to a third party.
- 📝 **Saved message templates:** write a message once, reuse it from any conversation.
- 🎨 **Themed launcher icon:** pick the app icon's colour from an in-app picker instead of a single fixed icon.
- 🔊 **Optional sound on send:** off by default, with a choice of tones and a volume slider, independent from the notification sound for incoming messages.
- ⚡ **Configurable quick-action button** in the conversation header.
- 🖼️ **Setting to show or hide the contact photo** in conversations.
- 📋 **"Copy code" action on verification-code notifications**, and more reliable code detection (letter-prefixed codes, multi-group codes, and codes preceded by a shorter number).
- 📱 **Edge-to-edge display setting.**
- 🌐 **A landing page for the project**, under `website/`.

### 🔄 Changed
- 🏷️ **Rebranded to Open Messages**, name, icons, package and branding, with no functional link to the app it was forked from.
- 🖌️ **UI, theme system and dependency injection overhaul**, including a modernized send button, mic button, and a Material bottom-sheet attachment menu in place of the old speed-dial.
- 🗂️ **Backup manager UI redesigned** and moved into the settings list.

### 🐛 Fixed
- 🐛 **A number of bugs inherited from the original codebase.**
