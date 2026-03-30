Run `./gradlew installGitHooks` once after cloning to copy the shared `pre-push` hook into `.git/hooks`.

The hook runs `./gradlew qualityGate` and blocks pushes when formatting, tests, or quality checks fail.
