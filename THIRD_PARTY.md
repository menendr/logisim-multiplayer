# Third-party notices

Original Logisim 2.7.1 is Copyright Carl Burch and contributors, GPL version 2 or (at your option) later. Original notices are preserved in client/src and the original GPLv2 text is LICENSE. Multiplayer additions are GPL-2.0-or-later. Modified original files identify the change date. Executable JARs include the complete application Java source under source/. Distribute corresponding source and build tools with binaries.

The combined build uses the GPLv3 option allowed by the original GPL-2.0-or-later grant, accommodating Apache-2.0 and LGPLv3 dependencies; licenses/GPL-3.0.txt is included. This does not change the original source notices. Independent libraries and resources retain their own terms.

## Original bundled dependencies

The original JARs are preserved byte-for-byte from the official 2.7.1 tag in client/libs. Exact checksums and archive inventory are in docs/dependency-sha256.json and docs/dependencies.md.

- ColorPicker, Jeremy Wood: Modified BSD. License text from its 1.0.0 source distribution is licenses/colorpicker-License.html; source archive in third_party/. Source: https://deb.debian.org/debian/pool/main/c/colorpicker/ .
- JFontChooser, Dr Christos Bohoris (2009): LGPLv3 as stated in its source headers. Source archive in third_party/; LGPLv3/GPLv3 texts in licenses/. Source: https://deb.debian.org/debian/pool/main/f/fontchooser/ . The unchanged library can be replaced/relinked using the included build tools; reverse engineering for debugging library modifications is not restricted.
- JavaHelp, Sun Microsystems: original binary is 2.0_03, as its manifest records. The current upstream CDDL/GPLv2-with-Classpath license is included as licenses/JavaHelp-LICENSE.txt, reference https://github.com/javaee/javahelp . This reference is not proof of the exact historical binary's licensing; historical source/license matching remains an outstanding release audit item.
- MRJAdapter, Steve Roy: unchanged original binary from the official Logisim tag. The archive contains no separate license text. Exact historical source/license matching remains an outstanding release audit item; no license is invented for it here.
- GIFEncoder and TableSorter: original source notices remain in their respective client/src files.
- Farm-Fresh Web Icons, FatCow: original simulation control icons are CC BY 3.0, separately from the GPL. Attribution and the original notice are preserved in the bundled original help, doc/en/html/guide/about/index.html. Original source: http://www.fatcow.com/free-icons ; license: https://creativecommons.org/licenses/by/3.0/ .

The legacy JavaHelp/MRJAdapter source/license matching audit is incomplete. This local prototype is not represented as a fully cleared redistribution release. Preserve these notices and the original dependency archives; resolve the historical dependency audit before publishing a distribution.

## Added dependencies

- Gson 2.11.0, Google, Apache License 2.0: https://github.com/google/gson . Text: licenses/Apache-2.0.txt.
- Java-WebSocket 1.5.7, Nathan Rajlich and contributors, MIT: https://github.com/TooTallNate/Java-WebSocket . Text: licenses/Java-WebSocket-MIT.txt.
- SLF4J API and Simple 2.0.16, QOS.ch, MIT: https://www.slf4j.org/license.html . Text: licenses/SLF4J-MIT.txt.

Added binaries come from Maven Central through tools/fetch_dependencies.py with fixed coordinates. Source JARs are available from those same coordinates with the -sources.jar classifier. License texts and dependency checksums are included. Network input is never deserialized as arbitrary Java objects.
