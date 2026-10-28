package com.logisim.multiplayer;
import com.google.gson.*;
import com.cburch.logisim.file.*;
import com.cburch.logisim.circuit.*;
import com.cburch.logisim.comp.*;
import com.cburch.logisim.data.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class IntegrationTest {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static class Peer extends WebSocketClient {
  final String id=UUID.randomUUID().toString();final BlockingQueue<JsonObject> inbox=new LinkedBlockingQueue<>();
  Peer(int port)throws Exception{super(new URI("ws://127.0.0.1:"+port));check(connectBlocking(10,TimeUnit.SECONDS),"connect");}
  public void onOpen(ServerHandshake h){}public void onMessage(String s){inbox.add(JsonParser.parseString(s).getAsJsonObject());}public void onClose(int c,String s,boolean r){}public void onError(Exception e){}
  JsonObject next(String type)throws Exception{JsonObject m=inbox.poll(15,TimeUnit.SECONDS);check(m!=null,"receive "+type);check(Model.str(m,"type").equals(type),"expected "+type+" got "+m);return m;}
  void join(String token){JsonObject m=Server.message("join");m.addProperty("sessionId",token);m.addProperty("clientId",id);m.addProperty("nickname","Test");send(m.toString());}
  JsonObject create(JsonObject state)throws Exception{JsonObject m=Server.message("create");m.addProperty("clientId",id);m.addProperty("nickname","Test");m.add("state",state);send(m.toString());return next("welcome");}
 }
 static Server start(Path dir)throws Exception{Server s=new Server(new InetSocketAddress("127.0.0.1",0),dir);s.start();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(s.getPort()==0&&System.nanoTime()<end)Thread.sleep(10);check(s.getPort()>0,"server starts");return s;}
 static JsonObject add(JsonObject state,String type,int x,int y)throws Exception{
  LogisimFile f=Model.materialize(state);JsonObject d=new JsonObject();d.addProperty("circuit","main");d.addProperty("type",type);d.addProperty("x",x);d.addProperty("y",y);d.add("attrs",new JsonObject());CircuitMutation m=new CircuitMutation(f.getMainCircuit());m.add(Model.component(f,d));m.execute();return Model.capture(f);
 }
 static JsonObject edit(long revision,JsonObject before,JsonObject after){JsonObject m=Server.message("edit");m.addProperty("operationId",UUID.randomUUID().toString());m.addProperty("baseRevision",revision);m.add("changes",Model.diff(before,after));return m;}
 static JsonObject commit(Peer a,Peer b,JsonObject before)throws Exception{JsonObject ca=a.next("commit"),cb=b.next("commit");check(ca.equals(cb),"same accepted operation order");JsonObject next=Model.patched(before,ca.getAsJsonArray("changes"));check(Model.hash(next).equals(Model.str(ca,"hash")),"actual state hash matches");return next;}
 static void accepted(Peer sender,JsonObject request){sender.send(request.toString());}
 public static void main(String[] args)throws Exception{
  Path data=Files.createTempDirectory("logisim-integration-");Server s=start(data);Peer a=new Peer(s.getPort()),b=new Peer(s.getPort());String token;JsonObject state;long revision=0;
  try{
   state=Model.capture(Model.blank());JsonObject welcome=a.create(state);token=Model.str(welcome,"sessionId");b.join(token);check(b.next("welcome").get("state").equals(state),"initial model equality");
   JsonObject op=edit(revision,state,add(state,"Gates/AND Gate",120,100));accepted(a,op);state=commit(a,b,state);revision++;
   accepted(a,op);check(a.next("duplicate").get("acceptedRevision").getAsLong()==revision,"idempotent duplicate");
   JsonObject op2=edit(revision,state,add(state,"Wiring/Pin",300,100));accepted(b,op2);state=commit(a,b,state);revision++;
   JsonObject before=state.deepCopy();JsonObject oa=edit(revision,before,add(before,"Gates/OR Gate",400,200)),ob=edit(revision,before,add(before,"Gates/NOT Gate",600,200));
   accepted(a,oa);accepted(b,ob);state=commit(a,b,state);state=commit(a,b,state);revision+=2;check(state.size()==before.size()+2,"unrelated stale-base concurrent changes both retained");
   // Attribute edit and a concurrent delete of the exact same component.
   String gate=state.keySet().stream().filter(k->k.contains("AND Gate")).findFirst().orElseThrow();
   JsonObject changed=state.deepCopy();changed.getAsJsonObject(gate).getAsJsonObject("attrs").addProperty("label","shared gate");
   JsonObject deletion=state.deepCopy();deletion.remove(gate);JsonObject attrOp=edit(revision,state,changed),delOp=edit(revision,state,deletion);
   accepted(a,attrOp);state=commit(a,b,state);revision++;
   accepted(b,delOp);check(Model.str(b.next("rejected"),"reason").contains("Conflict"),"delete versus modify rejected");
   JsonObject abaSource=state.deepCopy();JsonObject staleDelete=edit(revision,state,deletion);
   accepted(a,edit(revision,state,deletion));state=commit(a,b,state);revision++;
   accepted(b,edit(revision,state,abaSource));state=commit(a,b,state);revision++;
   accepted(a,staleDelete);check(Model.str(a.next("rejected"),"reason").contains("Conflict"),"ABA delete/recreate protected by target revision");
   accepted(a,edit(revision,state,deletion));state=commit(a,b,state);revision++;
   accepted(b,delOp);b.next("rejected");check(!state.has(gate),"double deletion cannot remove unrelated work");
   // Wire creation, deletion, movement; one action can change several wires and components.
   LogisimFile f=Model.materialize(state);CircuitMutation mutation=new CircuitMutation(f.getMainCircuit());mutation.add(Wire.create(Location.create(100,300),Location.create(300,300)));mutation.add(Wire.create(Location.create(300,300),Location.create(300,400)));mutation.execute();
   accepted(b,edit(revision,state,Model.capture(f)));state=commit(a,b,state);revision++;
   f=Model.materialize(state);Component pin=f.getMainCircuit().getNonWires().stream().filter(c->c.getFactory().getName().equals("Pin")).findFirst().orElseThrow();mutation=new CircuitMutation(f.getMainCircuit());mutation.remove(pin);mutation.add(pin.getFactory().createComponent(Location.create(320,100),(AttributeSet)pin.getAttributeSet().clone()));mutation.remove(f.getMainCircuit().getWires().iterator().next());mutation.execute();
   accepted(a,edit(revision,state,Model.capture(f)));state=commit(a,b,state);revision++;
   // Subcircuits, references and circuit rename are represented as one atomic graph update.
   f=Model.materialize(state);Circuit sub=new Circuit("unit");f.addCircuit(sub);mutation=new CircuitMutation(f.getMainCircuit());mutation.add(sub.getSubcircuitFactory().createComponent(Location.create(500,400),sub.getSubcircuitFactory().createAttributeSet()));mutation.execute();
   accepted(a,edit(revision,state,Model.capture(f)));state=commit(a,b,state);revision++;
   f=Model.materialize(state);f.getCircuit("unit").getStaticAttributes().setValue(CircuitAttributes.NAME_ATTR,"renamed");accepted(b,edit(revision,state,Model.capture(f)));state=commit(a,b,state);revision++;
   check(Model.materialize(state).getCircuit("renamed")!=null,"rename and dependent instances converge");
   JsonObject future=edit(revision+10,state,add(state,"Gates/XOR Gate",700,500));accepted(a,future);a.next("rejected");
   JsonObject bad=edit(revision,state,add(state,"Gates/XOR Gate",700,500));bad.getAsJsonArray("changes").get(0).getAsJsonObject().getAsJsonObject("after").addProperty("type","java/exec");accepted(b,bad);b.next("rejected");
   a.send("{malformed");a.next("rejected");
   a.send("[".repeat(40)+"0"+"]".repeat(40));a.next("rejected");
   JsonObject reused=op.deepCopy();reused.addProperty("baseRevision",revision);accepted(a,reused);check(Model.str(a.next("rejected"),"reason").contains("reused"),"operation ID cannot be reused for different content");
   Peer third=new Peer(s.getPort());third.join(token);third.next("rejected");third.closeBlocking();
   // Vanilla compatibility uses the original reader and a .circ export containing no multiplayer extensions.
   byte[] exported=ProjectIO.write(Model.materialize(state));Files.write(data.resolve("export.circ"),exported);LogisimFile vanilla=LogisimFile.load(new java.io.ByteArrayInputStream(exported),new Model.StrictLoader());check(Model.capture(vanilla).equals(state),"vanilla 2.7.1 exported model equality");
   // A failed durability step must never advance authority or acknowledge the candidate.
   Path blockedGeneration=data.resolve(Server.key(token)).resolve(String.format("%012d",revision+1));Files.createDirectories(blockedGeneration);
   accepted(a,edit(revision,state,add(state,"Gates/XNOR Gate",900,600)));a.next("rejected");a.send(Server.message("sync").toString());JsonObject unchanged=a.next("snapshot");check(unchanged.get("state").equals(state)&&unchanged.get("revision").getAsLong()==revision,"failed persistence leaves authoritative model unchanged");Files.delete(blockedGeneration);
   // Crash after send/before acknowledgement, then idempotent retry following server restart.
   JsonObject uncertain=edit(revision,state,add(state,"Gates/NAND Gate",800,500));accepted(a,uncertain);JsonObject committed=b.next("commit");state=Model.patched(state,committed.getAsJsonArray("changes"));revision++;
   String client=a.id;a.closeBlocking();b.closeBlocking();s.shutdown();
   // Interrupted generation must never become authoritative.
   Path session=data.resolve(Server.key(token));Path interrupted=session.resolve("pending-interrupted");Files.createDirectories(interrupted);Files.writeString(interrupted.resolve("commit.json"),"{broken");
   s=start(data);a=new Peer(s.getPort());b=new Peer(s.getPort());
   JsonObject rejoin=Server.message("join");rejoin.addProperty("clientId",client);rejoin.addProperty("nickname","Test");rejoin.addProperty("sessionId",token);a.send(rejoin.toString());JsonObject recovered=a.next("welcome");check(recovered.get("state").equals(state)&&recovered.get("revision").getAsLong()==revision,"restart restores full accepted model");
   accepted(a,uncertain);a.next("duplicate");b.join(token);check(b.next("welcome").get("state").equals(state),"new client after revisions and restart");
   // Corrupt newest generation: retain a verified older generation, flag read-only, preserve damaged bytes.
   a.closeBlocking();b.closeBlocking();s.shutdown();Path latest=session.resolve(String.format("%012d",revision));Files.writeString(latest.resolve("commit.json"),"corrupt");
   s=start(data);a=new Peer(s.getPort());a.join(token);JsonObject fallback=a.next("welcome");check(fallback.get("readOnly").getAsBoolean(),"corrupt latest generation recovered read-only");check(fallback.get("revision").getAsLong()==revision-1,"fallback previous verified generation");check(Files.readString(latest.resolve("commit.json")).equals("corrupt"),"corrupt generation preserved");
   System.out.println("PASS: "+checks+" integration assertions; artifacts: "+data);
  }finally{a.close();b.close();s.shutdown();}
 }
}
