# RAG Quality Gate

Live validation stores every run under
`module-ai/build/reports/rag-validation/<run-id>/`. The directory contains an atomically written
`draft.json` plus timestamped Markdown and JSON judge reports. Reports include commit, model,
top-k, prompt hash, and production-docs hash.

## Generate Draft

```powershell
./gradlew :module-ai:ragLiveValidation `
  -PragValidationRunId=release-candidate-1 `
  -PragValidationGenerationOnly=true `
  --console=plain
```

## Resume Missing Executions And Judge

```powershell
./gradlew :module-ai:ragLiveValidation `
  -PragValidationResumeFrom=module-ai/build/reports/rag-validation/release-candidate-1/draft.json `
  -PragValidationIngest=false `
  --console=plain
```

## Judge Existing Draft Only

```powershell
./gradlew :module-ai:ragLiveValidation `
  -PragValidationResumeFrom=module-ai/build/reports/rag-validation/release-candidate-1/draft.json `
  -PragValidationJudgeOnly=true `
  -PragValidationIngest=false `
  --console=plain
```

Resume fails when commit, model, top-k, prompt hash, or docs hash differs from the saved draft.
Use `.github/workflows/rag-live-validation.yml` for a manual staging run with evidence uploaded by
GitHub run ID.
