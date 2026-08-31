/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.runtime.state.fault;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Test harness for issue #5314 scenario 1: crash a child JVM via {@code Process.destroyForcibly()}
 * (SIGKILL on POSIX / TerminateProcess on Windows) at a deterministic point, then relaunch a
 * fresh JVM against the same on-disk stores and verify the recovered state.
 *
 * <p>The harness is gated on the {@code ENABLE_JVM_CRASH_HARNESS} environment variable. CI
 * environments that lack process-control permissions (most sandboxes, Windows containers
 * without proper job objects) can skip it without breaking the rest of the suite.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   JvmCrashHarness h = new JvmCrashHarness(tempDir);
 *   h.runChild(args, child -> {
 *       // pre-crash setup; the child writes a sentinel file when it reaches the crash gate
 *       Path sentinel = tempDir.resolve("crash-gate");
 *       Files.writeString(sentinel, "ready");
 *       return sentinel;
 *   });
 *   // After SIGKILL the harness waits for the sentinel, then relaunches and asserts.
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "ENABLE_JVM_CRASH_HARNESS", matches = "true")
public final class JvmCrashHarness {

    private static final Logger LOGGER = Logger.getLogger(JvmCrashHarness.class.getName());

    private final Path workingDir;

    public JvmCrashHarness(Path workingDir) {
        this.workingDir = workingDir;
    }

    /**
     * Spawn a child JVM with {@code args} (passed to {@code java -cp ... Main args...}). The
     * {@code crashGate} function runs in the test JVM: when the child writes a "ready" file
     * matching the returned {@link Path}, the harness kills the child via SIGKILL and relaunches
     * a fresh JVM with the same args. The function may return {@code null} to skip the relaunch.
     */
    public int runChildAndCrash(Function<Path, Path> crashGate, List<String> args) throws Exception {
        int firstRun = runChildUntilSentinel(crashGate, args, true);
        if (crashGate.apply(workingDir) == null) {
            return firstRun;
        }
        // Relaunch a fresh JVM with the same args; the function re-applies the same gate path so
        // the test's child can detect "second run" via the sentinel's content.
        return runChildUntilSentinel(crashGate, args, false);
    }

    private int runChildUntilSentinel(Function<Path, Path> gateFn, List<String> args, boolean firstRun)
            throws Exception {
        Path gate = gateFn.apply(workingDir);
        if (gate == null) {
            return 0;
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + "/bin/java");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        // Mark which run this is so the child can branch.
        cmd.add("-Dcrash.harness.run=" + (firstRun ? "1" : "2"));
        cmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(cmd).directory(workingDir.toFile()).redirectErrorStream(true);
        Process p = pb.start();
        // Drain stdout asynchronously so the child doesn't block on a full pipe.
        Thread drainer = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    LOGGER.fine("[child] " + line);
                }
            } catch (Exception expected) {
                // Best-effort drain: when the child is SIGKILLed (the whole point of this
                // harness) the stream dies mid-read and there is nothing to recover.
            }
        }, "crash-harness-drain");
        drainer.setDaemon(true);
        drainer.start();

        // Wait for the sentinel file the child writes when it reaches the crash gate.
        long deadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(gate) && Files.size(gate) > 0) {
                // Hard-kill the child (SIGKILL / TerminateProcess). The child does not get a
                // shutdown hook — we are simulating a JVM crash.
                p.destroyForcibly();
                if (!p.waitFor(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("child JVM did not die after SIGKILL");
                }
                return p.exitValue();
            }
            Thread.sleep(50);
        }
        p.destroyForcibly();
        throw new IllegalStateException("child JVM did not reach crash gate within 30s");
    }
}
