package com.logisim.multiplayer;
import com.google.gson.*;
import java.nio.file.*;
/** External Compose smoke test. Never prints the generated test invite. */
public class ComposeTest {
 public static void main(String[] args)throws Exception{
  int port=Integer.parseInt(args[1]);Path file=Path.of("build/compose-test-session.json");IntegrationTest.Peer a=new IntegrationTest.Peer(port);
  try{
   if(args[0].equals("create")){
    JsonObject state=Model.capture(Model.blank());JsonObject welcome=a.create(state);String token=Model.str(welcome,"sessionId");
    JsonObject next=IntegrationTest.add(state,"Gates/AND Gate",120,100);a.send(IntegrationTest.edit(0,state,next).toString());JsonObject commit=a.next("commit");JsonObject expected=Model.patched(state,commit.getAsJsonArray("changes"));
    JsonObject saved=new JsonObject();saved.addProperty("sessionId",token);saved.add("state",expected);saved.addProperty("revision",1);Files.writeString(file,saved.toString());System.out.println("PASS: Compose session and durable gate creation");
   }else{
    JsonObject saved=JsonParser.parseString(Files.readString(file)).getAsJsonObject();a.join(Model.str(saved,"sessionId"));JsonObject welcome=a.next("welcome");IntegrationTest.check(welcome.get("state").equals(saved.get("state")),"container restart actual model equality");IntegrationTest.check(welcome.get("revision").equals(saved.get("revision")),"container restart revision");System.out.println("PASS: Compose restart retained accepted gate and revision");
   }
  }finally{a.closeBlocking();}
 }
}
