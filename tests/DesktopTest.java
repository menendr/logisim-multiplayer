package com.logisim.multiplayer;
import com.google.gson.*;
import com.cburch.logisim.proj.*;
import com.cburch.logisim.file.*;
import com.cburch.logisim.circuit.*;
import com.cburch.logisim.comp.*;
import com.cburch.logisim.data.*;
import javax.swing.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Drives the production desktop action hook and network client on the Swing EDT. */
public class DesktopTest {
 static int checks;static List<String> notifications=new ArrayList<>();
 static <T>T edt(Callable<T> fn)throws Exception{FutureTask<T> task=new FutureTask<>(fn);SwingUtilities.invokeAndWait(task);return task.get();}
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static void await(Callable<Boolean> test,String why)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end){if(edt(test)){checks++;return;}Thread.sleep(20);}throw new AssertionError(why+"; notifications="+notifications);}
 static Collaboration client(int port,String token)throws Exception{return edt(()->{
  Project p=new Project(Model.blank());p.getOptions().getAttributeSet().setValue(Options.sim_limit_attr,2345);Collaboration.menu(p);Collaboration c=Collaboration.instance(p);c.notification=notifications::add;c.active=true;c.address="ws://127.0.0.1:"+port;c.nickname="Test";c.sessionId=token;c.accepted=Model.capture(p.getLogisimFile());c.open();return c;
 });}
 static void gate(Collaboration c,String type,int x,int y){JsonObject d=new JsonObject();d.addProperty("circuit","main");d.addProperty("type",type);d.addProperty("x",x);d.addProperty("y",y);d.add("attrs",new JsonObject());CircuitMutation m=new CircuitMutation(c.project.getCurrentCircuit());m.add(Model.component(c.project.getLogisimFile(),d));c.project.doAction(m.toAction(null));}
 static boolean idle(Collaboration c){return c.ready&&c.pending==null;}
 static boolean same(Collaboration a,Collaboration b){return Model.capture(a.project.getLogisimFile()).equals(Model.capture(b.project.getLogisimFile()));}
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("logisim-desktop-test-");Server server=IntegrationTest.start(dir);int port=server.getPort();Collaboration a=client(port,null);await(()->idle(a),"create desktop session");Collaboration b=client(port,edt(()->a.sessionId));
  try{
   await(()->idle(b)&&same(a,b),"join desktop session");check(edt(()->b.project.getOptions().getAttributeSet().getValue(Options.sim_limit_attr)==2345),"join retains local project options");
   edt(()->{gate(a,"Gates/AND Gate",120,100);return null;});await(()->idle(a)&&same(a,b),"A original action reaches B");
   edt(()->{gate(b,"Gates/OR Gate",300,100);return null;});await(()->idle(b)&&same(a,b),"B original action reaches A");
   edt(()->{gate(a,"Gates/NOT Gate",400,200);gate(b,"Wiring/Pin",600,200);return null;});await(()->idle(a)&&idle(b)&&same(a,b)&&a.revision==4&&b.revision==4,"concurrent original actions retain both edits");
   edt(()->{JsonObject outOfOrder=a.message("commit");outOfOrder.addProperty("revision",a.revision+2);a.receive(outOfOrder.toString());return null;});await(()->idle(a)&&same(a,b)&&a.revision==4,"out of order commit requests recovery without model corruption");
   edt(()->{a.project.undoAction();return null;});await(()->idle(a)&&same(a,b)&&a.revision==5&&a.project.getCurrentCircuit().getNonWires().size()==3,"own undo preserves partner edits");
   edt(()->{Collaboration.redo(a.project);return null;});await(()->idle(a)&&same(a,b)&&a.revision==6&&a.project.getCurrentCircuit().getNonWires().size()==4,"redo restores only own edit");
   // Invalid circuit rename is rolled back through the original action inverse and authoritative reconciliation.
   edt(()->{CircuitMutation m=new CircuitMutation(a.project.getCurrentCircuit());m.setForCircuit(CircuitAttributes.NAME_ATTR,"bad/name");a.project.doAction(m.toAction(null));return null;});check(edt(()->same(a,b)&&a.pending==null),"unsupported edit safely reconciled");check(notifications.size()==1,"unsupported edit explained");notifications.clear();
   // Original clipboard/selection actions need a real Canvas and are covered in GuiTest/manual verification.
   server.shutdown();await(()->!a.ready&&!b.ready,"transport loss freezes collaborative edits");
   server=new Server(new InetSocketAddress("127.0.0.1",port),dir){
    boolean omitFirstJoin=true;
    @Override public synchronized void onMessage(org.java_websocket.WebSocket ws,String text){
     if(omitFirstJoin&&Model.str(JsonParser.parseString(text).getAsJsonObject(),"type").equals("connect")){omitFirstJoin=false;return;}
     super.onMessage(ws,text);
    }
   };server.start();try{await(()->idle(a)&&idle(b)&&same(a,b)&&a.revision==6,"automatic reconnect recovers even when the first join response is missing");}catch(AssertionError e){System.err.println(edt(()->"A: "+a.status+" ready="+a.ready+" revision="+a.revision+" active="+a.active+" socket="+(a.socket==null?"none":a.socket.getReadyState())+" retries="+a.retries+"; B: "+b.status+" ready="+b.ready+" revision="+b.revision+" active="+b.active+" socket="+(b.socket==null?"none":b.socket.getReadyState())+" retries="+b.retries));throw e;}
   edt(()->{gate(b,"Gates/XOR Gate",700,300);return null;});await(()->idle(b)&&same(a,b)&&a.revision==7,"edit following reconnect");
   edt(()->{a.leave();gate(a,"Gates/NOR Gate",800,300);check(a.project.getCurrentCircuit().getNonWires().size()==6,"offline add works");a.project.undoAction();check(a.project.getCurrentCircuit().getNonWires().size()==5,"offline undo works");return null;});
   check(notifications.isEmpty(),"no unexpected client errors");
   System.out.println("PASS: "+checks+" desktop assertions: production action hook, EDT replay, concurrent edits, rollback, undo restriction, automatic reconnect, offline behavior");
  }finally{edt(()->{a.leave();b.leave();a.project.getSimulator().shutDown();b.project.getSimulator().shutDown();return null;});server.shutdown();}
 }
}
