package com.logisim.multiplayer;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
/** Server-authorized history, concurrency, ABA, idempotence and tokenless shared connection. */
public class UndoTest extends IntegrationTest {
 static JsonObject command(String kind,long rev){JsonObject m=Server.message(kind);m.addProperty("operationId",UUID.randomUUID().toString());m.addProperty("baseRevision",rev);return m;}
 static void connect(Peer p,JsonObject seed){JsonObject m=Server.message("connect");m.addProperty("clientId",p.id);m.addProperty("nickname","Test");if(seed!=null)m.add("state",seed);p.send(m.toString());}
 static JsonObject label(JsonObject state,String key,String value){JsonObject n=state.deepCopy();n.getAsJsonObject(key).getAsJsonObject("attrs").addProperty("label",value);return n;}
 public static void main(String[] args)throws Exception{
  Path data=Files.createTempDirectory("logisim-undo-");Server s=start(data);Peer a=new Peer(s.getPort()),b=new Peer(s.getPort());long rev=0;JsonObject state=Model.capture(Model.blank());
  try{
   connect(a,state);JsonObject welcome=a.next("welcome");check(!welcome.has("sessionId"),"tokenless welcome");
   connect(b,add(state,"Gates/XOR Gate",900,900));check(b.next("welcome").get("state").equals(state),"second connect cannot replace shared state");check(s.rooms.size()==1,"one server project");
   accepted(a,edit(rev,state,add(state,"Gates/AND Gate",120,100)));state=commit(a,b,state);rev++;
   String gate=state.keySet().stream().filter(k->k.contains("AND Gate")).findFirst().orElseThrow();
   accepted(b,edit(rev,state,add(state,"Wiring/Pin",300,100)));state=commit(a,b,state);rev++;
   JsonObject undo=command("undo",rev);accepted(a,undo);state=commit(a,b,state);rev++;check(!state.has(gate)&&state.keySet().stream().anyMatch(k->k.contains("Wiring/Pin")),"undo preserves unrelated partner pin");
   accepted(a,undo);check(a.next("duplicate").get("acceptedRevision").getAsLong()==rev,"undo retry applied once");
   accepted(a,command("redo",rev));state=commit(a,b,state);rev++;check(state.has(gate),"redo restores gate");
   accepted(a,edit(rev,state,label(state,gate,"first")));state=commit(a,b,state);rev++;
   accepted(a,edit(rev,state,label(state,gate,"second")));state=commit(a,b,state);rev++;
   accepted(a,command("undo",rev));state=commit(a,b,state);rev++;check(Model.str(state.getAsJsonObject(gate).getAsJsonObject("attrs"),"label").equals("first"),"undo most recent own attribute edit");
   accepted(a,command("undo",rev));state=commit(a,b,state);rev++;check(Model.str(state.getAsJsonObject(gate).getAsJsonObject("attrs"),"label").isEmpty(),"sequential own undo on same object");
   accepted(a,command("redo",rev));state=commit(a,b,state);rev++;
   accepted(b,edit(rev,state,label(state,gate,"partner")));state=commit(a,b,state);rev++;
   accepted(a,command("redo",rev));check(Model.str(a.next("rejected"),"reason").contains("partner"),"partner touch invalidates redo");
   accepted(b,command("undo",rev));state=commit(a,b,state);rev++;
   accepted(a,command("redo",rev));a.next("rejected");check(Model.str(state.getAsJsonObject(gate).getAsJsonObject("attrs"),"label").equals("first"),"ABA restoration does not revive invalidated history");
   accepted(a,edit(rev,state,add(state,"Gates/OR Gate",500,100)));state=commit(a,b,state);rev++;
   accepted(a,command("undo",rev));state=commit(a,b,state);rev++;
   accepted(a,edit(rev,state,add(state,"Gates/NAND Gate",700,100)));state=commit(a,b,state);rev++;
   accepted(a,command("redo",rev));check(Model.str(a.next("rejected"),"reason").contains("Nothing"),"new edit clears redo");
   String client=a.id;a.closeBlocking();b.closeBlocking();s.shutdown();s=start(data);a=new Peer(s.getPort());b=new Peer(s.getPort());
   JsonObject rejoin=Server.message("connect");rejoin.addProperty("clientId",client);rejoin.addProperty("nickname","Test");a.send(rejoin.toString());check(a.next("welcome").get("state").equals(state),"shared model persists without invite");connect(b,null);b.next("welcome");
   accepted(a,command("undo",rev));state=commit(a,b,state);rev++;check(state.keySet().stream().noneMatch(k->k.contains("NAND Gate")),"history persists across restart for reconnecting client");
   check(Collaboration.serverUri("192.168.1.10").toString().equals("ws://192.168.1.10:8080"),"bare IP accepted");check(Collaboration.serverUri("127.0.0.1:18080").getPort()==18080,"explicit host port accepted");
   System.out.println("PASS: "+checks+" assertions: tokenless single project and safe per-client undo/redo");
  }finally{a.close();b.close();s.shutdown();}
 }
}
