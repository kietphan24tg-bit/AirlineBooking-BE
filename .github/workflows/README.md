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
- Push the image to Docker Hub with the `staging` and `sha-<commit>` tags

Main steps:
1. Checkout source
2. Setup Java 21 and Gradle cache
3. Run `clean qualityGate :app-bootstrap:bootJar`
4. Validate Docker Hub secrets
5. Login to Docker Hub
6. Build and push the image

Published tags:
- `<repository>:staging`
- `<repository>:sha-<commit-sha>`

### `deploy-production.yml`
Trigger:
- Manual run via `workflow_dispatch`

Purpose:
- Allow controlled production image publishing from a selected Git ref
- Re-run verification before publishing
- Build a Docker image
- Push the image to Docker Hub with the selected production tag and a `sha-<commit>` tag

Main steps:
1. Checkout the requested `git_ref`
2. Setup Java 21 and Gradle cache
3. Run `clean qualityGate :app-bootstrap:bootJar`
4. Validate Docker Hub secrets
5. Login to Docker Hub
6. Build and push the image

Published tags:
- `<repository>:<image_tag>`
- `<repository>:sha-<commit-sha>`

## Expected Delivery Flow

- PR to `main`
  - `ci.yml`
  - build + test + quality gate
- Push to `main`
  - `deploy-staging.yml`
  - build + test + push staging image
- Manual release
  - `deploy-production.yml`
  - build + test + push production image

## Required GitHub Secrets

### Docker Hub
- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`
- `DOCKERHUB_REPOSITORY`

`DOCKERHUB_REPOSITORY` example:
- `your-dockerhub-username/airline-booking`

## Required GitHub Environments

Create these environments in the repository settings:
- `staging`
- `production`

Recommended:
- add required reviewers to the `production` environment
- keep `staging` automatic

## Railway Setup When Using Docker Hub Images

Railway does not automatically split `staging` and `production` for you.
You need to configure separate Railway deployment targets.

Recommended setup:
- Railway staging service or staging environment watches:
  - `docker.io/<repository>:staging`
  - `SPRING_PROFILES_ACTIVE=staging`
- Railway production service or production environment watches:
  - `docker.io/<repository>:production`
  - or another explicit release tag such as `v1.0.0`
  - `SPRING_PROFILES_ACTIVE=prod`

Practical note:
- Railway must be configured to use a Docker image source instead of source-code deployment.
- Railway image auto updates must be enabled if you want a newly pushed tag to be pulled automatically.
- If you use a moving tag such as `staging` or `production`, Railway can redeploy when it detects a new digest for that tag.
- If you use immutable version tags, you must point Railway at the new tag when releasing.

## Recommended Branch Protection For `main`

Enable these settings on `main`:
- Require a pull request before merging
- Require approvals
- Require status checks to pass before merging

Recommended required status check:
- `Build, Test, Quality Gate`

## Notes

- The workflows now publish Docker images to Docker Hub.
- The app artifact is built from `:app-bootstrap:bootJar`.
- If you want guaranteed immediate staging and production rollout from GitHub Actions, code-source deployment with `railway up` is more deterministic than waiting for image tag detection.
