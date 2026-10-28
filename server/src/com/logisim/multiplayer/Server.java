package com.logisim.multiplayer;
import com.google.gson.*;
import org.java_websocket.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.handshake.*;
import org.java_websocket.drafts.Draft_6455;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** One serialized authority; all mutations are validated by classic Logisim. GPL-2.0-or-later. */
public class Server extends WebSocketServer {
 public static final int MAX_MESSAGE=2000000;
 static final String SHARED_KEY=Store.sha("logisim-multiplayer-shared-project-v1".getBytes(StandardCharsets.UTF_8));
 final String sharedKey;
 final Store store; final Map<String,Room> rooms=new HashMap<>();final Map<WebSocket,Peer> peers=new HashMap<>();
 final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"connection-expiry");t.setDaemon(true);return t;});
 static class Peer{Room room;String client;long window=System.currentTimeMillis();int messages;}
 static class Room{String key;JsonObject record;Set<WebSocket> sockets=new HashSet<>();Room(String k,JsonObject r){key=k;record=r;}}
 public Server(InetSocketAddress address,Path data)throws Exception{this(address,data,System.getenv("SHARED_PROJECT_KEY"));}
 public Server(InetSocketAddress address,Path data,String configured)throws Exception{
  super(address,2,Collections.singletonList(new Draft_6455(Collections.emptyList(),MAX_MESSAGE)));
  store=new Store(data);store.restore().forEach((k,r)->rooms.put(k,new Room(k,r)));
  if(configured!=null&&!configured.isBlank()){if(!configured.matches("[0-9a-f]{64}")||!rooms.containsKey(configured)){store.close();throw new IllegalArgumentException("SHARED_PROJECT_KEY must identify a verified existing project");}sharedKey=configured;}
  else if(rooms.containsKey(SHARED_KEY)||rooms.isEmpty())sharedKey=SHARED_KEY;
  else if(rooms.size()==1)sharedKey=rooms.keySet().iterator().next();
  else {store.close();throw new IllegalArgumentException("Several legacy projects found; set SHARED_PROJECT_KEY to choose one. Stored projects are preserved.");}
  setConnectionLostTimeout(20);setReuseAddr(true);setMaxPendingConnections(32);
 }
 public static JsonObject message(String type){JsonObject m=new JsonObject();m.addProperty("v",1);m.addProperty("type",type);return m;}
 static long revision(Room r){return r.record.get("revision").getAsLong();}
 static String key(String token){if(!token.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Invalid session ID");return SHARED_KEY;}
 static String uuid(JsonObject o,String k){String s=Model.str(o,k);if(!s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new IllegalArgumentException("Invalid ID");return s;}
 static void send(WebSocket ws,JsonObject m){if(ws.isOpen()){if(ws instanceof org.java_websocket.WebSocketImpl impl && impl.outQueue.size()>8){ws.close(1013,"Slow consumer; reconnect for snapshot");return;}ws.send(m.toString());}}
 void snapshot(WebSocket ws,Room room,String type){JsonObject m=message(type);m.addProperty("revision",revision(room));m.add("state",room.record.get("state"));m.addProperty("hash",Model.hash(m.get("state")));m.add("history",History.availability(room.record));m.addProperty("readOnly",room.record.has("readOnly")&&room.record.get("readOnly").getAsBoolean());send(ws,m);}
 @Override public synchronized void onOpen(WebSocket ws,ClientHandshake h){
  if(peers.size()>=32){ws.close(1013,"Server connection limit");return;}peers.put(ws,new Peer());
  timer.schedule(()->{synchronized(this){Peer p=peers.get(ws);if(p!=null&&p.room==null)ws.close(1008,"Join timeout");}},15,TimeUnit.SECONDS);
 }
 @Override public synchronized void onClose(WebSocket ws,int c,String s,boolean remote){Peer p=peers.remove(ws);if(p!=null&&p.room!=null)p.room.sockets.remove(ws);}
 @Override public synchronized void onMessage(WebSocket ws,String text){
  Peer p=peers.get(ws);if(p==null)return;
  String op=null;
  try{
   if(text.length()>MAX_MESSAGE)throw new IllegalArgumentException("Message too large");
   long now=System.currentTimeMillis();if(now-p.window>1000){p.window=now;p.messages=0;}if(++p.messages>40){ws.close(1008,"Rate limit");return;}
   Model.checkJsonDepth(text);JsonObject m=JsonParser.parseString(text).getAsJsonObject();if(!m.has("v")||!m.get("v").getAsString().equals("1"))throw new IllegalArgumentException("Protocol version mismatch");String type=Model.str(m,"type");
   if(type.equals("health")){send(ws,message("healthy"));return;}
   if(type.equals("connect")||type.equals("create")||type.equals("join")){
    if(p.room!=null)throw new IllegalArgumentException("Already joined");String client=uuid(m,"clientId");String nickname=Model.str(m,"nickname");if(nickname.isBlank()||nickname.length()>40||nickname.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid nickname");
    Room r=rooms.get(sharedKey);
    if(r==null){
     if(type.equals("join"))throw new IllegalArgumentException("No shared project exists yet");
     if(m.has("cannotInitialize")&&m.get("cannotInitialize").getAsBoolean())throw new IllegalArgumentException("Open a supported circuit or an empty window to initialize the server project");
     JsonObject state=m.has("state")?m.getAsJsonObject("state"):Model.capture(Model.blank());Model.validate(state);Model.materialize(state);
     JsonObject record=new JsonObject();record.addProperty("revision",0);record.add("state",state.deepCopy());record.add("receipts",new JsonObject());record.add("touched",new JsonObject());
     r=new Room(sharedKey,record);store.commit(r.key,record);rooms.put(r.key,r);
    }
    if(r.sockets.size()>=2)throw new IllegalArgumentException("Session already has two collaborators");
    for(WebSocket other:r.sockets)if(peers.get(other).client.equals(client))throw new IllegalArgumentException("Client already connected");
    p.room=r;p.client=client;r.sockets.add(ws);
    JsonObject welcome=message("welcome");welcome.addProperty("revision",revision(r));welcome.add("state",r.record.get("state"));welcome.addProperty("hash",Model.hash(welcome.get("state")));if(!type.equals("connect"))welcome.addProperty("sessionId","A".repeat(43));welcome.add("history",History.availability(r.record));welcome.addProperty("readOnly",r.record.has("readOnly")&&r.record.get("readOnly").getAsBoolean());send(ws,welcome);return;
   }
   if(p.room==null)throw new IllegalArgumentException("Join a session first");Room r=p.room;
   if(type.equals("sync")){snapshot(ws,r,"snapshot");return;}
   if(!Set.of("edit","undo","redo").contains(type))throw new IllegalArgumentException("Unknown message type");
   op=uuid(m,"operationId");String receiptKey=p.client+"/"+op;JsonObject receipts=r.record.getAsJsonObject("receipts");
   String requestHash=Model.hash(m);
   if(receipts.has(receiptKey)){
    JsonObject receipt=receipts.getAsJsonObject(receiptKey);if(!requestHash.equals(Model.str(receipt,"requestHash")))throw new IllegalArgumentException("Operation ID reused for different content");
    JsonObject ack=message("duplicate");ack.addProperty("operationId",op);ack.addProperty("acceptedRevision",receipt.get("revision").getAsLong());send(ws,ack);return;
   }
   if(r.record.has("readOnly")&&r.record.get("readOnly").getAsBoolean())throw new IllegalArgumentException("Recovered session is read-only; administrator review required");
   if(receipts.size()>=10000)throw new IllegalArgumentException("Session transaction limit; export and create a new session");
   long base=m.get("baseRevision").getAsLong();if(base<0||base>revision(r)||!m.get("baseRevision").getAsString().equals(Long.toString(base)))throw new IllegalArgumentException("Invalid base revision");
   JsonArray changes=type.equals("edit")?m.getAsJsonArray("changes"):History.changes(r.record,p.client,type);
   JsonObject touched=r.record.has("touched")?r.record.getAsJsonObject("touched"):new JsonObject();
   for(JsonElement change:changes){String k=Model.str(change.getAsJsonObject(),"key");if(type.equals("edit")&&touched.has(k)&&touched.get(k).getAsLong()>base)throw new IllegalArgumentException("Conflict: target changed since your base revision");}
   if(touched.size()+changes.size()>100000)throw new IllegalArgumentException("Session object history limit");
   JsonObject next=Model.patched(r.record.getAsJsonObject("state"),changes);
   // Replay into a separate model before durability or acknowledgement. Never mutate authoritative state on rejection.
   LogisimValidation.validate(next);
   JsonObject record=r.record.deepCopy();record.add("state",next);record.addProperty("revision",revision(r)+1);
   JsonObject receipt=new JsonObject();receipt.addProperty("revision",revision(r)+1);receipt.addProperty("requestHash",requestHash);record.getAsJsonObject("receipts").add(receiptKey,receipt);
   if(!record.has("touched"))record.add("touched",new JsonObject());
   for(JsonElement change:changes)record.getAsJsonObject("touched").addProperty(Model.str(change.getAsJsonObject(),"key"),revision(r)+1);
   History.committed(record,p.client,type,changes,revision(r)+1);
   store.commit(r.key,record);r.record=record;
   JsonObject commit=message("commit");commit.addProperty("operationId",op);commit.addProperty("clientId",p.client);commit.addProperty("revision",revision(r));commit.add("changes",changes);commit.addProperty("hash",Model.hash(next));commit.addProperty("kind",type);commit.add("history",History.availability(record));
   for(WebSocket socket:new ArrayList<>(r.sockets))send(socket,commit);
  }catch(Exception e){
   JsonObject error=message("rejected");if(op!=null)error.addProperty("operationId",op);
   String reason=e instanceof IllegalArgumentException?Objects.toString(e.getMessage(),"Malformed message"):"Server could not commit; edit not acknowledged";
   error.addProperty("reason",reason.substring(0,Math.min(256,reason.length())));send(ws,error);
  }catch(StackOverflowError e){ws.close(1008,"Excessive message nesting");}
 }
 @Override public void onMessage(WebSocket ws,java.nio.ByteBuffer bytes){ws.close(1003,"Only JSON text messages supported");}
 @Override public void onError(WebSocket ws,Exception e){System.err.println("WebSocket transport error (details suppressed)");}
 @Override public void onStart(){System.out.println("Logisim Multiplayer listening on port "+getPort());}
 public void shutdown()throws Exception{timer.shutdownNow();stop(2000);store.close();}
 static class LogisimValidation {static void validate(JsonObject state)throws Exception{
  var file=Model.materialize(state);byte[] circ=com.cburch.logisim.file.ProjectIO.write(file);
  // Read only our own serializer output; never load arbitrary network XML or external libraries.
  var loaded=com.cburch.logisim.file.LogisimFile.load(new java.io.ByteArrayInputStream(circ),new Model.StrictLoader());
  if(loaded==null||!Model.capture(loaded).equals(state))throw new IllegalArgumentException("Project failed vanilla serialization validation");
 }}
 public static void main(String[] args)throws Exception{
  if(args.length>0&&args[0].equals("--health")){
   CountDownLatch latch=new CountDownLatch(1);boolean[] healthy={false};
   WebSocketClient probe=new WebSocketClient(new URI("ws://127.0.0.1:"+System.getenv().getOrDefault("PORT","8080"))){
    public void onOpen(ServerHandshake h){send(message("health").toString());}public void onMessage(String s){healthy[0]=s.contains("healthy");latch.countDown();}public void onClose(int c,String s,boolean r){latch.countDown();}public void onError(Exception e){latch.countDown();}};
   probe.connect();latch.await(3,TimeUnit.SECONDS);probe.close();System.exit(healthy[0]?0:1);
  }
  Server server=new Server(new InetSocketAddress(System.getenv().getOrDefault("BIND_ADDRESS","127.0.0.1"),Integer.parseInt(System.getenv().getOrDefault("PORT","8080"))),Paths.get(System.getenv().getOrDefault("DATA_DIR","data")));
  Runtime.getRuntime().addShutdownHook(new Thread(()->{try{server.shutdown();}catch(Exception ignored){}}));server.start();
 }
}

