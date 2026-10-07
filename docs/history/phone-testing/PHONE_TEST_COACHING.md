# 1 Post-run coaching phone check

## 1.1 Install

Install WAYiRUN-2026-09-18_12-43-46_EDT.apk from app/build/outputs/apk/debug over the existing app. Do not uninstall. Your runs, Google login and saved account key should remain available. The installed version label remains 0.1.0-dev-sync2; use the APK timestamp to identify this handoff.

## 1.2 Selected coaching

Finish a short run online with Post-run coaching checked. Expect the usual completion announcement first, then a short Cedar recap once synchronization and generation finish. This uses your OpenAI API credits and sends the complete current run and immediately preceding completed run, including GPS. Tap the animation while speech is playing: the saved summary should appear and speech should continue. A slow request can fall back rather than delay the summary indefinitely.

## 1.3 Opt-out and offline behavior

For a separate short run, uncheck Post-run coaching before swiping. Expect only the ordinary completion cue: no coaching or recorded encouragement. For an offline finish with the checkbox selected, expect one of the onboard encouragement recordings after the completion cue, and a usable saved summary. The offline recording uses a different local voice; it is not Cedar.

## 1.4 Audio and recovery

Check speaker/headphone volume and music behavior on the real phone. Generated coaching playback should release audio focus when finished. Closing the animation must not cut it off. Starting another run or discarding the finished run cancels outstanding coaching. Reopening a completed run after app termination must not trigger another paid generation. Failed/uncertain jobs have no automatic retry in this version.

## 1.5 Already verified and still pending

Assembly, lint and 58 JVM unit tests pass. Seven focused emulator tests pass, covering finish controls, opt-out selection, dismissible animation, persisted attempt suppression and actual onboard-recording playback completion. Backend: 80 automated tests and 22 development smoke checks pass. Automated tests did not use the user's key, send private runs to OpenAI or verify the quality of a real Cedar recap. Coaching results are stored with their runs server-side, but desktop coaching presentation and inclusion in CSV export remain follow-up work.

## 1.6 September 19 playback repair check

Install WAYiRUN-2026-09-19_08-20-22_EDT.apk over the current app. Finish a new short run with Post-run coaching checked and wait for the spoken recap. Compare it with that run's saved website text. Android now finalizes downloaded streaming WAV headers before MediaPlayer playback; it previously rejected the file and played reusable fallback encouragement. Photos are unchanged and their Keep/Skip regression tests pass. Existing finished runs do not automatically replay or request another paid recap.
