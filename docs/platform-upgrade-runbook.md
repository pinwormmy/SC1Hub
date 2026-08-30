# SC1Hub Platform Upgrade Runbook

This runbook records the supported baseline for the Cafe24 general hosting
environment. The Cafe24 runtime must remain Tomcat 8.5 / JDK 8 and releases
must not require a hosting-plan change.

## Target

- Java 8 bytecode and runtime
- Existing external Tomcat 8.5.x
- Spring Boot 2.7.2 application WAR
- MyBatis Spring Boot Starter 2.2.0
- Gradle 7.5
- Existing MariaDB and hosting plan, unchanged
- Cafe24's `catalina.sh` fixes `-XX:MaxMetaspaceSize=64m`; releases cannot
  raise it

Spring Boot 2.7.18 exceeded the host's 64 MB metaspace limit during a controlled
deployment and was rolled back. Spring Boot 3, Jakarta Servlet, Tomcat 10, and
JDK 17 are also out of scope because the current plan does not provide enough
runtime headroom.

## Release gates

Do not deploy until all of the following are true:

1. `./gradlew clean build` passes on an actual Java 8 JDK.
2. The WAR is checked for Java EE dependencies and contains no Jakarta Servlet
   API jars.
3. The current production WAR and configuration have verified backups and
   recorded SHA-256 checksums.
4. The checkout is clean, on `main`, and the release commit is contained in
   `origin/main`.
5. The server is still Tomcat 8.5 / JDK 8 with `-Xmx64m` and the fixed 64 MB
   Metaspace cap before deployment.

## Metaspace budget

Metaspace, not heap, is what rolled back the last upgrade attempt, so it has its
own gate. Loaded classes are never unloaded within one JVM lifetime, so usage
only climbs until the next restart; production plateaus near 88% of the 64 MB
cap within about two hours of every restart.

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

Both thresholds sit above the ~88% steady state on purpose. A threshold at or
below it is a one-way switch: usage never falls back, so the pause would hold
until the next restart. Each pause and resume is logged once at WARN, on the
transition only.

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
- Review `$TOMCAT_DIR/logs/metaspace-history.log` and the sampled Metaspace
  lines in the Catalina log; a WARN pause transition means the JVM reached a
  tier and the next restart is what clears it.
- Keep the legacy artifact and verified backups through the stability period.
- Perform structural refactoring only after this dependency-only release is
  stable.
