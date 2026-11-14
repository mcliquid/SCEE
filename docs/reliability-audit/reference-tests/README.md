# Reliability audit regression reproductions

These three classes are the original reproductions from the SCEE-mc reliability audit.
The sources are unchanged. They assert the desired behaviour, so they fail on the
pre-fix code. They are not part of `src/` and are not run by CI.

| Test | Finding | What a failure means |
|---|---|---|
| `AuditSidepathEmptyAnswerTest` — the UI allows re-selecting the road that is already tagged | B3 | The already-tagged unnamed road is no longer recognised as a match. |
| `AuditSidepathEmptyAnswerTest` — confirming the already tagged road does not throw when the edit is built | B3 | Building `UpdateElementTagsAction` for that no-op still throws. Package A avoids the constructor instead of making the empty action legal. |
| `AuditUploadReliabilityTest` — node creation is not duplicated when the server reply is lost and the upload is retried | A1 | A lost create reply is uploaded again. |
| `AuditUploadReliabilityTest` — tag edit is not uploaded a second time when the server reply is lost | A1 | A lost tag-edit reply is uploaded again. |
| `AuditUploadReliabilityTest` — undo during in-flight upload either prevents the upload or queues a revert | A2 | Undo during the request neither stops the upload nor queues a revert. |
| `AuditUploadReliabilityTest` — a permanently rejected edit does not block later edits | B1 | One rejected edit still blocks the queue. |
| `AuditNoteTrackUploadTest` — gpx trace is uploaded once even if the trace reply is lost and upload is retried | B2 | The GPX trace is posted again after a lost reply. |

A1, A2, B1 and B2 are not fixed in Package A. Do not move these files into a Gradle source set, and do not weaken them so the suite goes green.

## Run by hand

From the repository root, compile and run one class on the host unit-test classpath:

```powershell
.\gradlew.bat :app:testAndroidHostTest --tests de.westnordost.streetcomplete.quests.is_sidepath.AuditSidepathEmptyAnswerTest
```

That command does nothing until the file is temporarily copied into
`app/src/commonTest/kotlin/` under the same package. Remove the copy afterwards.
A failing result is the expected signal for a finding that is still open.
The Package A sidepath fix is covered in the normal suite by `AddIsSidepathEmptyChangeTest`.
