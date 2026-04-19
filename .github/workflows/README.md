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
- Trigger a **Render** deploy via **Deploy Hook** so the staging Web Service pulls the new image

Main steps:
1. Checkout source
2. Setup Java 21 and Gradle cache
3. Run `clean qualityGate :app-bootstrap:bootJar`
4. Validate Docker Hub secrets
5. Login to Docker Hub
6. Build and push the image (`<repository>:staging`)
7. `POST` to `RENDER_DEPLOY_HOOK_URL` (staging environment secret)

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
- Trigger a **Render** deploy via **Deploy Hook** for the production Web Service

Main steps:
1. Checkout the requested `git_ref`
2. Setup Java 21 and Gradle cache
3. Run `clean qualityGate :app-bootstrap:bootJar`
4. Validate Docker Hub secrets
5. Login to Docker Hub
6. Build and push the image
7. `POST` to `RENDER_DEPLOY_HOOK_URL` (production environment secret)

Published tags:
- `<repository>:<image_tag>` (default `production`)

## Expected Delivery Flow

- PR to `main`
  - `ci.yml`
  - build + test + quality gate
- Push to `main`
  - `deploy-staging.yml`
  - build + test + push staging image + Render staging deploy hook
- Manual release
  - `deploy-production.yml`
  - build + test + push production image + Render production deploy hook

## Required GitHub Secrets

### Docker Hub (repository secrets)
- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`
- `DOCKERHUB_REPOSITORY`

`DOCKERHUB_REPOSITORY` example:
- `your-dockerhub-username/airline-booking`

### Render (per environment)

Add **`RENDER_DEPLOY_HOOK_URL`** to each GitHub **Environment** that runs deploy workflows:

| Environment   | Secret                     | Used by                 |
|---------------|----------------------------|-------------------------|
| `staging`     | `RENDER_DEPLOY_HOOK_URL`   | `deploy-staging.yml`    |
| `production`  | `RENDER_DEPLOY_HOOK_URL`   | `deploy-production.yml` |

Create the URL in the Render Dashboard: **Web Service → Settings → Deploy Hook**. The URL contains a secret key; store it only in GitHub **Environment secrets**, never in the repository.

**Staging and production should use different hooks** (one Deploy Hook per Render service).

Deprecated (Railway-only; remove from GitHub if present):
- `RAILWAY_TOKEN`
- `RAILWAY_SERVICE_ID`

## Required GitHub Environments

Create these environments in the repository settings:
- `staging`
- `production`

Recommended:
- add required reviewers to the `production` environment
- keep `staging` automatic

## Render setup when using Docker Hub images

Configure **separate** Render Web Services for staging and production. Each should use **Existing image** from Docker Hub (not Git-based build), pointing at the same repository your workflows push to.

**Staging service**
- Image: `docker.io/<repository>:staging` (match `DOCKERHUB_REPOSITORY` + `:staging`)
- Environment variables (minimum): `SPRING_PROFILES_ACTIVE=staging` so Springdoc/Swagger and `application-staging.yml` apply; add DB, Redis, JWT, CORS, etc. per `.env.example`
- Health check path: `/actuator/health`
- If the Docker Hub repo is private, add registry credentials on Render

**Production service**
- Image: `docker.io/<repository>:production` (or your release tag)
- Environment: `SPRING_PROFILES_ACTIVE=prod` and production secrets

**Deploy Hook**
- After each `docker push`, GitHub Actions calls the hook so Render starts a new deploy and pulls the latest digest for that tag.

If a Deploy Hook URL is ever exposed publicly, **revoke it on Render and create a new hook**, then update the GitHub secret.

## Recommended Branch Protection For `main`

Enable these settings on `main`:
- Require a pull request before merging
- Require approvals
- Require status checks to pass before merging

Recommended required status check:
- `Build, Test, Quality Gate`

## Post-deploy verification (manual)

1. Render logs: deploy succeeded, container healthy.
2. `GET https://<your-service>.onrender.com/actuator/health` → 200.
3. Staging with `SPRING_PROFILES_ACTIVE=staging`: optional checks for `/swagger-ui.html` and `/v3/api-docs`.

## Notes

- The workflows publish Docker images to Docker Hub; the app artifact is built from `:app-bootstrap:bootJar`.
- Immediate rollout after push is driven by the **Deploy Hook**; ensure the Render service watches the correct image tag.
