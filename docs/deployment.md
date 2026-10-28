# Deployment

## Linux home server

Install Docker Engine with Compose v2 using your distribution's supported instructions. This service requires no X server, display, desktop environment, database or VNC.

```sh
mkdir -p data
sudo chown 10001:10001 data
cp .env.example .env
docker compose up -d --build
docker compose ps
docker compose logs --tail=50
```

The bind-mounted data directory must be writable by container UID/GID 10001. The image runs without root, with a read-only root filesystem, bounded temporary storage, dropped capabilities, a 256 MiB JVM heap and a 384 MiB container memory limit. Port 8080 inside the container is the only service port. The container health check opens a WebSocket and exchanges a versioned health message.

`HOST_PORT` defaults to 8080. If this port is occupied or reserved by Windows, set another value in `.env`, such as `HOST_PORT=18080`, then run `docker compose up -d`. The host binding remains 127.0.0.1. The local verification used port 18080. No firewall or router settings are changed.

## TLS reverse proxy

Use an existing HTTPS reverse proxy and its existing certificate management. Do not put certificates in the JAR. For a host-based nginx proxy, an example location inside your existing TLS server block is:

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 90s;
    proxy_send_timeout 90s;
    client_max_body_size 2m;
}
```

Connect desktops to `wss://your-host/`. Java validates the server certificate normally. If your proxy runs in a container, attach it to an appropriate shared Docker network and proxy to `collaboration:8080`; 127.0.0.1 inside that proxy container refers to the proxy itself. A private VPN or SSH tunnel is another option. A local SSH tunnel can forward a private host port and use `ws://localhost:18080` over that tunnel.

The server now hosts one shared project without invite tokens or accounts. Anyone who can reach it can take an available collaborator slot and edit. Keep it on your private network or behind your existing HTTPS reverse proxy/access restrictions. For direct LAN connections, set HOST_BIND=0.0.0.0 in .env and use the host LAN address plus HOST_PORT. The default remains loopback. No firewall/port forwarding is configured automatically. Public WSS/certificate deployment has not been tested here.

## Persistence and backups

The selected project directory contains immutable revision directories with `project.circ` and `commit.json`. Metadata holds authoritative model state, revision, operation receipts, last-touched revisions and integrity hashes. The shared project needs no token. Accepted commits are written/fsynced and atomically published before broadcasts. Last 32 generations plus every 100th generation are retained automatically. A server restart ignores incomplete `pending-*` directories. Keep the bind mount when updating:

```sh
docker compose up -d --build
```

For an external backup, stop the service briefly, copy the entire `data` directory to separate storage, then start it. Never edit a live generation or copy only project.circ while expecting to retain session revision/idempotency history. Do not run two servers against the same directory; the process lock prevents that.

A damaged latest generation causes a fallback to the latest verified generation and read-only recovery. Logs report the problem without session secrets. Preserve the entire directory for investigation. Export the recovered `.circ`, review it against your backups and create a new session from the reviewed project. This avoids silently overwriting potentially accepted newer work. If every generation is damaged, the files remain on disk and the session is unavailable.

## Direct Java server

For local development, the executable server defaults to loopback:

```sh
java -Djava.awt.headless=true -jar dist/Logisim-Multiplayer-Server.jar
```

Environment variables: `PORT` (8080), `BIND_ADDRESS` (127.0.0.1 outside Docker), `DATA_DIR` (data). Docker sets BIND_ADDRESS=0.0.0.0 inside its network namespace; Compose still binds the host loopback address. A supported Java runtime with standard logical fonts is required. All normal application messages are JSON text; binary frames are rejected.

## Migration from the previous multi-session prototype

Existing stored projects are never replaced by a connecting client's local file. If there is exactly one legacy project, it is reused automatically. If several exist, set SHARED_PROJECT_KEY in .env to the 64-character directory name under data that contains the project you want, then rebuild/restart. Other directories stay intact as archives. This checkout selects the existing populated circuit; its legacy directory and revisions are preserved in place. Keep the local .env when updating. The server runs only the selected project regardless of any legacy create/join messages.

Undo/redo history is persisted with new commits. A reconnecting desktop keeps its client identity across a network/server interruption; the saved server/name profile preserves that identity across desktop restarts. Different saved names have separate identities; forgetting a profile loses its history identity. Edits accepted before this update have no reconstructed undo history. Export a reviewed copy if you want to start a different shared project; stop the server and point DATA_DIR at a fresh directory rather than submitting a replacement snapshot over the network.
