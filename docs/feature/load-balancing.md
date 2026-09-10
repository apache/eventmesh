# Load Balancing

**Audience:** operators running multiple EventMesh instances, and anyone
wondering how traffic spreads across a cluster without peer forwarding.
Part of the offset / load-balancing / frame design trio; see also
[Offset management](offset-management.md).

---

## Architectural position

> **Balancing happens at the session-assignment layer (the `recommend`
> entry point), not at the pull/dispatch layer.**

Each instance pulls — passively, on demand — only for the clients it
serves. No peer-to-peer pull, no forwarding. First balance *which client
belongs to which instance* (the session layer); each instance's pull volume
then roughly follows — instead of "pull equally everywhere, then balance
the dispatch".

## The full-stickiness model

```
Old model (retired):                       New model (full stickiness):
  PartitionOwnership (partition%n)            × no partition ownership
  ClusterCoordinator (cross-instance fwd)     × no forwarding
  HttpForwarder (HTTP forward to peer)        × no forwarding
  ClusterSubscriptionStore (cluster subs)     × local subscriptions

enableCluster keeps only:
  ClusterMembership (heartbeat + load metrics)   feeds /session/recommend scoring
```

The forwarding classes (`PartitionOwnership` / `ClusterCoordinator` /
`HttpForwarder`) are retained but unused (reserve + tests).

## Zero client burden — instances self-report load

Instances collect their own load metrics locally (`LoadMeter`); clients
never report anything.

| Metric | Source |
| --- | --- |
| `activeSessions` | `SessionRouter` sinks / subscribeSinks size |
| `inflowBytes/s` | `UniIngressService` publish byte counters (bucketed per clientId → per-client traffic profile) |
| `outflowBytes/s` | SSE / poll egress byte counters |
| `cpuLoad` | `OperatingSystemMXBean` |

These are written with the existing 5 s heartbeat into
`/em/instances/<id>` = `<ts>|<addr>|<activeSessions>|<byteRate>|<cpuLoad>`.

## The balancing loop

```
① client first connects to any instance
   │
② GET /session/recommend?clientId=xxx
   │ → that instance reads the cluster-wide /em/instances/ (all instances + load)
   │ → score = activeSessions×w1 + byteRate×w2 + cpuLoad×w3
   │ → overload feedback: cpuLoad>0.8 or inflow>5MB/s → score += 10000 (yield)
   │ → big-client spreading: check the client's existing session spread; prefer not-yet-saturated instances
   │
③ returns the recommended instanceUrl
   │
④ POST /session/open → {sessionId, agentId, instanceUrl}
   │ → the SDK pins subsequent turn/close to that instance
   │
⑤ POST /events/subscribe → {subscriptionId, instanceUrl}
   │ → the SDK pins subsequent poll/ack to that instance
   │
⑥ all further requests go straight to that instance
   │ → instances pull only for their own clients; no peers, no forwarding
   │
⑦ failure / instance unreachable → the SDK re-fetches /session/recommend for another instance
```

## Session-granularity stickiness

- `/session/open` and `/events/subscribe` both return `instanceUrl`.
- SDK `SessionHandle.instanceUrl` → `StreamingSession` uses
  `client.withBaseUrl(instanceUrl)` to pin subsequent turn/close.
- SDK `subscribe` → `capturePollInstance` sets `pollBaseUrl`; subsequent
  poll+ack go to that instance.
- **`advertisedAddr` defaults to empty** (single-instance / tests /
  LB-compatible); set `-Deventmesh.http.advertisedAddr=host:port` to enable
  pinning.

## The costs (accepted trade-offs)

- **No forwarding ⇒ subscribers must be sticky** (otherwise a poll landing
  on a different instance sees nothing) — guaranteed by
  subscribe→instanceUrl.
- **Multiple subscribers of one topic spread across instances ⇒ each
  instance pulls every partition** (MQ read amplification: N instances = N
  reads) — the price paid to eliminate the forwarding hop and global
  coordination complexity.
- **A single session whose traffic exceeds one instance's ceiling cannot be
  split** → the instance rate-limits/rejects — a known boundary with no
  workaround.

## Where the code lives

| Piece | Location |
| --- | --- |
| Load meter | `eventmesh-runtime/.../ingress/LoadMeter.java` |
| Cluster membership / heartbeat | `eventmesh-runtime/.../cluster/ClusterMembership.java` |
| Recommend scoring | `UniIngressService` (`/session/recommend`) |
| Session routing | `eventmesh-runtime/.../session/SessionRouter.java`, `Matchmaker.java` |
| SDK pinning | `eventmesh-sdks/.../cloudevents/` (`capturePollInstance`, `SessionHandle`) |
| Tests | `ClusterMembershipLoadTest`, `LoadMeterTest`, `LoadBalancingScoringTest` |
