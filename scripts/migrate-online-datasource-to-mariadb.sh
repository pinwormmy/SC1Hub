#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 2 ]]; then
    echo "Usage: $0 <application-online.properties> <backup-path>" >&2
    exit 2
fi

CONFIG_PATH="$1"
BACKUP_PATH="$2"

if [[ ! -s "$CONFIG_PATH" ]]; then
    echo "Online datasource config is missing or empty." >&2
    exit 1
fi

DRIVER_COUNT="$(grep -c '^spring.datasource.driver-class-name=' "$CONFIG_PATH" || true)"
URL_COUNT="$(grep -c '^spring.datasource.url=' "$CONFIG_PATH" || true)"
if [[ "$DRIVER_COUNT" != "1" || "$URL_COUNT" != "1" ]]; then
    echo "Expected exactly one datasource driver and URL setting." >&2
    exit 1
fi

if grep -q '^spring.datasource.driver-class-name=org.mariadb.jdbc.Driver$' "$CONFIG_PATH" \
        && grep -q '^spring.datasource.url=jdbc:mariadb://' "$CONFIG_PATH"; then
    echo "Datasource config already uses MariaDB Connector/J."
    exit 0
fi

if ! grep -Eq '^spring.datasource.driver-class-name=com\.mysql\.(cj\.)?jdbc\.Driver$' "$CONFIG_PATH" \
        || ! grep -q '^spring.datasource.url=jdbc:mysql://' "$CONFIG_PATH"; then
    echo "Datasource config is not a recognized MySQL-to-MariaDB migration source." >&2
    exit 1
fi

if grep -Eq '^spring.datasource.url=.*([?&])useSSL=true([&#]|$)' "$CONFIG_PATH"; then
    echo "Automatic migration is disabled for useSSL=true; choose a MariaDB sslMode explicitly." >&2
    exit 1
fi

CONFIG_DIR="$(dirname "$CONFIG_PATH")"
TEMP_PATH="$(mktemp "$CONFIG_DIR/.application-online.properties.migrate.XXXXXX")"
cleanup() {
    rm -f "$TEMP_PATH"
}
trap cleanup EXIT

if [[ -e "$BACKUP_PATH" ]]; then
    if ! cmp -s "$CONFIG_PATH" "$BACKUP_PATH"; then
        echo "Existing pre-Jakarta backup does not match the migration source." >&2
        exit 1
    fi
else
    cp -p "$CONFIG_PATH" "$BACKUP_PATH"
    chmod 600 "$BACKUP_PATH"
fi

sed -E \
    -e 's#^spring\.datasource\.driver-class-name=com\.mysql\.(cj\.)?jdbc\.Driver$#spring.datasource.driver-class-name=org.mariadb.jdbc.Driver#' \
    -e 's#^spring\.datasource\.url=jdbc:mysql:#spring.datasource.url=jdbc:mariadb:#' \
    -e '/^spring\.datasource\.url=/ s/serverTimezone=[^&]*/connectionTimeZone=LOCAL/g' \
    -e '/^spring\.datasource\.url=/ s/useSSL=false/sslMode=disable/g' \
    "$CONFIG_PATH" > "$TEMP_PATH"

if ! grep -q '^spring.datasource.driver-class-name=org.mariadb.jdbc.Driver$' "$TEMP_PATH" \
        || ! grep -q '^spring.datasource.url=jdbc:mariadb://' "$TEMP_PATH" \
        || grep -Eq '^spring.datasource.driver-class-name=com\.mysql\.|^spring.datasource.url=jdbc:mysql:' "$TEMP_PATH"; then
    echo "Migrated datasource config did not pass validation." >&2
    exit 1
fi

chmod --reference="$CONFIG_PATH" "$TEMP_PATH" 2>/dev/null || chmod 600 "$TEMP_PATH"
mv "$TEMP_PATH" "$CONFIG_PATH"
trap - EXIT
echo "Datasource config migrated; the pre-Jakarta backup was retained."
