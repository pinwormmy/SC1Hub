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

## Git and Worktrees

- Keep the primary checkout on clean `main` for integration and release. Implement each task in one worktree on `codex/<task>`.
- Base new work on current local `main` after fetching remote refs. If Codex starts detached, create the task branch in that worktree; do not nest worktrees.
- At task start, inspect `git status --short --branch`, `git worktree list`, and relevant history. Preserve unrelated work and other worktrees.
- For completed work, review the diff, run relevant checks, and make a concise commit.
- Merge, push, open a PR, deploy, or remove worktrees/branches only when explicitly requested. Remove only clean worktrees and merged, superseded, or abandoned branches.

## Push and Deploy Gate

Run releases from the primary worktree: fetch/prune, confirm clean `main`, review `origin/main...main` and all worktrees, integrate only intended commits, then run `./gradlew clean build`. Push or deploy only with explicit authorization.

## Assistant-Bot Diagnostics

- Check before speculating: `GET /api/admin/assistant-bot/history?days=3&limit=100` and `GET /api/admin/assistant-bot/history/summary?days=3`.
- Inspect `personaName`, `generationMode`, `status`, `publishedPostNum`, and `createdAt`.
- `sc1hub.assistant.bot.autoPublishCatchUpEnabled=true` processes missed daily slots later the same day.

## Handoff

Report changed behavior/files, checks and results, branch/commit, and remaining pre-deploy work. PRs with UI changes need screenshots.
