# Note upload: real Android process restart

This harness uses only the new, isolated `de.westnordost.streetcomplete.test` package and a mock HTTP server bound to loopback port 18765. Do not install a regular SCEE APK for this test. Do not run the default connected test suite: existing API integration tests may contact external services.

1. Build `:app:packageAndroidDeviceTest -PnoteRestartTest=true`. In restricted environments, set `ANDROID_USER_HOME` and temporary directories to an ignored workspace directory. The default instrumentation runner remains unchanged without the flag.
2. Run `python app/src/androidDeviceTest/python/mock_server.py --output-dir output/note-upload-restart` from the repository root. Use a fresh output directory for each evidence collection.
3. Check the APK manifest: package and target must be `de.westnordost.streetcomplete.test`, Application must be `android.app.Application`, runner must be `de.westnordost.streetcomplete.NoteRestartInstrumentation`. Ensure this package does not already contain data that must be preserved.
4. `adb reverse tcp:18765 tcp:18765`
5. Install only `app/build/outputs/apk/androidTest/app-androidTest.apk`.
6. `adb shell am instrument -w -e phase prepare de.westnordost.streetcomplete.test/de.westnordost.streetcomplete.NoteRestartInstrumentation`
7. Wait for `READY_TO_KILL`. From another console, record `adb shell pidof de.westnordost.streetcomplete.test`, then explicitly kill only the test package: `adb shell am force-stop de.westnordost.streetcomplete.test`. Verify `pidof` is empty.
8. `adb shell am instrument -w -e phase verify de.westnordost.streetcomplete.test/de.westnordost.streetcomplete.NoteRestartInstrumentation`
9. Require `PASS`, instrumentation code -1, and a different PID. The first run's aborted instrumentation result is intentional.
10. Inspect the mock evidence: one photo upload, exactly two CREATEs with different texts (`restart-safe` with photo, `independent` without), one GET of note 1, one activation. Repeated retries must not create another original note.
11. Stop the server using GET `http://127.0.0.1:18765/__shutdown` and remove only this forwarding with `adb reverse --remove tcp:18765`.

The prepare phase seeds a synthetic version-23 DB with a pending edit and photo path, migrates through the production initializer, uploads a synthetic photo, and POSTs through the real API client/uploader. The mock commits the first note then closes its socket without replying. The process stays alive with its DB open until force-stop. The verify phase checks persisted state, three blocked retries, independent queue progress, GET-based manual resolution and photo activation.

The test refuses to overwrite an existing synthetic DB. Before repeating, preserve the evidence and deliberately remove only the isolated test data (or use a fresh test profile). Never clear, stop or reinstall a production package. No production tokens are used; redirects are disabled. There is no automatic cleanup that deletes local data.

This tests process death, not power loss. The actual product dialog is separately covered by Android host Compose tests; interactive physical UI and field-network testing remain separate checks.

## COMMENT restart

Use a fresh mock server and the same procedure with phases `comment-prepare` and `comment-verify`.
This uses a separate `note-comment-restart-synthetic.db`, preserving the CREATE evidence.
The mock accepts the first COMMENT on note 50 then drops the response. After real process death,
three retries send no POST, a later CLOSE on note 50 stays blocked, and a COMMENT on note 51 succeeds.
Explicit local discard of the uncertain edit then permits the CLOSE. Require exactly three POSTs:
`50/comment`, `51/comment`, `50/close`, each once. The harness never infers comment identity from GET.
