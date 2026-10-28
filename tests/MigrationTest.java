package com.logisim.multiplayer;
import com.google.gson.*;
import java.net.*;
import java.nio.file.*;
/** Existing legacy files must remain intact and ambiguity must never silently choose a project. */
public class MigrationTest extends IntegrationTest {
 public static void main(String[] args)throws Exception{
  Path data=Files.createTempDirectory("logisim-migration-");String selected="1".repeat(64),other="2".repeat(64);JsonObject state=add(Model.capture(Model.blank()),"Gates/AND Gate",120,100);
  try(Store store=new Store(data)){
   for(String key:new String[]{selected,other}){JsonObject r=new JsonObject();r.addProperty("revision",0);r.add("state",key.equals(selected)?state:Model.capture(Model.blank()));r.add("receipts",new JsonObject());r.add("touched",new JsonObject());store.commit(key,r);}
  }
  try{new Server(new InetSocketAddress("127.0.0.1",0),data,null);throw new AssertionError("Ambiguous legacy projects must require selection");}catch(IllegalArgumentException expected){check(expected.getMessage().contains("SHARED_PROJECT_KEY"),"explicit migration selection required");}
  Server s=new Server(new InetSocketAddress("127.0.0.1",0),data,selected);s.start();for(int i=0;s.getPort()==0&&i<500;i++)Thread.sleep(10);Peer a=new Peer(s.getPort());
  try{UndoTest.connect(a,null);JsonObject welcome=a.next("welcome");check(welcome.get("state").equals(state),"selected legacy circuit preserved");check(!welcome.has("sessionId"),"migration needs no invite");check(Files.isDirectory(data.resolve(other)),"unselected legacy directory retained");check(Files.readString(data.resolve(selected).resolve("000000000000/commit.json")).contains(Model.hash(state)),"original committed generation retained");System.out.println("PASS: legacy migration selects a project explicitly and preserves both originals");}
  finally{a.closeBlocking();s.shutdown();}
  Path metadata=data.resolve(selected).resolve("000000000000/commit.json");JsonObject tampered=JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();tampered.add("history",new JsonObject());Files.writeString(metadata,tampered.toString());
  try(Store store=new Store(data)){var recovered=store.restore();check(!recovered.containsKey(selected)&&recovered.containsKey(other),"metadata/history corruption detected even when model and .circ hashes still match");}
 }
}
