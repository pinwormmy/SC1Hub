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

- Keep the primary checkout on clean `main` for integration and release coordination. Implement each task in one worktree on `codex/<task>`.
- Base new work on current local `main` after fetching remote refs. If Codex starts detached, create the task branch in that worktree; do not nest worktrees.
- At task start, inspect `git status --short --branch`, `git worktree list`, and relevant history. Preserve unrelated work and other worktrees.
- The session that implements a task owns its integration into `main`; do not hand integration to a new context-free session by default.
- Synchronize `main` into the task branch and resolve task conflicts in the task worktree. Integrate the task branch into `main` from the worktree where `main` is checked out, while retaining the originating task session and context.
- Before integration, review the primary worktree, all worktrees, the task diff, and the exact commits to include. For completed work, run relevant checks, make a concise commit, and merge only the reviewed task commits.
- If the originating session cannot write to the `main` worktree, stop and hand off the branch, exact commit SHA, diff scope, checks run, and proposed merge command.
- Merge, push, open a PR, deploy, or remove worktrees/branches only when explicitly requested. Remove only clean worktrees and merged, superseded, or abandoned branches.

## Release and Deploy Gate

- Treat the commit SHA, not the worktree location, as the release identity. A release may run from the primary or a clean task worktree.
- Before release, fetch/prune, confirm the release worktree is clean and on a named committed revision, review `origin/main...HEAD` and all worktrees, and confirm that no unrelated commits are included.
- By default, deploy only a commit already contained in `origin/main`. Deploying an unintegrated task-branch commit is an exception requiring explicit authorization and a report of the divergence and remaining integration work.
- Run `./gradlew clean build` from the exact worktree and commit being released. Push or deploy only with explicit authorization; authorization for one does not authorize the other.
- After deployment, verify both the server health check and the public application endpoint. Report the deployed commit SHA and verification result.

## Assistant-Bot Diagnostics

- Check before speculating: `GET /api/admin/assistant-bot/history?days=3&limit=100` and `GET /api/admin/assistant-bot/history/summary?days=3`.
- Inspect `personaName`, `generationMode`, `status`, `publishedPostNum`, and `createdAt`.
- `sc1hub.assistant.bot.autoPublishCatchUpEnabled=true` processes missed daily slots later the same day.

## Handoff

Report changed behavior/files, checks and results, branch/commit, and remaining pre-deploy work. PRs with UI changes need screenshots.
