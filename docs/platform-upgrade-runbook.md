# SC1Hub Platform Upgrade Runbook

This runbook covers the no-plan-change platform refresh for the Cafe24 general
hosting environment. The Cafe24 runtime must remain Tomcat 8.5 / JDK 8; the
release changes only the application WAR and build dependencies.

## Target

- Java 8 bytecode and runtime
- Existing external Tomcat 8.5.x
- Spring Boot 2.7.18 application WAR
- MyBatis Spring Boot Starter 2.3.2
- Gradle 7.6.4
- Existing MariaDB and hosting plan, unchanged

Spring Boot 3, Jakarta Servlet, Tomcat 10, and JDK 17 are explicitly out of
scope because the current 64 MB hosting limit does not provide enough runtime
headroom.

## Release gates

Do not deploy until all of the following are true:

1. `./gradlew clean build` passes on an actual Java 8 JDK.
2. The WAR verifier confirms Java EE dependencies and rejects embedded Tomcat
   jars or Jakarta Servlet API jars from `WEB-INF/lib`.
3. The current production WAR and configuration have verified backups and
   recorded SHA-256 checksums.
4. The checkout is clean, on `main`, and the release commit is contained in
   `origin/main`.
5. The server is still Tomcat 8.5 / JDK 8 with `-Xmx64m` before deployment.

## Deployment

1. Upload the verified WAR without changing the Cafe24 server environment or
   hosting plan.
2. Preserve the previous WAR as `ROOT.war.rollback`.
3. Stop Tomcat, replace the WAR, remove only the exploded `ROOT` directory,
   and restart Tomcat.
4. Require the local health endpoint to stay responsive continuously for at
   least 30 seconds after startup.
5. Verify the public endpoints, login/session behavior, uploaded images, and
   unauthenticated admin API denial.
6. Inspect the new Catalina log for startup errors and memory failures.

## Rollback

Rollback immediately if startup fails, the 30-second stability gate fails,
database connectivity fails, JSPs do not render, or a critical public check
fails.

1. Stop Tomcat and preserve the failed deployment log for diagnosis.
2. Restore `ROOT.war.rollback` as `ROOT.war`.
3. Remove only the failed exploded `ROOT` directory and restart Tomcat.
4. Repeat the local and public endpoint checks.
5. Do not change the Cafe24 runtime or plan during rollback; both WARs target
   the same Tomcat 8.5 / JDK 8 environment.

## Post-deployment

- Monitor HTTP 5xx responses, login/session failures, image delivery, database
  connection errors, and memory errors.
- Keep the legacy artifact and verified backups through the stability period.
- Perform structural refactoring only after this dependency-only release is
  stable.
