# Repository Guidelines

## Project Structure

- `src/main/java/com/sc1hub`: Spring Boot application code (controllers, services, interceptors, configs).
- `src/main/resources`: application properties, MyBatis config, mapper XML, static assets.
- `src/main/webapp/WEB-INF/views`: JSP views (`*.jsp`, `*.jspf` includes).
- `src/test/java`: JUnit 5 tests.

## Build, Test, and Development Commands

- `./gradlew clean build`: Compiles and packages the app (WAR enabled).
- `./gradlew test`: Runs the JUnit 5 test suite (reference only; the repository owner runs tests in their IDE).
- `./gradlew bootRun`: Runs locally (port `8082` is set in `build.gradle`).

If you are on Windows/WSL and `./gradlew` fails, ensure the script uses LF line endings.

## Coding Style & Naming Conventions

- Java: 4-space indentation, no trailing whitespace.
- Prefer constructor injection over field injection for new/modified code.
- Naming patterns:
  - Controllers: `*Controller`
  - Services: `*Service`, `*ServiceImpl`
  - DTOs: `*DTO`
  - MyBatis mappers: `*Mapper` (+ `src/main/resources/mapper/*Mapper.xml`)

## Testing Guidelines

- Frameworks: JUnit 5 (Jupiter) + Mockito (via Spring Boot test starter).
- Keep tests unit-level by default (mock mappers/services); avoid requiring DB, SMTP, or external services.
- Naming: `*Test` classes under the matching package (e.g., `com.sc1hub.board`).
- When Codex is running in WSL, do not run tests automatically; the repository owner runs them directly in their IDE.

## Commit & Pull Request Guidelines

- Commits in this repo use short, descriptive messages (often Korean). Keep them concise and action-focused.
- PRs should include: purpose, key changes, and any UI-impact screenshots (JSP/CSS/JS).

## Security & Configuration Tips

- Do not commit secrets. Local/online profiles should live in `application-local.properties` and
  `application-online.properties` (both are ignored by `.gitignore`).
- Keep generated artifacts out of git (e.g., `build/`, `*.log` like `server.log`).

## Agent Notes (Codex/AI)

- When working from WSL, do not run tests automatically. Write or adjust test code only, and leave all test execution to the repository owner in their IDE.
- Assistant bot operations can be inspected with admin JSON APIs:
  - `GET /api/admin/assistant-bot/history?days=3&limit=100`: recent bot generation history rows, excluding large `raw_json`.
  - `GET /api/admin/assistant-bot/history/summary?days=3`: counts grouped by persona, board, mode, and status, with the latest timestamp.
- For assistant bot activity issues, check the history APIs before speculating. Useful fields are `personaName`, `generationMode`, `status`, `publishedPostNum`, and `createdAt`.
- `sc1hub.assistant.bot.autoPublishCatchUpEnabled=true` lets the scheduler process missed daily random slots later in the same day.
