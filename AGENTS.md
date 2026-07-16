# SC1Hub Agent Guide

## Project

- Spring Boot + JSP + MyBatis; Gradle builds a WAR.
- Java: `src/main/java/com/sc1hub`
- Config, mapper XML, SQL, static assets: `src/main/resources`
- JSP: `src/main/webapp/WEB-INF/views`; tests: `src/test/java`

## Commands and Code

- `./gradlew test`: test suite.
- `./gradlew clean build`: compile, test, and package.
- `./gradlew bootRun`: local server on port `8082`.
- Run the narrowest relevant test after changes; use the full build for broad or release-bound work. Report skipped or failed verification and why.
- Do not run checks requiring production DB, SMTP, credentials, or external services unless explicitly requested and safely configured.
- Use 4-space Java indentation, constructor injection, and existing names such as `*Controller`, `*Service`, `*DTO`, and paired Java/XML `*Mapper` files.
- Keep secrets in ignored `application-local.properties` or `application-online.properties`; do not commit generated files.

## Git Workflow

- SC1Hub is maintained by one developer and does not use pull requests or Git worktrees by default. Use only the primary local checkout; do not create Codex-managed worktrees or additional `git worktree` checkouts.
- Work directly on `main` for normal tasks. At task start, confirm the current branch and inspect `git status --short --branch` plus relevant history. Preserve unrelated changes and never discard, hide, or overwrite them.
- Fetch remote refs when they matter, and use `git pull --ff-only origin main` before work when `origin/main` may have advanced. If `main` has diverged or the checkout contains conflicting user changes, inspect and report the situation instead of forcing it into shape.
- Use a temporary `codex/<task>` branch in the same checkout only when the developer explicitly requests a branch or the task clearly requires isolation. Do not create a worktree for it. Merge it back into local `main` only when explicitly requested.
- Before committing, review the diff and run the narrowest relevant checks. Commit only task-related files and report the current branch, exact commit SHA, and checks run.
- Push, deploy, merge a temporary branch, or delete an unmerged branch only when explicitly requested. Do not open a pull request unless the developer explicitly asks for one.

## Release and Deploy Gate

- Run releases from the primary local checkout. Before release, fetch/prune, confirm it is clean and on `main`, review `origin/main...main` and local branches, and confirm that no unrelated commits are included.
- Run `./gradlew clean build` from the exact `main` commit intended for release. Push `main` only after this final verification succeeds. By default, deploy only a commit already contained in `origin/main`.
- Push or deploy only with explicit authorization; authorization for one does not authorize the other. Deploying an unpushed or unintegrated commit requires separate explicit authorization and a report of the divergence.
- After deployment, verify both the server health check and the public application endpoint. Report the deployed commit SHA and verification result.

## Assistant-Bot Diagnostics

- Check before speculating: `GET /api/admin/assistant-bot/history?days=3&limit=100` and `GET /api/admin/assistant-bot/history/summary?days=3`.
- Inspect `personaName`, `generationMode`, `status`, `publishedPostNum`, and `createdAt`.
- `sc1hub.assistant.bot.autoPublishCatchUpEnabled=true` processes missed daily slots later the same day.

## Handoff

Report changed behavior/files, checks and results, current branch and exact commit SHA when committed, and whether the final build, `main` push, and deploy remain. If a temporary branch is still unmerged, state that clearly and include the proposed merge command. UI changes need screenshots before release.
