# Upstream provenance and modifications

Source: Carl Burch's official Subversion repository, `https://svn.code.sf.net/p/circuit/code/tags/2.7.1/`.

The repository advertised HEAD revision 256 at retrieval. WebDAV PROPFIND on the tag reports its last-changed revision as **247**, with creation date `2011-03-22T12:59:06.810453Z`, checked-in path `/p/circuit/code/!svn/ver/247/tags/2.7.1`. The immutable export URL used by the acquisition utility is `https://svn.code.sf.net/p/circuit/code/!svn/bc/247/tags/2.7.1/`. `Main.java` declares `LogisimVersion.get(2, 7, 1)` and copyright year 2011. This is classic Logisim, not Evolution or another fork.

Downloaded trees: src, libs, scripts, notes, doc. Original per-file SHA-256 digests are in upstream-sha256.json. The fork modifies only a few original Java files:

- Project.java: action/undo/file-change guards and a history reset method.
- LogisimMenuBar.java: Collaboration menu insertion.
- MenuEdit.java: server-authorized undo/redo availability, Edit-menu entries and Ctrl+Z/Ctrl+Y/Ctrl+Shift+Z shortcuts.
- SimulationTreeNode.java and ComponentSelector.java: raw Enumeration return signatures for legacy TreeNode implementations when compiling with Java 17.

Original versions of modified files are retained under client/upstream-original. All collaboration implementation is under client/collaboration, shared/src and server/src. Model serialization uses a small package bridge; no simulator changes were made.

Original build: the upstream Python build script targets Java 5 and shells out to javac/jar. We inspected it and implemented a Python 3 argument-list build that works with modern JDKs and spaces in Windows paths. tools/build.py reconstructs the unmodified source from retained originals and compiles with `--release 8`, then packages an original reference JAR. A leading UTF-8 BOM in CurveUtil.java is removed from the temporary compiler copy only. Source bytes are preserved.

The unmodified build was compiled on Temurin 25.0.2 and launched a responding Logisim window before hooks were installed. FeasibilityProof was separately compiled against that unmodified reference JAR and passed event capture, independent AND-gate replay, serialized byte equality and inverse-transaction checks. Production builds target Java 17 and include resources, generated original help, dependencies, license texts and corresponding Java source.

The official release listing is https://sourceforge.net/projects/circuit/files/2.7.x/2.7.1/. Original licensing is GPLv2 or later. The original notices and license are preserved, with separate notices for third-party libraries and icons in THIRD_PARTY.md.
