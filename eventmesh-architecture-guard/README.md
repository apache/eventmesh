# :eventmesh-architecture-guard

Minimum-viable architecture guard for issue #5305. Ships with the #5298
PR (sub-package split of eventmesh-common).

## What it does

Five ArchUnit rules that enforce the new org.apache.eventmesh.common
sub-package boundaries declared in #5298:

| Rule                       | Forbids                                                                                  |
|----------------------------|------------------------------------------------------------------------------------------|
| ruleInternalHidden         | org.apache.eventmesh.common.internal.. reached from outside org.apache.eventmesh.common.. |
| ruleHttpProtocolHidden     | org.apache.eventmesh.common.protocol.http.. from modules other than meshmessage / sdks    |
| ruleGrpcProtocolHidden     | org.apache.eventmesh.common.protocol.grpc.. from modules other than meshmessage / sdks    |
| ruleTcpProtocolHidden      | org.apache.eventmesh.common.protocol.tcp.. from modules other than meshmessage / sdks / runtime |
| ruleOldUtilsRenamed        | org.apache.eventmesh.common.utils.. (renamed to .util.. in #5298)                         |

## Severity: WARN in 1.13.0, FAIL in 1.14.0

The 1.13.0 release ships the rules in WARN mode (rule evaluations are
logged via System.out.println in ArchitectureRulesTest, but violations
do NOT fail `gradle check`). From 1.14.0 the *_warn test methods will
be replaced with hard rule.check(classes) assertions, at which point
new violations WILL fail `gradle architectureCheck` and thus the build.

## Running locally

```bash
./gradlew :eventmesh-architecture-guard:architectureCheck
```
