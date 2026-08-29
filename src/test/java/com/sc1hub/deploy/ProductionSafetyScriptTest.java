package com.sc1hub.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSafetyScriptTest {

    @Test
    void deployWarmsRepresentativeRoutesAndRejectsLowMetaspaceHeadroom() throws Exception {
        String deployScript = read(Paths.get("deploy.sh"));

        assertTrue(deployScript.contains("-Dsun.reflect.inflationThreshold=2147483647"));
        assertTrue(deployScript.contains("-XX:OnOutOfMemoryError=exec"));
        assertTrue(deployScript.contains("%p"));
        assertTrue(deployScript.contains("OOM_ARG_COUNT"));
        assertTrue(deployScript.contains("REMOTE_OOM_RECOVERY_SCRIPT"));
        assertTrue(deployScript.contains("warm_up_representative_routes"));
        assertTrue(deployScript.contains("/boards/pvstboard/readPost?postNum=2"));
        assertTrue(deployScript.contains("verify_metaspace_headroom"));
        assertTrue(deployScript.contains("jinfo -flag MaxMetaspaceSize"));
        assertTrue(deployScript.contains("METASPACE_PERCENT"));
        assertTrue(deployScript.contains("-ge 95"));
        assertTrue(deployScript.contains("-ge 85"));
        assertTrue(deployScript.contains("metaspace-history.log"));
        assertFalse(deployScript.contains("-ge 60000"));
        assertTrue(deployScript.contains("rollback_and_restart"));
    }

    @Test
    void deployPreservesCatalinaOutInsteadOfTruncatingIt() throws Exception {
        String deployScript = read(Paths.get("deploy.sh"));

        assertTrue(deployScript.contains("ROTATED="));
        assertTrue(deployScript.contains("Could not preserve catalina.out"));
        assertTrue(deployScript.contains("NR > 10"));
    }

    @Test
    void oomRecoveryOnlyTerminatesOneVerifiedCatalinaProcess() throws Exception {
        String recoveryScript = read(Paths.get("scripts/restart-tomcat-after-oom.sh"));

        assertTrue(recoveryScript.startsWith("#!/home/bin/bash2\n"));
        assertTrue(recoveryScript.contains("org.apache.catalina.startup.Bootstrap"));
        assertTrue(recoveryScript.contains("verify_catalina_pid"));
        assertTrue(recoveryScript.contains("EXPECTED_PID"));
        assertTrue(recoveryScript.contains("close_inherited_socket_descriptors"));
        assertTrue(recoveryScript.contains("[[ -S \"$fd_path\" ]]"));
        assertTrue(recoveryScript.contains("wait_for_ports_to_close"));
        assertTrue(recoveryScript.contains("unset CATALINA_OPTS"));
        assertTrue(recoveryScript.contains("if (( pid_count > 1 )); then"));
        assertTrue(recoveryScript.contains("RESTART_COOLDOWN_SECONDS"));
        assertTrue(recoveryScript.contains("DRY_RUN"));
        assertFalse(recoveryScript.contains("jps"));
        assertFalse(recoveryScript.contains("pkill"));
        assertFalse(recoveryScript.contains("killall"));
    }

    @Test
    void oomRecoveryDryRunVerifiesExpectedPidFromProc(@TempDir Path tempDir) throws Exception {
        Path tomcatDir = tempDir.resolve("tomcat");
        Path procRoot = tempDir.resolve("proc");
        Path processDir = procRoot.resolve("4242");
        Path stateDir = tempDir.resolve("state");
        Path logFile = tempDir.resolve("oom-recovery.log");
        Files.createDirectories(processDir);
        String commandLine = "java\0"
                + "org.apache.catalina.startup.Bootstrap\0"
                + "-Dcatalina.home=" + tomcatDir + "\0";
        Files.write(processDir.resolve("cmdline"), commandLine.getBytes(StandardCharsets.UTF_8));

        ProcessBuilder processBuilder = new ProcessBuilder(
                "/bin/bash", "scripts/restart-tomcat-after-oom.sh", "4242");
        processBuilder.environment().put("TOMCAT_DIR", tomcatDir.toString());
        processBuilder.environment().put("PROC_ROOT", procRoot.toString());
        processBuilder.environment().put("STATE_DIR", stateDir.toString());
        processBuilder.environment().put("LOG_FILE", logFile.toString());
        processBuilder.environment().put("DRY_RUN", "true");
        Process process = processBuilder.start();

        assertEquals(0, process.waitFor());
        String log = read(logFile);
        assertTrue(log.contains("Dry run verified Catalina PID 4242"));
        assertFalse(Files.exists(stateDir.resolve("last-run-epoch")));
    }

    private String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
