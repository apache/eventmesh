// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

//! EventMesh runtime lifecycle for the e2e suite.
//!
//! [`ensure_runtime`] is process-global (guarded by a `OnceLock`): the first
//! caller starts the rocketmq stack via `docker compose` and blocks until its
//! healthcheck passes (`up --wait`). Subsequent callers (other parallel test
//! threads) reuse the already-running server.
//!
//! If **we** started the stack, a [`ctor::dtor`] brings it down once the test
//! binary exits. Set `EVENTMESH_E2E_EXTERNAL=1` to skip Docker entirely and use
//! a server you started yourself.

use std::path::PathBuf;
use std::process::Command;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::OnceLock;
use std::time::Duration;

use tracing::{info, warn};

/// gRPC port of the EventMesh runtime.
pub(crate) const GRPC_PORT: u16 = 10_205;
/// HTTP port of the EventMesh runtime.
pub(crate) const HTTP_PORT: u16 = 10_105;
/// TCP port of the EventMesh runtime.
pub(crate) const TCP_PORT: u16 = 10_000;
/// Admin (HTTP) port, used for topic creation + readiness probes.
pub(crate) const ADMIN_PORT: u16 = 10_106;
/// Host the runtime is reachable on from the test host.
pub(crate) const HOST: &str = "127.0.0.1";

/// Set to true iff the harness itself launched `docker compose`, so the dtor
/// only tears down what it started.
static TEARDOWN_NEEDED: AtomicBool = AtomicBool::new(false);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Mode {
    /// A server was already reachable (or `EVENTMESH_E2E_EXTERNAL` set); we did
    /// not start anything.
    External,
    /// We launched `docker compose` and must stop it on exit.
    Started,
    /// No Docker and no server — tests must skip.
    Unavailable,
}

static MODE: OnceLock<Mode> = OnceLock::new();

/// Absolute path of this crate's manifest dir, captured at compile time. The
/// `docker-compose.yml` + `docker/conf/` live alongside the crate, keeping the
/// e2e suite fully self-contained.
const MANIFEST_DIR: &str = env!("CARGO_MANIFEST_DIR");

fn compose_file() -> PathBuf {
    PathBuf::from(MANIFEST_DIR).join("docker-compose.yml")
}

/// Best-effort make the bind-mounted `docker/conf/` dir traversable and its
/// files world-readable.
///
/// The compose file bind-mounts `./docker/conf/*` into containers that run as
/// a different uid than the host user (e.g. the rocketmq image runs as uid
/// 3000). On hosts where the repo lives behind a restrictive ACL
/// (`other::---`), those containers can't read their own config and the broker
/// crashes on boot with `FileNotFoundException: ... (Permission denied)`. We
/// open the perms once, up front, so the e2e suite is self-contained.
///
/// No-op where the perms are already open; failures (e.g. read-only fs) are
/// ignored — the real failure will surface as an unreadable-config crash from
/// `docker compose up` below.
fn ensure_conf_readable() {
    let dir = PathBuf::from(MANIFEST_DIR).join("docker").join("conf");
    // Directory must be traversable (o+x) for the container to resolve files
    // inside it; files must be readable (o+r).
    let _ = Command::new("chmod").args(["o+rx", dir.to_str().unwrap_or(".")]).status();
    if let Ok(entries) = std::fs::read_dir(&dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            let _ = Command::new("chmod")
                .args(["o+r", path.to_str().unwrap_or(".")])
                .status();
        }
    }
}

/// Ensure an EventMesh runtime is reachable. Returns `false` (and emits a skip
/// notice) when neither Docker nor a live server is available.
///
/// Idempotent and thread-safe: the (potentially slow) `docker compose up` runs
/// at most once per process; parallel test threads block on the `OnceLock`
/// until it resolves.
pub(crate) fn ensure_runtime() -> bool {
    let &mode = MODE.get_or_init(initialize);
    match mode {
        Mode::External | Mode::Started => true,
        Mode::Unavailable => {
            // Already warned during init; just signal "skip".
            false
        }
    }
}

/// The hostname an EventMesh runtime should use to POST webhook callbacks to a
/// server running in this test process.
///
/// The runtime almost always lives in a container — either the harness started
/// it via `docker compose` (`Mode::Started`), or the user pre-started the very
/// same compose file and we reuse it (`Mode::External`). In both cases the
/// container cannot reach the test process via `127.0.0.1` (that resolves to
/// the container's own loopback), so we advertise `host.docker.internal`,
/// which both profiles in `docker-compose.yml` map to the host gateway.
///
/// Override with the `EVENTMESH_E2E_WEBHOOK_HOST` env var for non-containerized
/// setups (e.g. a runtime running directly on the host via `bin/start.sh`, in
/// which case `127.0.0.1` is correct) or a server on another host.
pub(crate) fn webhook_host() -> String {
    if let Ok(h) = std::env::var("EVENTMESH_E2E_WEBHOOK_HOST") {
        return h;
    }
    match MODE.get() {
        // Both compose profiles map host.docker.internal -> host-gateway, so
        // callbacks from the container reach the test process on the host.
        // This deliberately covers Mode::External too: the common "external"
        // case is a user who pre-started this crate's compose file, where the
        // runtime is still containerized and 127.0.0.1 would route callbacks
        // to the container's loopback and silently time out the webhook tests.
        // For a genuinely non-containerized local runtime, set
        // EVENTMESH_E2E_WEBHOOK_HOST=127.0.0.1.
        Some(&Mode::Started | &Mode::External) => "host.docker.internal".to_string(),
        _ => "127.0.0.1".to_string(),
    }
}

