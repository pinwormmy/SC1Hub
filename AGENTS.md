# SC1Hub Agent Guide

## Project Map

- Spring Boot + JSP + MyBatis application; Gradle builds a WAR.
- Java: `src/main/java/com/sc1hub`
- Config, mapper XML, SQL, static assets: `src/main/resources`
- JSP views and includes: `src/main/webapp/WEB-INF/views`
- JUnit 5 tests: `src/test/java`

## Commands

- `./gradlew test`: run the test suite.
- `./gradlew clean build`: compile, test, and package the WAR.
- `./gradlew bootRun`: run locally on port `8082`.
- After a change, run the narrowest relevant test first; use the full suite or build when the scope warrants it.
- Never run checks that require production DB, SMTP, credentials, or other external services unless the user explicitly requests it and safe configuration is present.
- If verification cannot run, report the exact command omitted or failed and the reason.

## Implementation Rules

- Use 4-space Java indentation and no trailing whitespace.
- Prefer constructor injection in new or modified code.
- Follow existing names: `*Controller`, `*Service`, `*ServiceImpl`, `*DTO`, and paired `*Mapper.java` / `mapper/*Mapper.xml`.
- Preserve unrelated user changes. Do not rewrite, reset, or delete work outside the requested scope.
- Keep secrets out of Git. Local and online secrets belong in ignored `application-local.properties` and `application-online.properties` files.
- Do not commit generated files such as `build/` or `*.log`.

## Worktree-First Git Workflow

- Keep the primary checkout as the clean integration worktree for `main`; do implementation in a task worktree.
- Use one worktree, one branch, and one Codex task per change. Name Codex branches `codex/<short-task-name>`.
- Before creating a task worktree, update remote refs and confirm the integration worktree is clean. Base the task on the current local `main` so unpushed integrated commits are not skipped.
- If Codex already opened a detached worktree, create the task branch there with `git switch -c codex/<short-task-name>`; do not create a nested worktree.
- At task start, inspect `git status --short --branch`, `git worktree list`, and relevant branch history. Never assume another worktree is disposable.
- After implementation, run relevant checks, review the diff, and make a concise action-focused commit when the user requested completed or commit-ready work.
- Merge into `main`, push, open a PR, delete worktrees/branches, or deploy only when the user explicitly requests that operation.
- Remove a worktree only after confirming it is clean. Delete its branch only after confirming the work is merged, superseded, or explicitly abandoned.
- Never force-push or use destructive reset/checkout commands unless the user explicitly requests them.

## Integration, Push, and Deploy Gate

Perform release operations from the primary integration worktree, not an implementation worktree:

1. Fetch and prune remote refs.
2. Confirm `main` is checked out and `git status` is clean.
3. Review worktrees, merged/unmerged branches, and `origin/main...main` commits.
4. Integrate only the intended task commits and run `./gradlew clean build` unless a narrower check is explicitly accepted.
5. Push or deploy only with explicit user authorization, then report the commit and verification result.

## Repository-Specific Diagnostics

- For assistant-bot activity issues, inspect the admin history APIs before speculating:
  - `GET /api/admin/assistant-bot/history?days=3&limit=100`
  - `GET /api/admin/assistant-bot/history/summary?days=3`
- Useful fields: `personaName`, `generationMode`, `status`, `publishedPostNum`, and `createdAt`.
- `sc1hub.assistant.bot.autoPublishCatchUpEnabled=true` allows missed daily random slots to run later the same day.

## Handoff

- Summarize changed files and behavior, verification commands and results, the current branch/commit, and any remaining pre-deploy checks.
- For PRs, include purpose, key changes, and screenshots for JSP/CSS/JS UI changes.
