# Contributing to Apache EventMesh

Thank you for your interest in contributing to Apache EventMesh! This page
gives a quick orientation for new contributors. For the full governance
process (releases, PMC, voting), see the
[Apache EventMesh community site](https://eventmesh.apache.org/community/).

## Before you open a PR

1. Read the [EventMesh architecture guard](docs/architecture-guard.md)
   if your change touches any of these modules:
   - `eventmesh-common`
   - `eventmesh-runtime`
   - `eventmesh-spi`
   - `eventmesh-protocol-plugin`
   - `eventmesh-storage-plugin`
   - `eventmesh-connector-api` / `eventmesh-connector-runtime`
   - `eventmesh-connector-plugin/**`

   The guard runs in CI and fails on boundary violations. Local
   iteration:

   ```bash
   ./gradlew :eventmesh-architecture-guard:architectureCheck
   ```

2. If your change adds or modifies a **storage plugin capability**,
   update the [Storage SPI capability matrix](docs/storage-spi.md).

3. Make sure the build is green on your local:

   ```bash
   ./gradlew :eventmesh-storage-plugin:test
   ./gradlew check
   ```

## Opening a PR

- Sign off your commits (`git commit -s` adds the `Signed-off-by:` trailer).
- Use the PR template (auto-populated when you open a PR on GitHub).
- Reference the issue number with `Closes #NNNN` so the issue is
  auto-closed on merge.
- Wait for CI. The "Architecture Guard" check runs in parallel with
  the main Build job.

## Adding a new module

If you are introducing a new module (say `eventmesh-foo`):

1. Add a `testImplementation project(':eventmesh-foo')` line in
   `eventmesh-architecture-guard/build.gradle` if the new module's
   packages should be analysed.
2. Add an entry in the analysed-modules table in
   `docs/architecture-guard.md`.
3. Add an `on: pull_request: paths:` entry in
   `.github/workflows/architecture-guard.yml` so violations are
   flagged on PR.

## Code of Conduct

This project follows the [Apache Code of Conduct](https://www.apache.org/foundation/policies/conduct.html).
