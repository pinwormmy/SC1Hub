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

- Keep the primary local checkout on clean `main`; it is the integration and release worktree. Use Codex-managed worktrees for implementation tasks.
- A new managed worktree normally starts on a detached `HEAD`. This is expected even when the UI has no branch selector. Before the first task commit, create `codex/<task>` in that existing worktree; do not create a nested worktree.
- Treat the UI targets distinctly: **Local** means the primary checkout where `main` is checked out, while **Worktree/Workspace** means an isolated task checkout. Starting another Worktree/Workspace session does not select the task branch or make it the integration checkout.
- At task start, inspect `git status --short --branch`, `git worktree list`, and relevant history. Base the task on current local `main` after fetching remote refs, and preserve unrelated work and other worktrees.
- Finish implementation in the task worktree by reviewing the diff, running relevant checks, committing to the named task branch, and reporting the branch, exact commit SHA, base commit, and checks run.
- Never merge a task into `main` from a newly created managed worktree. A merge made while detached can create a commit without advancing `main`.
- To integrate, hand the task off to **Local** when available, or start an integration session explicitly against **Local**, not Worktree/Workspace. In the primary checkout, confirm `main` is checked out and clean, review all worktrees and the exact task commits, then merge the named task branch into `main`.
- Prefer `git merge --ff-only codex/<task>` when `main` has not diverged. If it fails, inspect the divergence and resolve it deliberately; do not force, reset, or silently merge unrelated commits.
- If Local is unavailable or the session cannot write to the primary checkout, stop after the task commit and hand off the branch, exact commit SHA, diff scope, checks run, and proposed merge command. Do not substitute another detached worktree for the Local integration checkout.
- Merge, push, open a PR, deploy, or remove worktrees/branches only when explicitly requested. Remove only clean worktrees and merged, superseded, or abandoned branches.

## Release and Deploy Gate

- Run releases from the primary Local checkout. Before release, fetch/prune, confirm it is clean and on `main`, review `origin/main...main` and all worktrees, and confirm that no unrelated commits are included.
- Run `./gradlew clean build` from the exact `main` commit being released. By default, deploy only a commit already contained in `origin/main`.
- Push or deploy only with explicit authorization; authorization for one does not authorize the other. Deploying an unpushed or unintegrated commit requires separate explicit authorization and a report of the divergence.
- After deployment, verify both the server health check and the public application endpoint. Report the deployed commit SHA and verification result.

## Assistant-Bot Diagnostics

- Check before speculating: `GET /api/admin/assistant-bot/history?days=3&limit=100` and `GET /api/admin/assistant-bot/history/summary?days=3`.
- Inspect `personaName`, `generationMode`, `status`, `publishedPostNum`, and `createdAt`.
- `sc1hub.assistant.bot.autoPublishCatchUpEnabled=true` processes missed daily slots later the same day.

## Handoff

Report changed behavior/files, checks and results, task branch, exact commit and base SHAs, and whether Local integration, push, and deploy remain. For an unmerged task, include the proposed Local merge command. PRs with UI changes need screenshots.
