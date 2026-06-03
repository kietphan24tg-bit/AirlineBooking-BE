# GitHub Actions Guide

This directory contains the CI/CD workflows for the `airline-booking` backend.

## Workflow Overview

### `ci.yml`
Trigger:
- Pull requests targeting `main`

Purpose:
- Run the repository quality gate before merge
- Verify formatting, checks, tests, and `:app-bootstrap:bootJar`

Main command:
```bash
./gradlew --no-daemon clean qualityGate :app-bootstrap:bootJar
```

### `deploy-staging.yml`
Trigger:
- Push to `main`

Purpose:
- Re-run verification on the merged commit
- Build a Docker image
- Push the image to Docker Hub with the `staging` tag
- Trigger a Coolify deploy via the environment's deploy webhook

Main steps:
1. Checkout source
2. Setup Java 21 and Gradle cache
3. Run `clean qualityGate :app-bootstrap:bootJar`
4. Validate Docker Hub secrets
5. Validate the Coolify deploy webhook secret
6. Login to Docker Hub
7. Build and push the image (`<repository>:staging`)
8. `GET` the `COOLIFY_DEPLOY_WEBHOOK` (staging environment secret)

Published tag:
- `<repository>:staging`

### `deploy-production.yml`
Trigger:
- Manual run via `workflow_dispatch`

Purpose:
- Allow controlled production image publishing from a selected Git ref
- Re-run verification before publishing
- Build a Docker image
- Push the image to Docker Hub with the selected production tag
- Trigger a Coolify deploy via the environment's deploy webhook

Main steps:
1. Checkout the requested `git_ref`
2. Setup Java 21 and Gradle cache
3. Run `clean qualityGate :app-bootstrap:bootJar`
4. Validate Docker Hub secrets
5. Validate the Coolify deploy webhook secret
6. Login to Docker Hub
7. Build and push the image
8. `GET` the `COOLIFY_DEPLOY_WEBHOOK` (production environment secret)

Published tags:
- `<repository>:<image_tag>` (default `production`)

## Expected Delivery Flow

- PR to `main`
  - `ci.yml`
  - build + test + quality gate
- Push to `main`
  - `deploy-staging.yml`
  - build + test + push staging image + Coolify staging deploy webhook
- Manual release
  - `deploy-production.yml`
  - build + test + push production image + Coolify production deploy webhook

## Required GitHub Secrets

### Docker Hub (repository secrets)
- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`
- `DOCKERHUB_REPOSITORY`

`DOCKERHUB_REPOSITORY` example:
- `your-dockerhub-username/airline-booking`

### Coolify (per environment)

Add `COOLIFY_DEPLOY_WEBHOOK` to each GitHub Environment that runs deploy workflows:

| Environment   | Secret                    | Used by                 |
|---------------|---------------------------|-------------------------|
| `staging`     | `COOLIFY_DEPLOY_WEBHOOK`  | `deploy-staging.yml`    |
| `production`  | `COOLIFY_DEPLOY_WEBHOOK`  | `deploy-production.yml` |

Important:
- This secret must be the full value of Coolify's `Deploy Webhook (auth required)`.
- Do not use the `GitHub Webhook Secret` from Coolify's manual git webhook section here.
- Example format: `https://coolify.example.com/api/v1/deploy?uuid=...&force=false`

Deprecated secrets to remove if present:
- `RENDER_DEPLOY_HOOK_URL`
- `RAILWAY_TOKEN`
- `RAILWAY_SERVICE_ID`

## Required GitHub Environments

Create these environments in the repository settings:
- `staging`
- `production`

Recommended:
- add required reviewers to the `production` environment
- keep `staging` automatic

## Coolify setup when using Docker Hub images

Configure separate Coolify applications for staging and production. Each should pull from the same Docker Hub repository your workflows publish to.

Suggested setup:
- staging image tag: `staging`
- production image tag: `production` or your chosen release tag
- health check path: `/actuator/health`
- environment variables: set `SPRING_PROFILES_ACTIVE=staging` or `SPRING_PROFILES_ACTIVE=prod` plus DB, Redis, JWT, CORS, mail, and payment secrets

After each `docker push`, GitHub Actions calls the Coolify deploy webhook so the app redeploys and pulls the latest image digest for that tag.

If a Coolify deploy webhook is exposed publicly, rotate it in Coolify and update the GitHub environment secret immediately.

## Recommended Branch Protection For `main`

Enable these settings on `main`:
- Require a pull request before merging
- Require approvals
- Require status checks to pass before merging

Recommended required status check:
- `Build, Test, Quality Gate`

## Post-deploy verification (manual)

1. Coolify deployment logs show a successful redeploy and healthy container.
2. `GET https://<your-service>/actuator/health` returns `200`.
3. Staging with `SPRING_PROFILES_ACTIVE=staging`: optional checks for `/swagger-ui.html` and `/v3/api-docs`.

## Notes

- The workflows publish Docker images to Docker Hub; the app artifact is built from `:app-bootstrap:bootJar`.
- Immediate rollout after push is driven by the Coolify deploy webhook; ensure each Coolify app watches the correct image tag.
