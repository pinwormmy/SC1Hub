# SC1Hub Platform Upgrade Runbook

This runbook records the supported baseline for the Cafe24 Tomcat JSP hosting
environment on the business plan. Releases must not require a hosting-plan
change beyond the business plan already in effect.

## Target

- Java 17 bytecode and runtime
- External Tomcat 10.0.x (Servlet 5.0 / JSP 3.0, Jakarta EE 9)
- Spring Boot 3.1.x application WAR
- MyBatis Spring Boot Starter 3.0.x
- Gradle 8.14
- MySQL Connector/J against the existing MariaDB, unchanged
- The business plan's `JAVA_OPTS` supplies `-XX:MaxMetaspaceSize=128m -Xmx128m`;
  releases must not override it (Cafe24 forbids per-account JVM tuning)

Spring Boot 3.2 and newer are **out of scope**: Spring Framework 6.1 raises the
runtime baseline to Jakarta EE 10 (Servlet 6.0, Tomcat 10.1+), and Cafe24's
highest offered environment is Tomcat 10.0. Framework 6.0 — that is, Boot 3.1 —
is the last line that runs on Servlet 5.0. Revisit only if Cafe24 starts
offering Tomcat 10.1.

The 2026-08-22 attempt at Boot 3.5 / JDK 17 was rolled back. Its stated cause was
the then-current 64 MB Metaspace cap; it also targeted a Boot line that Tomcat
10.0 does not support. Both conditions are addressed above.

## Release gates

Do not deploy until all of the following are true:

1. `./gradlew clean build` passes on a Java 17 JDK.
2. The WAR carries no container-provided API: no `javax.servlet` jar, and no
   Servlet, JSP, or EL API jar. `verifyProductionWarFootprint` enforces this —
   shipping the Servlet 6.0 API in particular would advertise methods Tomcat
   10.0 does not implement.
3. The current production WAR and configuration have verified backups and
   recorded SHA-256 checksums.
4. The checkout is clean, on `main`, and the release commit is contained in
   `origin/main`.
5. The server is already on Tomcat 10.0.x / JDK 17 before the WAR is installed.
   `deploy.sh` refuses the release otherwise, because a Jakarta WAR cannot start
   on Tomcat 8.5 — and, symmetrically, the previous javax WAR cannot start on
   Tomcat 10. Rolling back therefore means changing the Cafe24 server
   environment back first; `ROLLBACK_REQUIRES_LEGACY_RUNTIME` stops the script
   from restarting the old WAR on the wrong runtime.

## Metaspace budget

Metaspace, not heap, is what rolled back the 2026-08-22 attempt, so it keeps its
own gate. Loaded classes are never unloaded within one JVM lifetime, so usage
only climbs until the next restart.

Measured on Tomcat 10.0.27 / JDK 17 / Boot 3.1.12 with the production JVM flags
(2026-08-31): **47.0 MB of the 128 MB cap (37%) with 9,337 classes loaded**,
after warming the representative routes. For comparison, JDK 8 / Boot 2.7.2 in
production plateaued at 57 MB with 10,357 classes — the upgraded stack loads
*fewer* classes and leaves roughly 80 MB of headroom instead of 8 MB.

`deploy.sh` enforces the budget automatically and runs the check twice, once
after the representative route warm-up and once after the stability window. Each
check reads `MaxMetaspaceSize` with `jinfo` and usage with `jstat`, appends the
measurement to `$TOMCAT_DIR/logs/metaspace-history.log`, warns at 85%, and
refuses the release at 95%. The same check requires the production JVM to carry
exactly one `-Dsun.reflect.inflationThreshold=2147483647` argument and exactly
one `-XX:OnOutOfMemoryError=exec .../restart-tomcat-after-oom.sh %p` argument;
a missing or duplicated argument fails the release.

The deploy-time reading is the lowest of the JVM's life, so the application also
samples its own Metaspace pool every ten minutes (`MetaspaceUsageLogger`),
because the host provides no crontab. It logs used/max/percent and escalates to
WARN at `sc1hub.monitoring.metaspaceWarnPercent`. Two tiered pauses then protect
the core site without switching AI off wholesale:

- `sc1hub.monitoring.metaspaceBackgroundPausePercent` (92): scheduled bot
  publishing and RAG index updates yield first, because they repeat on their own
  schedule and lose nothing by skipping a run.
- `sc1hub.monitoring.metaspaceAiPausePercent` (96): every outbound AI call,
  including user-facing search, stops.

Both thresholds sat above the 64 MB era's ~88% steady state on purpose: a
threshold at or below the steady state is a one-way switch, because usage never
falls back and the pause would hold until the next restart. On 128 MB the steady
state is far below them, so they now behave as genuine emergency brakes rather
than near-miss triggers. Each pause and resume is logged once at WARN, on the
transition only.

## Deployment

1. Change the Cafe24 server environment to Tomcat 10.0.x / JDK 17 first
   ("변경신청 → 서버환경 변경", applied within about five minutes), then confirm
   with `bin/version.sh` and re-check that `JAVA_OPTS` still carries 128m.
   Upload the verified WAR only after that.
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

1. Stop Tomcat and preserve the failed deployment log for diagnosis. Read
   `catalina.YYYY-MM-DD.log`, not `catalina.out`, which `deploy.sh` rotates.
2. Change the Cafe24 server environment back to Tomcat 8.5 / JDK 8. The previous
   WAR is a javax build and cannot start on Tomcat 10, so this comes before
   restoring it.
3. Restore `ROOT.war.rollback` as `ROOT.war`.
4. Remove only the failed exploded `ROOT` directory and restart Tomcat.
5. Repeat the local and public endpoint checks.
6. Leave the hosting plan alone: the business plan is what supplies the 128 MB
   cap, and a plan downgrade is not reversible.

## Post-deployment

- Monitor HTTP 5xx responses, login/session failures, image delivery, database
  connection errors, and memory errors.
- Review `$TOMCAT_DIR/logs/metaspace-history.log` and the sampled Metaspace
  lines in the Catalina log; a WARN pause transition means the JVM reached a
  tier and the next restart is what clears it.
- Keep the legacy artifact and verified backups through the stability period.
- Perform structural refactoring only after this dependency-only release is
  stable.
