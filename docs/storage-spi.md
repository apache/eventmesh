# EventMesh Storage SPI

> Issue: #5342 (Q6 storage capability coverage)
> Landed in: PR #5323 (#5303 -- StorageCapabilities) + PR (this doc)

This document is the **single source of truth** for which storage plugin
implements which capability, and how callers detect a capability at
runtime. Plugin authors update this matrix when they add or drop a
capability; reviewers check it on every PR that touches a storage plugin.

## SPI surface

`MeshStoragePlugin` (in
`org.apache.eventmesh.api.storage`) is the unified storage SPI every
backend implements. The 7 capabilities are sub-interfaces of
`StorageCapabilities` (same package). A plugin **declares** a capability
by listing it in its `implements` clause. A caller **detects** a
capability with `instanceof`:

```java
if (storage instanceof StorageCapabilities.EndOffsetQuery) {
    long end = ((StorageCapabilities.EndOffsetQuery) storage)
        .endOffset(topic, partition);
}
```

The 3 universal capabilities (TopicManagement, PartitionAssignment,
ExplicitOffsetCommit) MUST be implemented by every backend. The 4
backend-specific capabilities (EndOffsetQuery, AlignPullOffset,
DeferredPopAck, LiteTopic) are declared only by the backends that
actually implement them.

## Capability matrix

| Capability              | Universal?  | Kafka | RocketMQ 4.x | RocketMQ 5.x | Why                                                                   |
|-------------------------|-------------|-------|--------------|--------------|-----------------------------------------------------------------------|
| TopicManagement         | yes (U)     |   Y   |       Y      |       Y      | All 3 MQs expose `createTopic` (Kafka Admin / RocketMQ Admin).        |
| PartitionAssignment     | yes (U)     |   Y   |       Y      |       Y      | All 3 MQs treat the topic as a partitionable stream.                  |
| ExplicitOffsetCommit    | yes (U)     |   Y   |       Y      |       Y      | All 3 MQs persist an explicit commitOffset on demand.                |
| EndOffsetQuery          | no (Kafka)  |   Y   |       N      |       N      | Only Kafka exposes a high-watermark via `endOffsets`.                 |
| AlignPullOffset         | mixed       |   Y   |       Y      |       N      | Kafka + RocketMQ 4.x manage a client-side pull cursor; R5 POP is broker-managed. |
| DeferredPopAck          | R5 only     |   N   |       N      |       Y      | R5 POP holds messages invisible until client ACK.                     |
| LiteTopic               | R5 only     |   N   |       N      |       Y      | R5 RIP-83 lite sub-topics; not in Kafka or R4.                        |

U = Universal (every backend MUST implement).

## Plugin load-time contract

`StorageCapabilities` is a **marker interface system**, not a Java SPI
attribute. There is no plugin-load-time capability declaration
(currently). The contract is:

- A plugin **implements** a sub-interface -> it has the method
- A caller uses `instanceof` to detect and dispatch
- The `MeshStoragePluginTCK` (JUnit 5 abstract base class) verifies
  the plugin actually implements every capability its test class
  declares in `expectedCapabilities()`

Why no load-time check: the current `EventMeshSPI` loader reads the
`META-INF/eventmesh/...` registry file; capabilities are an
**interface-implements** property, not a metadata property. Adding a
load-time capability gate would require changing the SPI loader to
reflect over `implements` clauses -- possible but not yet implemented
(see "Future work" below).

## TCK coverage

Each backend ships a `*MeshStoragePluginTCKTest` class that extends
`MeshStoragePluginTCK` and runs:

- `pluginDeclaresExpectedCapabilities` -- every capability listed in
  `expectedCapabilities()` is actually `instanceof`-true
- `initWithMinimalPropsDoesNotThrow` -- init is lazy / safe
- `shutdownAfterInitIsIdempotent` -- shutdown can be called twice
- `createTopicIsCallable_whenDeclared` -- TopicManagement contract
- `commitOffsetIsCallable_whenDeclared` -- ExplicitOffsetCommit contract
- `assignPartitionsIsCallable_whenDeclared` -- PartitionAssignment contract
- Backend-specific tests gated on the corresponding capability

The TCK is part of every plugin module's `src/test/java` and is
included in the standard `./gradlew :eventmesh-storage-plugin:test`
build (and CI).

## Future work (deliberately out of scope for #5342)

1. **Load-time capability gate** -- reflect over `implements` at SPI
   load time, log a WARN if a plugin's metadata lists a capability it
   does not implement, log an ERROR if a caller requires a capability
   the plugin does not list. Requires an SPI loader change.
2. **Capability matrix auto-generator** -- a Gradle task that
   reflection-scans each `*MeshStoragePlugin.class` for `implements
   StorageCapabilities.X` and updates this doc on every build. Until
   then, plugin authors must update the table by hand on capability
   changes.
3. **Kafka LiteTopic / R4 EndOffsetQuery shims** -- if a future use
   case needs these, the missing capabilities can be implemented via
   a thin wrapper (Kafka's lite-topic emulation via compacted topic +
   key-prefix; R4's endOffset via `maxOffset`).
