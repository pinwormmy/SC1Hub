package com.sc1hub.deploy;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSafetyScriptTest {

    @Test
    void deployWarmsRepresentativeRoutesAndRejectsLowMetaspaceHeadroom() throws Exception {
        String deployScript = read(Paths.get("deploy.sh"));

        assertTrue(deployScript.contains("-Dsun.reflect.inflationThreshold=2147483647"));
        assertTrue(deployScript.contains("-XX:OnOutOfMemoryError="));
        assertTrue(deployScript.contains("REMOTE_OOM_RECOVERY_SCRIPT"));
        assertTrue(deployScript.contains("warm_up_representative_routes"));
        assertTrue(deployScript.contains("/boards/pvstboard/readPost?postNum=2"));
        assertTrue(deployScript.contains("verify_metaspace_headroom"));
        assertTrue(deployScript.contains("-ge 60000"));
        assertTrue(deployScript.contains("rollback_and_restart"));
    }

    @Test
    void oomRecoveryOnlyTerminatesOneVerifiedCatalinaProcess() throws Exception {
        String recoveryScript = read(Paths.get("scripts/restart-tomcat-after-oom.sh"));

        assertTrue(recoveryScript.startsWith("#!/home/bin/bash2\n"));
        assertTrue(recoveryScript.contains("org.apache.catalina.startup.Bootstrap"));
        assertTrue(recoveryScript.contains("verify_catalina_pid"));
        assertTrue(recoveryScript.contains("if (( pid_count > 1 )); then"));
        assertTrue(recoveryScript.contains("RESTART_COOLDOWN_SECONDS"));
        assertTrue(recoveryScript.contains("DRY_RUN"));
        assertFalse(recoveryScript.contains("pkill"));
        assertFalse(recoveryScript.contains("killall"));
    }

    private String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