/// The resolved runtime mode, or `None` before [`ensure_runtime`] has been
/// called.
///
/// Tests use this to distinguish the harness-launched broker (always the
/// `rocketmq` profile, where every feature is expected to work) from an
/// externally-provided server (which may be the feature-limited standalone
/// broker).
pub(crate) fn mode() -> Option<Mode> {
    MODE.get().copied()
}

fn initialize() -> Mode {
    // 1) Explicit "use my own server" override.
    if std::env::var_os("EVENTMESH_E2E_EXTERNAL").is_some() {
        if probe_admin(Duration::from_secs(10)) {
            info!("EVENTMESH_E2E_EXTERNAL set and server is reachable");
            return Mode::External;
        }
        warn!(
            "EVENTMESH_E2E_EXTERNAL set but no server on {HOST}:{ADMIN_PORT}; \
             marking unavailable"
        );
        return Mode::Unavailable;
    }

    // 2) Server already up? Reuse it, start nothing.
    if probe_admin(Duration::from_secs(2)) {
        info!("found an already-running EventMesh; reusing it");
        return Mode::External;
    }

    // 3) Try to launch via docker compose.
    if !docker_available() {
        eprintln!(
            "[e2e] skipping: no EventMesh server on {HOST}:{ADMIN_PORT} and \
             `docker` is not on PATH. Start one with \
             `docker compose --profile rocketmq up -d`, or set \
             EVENTMESH_E2E_EXTERNAL=1."
        );
        return Mode::Unavailable;
    }

    let compose = compose_file();
    let project_dir = PathBuf::from(MANIFEST_DIR);

    // The compose file bind-mounts ./docker/conf/* into containers that run
    // as a different uid (rocketmq runs as uid 3000). On hosts where the
    // conf dir/files inherit a restrictive ACL (`other::---`), those
    // containers can't even read their own config and the broker crashes on
    // boot. Make the conf dir traversable and its files world-readable before
    // bringing the stack up so the suite is self-contained regardless of the
    // host's umask/ACL. Best-effort: a no-op where the perms are already open.
    ensure_conf_readable();

    info!(?compose, "starting EventMesh via docker compose (rocketmq)");
    let up = Command::new("docker")
        .args([
            "compose",
            "-f",
            compose.to_str().expect("utf-8 compose path"),
            "--project-directory",
            project_dir.to_str().expect("utf-8 project dir"),
            "--profile",
            "rocketmq",
            "up",
            "-d",
            "--wait",
        ])
        // Run from the crate dir so the relative bind-mounts in the compose file
        // (./docker/conf/...) resolve against this crate, not the repo root.
        .current_dir(&project_dir)
        .status();
    match up {
        Ok(s) if s.success() => {
            TEARDOWN_NEEDED.store(true, Ordering::SeqCst);
            // `--wait` returns once the healthcheck is RUNNING; give the gRPC
            // listener a final moment to settle.
            wait_for_admin(Duration::from_secs(30));
            info!("EventMesh runtime is up");
            Mode::Started
        }
        Ok(s) => {
            eprintln!("[e2e] `docker compose up` exited with {s}; skipping tests");
            Mode::Unavailable
        }
        Err(e) => {
            eprintln!("[e2e] failed to invoke `docker compose`: {e}; skipping tests");
            Mode::Unavailable
        }
    }
}

/// Best-effort teardown of the stack we started. Runs exactly once, at process
/// exit (including panic unwind under the default test profile).
#[ctor::dtor]
fn teardown() {
    if !TEARDOWN_NEEDED.load(Ordering::SeqCst) {
        return;
    }
    let compose = compose_file();
    let project_dir = PathBuf::from(MANIFEST_DIR);
    info!("stopping EventMesh via docker compose");
    let _ = Command::new("docker")
        .args([
            "compose",
            "-f",
            compose.to_str().expect("utf-8 compose path"),
            "--project-directory",
            project_dir.to_str().expect("utf-8 project dir"),
            "--profile",
            "rocketmq",
            "down",
        ])
        .current_dir(&project_dir)
        .status();
}

fn docker_available() -> bool {
    Command::new("docker")
        .arg("--version")
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .status()
        .is_ok_and(|s| s.success())
}

/// Poll the admin HTTP port until it accepts a connection or `timeout` elapses.
fn wait_for_admin(timeout: Duration) -> bool {
    let deadline = std::time::Instant::now() + timeout;
    while std::time::Instant::now() < deadline {
        if probe_admin(Duration::from_millis(500)) {
            return true;
        }
        std::thread::sleep(Duration::from_millis(500));
    }
    false
}

/// Try a single TCP connect to the admin port within `per_attempt` (capped).
fn probe_admin(per_attempt: Duration) -> bool {
    use std::net::TcpStream;
    let addr = format!("{HOST}:{ADMIN_PORT}");
    TcpStream::connect_timeout(
        &addr.parse().expect("valid admin addr"),
        per_attempt.min(Duration::from_secs(2)),
    )
    .is_ok()
}
