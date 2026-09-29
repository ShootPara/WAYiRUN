# 1 WAYiRUN open work

Status: Feature-development baseline classification
Rule: bugs, unverified behavior, deliberate deferrals, and release work are separate categories. An item MUST NOT move between categories without evidence or an explicit product decision.

## 1.1 Genuine bugs

### 1.1.1 Indoor automatic-pause physical regression acceptance

The user previously observed an indoor run auto-pause while moving. Code now rejects step-counter silence as stationary evidence and requires fresh accelerometer coverage, but the correction has not passed carried-phone and screen-off acceptance. Until that acceptance passes, treat the reported behavior as an open bug with an implemented candidate correction—not as a request to redesign auto-pause.

Acceptance: a carried-phone indoor run does not auto-pause while continuously moving; reliable stillness and resumed movement trigger approximately the accepted five-/two-second transitions; manual pause never auto-resumes.

## 1.2 Implemented but unverified behavior

These items have implementation and automated evidence but lack sufficient real-device or combined-system evidence. They are verification work, not feature TODOs.

- Quantitative outdoor GPS distance, pace and route accuracy against an independent reference.
- Indoor step counts and stride-derived distance on representative hardware.
- Outdoor GPS loss/reacquisition and step fallback without jumps or double counting.
- Long screen-off/background operation, notification accessibility and battery behavior.
- Speaker/headphone audio focus ducking and restoration with YouTube Music.
- Real goal/milestone/completion cue timing over longer runs.
- Physical process-death/reboot recovery and current external-activity return flows.
- Rear/front camera behavior, picker cancellation, rotation, save destinations and share targets.
- Real OpenAI coaching playback after streaming-WAV finalization and appropriate anomalous/test-run tone.
- Health Connect consent, displayed records, retry, account switching and deletion cleanup on a user phone.
- Full phone/web/public/delete reconciliation after connectivity loss.

Comprehensive physical acceptance is intentionally postponed until the release phase. These entries remain visible so implementation presence is never mistaken for runtime proof.

## 1.3 Deliberately deferred features

These ideas are neither bugs nor commitments for the next feature milestone. They require separate product design and approval.

- Desktop week/month/year/lifetime statistics views.
- Desktop sorting/display options and charts.
- Coaching voice selection; Cedar remains fixed.
- Achievement personal-best/negative-split/progression calculations across paused or source-changing runs lacking a continuous reliable curve.
- Automatic download/restoration of cloud photos onto a new phone.

The following are excluded rather than deferred: basemaps, automatic media transport, notification-listener access, social features, calories/general health aggregation, Health Connect imports, and a standalone watch app.

## 1.4 Release and production work

This work is required only when preparing a release. It MUST NOT block the feature-development baseline and MUST NOT begin without an approved release milestone.

- Promote or otherwise package the functional Android product in a release variant; the present release variant is a name-only shell.
- Choose the production application ID and configure production Google OAuth.
- Establish release signing, key recovery/ownership and distribution procedures.
- Establish production Cloudflare Worker/D1/R2 choices, domains, secrets, quotas, observability and deployment controls.
- Complete privacy policy, Health Connect declarations, store disclosures and data-retention decisions.
- Review development-only labels, URLs, OAuth client IDs, build diagnostics and fallback/test assets for release disposition.
- Establish development-deployment parity and later production deployment verification.
- Run comprehensive physical-device, upgrade, battery, sensor, audio, camera, recovery and end-to-end release acceptance.

## 1.5 Closed or superseded items

The following MUST NOT be reopened as cleanup work merely because historical documents mention them:

- Leaflet/OpenStreetMap/static-map/basemap rendering;
- automatic player pause/resume/next/previous behavior;
- notification-listener permission or music-control setup;
- keeping a photo automatically publishing a run;
- every-foreground setup/permission prompting;
- silent account claiming of pre-account runs.

Current replacements are documented in `REQUIREMENTS.md` and `CURRENT_STATE.md`.
