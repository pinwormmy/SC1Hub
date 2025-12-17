# Repository Guidelines

## Project Structure

- `src/main/java/com/sc1hub`: Spring Boot application code (controllers, services, interceptors, configs).
- `src/main/resources`: application properties, MyBatis config, mapper XML, static assets.
- `src/main/webapp/WEB-INF/views`: JSP views (`*.jsp`, `*.jspf` includes).
- `src/test/java`: JUnit 5 tests.

## Build, Test, and Development Commands

- `./gradlew clean build`: Compiles and packages the app (WAR enabled).
- `./gradlew test`: Runs the JUnit 5 test suite.
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

## Commit & Pull Request Guidelines

- Commits in this repo use short, descriptive messages (often Korean). Keep them concise and action-focused.
- PRs should include: purpose, key changes, and any UI-impact screenshots (JSP/CSS/JS).

## Security & Configuration Tips

- Do not commit secrets. Local/online profiles should live in `application-local.properties` and
  `application-online.properties` (both are ignored by `.gitignore`).
- Keep generated artifacts out of git (e.g., `build/`, `*.log` like `server.log`).

## Agent Notes (Codex/AI)

- Do not run tests automatically. Write/adjust test code only; the repository owner runs tests in their IDE.
