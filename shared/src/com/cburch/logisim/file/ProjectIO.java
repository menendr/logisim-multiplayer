package com.cburch.logisim.file;
import java.io.*;
/** GPL-2.0-or-later. Access to the original package-private serializer. */
public final class ProjectIO {
 public static byte[] write(LogisimFile f) throws IOException {
  ByteArrayOutputStream out=new ByteArrayOutputStream(); f.write(out,f.getLoader()); return out.toByteArray();
 }
}
