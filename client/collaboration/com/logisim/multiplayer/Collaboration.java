package com.logisim.multiplayer;
import com.google.gson.*;
import com.cburch.logisim.proj.*;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.file.*;
import com.cburch.logisim.circuit.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.drafts.Draft_6455;
import java.net.*;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.*;
import java.util.prefs.Preferences;
import javax.swing.*;

/** Small EDT-bound adapter. Local gestures run unchanged; only model deltas cross the network. GPL-2.0-or-later. */
public final class Collaboration {
 private static final Map<Project,Collaboration> INSTANCES=new WeakHashMap<>();
 final Project project; final JMenu menu=new JMenu("Collaboration");
 final Preferences prefs=Preferences.userNodeForPackage(Collaboration.class);
 SavedServers saved=new SavedServers(prefs);
 String clientId=UUID.randomUUID().toString();
 WebSocketClient socket;String address,nickname,sessionId,status="Disconnected",connectionFailure;boolean active,bypass,ready,readOnly,closing;
 JsonObject historyState=new JsonObject();
 JsonObject accepted,pending;long revision;int retries;
 javax.swing.Timer retryTimer;javax.swing.Timer ackTimer;javax.swing.Timer synchronizationTimer;
 private Collaboration(Project p){project=p;menu.addMenuListener(new javax.swing.event.MenuListener(){public void menuSelected(javax.swing.event.MenuEvent e){buildMenu();}public void menuDeselected(javax.swing.event.MenuEvent e){}public void menuCanceled(javax.swing.event.MenuEvent e){}});notification=text->JOptionPane.showMessageDialog(project.getFrame(),text,"Logisim Multiplayer",JOptionPane.INFORMATION_MESSAGE);}
 public static JMenu menu(Project p){
  if(p==null){JMenu disabled=new JMenu("Collaboration");disabled.setEnabled(false);return disabled;}
  Collaboration c=INSTANCES.computeIfAbsent(p,Collaboration::new);c.buildMenu();return c.menu;
 }
 public static JMenu menu(Project p,JFrame parent){
  JMenu result=menu(p);
  if(p!=null && parent instanceof com.cburch.logisim.gui.main.Frame){parent.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosed(java.awt.event.WindowEvent event){Collaboration c=INSTANCES.remove(p);if(c!=null)c.leave();}});}
  return result;
 }
 static Collaboration instance(Project p){return INSTANCES.get(p);}
 public static boolean intercept(Project p,Action action){Collaboration c=instance(p);if(c==null||!c.active||c.bypass)return false;c.perform(action);return true;}
 public static boolean interceptUndo(Project p){Collaboration c=instance(p);if(c==null||!c.active||c.bypass)return false;c.requestHistory("undo");return true;}
 public static void redo(Project p){Collaboration c=instance(p);if(c!=null&&c.active)c.requestHistory("redo");}
 public static boolean canUndo(Project p){Collaboration c=instance(p);return c!=null&&c.available("undo");}
 public static String historyReason(Project p,String kind){Collaboration c=instance(p);if(c==null)return "Redo is available during collaboration";if(c.pending!=null||!c.ready)return "Wait for synchronization";if(c.readOnly)return "Read-only recovery";return c.historyState.has(kind+"Reason")?Model.str(c.historyState,kind+"Reason"):"Nothing to "+kind;}
 public static boolean canRedo(Project p){Collaboration c=instance(p);return c!=null&&c.available("redo");}
 boolean available(String kind){return active&&ready&&pending==null&&!readOnly&&historyState.has(kind)&&historyState.get(kind).getAsBoolean();}
 void updateHistory(JsonObject m){historyState=m.has("history")&&m.getAsJsonObject("history").has(clientId)?m.getAsJsonObject("history").getAsJsonObject(clientId).deepCopy():new JsonObject();}
 void requestHistory(String kind){
  if(!available(kind)){explain(!ready||pending!=null?"Wait until synchronization finishes.":historyState.has(kind+"Reason")?Model.str(historyState,kind+"Reason"):"Nothing to "+kind);return;}
  pending=message(kind);pending.addProperty("operationId",UUID.randomUUID().toString());pending.addProperty("baseRevision",revision);
  try{send(pending);waitForAck();status("Synchronizing");project.repaintCanvas();}catch(Exception e){lost();}
 }
 public static void fileChanging(Project p){Collaboration c=instance(p);if(c!=null&&c.active&&!c.bypass)c.leave();}
 public static boolean inSession(Project p){Collaboration c=instance(p);return c!=null&&c.active;}
 void buildMenu(){
  menu.removeAll();SavedServers.Profile last=saved.last();
  if(last==null)item("Connect to Server...",()->dialog(false,false));
  else{
   boolean connected=ready&&active&&clientId.equals(last.clientId())&&last.address().equals(address);
   JMenuItem quick=new JMenuItem((connected?"Connected to ":"Connect to ")+last.label());quick.setToolTipText("Connect as "+last.name());quick.setEnabled(!connected);quick.addActionListener(e->connectProfile(last));menu.add(quick);
   JMenu servers=new JMenu("Saved Servers");for(SavedServers.Profile profile:saved.list()){
    JMenuItem entry=new JMenuItem(profile.label());entry.setEnabled(!(ready&&active&&clientId.equals(profile.clientId())&&profile.address().equals(address)));entry.addActionListener(e->connectProfile(profile));servers.add(entry);
   }menu.add(servers);item("Add Server...",()->dialog(false,false));item("Forget a Saved Server...",this::forgetServer);
  }
  menu.addSeparator();item("Disconnect",this::disconnect);item("Work Offline",this::leave);
  item("Connection Status",()->explain(status+(connectionFailure==null?"":"\n"+connectionFailure)+"\nConnected as: "+Objects.toString(nickname,"Not connected")+"\nRevision: "+revision+"\nUndo and redo apply only to your edits. Your saved server profile remembers your history identity across restarts. A partner's changes to an affected object can prevent undo. Simulation inputs remain local."));
 }
 void forgetServer(){
  List<SavedServers.Profile> profiles=saved.list();if(profiles.isEmpty())return;
  String[] labels=profiles.stream().map(SavedServers.Profile::label).toArray(String[]::new);
  Object choice=JOptionPane.showInputDialog(project.getFrame(),"Forget this saved address, name and personal undo profile on this computer. The shared project stays on the server.","Forget saved server",JOptionPane.QUESTION_MESSAGE,null,labels,labels[0]);
  if(choice!=null)for(int i=0;i<labels.length;i++)if(labels[i].equals(choice)){try{saved.forget(profiles.get(i));buildMenu();}catch(Exception e){explain(e.getMessage());}break;}
 }
 void item(String text,Runnable run){JMenuItem i=new JMenuItem(text);i.addActionListener(e->run.run());menu.add(i);}
 java.util.function.Consumer<String> notification;
 void explain(String text){notification.accept(text);}
 void status(String value){status=value;menu.setText("Collaboration ("+value+")");}
 static URI serverUri(String text)throws Exception{
  String value=text.trim();if(!value.contains("://"))value="ws://"+value;
  URI uri=new URI(value);if(!Set.of("ws","wss").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getQuery()!=null)throw new IllegalArgumentException("Enter a server IP/address, optionally with a port, or a ws:// / wss:// URL");
  if(uri.getScheme().equals("ws")&&uri.getPort()==-1)uri=new URI("ws",null,uri.getHost(),8080,uri.getPath(),null,null);
  return uri;
 }
 void dialog(boolean unusedCreate,boolean unusedJoin){
  JTextField url=new JTextField(address!=null?address:prefs.get("server","ws://127.0.0.1:8080"),28);
  JTextField nick=new JTextField(nickname!=null?nickname:prefs.get("nickname","Student"),28);
  JPanel panel=new JPanel(new java.awt.GridLayout(0,1,0,5));panel.add(new JLabel("Server IP/address (include :port if needed)"));panel.add(url);panel.add(new JLabel("Your name"));panel.add(nick);panel.add(new JLabel("The server and your name are remembered on this computer."));
  if(JOptionPane.showConfirmDialog(project.getFrame(),panel,"Connect to shared project",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
  try{connectProfile(saved.remember(url.getText(),nick.getText()));}catch(Exception e){explain(Objects.toString(e.getMessage(),"Unable to connect"));}
 }
 void connectProfile(SavedServers.Profile profile){
  try{
   if(active&&ready&&profile.address().equals(address)&&profile.clientId().equals(clientId))return;
   if(active&&pending!=null&&(!profile.address().equals(address)||!profile.clientId().equals(clientId))){explain("Wait for your current edit to finish before switching servers or names.");return;}
   SavedServers.Profile stored=saved.remember(profile.address(),profile.name());
   JsonObject initial;try{initial=Model.capture(project.getLogisimFile());Model.validate(initial);}catch(IllegalArgumentException unsupported){initial=null;}
   JsonObject retry=active&&Objects.equals(address,stored.address())&&clientId.equals(stored.clientId())?pending:null;
   leave();address=stored.address();nickname=stored.name();clientId=stored.clientId();pending=retry;accepted=initial;active=true;ready=false;retries=0;
   project.clearCollaborationHistory();buildMenu();open();
  }catch(Exception e){explain(Objects.toString(e.getMessage(),"Unable to connect"));}
 }
 void open(){
  if(!active)return;connectionFailure=null;status("Synchronizing");ready=false;closing=false;
  final WebSocketClient current=new WebSocketClient(URI.create(address),new Draft_6455(Collections.emptyList(),2000000),null,10000){
   public void onOpen(ServerHandshake h){SwingUtilities.invokeLater(()->{
    if(socket!=this||!active)return;
    JsonObject m=message("connect");m.addProperty("clientId",clientId);m.addProperty("nickname",nickname);
    if(accepted!=null)m.add("state",accepted);else m.addProperty("cannotInitialize",true);send(m.toString());
   });}
   public void onMessage(String text){SwingUtilities.invokeLater(()->{if(socket==this&&active)receive(text);});}
   public void onClose(int code,String reason,boolean remote){SwingUtilities.invokeLater(()->{if(socket==this&&active)connectionFailed("The server closed the connection (code "+code+").");});}
   public void onError(Exception e){SwingUtilities.invokeLater(()->{if(socket==this&&active)connectionFailed(e instanceof java.net.ConnectException?"Connection refused. Check that the server is running and the port is correct.":e instanceof java.net.SocketTimeoutException?"The connection timed out.":"Connection failed ("+e.getClass().getSimpleName()+"). Check the server address and transport settings.");});}
  };
  socket=current;current.setConnectionLostTimeout(20);synchronizationDeadline();current.connect();
 }
 JsonObject message(String type){JsonObject m=new JsonObject();m.addProperty("v",1);m.addProperty("type",type);return m;}
 void send(JsonObject m){if(socket==null||!socket.isOpen())throw new IllegalStateException("Disconnected");if(m.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>2000000)throw new IllegalArgumentException("Transaction exceeds the message limit");socket.send(m.toString());}
 void synchronizationDeadline(){if(synchronizationTimer!=null)synchronizationTimer.stop();synchronizationTimer=new javax.swing.Timer(7000,e->{if(active&&!ready)connectionFailed("The server did not finish synchronization within seven seconds.");});synchronizationTimer.setRepeats(false);synchronizationTimer.start();}
 void stopSynchronizationDeadline(){if(synchronizationTimer!=null){synchronizationTimer.stop();synchronizationTimer=null;}}
 void sync(){ready=false;status("Synchronizing");synchronizationDeadline();send(message("sync"));}
 void receive(String text){
  try{
   Model.checkJsonDepth(text);JsonObject m=JsonParser.parseString(text).getAsJsonObject();if(m.get("v").getAsInt()!=1)throw new IllegalStateException("Protocol mismatch");String type=Model.str(m,"type");
   if(type.equals("welcome")||type.equals("snapshot")){
    JsonObject state=m.getAsJsonObject("state");if(!Model.hash(state).equals(Model.str(m,"hash")))throw new IllegalStateException("Snapshot hash mismatch");
    sessionId="shared";updateHistory(m);revision=m.get("revision").getAsLong();accepted=state.deepCopy();readOnly=m.has("readOnly")&&m.get("readOnly").getAsBoolean();
    reconcile(true);ready=true;stopSynchronizationDeadline();retries=0;status(readOnly?"Read-only recovery":"Connected and synchronized");project.repaintCanvas();
    if(pending!=null){send(pending);waitForAck();status("Synchronizing");}return;
   }
   if(type.equals("commit")){
    long rev=m.get("revision").getAsLong();if(rev<=revision){sync();return;}if(rev!=revision+1){sync();return;}
    JsonObject next=Model.patched(accepted,m.getAsJsonArray("changes"));if(!Model.hash(next).equals(Model.str(m,"hash")))throw new IllegalStateException("Committed state hash mismatch");
    accepted=next;revision=rev;updateHistory(m);
    if(pending!=null&&clientId.equals(Model.str(m,"clientId"))&&Model.str(pending,"operationId").equals(Model.str(m,"operationId"))){pending=null;stopAck();}
    reconcile(false);status(pending==null?"Connected and synchronized":"Synchronizing");return;
   }
   if(type.equals("duplicate")){pending=null;stopAck();sync();return;}
   if(type.equals("rejected")){
    if(pending!=null&&m.has("operationId")&&Model.str(pending,"operationId").equals(Model.str(m,"operationId"))){pending=null;stopAck();reconcile(false);sync();}
    else if(sessionId==null||!ready){disconnect();}
    String reason=Model.str(m,"reason");if(reason.equals("Client already connected"))reason="This saved user is already connected. Disconnect the other window or connect with a different name.";
    status("Conflict / "+reason);explain(reason+"\nThe rejected edit was not committed. Accepted server changes are retained.");return;
   }
  }catch(Exception e){ready=false;status("Synchronization conflict");try{sync();}catch(Exception ignored){lost();}}
 }
 void waitForAck(){stopAck();ackTimer=new javax.swing.Timer(10000,e->{if(active&&pending!=null){status("Acknowledgement delayed; reconnecting");lost();}});ackTimer.setRepeats(false);ackTimer.start();}
 void stopAck(){if(ackTimer!=null){ackTimer.stop();ackTimer=null;}}
 void reconcile(boolean snapshot){
  bypass=true;
  try{
   LogisimFile f=project.getLogisimFile();String viewed=project.getCurrentCircuit().getName();
   if(snapshot){LogisimFile fresh=Model.materialize(accepted);fresh.getOptions().copyFrom(f.getOptions(),fresh);project.setLogisimFile(fresh);Circuit view=fresh.getCircuit(viewed);if(view!=null)project.setCurrentCircuit(view);}
   else {Model.apply(f,accepted);if(!f.contains(project.getCurrentCircuit()))project.setCurrentCircuit(f.getMainCircuit());f.setDirty(true);}
   project.clearCollaborationHistory();project.repaintCanvas();
  }finally{bypass=false;}
 }
 boolean allowed(Action a){String n=a.getClass().getName();
  return n.equals("com.cburch.logisim.circuit.CircuitAction")||n.equals("com.cburch.logisim.tools.SetAttributeAction")||n.equals("com.cburch.logisim.gui.main.ToolAttributeAction")||n.startsWith("com.cburch.logisim.gui.main.SelectionActions$")||Set.of("AddCircuit","RemoveCircuit","SetMainCircuit").stream().anyMatch(s->n.equals("com.cburch.logisim.file.LogisimFileActions$"+s));
 }
 void perform(Action action){
  if(action==null)return;
  if(action.getClass().getName().equals("com.cburch.logisim.std.memory.RomContentsListener$Change")){
   bypass=true;try{action.undo(project);}finally{bypass=false;}
   explain("ROM contents editing is unavailable in collaboration. The local contents change was reverted.");return;
  }
  if(!ready||pending!=null||readOnly){status(readOnly?"Read-only recovery":"Synchronizing; please wait");Toolkit.getDefaultToolkit().beep();return;}
  if(!allowed(action)){explain("This editing operation is unavailable in collaboration. Leave the session to use it offline.\nCustom appearances, library changes, ROM contents editing and project options are restricted.");return;}
  JsonObject before=Model.capture(project.getLogisimFile());
  // A discrepancy here means an uninstrumented path changed the structure. Recover before allowing another edit.
  if(!before.equals(accepted)){reconcile(true);status("Recovered uncommitted local change; retry your edit");return;}
  try{
   bypass=true;project.doAction(action);bypass=false;
   JsonObject after=Model.capture(project.getLogisimFile());JsonArray changes=Model.diff(before,after);if(changes.isEmpty())return;
   Model.validate(after);Model.materialize(after);
   pending=message("edit");pending.addProperty("operationId",UUID.randomUUID().toString());pending.addProperty("baseRevision",revision);pending.add("changes",changes);
   status("Synchronizing");send(pending);waitForAck();project.repaintCanvas();
  }catch(Exception e){
   bypass=true;try{project.undoAction();}catch(Exception ignored){}finally{bypass=false;}
   pending=null;reconcile(true);status("Synchronization conflict");explain("Edit was not sent: "+Objects.toString(e.getMessage(),"unsupported edit"));
  }finally{bypass=false;}
 }
 void connectionFailed(String reason){
  boolean initial=sessionId==null;
  String endpoint=address==null?"the configured server":address;
  lost();connectionFailure="Unable to connect to "+endpoint+"\n"+reason+"\nFor a local Docker server, check its published HOST_PORT in .env or docker compose ps.";
  status(initial?"Connection failed":"Disconnected; retrying");
  if(initial&&retries<=1)explain(connectionFailure+"\nThe shared project could not be opened. Choose or add the correct server in the Collaboration menu and try again.");
 }
 void lost(){
  WebSocketClient failed=socket;socket=null;stopSynchronizationDeadline();if(failed!=null)failed.closeConnection(1001,"Reconnecting");
  if(retryTimer!=null)retryTimer.stop();
  ready=false;stopAck();status("Disconnected");if(accepted!=null)try{reconcile(false);}catch(Exception ignored){}
  if(active&&!closing){int delay=Math.min(30000,1000*(1<<Math.min(retries++,5)));retryTimer=new javax.swing.Timer(delay,e->open());retryTimer.setRepeats(false);retryTimer.start();}
 }
 void disconnect(){closing=true;ready=false;stopAck();stopSynchronizationDeadline();if(retryTimer!=null)retryTimer.stop();if(socket!=null){WebSocketClient old=socket;socket=null;old.closeConnection(1000,"Disconnected");}if(accepted!=null&&active)reconcile(false);status("Disconnected; reconnect or leave to edit offline");}
 void leave(){disconnect();historyState=new JsonObject();active=false;pending=null;sessionId=null;accepted=null;project.clearCollaborationHistory();status("Disconnected");}
}
