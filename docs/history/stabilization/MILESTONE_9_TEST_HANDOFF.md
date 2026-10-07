# 1 Milestone 9 test handoff

## 1.1 Status and scope

Phase 2 verification is complete. This milestone changes only Worker coaching classification, prompt guidance, tests and documentation. Earlier pending changes remain preserved. No deployment, paid provider call, Android build, phone install, commit or push occurred.

## 1.2 Classification contract

`run-quality.ts` derives `current.quality` after the current archive passes existing ownership/hash/record checks. The full current/previous archives remain present; the previous run is not classified as the subject. No stored run, archive hash, transport schema, job state or saved coaching is rewritten. Existing completed jobs keep their original messages. The augmented input goes through the existing byte/token bounds and identical count/text request path.

Strict test thresholds: active duration below 90,000 ms OR distance below 80.4672 meters (the authoritative 0.05-mile requirement). Vehicle evidence: 25,000 ms of consecutive pairs strictly faster than 1609.344/210 m/s from measured distance, or above 8 m/s from GPS coordinates. Each pair must have positive elapsed time no greater than 10 seconds, in the same measurement segment/source. Pauses/resume and recovery create new segments. Source changes, missing values, backwards timestamps/totals and long gaps reset the sustained window. Active snapshot timing may lag sensor timing; measurement elapsed uses the larger active/monotonic difference. No overall-average shortcut fills missing evidence.

GPS jump evidence: same-segment displacement of at least 100 meters at more than 20 m/s, or positive displacement at zero elapsed time. GPS route evidence requires accuracy at most 25 meters and subtracts both endpoint accuracy radii from displacement. Measurement and route evidence can overlap; gpsJumpEvidenceCount counts evidence pairs, not unique incidents. Isolated jumps reset the sustained window and do not themselves prove vehicle travel. Classification precedence: gps_anomaly, likely_vehicle, likely_test, normal; reasons preserve all fired rules. Normal means no rule fired, not verified sensor correctness. These are heuristic flags, not assertions about what the runner actually did.

Prompt guidance acknowledges possible test/non-running/GPS recordings, avoids performance/record praise and misleading previous-run comparisons, and remains supportive. Suspicious classification never prevents the existing requested coaching flow. Provider protocols, models, speech and retry behavior are unchanged.

## 1.3 Phase 2 execution

Read REQUIREMENTS.md, TASKS.md, the then-current working guide and this handoff. Review run-quality.ts, its tests and coaching integration before execution. Check the acquisition-order/segment assumptions against Android's stored measurement and route shapes. Inspect existing pending changes without reverting them.

From the repository root, run `git diff --check`. From `worker/`, run `npm.cmd test`. This includes TypeScript, deployment dry-run and all Worker tests. The dry-run must remain local; do not run deploy:dev, live smoke or remote migrations. Read individual failures and final test totals, not only shell status. Correct bounded defects and rerun only affected checks unless a broader change justifies the full gate.

The new classifier cases cover strict test thresholds, plausible/empty runs, 24/25-second sustained boundaries, GPS/step sources, accuracy filtering, isolated measurement/route jumps, label precedence, unchanged archives, source/segment boundaries, gaps, invalid timestamps and sparse samples. Context/provider tests verify complete retained records, same-owner previous-run selection, classification in actual stub request bodies, identical counted/generated input and prompt guardrails. The job test proves likely-test recordings reach ready through the existing flow. Existing deletion, key/session, unknown-paid-outcome, isolation and history tests must remain green. All provider calls are stubs; never retrieve a real key.

No UI changed; Android/emulator/screenshots are not required. Prompt request assertions prove the guidance was sent, not the quality of a live generated recap. Real coaching tone and detection thresholds remain acceptance using the user's eventual run recordings. No paid trial is required for this milestone.

## 1.4 Completion records

Source review confirmed Android stores measurement and route rows in insertion order and creates fresh segments across pause/resume, matching the classifier's continuity assumptions. `git diff --check` passed with line-ending warnings only. `npm.cmd test` passed TypeScript compilation, the local Cloudflare deployment dry-run and all 118 Worker tests with zero failures, skips or cancellations.

Ten new cases passed: eight focused classifier cases plus provider-request and durable-job integration. They verify strict duration/distance thresholds, the 24/25-second sustained boundary, measured GPS/steps, accuracy-aware route evidence, isolated GPS jumps and label precedence, segment/source/gap resets, sparse or invalid evidence, unchanged archives, same-owner previous history, identical count/text input and suspicious runs reaching ready. Existing ownership, deletion, encrypted-key, paid-stage uncertainty, history/export, route and publication coverage remained green.

Milestone 9 is complete. Prompt assertions prove anomaly guidance reaches the provider boundary; no real OpenAI key or paid call was used, so generated tone and heuristic thresholds remain acceptance with eventual user recordings. Milestone 4.0, publication API/web, is next and remained outside this phase.
