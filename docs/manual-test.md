# Manual two-computer verification

1. Start Compose on the server. Configure your existing TLS proxy/private network and confirm the container is healthy. Run the same dist/Logisim-Multiplayer.jar with Java 17+ on each computer.
2. Open a vanilla 2.7.1 .circ file on A. Connect A and B using the same server address and their names; no invite is needed. Preserve any unsaved B project before joining. Check both status menus show Connected and synchronized.
3. A adds an AND gate; B adds a pin. Confirm each edit appears in the other client. At the same time, add gates in distinct locations on both clients; verify both remain. Make conflicting edits to the same component; one must reject/reconcile visibly.
4. Connect wires, remove wires, drag a selected gate with its wire repairs, edit labels/facing/input counts, duplicate, copy/paste and delete a selection. Confirm complete resulting circuits match rather than only revision counters. Selections and views should remain independent.
5. Create a subcircuit, insert an instance, edit its pins, rename it and verify the instance stays valid. Remove unused circuits. Confirm recursive/missing dependencies cannot be committed. Use Ctrl+Z to undo your own changes and Ctrl+Y/Ctrl+Shift+Z to redo; partner edits at unrelated targets must remain. Partner touches of an affected target must disable the unsafe inverse.
6. Poke an input and run ticks on one client. Simulation inputs/ticks should remain local. Confirm the original truth-table behavior; this tests local simulation independently of structural collaboration.
7. Disable the network temporarily. Structural edits should freeze and provisional structure should reconcile. Restore it; automatic reconnect should recover. Disconnect/reconnect explicitly; then choose Work Offline and verify original offline undo works.
8. Restart the Compose service with both desktops connected. Accepted work must reappear. Close/reopen a desktop, reconnect by address/name and verify the state.
9. File → Save As exports a regular .circ. Open that file in unmodified Logisim 2.7.1 and inspect gates/wires/subcircuits and simulation truth values.
10. Try unsupported custom-appearance, ROM-content, external-library and project-option edits in session mode. They must be denied/reverted with an explanation; leave to use the original offline tools.

Executed automated coverage and its limits are listed in test-results.md. Public internet/WSS deployment, physical two-computer networking, macOS desktop and the full range of built-in components have not been claimed as tested.
