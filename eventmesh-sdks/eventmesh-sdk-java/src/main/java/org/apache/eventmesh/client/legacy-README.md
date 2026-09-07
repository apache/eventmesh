# Legacy TCP client (org.apache.eventmesh.client.tcp.*)

> **Status:** Legacy. Maintained for backward compatibility; no new features.

This package contains the legacy TCP client used by older EventMesh
deployments. It supports three wire formats:

* `client/tcp/impl/openmessage` - uses the OpenMessaging API. The
  `io.openmessaging:openmessaging-api` dependency is now declared as
  `implementation` in the SDK's `build.gradle`, so downstream users
  who only depend on the modern CloudEvents client do not see OMA types.
  Users of this legacy client must add `io.openmessaging:openmessaging-api`
  to their own classpath.
* `client/tcp/impl/cloudevent` - CloudEvents over the legacy TCP
  framing.
* `client/tcp/impl/eventmeshmessage` - the original `EventMeshMessage`
  length-prefixed framing.

## Migration to the modern CloudEvents HTTP client

1. Switch to
   `org.apache.eventmesh.client.cloudevents.CloudEventsClient`. See
   `docs/eventmesh-client-guide.md`.
2. The CloudEvents wire format replaces the `EventMeshMessage` / OMA
   `Message` envelope. Event payload stays the same.
3. Remove `io.openmessaging:openmessaging-api` from your application's
   dependencies once you have migrated.

A deprecation warning is logged on every legacy client construction; the
class itself is not removed in this release but is planned for removal
in the next major version.

Tracked by #5341. See `docs/protocols.md` for the full inventory.
