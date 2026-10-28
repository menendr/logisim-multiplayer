# Protocol v1 — single shared project with safe undo/redo

WebSocket JSON text, `{v:1,type:...}`. All state is authoritative on one server. No session ID or invite is required. Network reachability grants access; limit exposure to your intended collaborators.

## Connect

`connect`: `{v,type,clientId,nickname,state?}`. clientId is a canonical lowercase UUID; nickname is 1–40 non-control characters. The first connection initializes an absent project from the supplied supported state, or a blank project if none is provided. An existing project always wins: later local seeds are ignored. `cannotInitialize:true` refuses initialization from an unsupported local project while still allowing it to join an existing one.

`welcome` / `snapshot`: `{v,type,revision,state,hash,readOnly,history}`. No session ID is returned to modern clients. `sync` requests a snapshot. `health` receives `healthy` for container checks. Two collaborators are supported. Legacy create/join aliases map to this same project; they cannot select or replace another room. Old session IDs are no longer authentication credentials.

`SHARED_PROJECT_KEY` can select a verified legacy directory during migration. Without it, a fresh directory uses a fixed shared-project storage key; a single legacy project is reused automatically; several ambiguous legacy projects cause startup to fail with a selection instruction. All old directories remain untouched.

## Transactions

`edit`: `{v,type,operationId,baseRevision,changes}`. Each change is `{key,before,after}` with null for absence. Every affected key must match its before-value and must not have been touched since baseRevision. Stale bases are allowed for unrelated unchanged targets. Future/negative revisions and duplicate keys are rejected.

State entries: `main` is the main circuit name; `c/<base64url(UTF-8 name)>` contains `{name,attrs}`; component keys append `/<factory>/<x>,<y>` and contain `{circuit,type,x,y,attrs}`; wire keys append `/wire/<x>,<y>/<x2>,<y2>` and contain axis-aligned endpoints. Factories use `libraryName/factoryName` or `@subcircuitName`. Attributes use original Logisim standard strings, including all saved defaults.

`undo` / `redo`: `{v,type,operationId,baseRevision}`. Clients do not supply an arbitrary inverse. The server selects the most recent eligible operation from that client's history and derives its inverse or forward changes. Any other client's touch of an affected key permanently invalidates an entry, even after an ABA restoration. Own successive inverses can traverse the same object if exact before-values match. Full candidate model/graph/serializer validation applies to every inverse. A new edit clears that client's redo stack. Global history retains at most 100 entries / 4 MB; oldest entries are removed first.

All accepted changes receive a new revision, are persisted before acknowledgement and broadcast as `commit`: `{v,type,clientId,operationId,revision,kind,changes,hash,history}`. The history field maps client IDs to `{undo,redo,undoReason,redoReason}` availability, not arbitrary replayable requests. It is identical in broadcasts, while each desktop reads its own flags. Remote commits never enter the local single-user action/undo stack.

Receipts for `(clientId,operationId)` persist the original request hash and accepted revision. Exact retries return `duplicate` with `acceptedRevision`; altered reuse is rejected. Clients request a snapshot after a duplicate. `rejected` includes `{operationId?,reason}` and does not advance authority. Rejected/uncertain optimistic edits reconcile to accepted state. Revision gaps, order errors or mismatched SHA-256 hashes trigger snapshot recovery. Hash input is recursively key-sorted compact JSON.

Persistence stores `.circ`, state, revision, receipts, touched-key revisions, history and checksums (including full metadata integrity for new commits) in immutable atomic generations. Recovery and read-only corruption fallback remain unchanged. History survives server restart for the same reconnecting client UUID; the same saved server/name profile retains its identity after relaunch.

Limits: 2 MB frames/messages, JSON nesting 32, 32 connections, 40 messages/sec per connection, 64 circuits, 20,000 state entries, 1.5 MB UTF-8 state, 4,000 changes per transaction, 10,000 receipt entries, 100,000 historical keys. Each wire is capped at 20,000 coordinate units and total wire length at 500,000. Slow consumers are disconnected rather than buffering unbounded output. There is no arbitrary Java-object or network XML deserialization.
