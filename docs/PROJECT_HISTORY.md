# 1 WAYiRUN project history

This is the curated development history of WAYiRUN Version 1.0. Detailed plans, commands, test counts and point-in-time evidence are indexed in `history/README.md`; Git remains the forensic record.

## 1.1 Development approach

The project began from a concrete personal requirement: replace an existing running workflow with reliable offline-first recording, explicit data ownership and a cleaner finish experience. Product decisions were written down before bounded implementation passes. Each pass followed a plan → guardrails → implementation → verification rhythm, with source inspection and focused tests preceding broader gates.

Codex performed the implementation and repository work in small milestones while the owner supplied product decisions and real-device observations. Emulator and automated evidence were never treated as a substitute for phone behavior. Phone-test reports and structured Google Forms feedback were translated into bounded issue lists, reconciled against the product contract, and then verified without silently broadening scope.

## 1.2 September 11–14, 2026 — Android foundation

WAYiRUN began as a focused replacement for a commercial running app: reliable run tracking with a simpler personalized workflow. The first implementation established the run controller, foreground tracking, local Room persistence, interruption recovery, permissions, settings and audio cues.

Physical phone checks were kept distinct from emulator confidence from the beginning. Early reports exposed notification, audio and interaction issues that were corrected without treating compilation as device proof.

## 1.3 September 15–17 — Identity, synchronization and browser history

Cloud functionality was added after local tracking was established. Google identity and owner isolation preceded run synchronization. The design deliberately avoided silently claiming pre-account runs: local history remains unowned until explicit import.

The Cloudflare Worker gained sessions, resumable archive storage, deletion tombstones, restore and a private browser history. Export and deliberate deletion extended the browser from inspection into account-owned record management.

## 1.4 September 18–19 — Coaching and richer records

Per-account OpenAI-key storage, coaching generation, playback and history were added without a shared application key. Achievements, end-of-run photos, public individual-run pages and Health Connect export followed.

These features retained stable run identity so coaching, media, awards, publication and deletion could be reconciled rather than maintained as unrelated side data.

## 1.5 September 22–28 — Stabilization and product corrections

The project underwent a broad issue pass covering completed-run reopening, setup controls, announcements, synchronization, photos and automatic pause behavior.

Several product directions changed explicitly:

- Basemap-backed routes were replaced by provider-independent route geometry.
- Automatic transport control of external music was removed; playlist opening and temporary audio ducking remained.
- Publication became private by default and independent from keeping a photo.
- Permission prompting and account ownership behavior were tightened.

Photo synchronization recovery and matching development deployment preserved existing user data while repairing queue and status behavior.

The issue-and-quibble reconciliation converted accumulated phone feedback, test findings and product-direction changes into one ordered implementation sequence. That prevented superseded ideas from reappearing merely because older plans still mentioned them.

## 1.6 September 29–October 6 — Reconciled feature baseline

Divergent branch history was reconciled around the accepted product direction rather than flattened for appearance. The detector/GPS auto-pause policy replaced earlier counter- and acceleration-based candidates. Weather reliability, route-noodle presentation and the Settings information architecture were corrected and verified.

The result was a coherent functional baseline with historical alternatives retained as evidence rather than allowed to control current behavior.

## 1.7 October 6–7 — Productionization

Productionization created permanent Android identity, owner-controlled signing, dedicated production OAuth clients, a separate guarded production Worker/D1 environment and a production custom domain.

Existing development data was treated as irreplaceable. Migration tooling produced deterministic data-only exports, repeated rehearsal imports and complete reconciliation before the final frozen-source import. Development and production were kept separate, and development was preserved rather than reset or repurposed.

Production browser behavior and retained data were verified, and the source produced an owner-signed Version 1.0 APK. Google Play was intentionally removed from the Version 1.0 critical path while preserving it as a future distribution option.

## 1.8 Repository consolidation

After productionization, accumulated plans, phone reports, test handoffs and setup notes were moved under `docs/history/`. Current authority was consolidated into a small root documentation set so the repository presents the product as it exists rather than as a sequence of unfinished construction milestones. Private working material was purged from reachable history, while technical milestone evidence and the meaningful development narrative were retained.
