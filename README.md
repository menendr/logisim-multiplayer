# Logisim Multiplayer

Classic Logisim 2.7.1 with its original Swing editor, local simulation, and a **single persistent shared project per server**. Two users connect with only the server address and their name. No session ID or account is needed.

## Run

Requires Java 17+. Windows desktop tested with Temurin 25.0.2; Linux headless server tested with Temurin 17.0.20.1.

```sh
java -jar dist/Logisim-Multiplayer.jar
```

On a fresh clone on your Linux home server (Docker Engine and Compose must be installed):

```sh
cp .env.example .env
mkdir -p data
sudo chown 10001:10001 data
docker compose up -d --build
docker compose ps
```

The default address uses port 8080 and is accessible only from the server itself. For direct private-LAN access, set `HOST_BIND=0.0.0.0` in `.env` before starting, then connect to `HOME_SERVER_IP:8080`. For remote access through your HTTPS proxy, use its `wss://` address instead. See [deployment](docs/deployment.md). Server storage and local settings are not part of the Git repository; a fresh clone starts a new shared project.

Choose **Collaboration → Connect to Server**, enter the address and your name. Your partner does the same and automatically receives the same project. A bare IP uses WebSocket port 8080; `IP:port`, `ws://...` and `wss://...` are also accepted. On this Windows checkout, the local `.env` uses port 18080: enter **`127.0.0.1:18080`**. Verify the host port with `docker compose ps`.

When the server has no project yet, the first connection initializes it from that client's supported circuit. Later connections always load the server's project and cannot overwrite it with their local file. Save local work before connecting. The existing populated circuit in this checkout has been selected for migration; other old session directories remain preserved on disk.

The host binding defaults to loopback. For direct access on a trusted LAN, set `HOST_BIND=0.0.0.0` in `.env`, run `docker compose up -d`, and have your partner enter the host computer's LAN IP and published port. There is no invite-based access control: anyone who can reach the service can join an available slot and edit. Use a private network or your existing HTTPS reverse proxy for remote access. No firewall or router settings are changed automatically.

## Saved connections

Add a server once using **Connect to Server** and enter your name. The app saves both on this computer, outside the JAR. Existing remembered settings are imported automatically.

Next time, choose **Connect to [server] (name)** directly from the Collaboration menu, or pick an entry under **Saved Servers**. Use **Add Server** for another connection and **Forget a Saved Server** to remove one locally. Up to eight recent server/name profiles are remembered. Nothing connects automatically on launch.

Reopening or replacing the JAR on the same computer keeps these profiles. Each profile has its own undo identity; use the same saved server/name to resume its retained history. Different names remain independent.

## Undo and redo

- **Ctrl+Z**: undo your last compatible edit.
- **Ctrl+Y** or **Ctrl+Shift+Z**: redo it.
- The Edit menu shows when each is available.

Undo/redo are server-authorized transactions, durably saved and broadcast to both clients. Unrelated partner edits remain. If your partner has touched an affected object, the corresponding history entry is invalidated, even if the object later returns to its previous appearance. Dependencies are checked before an inverse can commit. A new edit clears your redo stack. History is bounded to 100 entries / 4 MB across the server; very large or old entries may fall out of history. The saved server/name profile retains its personal history identity across desktop and server restarts. Reconnect with the same saved profile to resume eligible undo/redo history. Different names have independent profiles; forgetting a profile loses its previous undo identity. Two windows cannot use the same profile at the same time. Edits made before profiles were introduced cannot be retroactively assigned to a saved profile. Original offline undo is retained; redo is provided for collaborative editing.

## Editing and recovery

Gates, pins, wires, movement, attributes, original selection copy/paste/duplicate/delete, multiple subcircuits, circuit creation/rename/removal and main-circuit changes are implemented. Both users can submit unrelated edits concurrently; each desktop permits one unacknowledged transaction. The server checks before-values, per-target revisions and full Logisim model validity before durable acceptance. Conflicting edits are rejected and reconciled.

**File → Save As** exports an ordinary `.circ`. Simulation ticks, input values, viewports, selections and tool choices remain local. Wait for **Connected and synchronized** before exporting an accepted copy. Network loss freezes shared editing and reconnects automatically. **Disconnect** keeps editing frozen; **Work Offline** exits collaboration and clears stale local history.

Unsupported in collaboration: external libraries, custom circuit appearances, ROM contents editor, project options/toolbar modifications, circuit ordering and unrecognized actions. These remain available offline. Names use 1–64 ASCII letters/digits/spaces/underscores/hyphens, starting with a letter. Identical factories overlapping at the same location are rejected. Limits include two collaborators, 64 circuits, 20,000 model entries, 4,000 changed entries per transaction, 1.5 MB state and 2 MB messages. Memory address width is capped at 16 bits. See [known limitations](docs/known-limitations.md).

## Build and test

JDK 17+ and Python 3.9+; dependencies are included, so normal builds need no Maven or network access.

```sh
python tools/build_collaboration.py
python tools/test.py --headless
python tools/test.py --gui  # graphical desktop required
```

Outputs: `dist/Logisim-Multiplayer.jar`, `dist/Logisim-Multiplayer-Server.jar`. The Docker build compiles from source and runs the headless suites. JARs include application Java source, original help and license texts.

[Architecture](docs/architecture.md) · [Protocol](docs/protocol.md) · [Deployment and migration](docs/deployment.md) · [Test evidence](docs/test-results.md) · [Upstream provenance](docs/upstream.md)

Original source is GPL-2.0-or-later. The historical JavaHelp/MRJAdapter dependency source/license matching audit remains incomplete; see [third-party notices](THIRD_PARTY.md) before redistribution. Public WSS/WAN and physical two-computer validation have not been claimed.
