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

## Cutover

1. Announce the maintenance window and stop writes where practical.
2. Recheck the data and database backups.
3. Change the Cafe24 server environment to Tomcat 10.0.x / JDK 17.
4. Confirm the active Java and Tomcat versions through the server connection.
5. Deploy the pre-verified Jakarta WAR without rebuilding it on the server.
6. Restart Tomcat and inspect startup logs for deployment errors.
7. Verify the local health check and then the public endpoints:
   - `/`
   - `/api/chat/messages?afterSeq=0`
   - `/strategy-tips`
   - an existing board list and post
   - login/session behavior
   - an uploaded image
8. Confirm that unauthenticated admin content API access is denied.

## Rollback

Rollback immediately if the application fails to start, database connectivity
fails, sessions cannot be created, JSPs do not render, or critical write paths
fail their smoke tests.

1. Preserve the failed Tomcat 10 logs and deployed WAR for diagnosis.
2. Change the Cafe24 server environment back to Tomcat 8.5.x / JDK 8.
3. Restore the verified legacy WAR and its matching external configuration.
4. Restart Tomcat and repeat the public endpoint checks.
5. Restore data or the database only if the cutover changed them unexpectedly.
   The platform migration itself must not contain schema changes.

## Post-cutover

- Monitor application errors, HTTP 5xx responses, session/login failures,
  image delivery, and database connection errors.
- Keep the legacy artifacts and verified backups until the new runtime has
  completed an agreed stability period.
- Perform structural refactoring only after the platform release is stable.
