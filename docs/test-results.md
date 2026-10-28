# Executed validation — 2026-10-27 (America/Montevideo)

## Current single-project / undo-redo update

Windows desktop: Temurin JDK 25.0.2, Python 3.11. Linux amd64 Docker builder/runtime: Temurin 17.0.20.1+1. Docker Desktop engine 29.5.3. No Linux/macOS graphical desktop or public WSS/WAN validation is claimed.

The final local command `python tools/build_collaboration.py` followed by `python tools/test.py --gui` passed:

| Suite | Executed result |
| --- | --- |
| FeasibilityProof | Original action event capture, independent replay, serialized equality and inverse transaction |
| ModelTest | Semantic delta replay, reconciliation, rejection and vanilla .circ round trip |
| SimulationTest | All four AND input combinations in original, replayed and vanilla-reopened models |
| ConnectionFailureTest | One actionable initial connection error with endpoint; local project preserved |
| IntegrationTest | **134 assertions passed**, including concurrent edits, conflict/ABA cases, wires, movement, attributes, subcircuits, idempotence, malformed messages, failure to persist, restart and corruption recovery |
| UndoTest | **127 assertions passed** for tokenless single-project connection and safe per-client undo/redo |
| MigrationTest | Explicit legacy selection, preservation of selected/unselected originals, missing-invite connection, and metadata/history corruption detection |
| DesktopTest | **17 assertions passed** using the production Project hook, network adapter and Swing EDT, including own undo/redo, out-of-order recovery and automatic reconnect with an intentionally missing first response |
| GuiTest | **14 assertions passed** in two real Swing frames: original clipboard/selection/attribute/subcircuit actions plus enabled Edit-menu Undo/Ctrl+Z and Redo/Ctrl+Y driving synchronized history |

The final Docker build compiled from source and reran all headless suites successfully on Java 17, including UndoTest and MigrationTest. `docker compose up -d --build` deployed the update and its WebSocket health check passed. The selected existing populated project remained at revision 14 with 110 model entries after migration; its directory and other legacy project directories were retained. No synthetic test edits were sent into that user project. The local service is published at 127.0.0.1:18080, as selected by the ignored local .env.

UndoTest verifies unrelated partner edits remain, duplicate undo is applied only once, repeated own attribute changes can be undone successively, redo restores own changes, a partner touch invalidates undo/redo even after ABA restoration, a new own edit clears redo, and history survives a server restart for the same reconnecting identity. Second connections supplying different initial projects cannot replace the existing shared project. Bare IP and explicit host:port parsing are tested. Inverse candidates use the same complete graph/model/serializer validation as edits.

New persisted generations include a full metadata checksum covering history, receipts and touched-key stamps in addition to model/.circ checksums. MigrationTest changes only temporary test metadata and verifies that this corruption is detected even if the model and .circ hashes still match. Legacy generations without the new checksum remain readable for migration.

## Earlier foundation checks

The official classic 2.7.1 source was compiled and launched before collaboration hooks were installed. FeasibilityProof was separately run against the unmodified reference JAR. The reference JAR was also placed first on the classpath for the simulator truth-table/export test. Two separately launched executable desktop JAR processes opened responding windows with empty stderr. Two synchronized test frames were visually inspected with matching structures and independent selections.

Earlier Compose testing created an isolated test session, committed an AND gate, restarted/rebuilt the container and verified actual model equality and revision restoration. That legacy invite-based smoke utility must not be run against the current populated user deployment; current tests use isolated temporary data directories. Earlier missing-response reconnect stalls were fixed with failed-socket cleanup and a seven-second synchronization deadline; the current suite deliberately omits the first response and requires recovery.

## Reproduce

```sh
python tools/build_collaboration.py
python tools/test.py --headless
python tools/test.py --gui   # graphical desktop required
docker compose up -d --build
docker compose exec -T collaboration java -Djava.awt.headless=true -jar /app/server.jar --health
```

No physical two-computer/WAN, real public proxy/certificate, exhaustive component, physical power-loss or storage-device failure test has been claimed. The exact historical JavaHelp/MRJAdapter dependency source/license audit remains incomplete; this is a functional tested prototype rather than a fully audited public distribution.

## Saved servers and personal history across JAR restarts

The updated desktop adds a direct last-server Connect action, Saved Servers, Add Server and Forget a Saved Server. Java Preferences persists up to eight normalized server/name profiles under the local user account, outside the JAR. Existing address/nickname preferences migrate automatically. Profiles retain their own client identity; no password/session secret or account system is added.

Executed on Windows/Temurin 25.0.2 and in the Linux/Temurin 17.0.20.1 Docker build:

- SavedServersTest writes a profile, launches a separate JVM, and verifies the exact saved address, name and identity are restored. It also checks legacy migration, deduplication, different-name identities, last-used selection, forgetting, bounded recents and corrupt local settings.
- SavedConnectionTest passed 14 assertions: two named profiles connect and edit, one desktop controller closes and a fresh one reopens the same stored profile, the production one-click menu reconnects without a dialog, and each user undoes only their own gate. The partner's gate remains; redo works after reopening.
- Existing feasibility, model, simulation, connection-failure, 134-assertion integration, 127-assertion undo, migration, 17-assertion desktop and 14-assertion GUI suites all passed again. Docker rebuilt and reran the headless suites successfully.

The runtime server code/protocol does not need a change for stable profile IDs, so the current healthy server was left running without interrupting collaborators. The updated executable JAR and source archive contain the client change. Profile-based history applies from this update forward; older random launch identities cannot be assigned retroactively. History retention and partner-touch invalidation still apply. Forgetting a profile removes its local identity, not the shared project.
