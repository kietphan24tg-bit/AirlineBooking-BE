# RAG validation release evidence

This directory contains immutable release-candidate evidence published from the
`module-ai:ragLiveValidation` task. Raw drafts remain outside Git when they
contain model output or other sensitive data.

## Required release artifacts

For one candidate configuration, publish:

- the full checklist report covering 135 cases;
- the language-stability report covering 36 cases;
- the run id, commit, chat/embedding model ids, prompt and docs hashes;
- the redacted database target, top-k, repeats, variants and pacing;
- the test command and the final exit status.

Targeted runs must use a different run id and report filename. They must never
overwrite the full-candidate report.

## Reproducible commands

Generation and judging can be resumed independently:

```text
./gradlew.bat :module-ai:ragLiveValidation \
  -PragValidationGenerationOnly=true \
  -PragValidationRunId=<candidate-run>

./gradlew.bat :module-ai:ragLiveValidation \
  -PragValidationJudgeOnly=true \
  -PragValidationResumeFrom=<draft-path> \
  -PragValidationRunId=<candidate-run>
```

Live validation requires a reachable staging PostgreSQL/PGVector instance and
the Gemini credential from the secret store. A failed connection or provider
quota is infrastructure evidence and must not be reported as a quality pass.

## Quota-safe execution

Do not run the full 135-case generation repeatedly against a free-tier key. Use
one smoke case to verify connectivity, then persist that draft and judge it
without additional model calls:

```text
./gradlew.bat :module-ai:ragLiveValidation \
  -PragValidationMaxCases=1 \
  -PragValidationDelayMillis=10000 \
  -PragValidationRunId=smoke-<date>

./gradlew.bat :module-ai:ragLiveValidation \
  -PragValidationGenerationOnly=true \
  -PragValidationMaxCases=135 \
  -PragValidationDelayMillis=10000 \
  -PragValidationRunId=<candidate-run>

./gradlew.bat :module-ai:ragLiveValidation \
  -PragValidationJudgeOnly=true \
  -PragValidationResumeFrom=<draft-path> \
  -PragValidationRunId=<candidate-run>
```

For day-to-day development, run `:module-ai:test` and the offline quality scan;
reserve the full live run for a paid/isolated project with a known quota. Never
retry a 429 in a tight loop; wait for the provider's retry window or switch to
the offline judge.
