# SC1Hub Platform Upgrade Runbook

This runbook covers the one-time migration from the legacy Cafe24 runtime to
the Jakarta-based runtime. It intentionally excludes application features and
database schema changes so that the previous WAR can be restored safely.

## Target

- Java 17
- External Tomcat 10.0.x
- Servlet 5.0 / JSP 3.0
- Spring Boot 3.5.x application WAR
- MariaDB 10.1.x, unchanged

## Release gates

Do not change the Cafe24 runtime until all of the following are true:

1. The full test suite and `./gradlew clean build` pass on Java 17.
2. The new WAR passes smoke tests on an external Tomcat 10.0.x instance.
3. Database connectivity is verified against a non-production MariaDB 10.1.x
   database using the production schema.
4. The current production WAR, uploaded files, configuration, and database
   have been backed up and the backup locations have been verified.
5. Both the old and new WAR artifacts have recorded SHA-256 checksums.
6. The Cafe24 Tomcat 10 home, webapps, log, and control-script paths have been
   confirmed. Do not assume they are identical to the Tomcat 8.5 paths.
7. Push authorization and deploy authorization have been obtained separately.

Run the optional connector check against a disposable compatibility database
with `SC1HUB_DB_COMPAT_URL`, `SC1HUB_DB_COMPAT_USERNAME`, and
`SC1HUB_DB_COMPAT_PASSWORD`, then execute
`./gradlew test --tests com.sc1hub.MariaDbConnectorIntegrationTest`. The
release gate still requires a MariaDB 10.1.x staging database; a newer MySQL or
H2 test does not substitute for that exact server-version check.

The upgrade WAR contains MariaDB Connector/J rather than MySQL Connector/J.
Before Tomcat is stopped, `deploy.sh` runs
`scripts/migrate-online-datasource-to-mariadb.sh` against the external online
configuration. The script retains `application-online.properties.pre-jakarta`
and refuses ambiguous configurations or `useSSL=true`; choose and verify an
explicit MariaDB `sslMode` before cutover in that case. Never remove the
pre-Jakarta configuration while rollback remains possible.

The legacy `serverTimezone` option is replaced with
`connectionTimeZone=LOCAL`, while the deployment configures the JVM with
`-Duser.timezone=Asia/Seoul`. This avoids requiring named time-zone tables on
the MariaDB server while keeping application-side date handling in Korea time.

## Cutover

1. Announce the maintenance window and stop writes where practical.
2. Recheck the data and database backups.
3. Change the Cafe24 server environment to Tomcat 10.0.x / JDK 17.
4. Confirm the active Java and Tomcat versions through the server connection.
5. Run the datasource configuration migration and verify that its backup exists.
6. Deploy the pre-verified Jakarta WAR without rebuilding it on the server.
7. Restart Tomcat and inspect startup logs for deployment errors.
8. Verify the local health check and then the public endpoints:
   - `/`
   - `/api/chat/messages?afterSeq=0`
   - `/strategy-tips`
   - an existing board list and post
   - login/session behavior
   - an uploaded image
9. Confirm that unauthenticated admin content API access is denied.

## Rollback

Rollback immediately if the application fails to start, database connectivity
fails, sessions cannot be created, JSPs do not render, or critical write paths
fail their smoke tests.

1. Preserve the failed Tomcat 10 logs and deployed WAR for diagnosis.
2. Change the Cafe24 server environment back to Tomcat 8.5.x / JDK 8.
3. Restore the verified legacy WAR and
   `application-online.properties.pre-jakarta` together.
4. Restart Tomcat and repeat the public endpoint checks.
5. Restore data or the database only if the cutover changed them unexpectedly.
   The platform migration itself must not contain schema changes.

## Post-cutover

- Monitor application errors, HTTP 5xx responses, session/login failures,
  image delivery, and database connection errors.
- Keep the legacy artifacts and verified backups until the new runtime has
  completed an agreed stability period.
- Perform structural refactoring only after the platform release is stable.
