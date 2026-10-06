# 1 WAYiRUN settings information architecture

Status: Current Settings UI contract

## 1.1 Purpose

The Android Settings experience is one vertically scrolling screen of collapsible category cards. Every category is collapsed when Settings opens, and users may expand more than one category at a time. Emoji provide visual anchors while text remains the accessible identity of every category and setting.

This organization changes presentation only. Existing preference keys, defaults, account workflows, integration behavior, run capture, and runtime consumers remain authoritative.

## 1.2 Category map

| Category | Current controls and content | Existing state or workflow |
| --- | --- | --- |
| 🏃 Run Tracking | Auto-pause, countdown, indoor stride value, stride unit | `auto-pause-enabled`, `countdown`, `stride-entry`, `stride-unit` in `local-settings` |
| 🔊 Audio & Milestones | Announcement master, time channel and interval, distance channel and interval | `announcements-enabled` plus the keys owned by `AnnouncementPreferences` |
| 📱 Run Display | Miles/kilometers, dark mode | `units`, `dark` in `local-settings` |
| 📸 After Your Run | Existing finish-choice explanation and weather-provider/license links | Per-finish photo, coaching, and sharing choices remain in the finish flow; no new preference is introduced |
| 🔗 Connected Services | YouTube Music playlist, Health Connect, OpenAI key | `music-playlist`; existing Health Connect state/workflow; existing account-scoped coaching-key API |
| ⚙️ Account & App | Google account, cloud synchronization and local-run import, permission actions, notification settings, build information | Existing account/session, Room synchronization state, and Android system settings actions |

## 1.3 Interaction rules

Category expansion is transient UI state and MUST NOT write preferences. Expanding one category MUST NOT collapse another. Changing a setting saves immediately through its existing persistence path; there is no Save button.

Settings changed during an active, paused, or countdown run apply only to the next run. The captured settings on the current run remain unchanged.

Subordinate announcement controls retain their configured values when their parent switch is disabled. The controls appear disabled while the parent is off and become usable again with their prior values when it is restored.

## 1.4 Responsive and accessibility rules

Category headers provide button semantics and expanded/collapsed state. Switches, selectable choices, text fields, sliders, and action rows retain their native Compose semantics and minimum touch sizes. At enlarged font scales, setting labels and trailing controls stack vertically to avoid collisions. Emoji supplement rather than replace text labels.

## 1.5 Scope boundaries

The Settings screen MUST expose the real current controls without adding conceptual mockup settings. In particular, there is no public-by-default option, GPS fallback selector, pace-display selector, automatic finish-photo option, or global coaching toggle. Stride remains blank until the user supplies a measured value, and runs remain private unless explicitly shared.
