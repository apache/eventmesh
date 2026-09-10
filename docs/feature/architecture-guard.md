# EventMesh Architecture Guard

> Issue: #5305 (P0), #5322 (fail-mode), #5342 (Q7 expansion)

This document is the **canonical reference** for the `eventmesh-architecture-guard`
module. It explains every ArchUnit rule, its rationale, and how to add a new one.

If you are a contributor opening a PR that touches one of the analysed modules,
**read this first** -- it tells you which boundaries you must respect and what
to expect if you cross one.

## How it works

The `eventmesh-architecture-guard` Gradle module runs an ArchUnit analysis
across the production classpath on every build (and in CI). Every rule is
asserted in **FAIL mode** (`rule.check(classes)` throws on violation), so a
boundary break fails the build.

The CI workflow at `.github/workflows/architecture-guard.yml` runs the
analysis as its own check on every PR that touches the analysed modules,
so a violation appears as "Architecture Guard" on the PR rather than buried
in the 30-minute Build job log.

The list of analysed modules (i.e. what triggers the workflow and what
shows up in the classpath for `loadProductionClasses()`):

| Module                                     | Reason for analysis                       |
|--------------------------------------------|-------------------------------------------|
| `eventmesh-common`                         | source of `common.internal..`, `common.protocol.*` |
| `eventmesh-runtime`                        | source of `runtime.tcp.internal..`, etc.  |
| `eventmesh-spi`                            | guaranteed "outside" package for that-clauses |
| `eventmesh-protocol-plugin`                | meshmessage / SDKs / protocol sub-packages |
| `eventmesh-storage-plugin` (kafka only)    | the storage plugin canary for ruleStoragePlugins* |
| `eventmesh-connector-api`                  | connector SPI boundary                    |
| `eventmesh-connector-runtime`              | runtime classes named in ruleConnector*   |
| `eventmesh-connector-plugin:eventmesh-connector-file` | the connector plugin canary for ruleConnector* |

## Severity: FAIL

Earlier versions used a `*_warn` rule shape that logged violations to SLF4J
but never failed. That mode was silent because the test module has no SLF4J
binding on its runtime classpath -- `LoggerFactory` hands back the NOP
logger and every `LOG.warn(report)` was discarded. **All rules now use
`rule.check(classes)`** in the JUnit test, which throws `AssertionError`
with a per-class violation report.

## Rules (12 total)

### `eventmesh-common` boundaries (5)

| # | Rule                       | Forbids                                                                                                |
|---|----------------------------|--------------------------------------------------------------------------------------------------------|
| 1 | ruleInternalHidden         | `org.apache.eventmesh.common.internal..` reached from outside `org.apache.eventmesh.common..`          |
| 2 | ruleHttpProtocolHidden     | `org.apache.eventmesh.common.protocol.http..` from modules other than meshmessage / sdks               |
| 3 | ruleGrpcProtocolHidden     | `org.apache.eventmesh.common.protocol.grpc..` from modules other than meshmessage / sdks               |
| 4 | ruleTcpProtocolHidden      | `org.apache.eventmesh.common.protocol.tcp..` from modules other than meshmessage / sdks / runtime      |
| 5 | ruleOldUtilsRenamed        | `org.apache.eventmesh.common.utils..` (renamed to `.util..` in #5298)                                  |

### `eventmesh-runtime` boundaries (4)

| #  | Rule                                  | Forbids                                                                       |
|----|---------------------------------------|-------------------------------------------------------------------------------|
| 6  | ruleRuntimeTcpInternalHidden          | `runtime.tcp.internal..` reached from outside `runtime.tcp..`                 |
| 7  | ruleRuntimeEngineIsolatedFromInfra    | `runtime.boot / ingress / delivery` depending on `runtime.tcp.internal..`     |
| 8  | ruleRuntimePushDoesNotImportCodec     | `runtime.push` depending on `runtime.tcp.internal..`                          |
| 9  | ruleRuntimeSubscriptionStateIsolated  | `runtime.ingress` depending on `runtime.state.internal..`                     |

### Plugin SPI boundaries (3)

| #  | Rule                                       | Forbids                                                                                                |
|----|--------------------------------------------|--------------------------------------------------------------------------------------------------------|
| 10 | ruleConnectorPluginsDependOnlyOnSpi        | `org.apache.eventmesh.connector.<plugin>..` depending on connector-runtime internals by name           |
| 11 | ruleStoragePluginsIsolated                 | one storage plugin's package reaching into another storage plugin's package                            |
| 12 | ruleStoragePluginsDependOnlyOnApi         | a storage plugin depending on `eventmesh.runtime..` or `eventmesh.connector.runtime..`                  |

## How to add a new rule

1. Open `ArchitectureRules.java` in
   `eventmesh-architecture-guard/src/main/java/org/apache/eventmesh/architecture/guard/`.
2. Add a `public static ArchRule ruleXxx = noClasses()...` (or `classes()...`)
   field. End the chain with `.because("...")` so the violation report
   tells the reader what to fix.
3. Add a `void ruleXxx_check() { ArchitectureRules.ruleXxx.check(classes); }`
   method in `ArchitectureRulesTest`. **The test method MUST be named
   `*_check`** (the B mode that fails on violation).
4. If your rule targets a module not yet in the `testImplementation`
   list of `eventmesh-architecture-guard/build.gradle`, add it.
5. Update the rule table in this document.
6. Run `./gradlew :eventmesh-architecture-guard:architectureCheck` to
   confirm your rule loads and the test passes on a clean tree.

## Running locally

```bash
./gradlew :eventmesh-architecture-guard:architectureCheck
# or, for the JUnit form (FAIL mode):
./gradlew :eventmesh-architecture-guard:test
```

## CI

`.github/workflows/architecture-guard.yml` runs the same task as its
own check on pushes and pull requests that touch the analysed modules.
The matrix build in `ci.yml` excludes `:eventmesh-architecture-guard:test`
to avoid running the rules twice.

## Intentional-violation proof (issue #5342 acceptance)

Issue #5342 asks for "an intentional-violation PR that proves the
guard catches violations." We achieve this **without a separate PR** by
shipping two canary classes in `src/test/java/`:

- `org.apache.eventmesh.connector.fakeplugin.FakePluginCanary` (introduced
  in PR #5328) -- reaches into
  `org.apache.eventmesh.connector.ConnectorRuntime`. The companion
  `ArchitectureRulesTest.ruleConnectorPluginsDependOnlyOnSpiCatchesViolations`
  asserts the rule fails with the canary class named in the report.
- `org.apache.eventmesh.storage.fakeplugin.FakeStorageCanary`
  (introduced in this issue) -- reaches into
  `org.apache.eventmesh.storage.rocketmq5.storage`. The companion
  `ruleStoragePluginsIsolated_catches` test asserts the same shape for
  the storage plugin boundary.

These canaries live in `src/test/java/` so production code stays clean,
and the production rule (`DO_NOT_INCLUDE_TESTS`) is exercised by a
focused unit test that re-imports the canary package. This is the same
pattern ArchUnit's own examples use for "prove the rule works."

## Further reading

- `docs/storage-spi.md` -- the storage capability matrix
- `eventmesh-architecture-guard/README.md` -- module-level summary
- CONTRIBUTING.md -- how to add a new module
