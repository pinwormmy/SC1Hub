package com.sc1hub.deploy;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightsailDeployScriptTest {

    private static final Path DIR = Paths.get("deploy/lightsail");

    @Test
    void nginxSendsCafe24ShapedProxyHeaders() throws Exception {
        String nginx = read(DIR.resolve("nginx-sc1hub.conf"));

        // IpService trusts the right-most X-Forwarded-For value; nginx must append its own peer.
        assertTrue(nginx.contains("proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;"));
        assertTrue(nginx.contains("proxy_set_header X-Forwarded-Proto https;"));
        // X-Real-IP marks plain HTTP for PlainHttpRedirectFilter, so a client value must never pass.
        assertTrue(nginx.contains("proxy_set_header X-Real-IP \"\";"));
        assertFalse(nginx.contains("X-Real-IP $remote_addr"));
        assertTrue(nginx.contains("return 301 https://sc1hub.com$request_uri;"));
        assertTrue(nginx.contains("server 127.0.0.1:8645;"));
    }

    @Test
    void tomcatListensOnLoopbackOnlyAndRestartsOnFailure() throws Exception {
        String serverXml = read(DIR.resolve("server.xml"));
        String unit = read(DIR.resolve("sc1hub-tomcat.service"));

        assertTrue(serverXml.contains("port=\"8645\" address=\"127.0.0.1\""));
        assertTrue(serverXml.contains("<Server port=\"-1\""));
        assertTrue(serverXml.contains("autoDeploy=\"false\""));
        assertTrue(unit.contains("User=sc1hub"));
        assertTrue(unit.contains("Restart=on-failure"));
        assertTrue(unit.contains("WorkingDirectory=/home/hosting_users/sc1hub/tomcat"));
        // jstat/jinfo in the release check need the JVM's /tmp/hsperfdata entry.
        assertFalse(unit.contains("PrivateTmp=true"));
    }

    @Test
    void provisionPinsTimeZoneJvmLimitsAndVerifiedTomcat() throws Exception {
        String provision = read(DIR.resolve("provision.sh"));

        assertTrue(provision.contains("timedatectl set-timezone Asia/Seoul"));
        assertTrue(provision.contains("-Duser.timezone=Asia/Seoul"));
        assertTrue(provision.contains("-XX:+ExitOnOutOfMemoryError"));
        assertTrue(provision.contains("SHA-512 mismatch"));
        assertTrue(provision.contains("CERTBOT_AGREE_TOS:-}\" == \"yes\""));
        assertTrue(provision.contains("APT::Periodic::Unattended-Upgrade \"1\";"));
    }

    @Test
    void deployKeepsTheReleaseGateWarmUpAndRollback() throws Exception {
        String deploy = read(Paths.get("deploy-lightsail.sh"));
        String install = read(DIR.resolve("remote-install.sh"));

        assertTrue(deploy.contains("git merge-base --is-ancestor HEAD origin/main"));
        assertTrue(deploy.contains("ALLOW_UNRELEASED_COMMIT:-false"));
        assertTrue(deploy.contains("./gradlew clean build"));
        assertTrue(deploy.contains("Deploy to Lightsail now? [y/N]"));
        assertTrue(install.contains("Apache Tomcat/10\\.1\\."));
        assertTrue(install.contains("/boards/pvstboard/readPost?postNum=2"));
        assertTrue(install.contains("verify_through_nginx"));
        assertTrue(install.contains("verify_metaspace_headroom"));
        assertTrue(install.contains("percent >= 95"));
        assertTrue(install.contains("rollback_and_restart"));
        assertTrue(install.contains("sql/stopped/*.sql"));
    }

    @Test
    void backupRefusesUnencryptedRemotesByDefault() throws Exception {
        String backup = read(DIR.resolve("sc1hub-backup.sh"));

        assertTrue(backup.contains("SC1HUB_BACKUP_ALLOW_PLAIN_REMOTE:-0"));
        assertTrue(backup.contains("\"$remote_type\" != \"crypt\""));
        assertTrue(backup.contains("--single-transaction"));
        assertTrue(backup.contains("gzip -t"));
    }

    private String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
