package com.logisim.multiplayer;
import com.google.gson.*;
import com.cburch.logisim.file.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Immutable, fsynced commit generations. GPL-2.0-or-later. */
public final class Store implements AutoCloseable {
 final Path root; final FileChannel lockChannel; final FileLock lock;
 public Store(Path root)throws IOException{
  this.root=root.toAbsolutePath();Files.createDirectories(this.root);
  lockChannel=FileChannel.open(this.root.resolve("server.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
  lock=lockChannel.tryLock();if(lock==null)throw new IOException("Data directory is already in use");
 }
 static void forceFile(Path path,byte[] bytes)throws IOException{try(FileChannel c=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())c.write(b);c.force(true);}}
 static void forceDir(Path dir)throws IOException{
  if(System.getProperty("os.name").startsWith("Windows"))return; // Java cannot fsync directory handles on Windows.
  try(FileChannel c=FileChannel.open(dir,StandardOpenOption.READ)){c.force(true);}
  catch(UnsupportedOperationException e){throw new IOException("Filesystem does not support required directory fsync",e);}
 }
 public synchronized void commit(String key,JsonObject record)throws IOException{
  if(!key.matches("[0-9a-f]{64}"))throw new IOException("Invalid storage key");
  Path session=root.resolve(key);Files.createDirectories(session);
  long rev=record.get("revision").getAsLong();Path destination=session.resolve(String.format("%012d",rev));
  if(Files.exists(destination))throw new IOException("Commit generation already exists");
  Path tmp=Files.createTempDirectory(session,"pending-");
  byte[] circ=ProjectIO.write(Model.materialize(record.getAsJsonObject("state")));
  record.addProperty("circHash",sha(circ));record.addProperty("hash",Model.hash(record.get("state")));record.addProperty("recordHash",integrity(record));
  forceFile(tmp.resolve("project.circ"),circ);forceFile(tmp.resolve("commit.json"),record.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));forceDir(tmp);
  Files.move(tmp,destination,StandardCopyOption.ATOMIC_MOVE);forceDir(session);
  // Keep 32 recent recovery states and one checkpoint every 100 edits.
  try(var paths=Files.list(session)){
   for(Path p:paths.filter(p->p.getFileName().toString().matches("[0-9]{12}")).toList()){
    long old=Long.parseLong(p.getFileName().toString());if(old<rev-31 && old%100!=0){Files.deleteIfExists(p.resolve("commit.json"));Files.deleteIfExists(p.resolve("project.circ"));Files.delete(p);}
   }
  }catch(IOException e){System.err.println("Recovery snapshot cleanup failed; committed state retained");}
 }
 public Map<String,JsonObject> restore()throws IOException{
  Map<String,JsonObject> result=new HashMap<>();
  try(var dirs=Files.list(root)) {for(Path dir:dirs.filter(Files::isDirectory).toList()){
   String key=dir.getFileName().toString();if(!key.matches("[0-9a-f]{64}"))continue;
   List<Path> generations;try(var entries=Files.list(dir)){generations=entries.filter(p->p.getFileName().toString().matches("[0-9]{12}")).sorted(Comparator.reverseOrder()).toList();}
   boolean damaged=false;
   for(Path generation:generations){
    try{
     Path meta=generation.resolve("commit.json"),circ=generation.resolve("project.circ");
     if(Files.size(meta)>24000000 || Files.size(circ)>4000000)throw new IOException("Oversized stored state");
     JsonObject record=JsonParser.parseString(Files.readString(meta)).getAsJsonObject();
     if(record.has("recordHash")&&!integrity(record).equals(Model.str(record,"recordHash")))throw new IOException("Metadata checksum mismatch");
     if(record.get("revision").getAsLong()!=Long.parseLong(generation.getFileName().toString()))throw new IOException("Revision mismatch");
     if(!Model.hash(record.get("state")).equals(Model.str(record,"hash")) || !sha(Files.readAllBytes(circ)).equals(Model.str(record,"circHash")))throw new IOException("Checksum mismatch");
     if(!Model.capture(Model.materialize(record.getAsJsonObject("state"))).equals(record.get("state")))throw new IOException("Model mismatch");
     record.addProperty("readOnly",damaged);result.put(key,record);break;
    }catch(Exception e){damaged=true;System.err.println("A stored generation failed verification; attempting recovery (session details suppressed)");}
   }
   if(!generations.isEmpty()&&!result.containsKey(key))System.err.println("A session could not be restored; stored files retained");
  }}return result;
 }
 static String integrity(JsonObject record){JsonObject copy=record.deepCopy();copy.remove("recordHash");copy.remove("readOnly");return Model.hash(copy);}
 static String sha(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
 public void close()throws IOException{lock.release();lockChannel.close();}
}
