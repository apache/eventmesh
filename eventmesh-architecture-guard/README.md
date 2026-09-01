# :eventmesh-architecture-guard

Architecture guard for issue #5305. Enforces the package boundaries
introduced by #5298 (`eventmesh-common` sub-packages) and #5297
(`eventmesh-runtime` sub-packages).

## What it does

Nine ArchUnit rules, asserted in FAIL mode: any violation breaks the
build.

`eventmesh-common` boundaries (from #5298):

| Rule                   | Forbids                                                                                   |
|------------------------|-------------------------------------------------------------------------------------------|
| ruleInternalHidden     | org.apache.eventmesh.common.internal.. reached from outside org.apache.eventmesh.common..  |
| ruleHttpProtocolHidden | org.apache.eventmesh.common.protocol.http.. from modules other than meshmessage / sdks      |
| ruleGrpcProtocolHidden | org.apache.eventmesh.common.protocol.grpc.. from modules other than meshmessage / sdks      |
| ruleTcpProtocolHidden  | org.apache.eventmesh.common.protocol.tcp.. from modules other than meshmessage / sdks / runtime |
| ruleOldUtilsRenamed    | org.apache.eventmesh.common.utils.. (renamed to .util.. in #5298)                          |

`eventmesh-runtime` boundaries (from #5297):

| Rule                                | Forbids                                                                  |
|-------------------------------------|--------------------------------------------------------------------------|
| ruleRuntimeTcpInternalHidden        | runtime.tcp.internal.. reached from outside runtime.tcp..                 |
| ruleRuntimeEngineIsolatedFromInfra  | runtime.boot / ingress / delivery depending on runtime.tcp.internal..     |
| ruleRuntimePushDoesNotImportCodec   | runtime.push depending on runtime.tcp.internal..                          |
| ruleRuntimeSubscriptionStateIsolated | runtime.ingress depending on runtime.state.internal..                    |

## Severity: FAIL

`ArchitectureRulesTest` calls `rule.check(classes)` for every rule, so
a violation throws and fails the task.

This replaced the original WARN mode, which turned out to be silent:
the module has no SLF4J binding on its test runtime classpath, so
`LoggerFactory` hands back the NOP logger and every
`LOG.warn(report)` call was discarded. Violations were reported
nowhere. FAIL mode does not depend on logging at all.

## Running locally

```bash
./gradlew :eventmesh-architecture-guard:architectureCheck
```

## Continuous integration

`.github/workflows/architecture-guard.yml` runs the same task as its
own check on pushes and pull requests that touch the analysed modules,
so a violation appears as "Architecture Guard" on the PR rather than
buried in the Build job log. The matrix build in `ci.yml` excludes
`:eventmesh-architecture-guard:test` to avoid running the rules twice.
