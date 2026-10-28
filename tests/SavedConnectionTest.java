package com.logisim.multiplayer;
import com.cburch.logisim.proj.*;
import java.nio.file.*;
import java.util.*;
import java.util.prefs.*;
/** One-click connection restores this user's undo after reopening, without touching their partner's edit. */
public class SavedConnectionTest extends DesktopTest {
 static Collaboration fresh(SavedServers settings)throws Exception{return edt(()->{Project p=new Project(Model.blank());Collaboration.menu(p);Collaboration c=Collaboration.instance(p);c.saved=settings;c.notification=notifications::add;c.buildMenu();return c;});}
 public static void main(String[] args)throws Exception{
  Preferences ownNode=Preferences.userRoot().node("com/logisim/multiplayer-tests/"+UUID.randomUUID()),partnerNode=Preferences.userRoot().node("com/logisim/multiplayer-tests/"+UUID.randomUUID());
  Server server=IntegrationTest.start(Files.createTempDirectory("logisim-saved-connect-"));SavedServers own=new SavedServers(ownNode),partner=new SavedServers(partnerNode);String address="127.0.0.1:"+server.getPort();var profile=own.remember(address,"Alex");var other=partner.remember(address,"Partner");Collaboration a=fresh(own),b=fresh(partner),reopened=null;
  try{
   edt(()->{a.connectProfile(profile);return null;});await(()->idle(a),"saved profile connects");
   edt(()->{b.connectProfile(other);return null;});await(()->idle(b)&&same(a,b),"partner profile connects");
   edt(()->{gate(a,"Gates/AND Gate",120,100);return null;});await(()->idle(a)&&same(a,b),"own edit");
   edt(()->{gate(b,"Gates/OR Gate",300,100);return null;});await(()->idle(b)&&same(a,b),"partner edit");
   edt(()->{a.leave();return null;});await(()->{synchronized(server){return server.peers.values().stream().noneMatch(p->profile.clientId().equals(p.client));}},"closed desktop releases its connection");
   reopened=fresh(new SavedServers(ownNode));final Collaboration c=reopened;
   edt(()->{check(c.menu.getItem(0).getText().startsWith("Connect to "),"last-server quick connection displayed");c.menu.getItem(0).doClick();return null;});
   await(()->idle(c)&&same(c,b)&&c.clientId.equals(profile.clientId()),"one-click saved connection restores original personal identity");
   check(edt(()->Collaboration.canUndo(c.project)),"personal undo is available immediately after reopening");
   edt(()->{c.project.undoAction();return null;});await(()->idle(c)&&same(c,b)&&c.revision==3,"restored profile can undo its own prior edit");
   check(edt(()->c.accepted.keySet().stream().noneMatch(k->k.contains("AND Gate"))&&c.accepted.keySet().stream().anyMatch(k->k.contains("OR Gate"))),"own undo preserves partner edit");
   edt(()->{Collaboration.redo(c.project);return null;});await(()->idle(c)&&same(c,b)&&c.revision==4,"redo after reopening");
   edt(()->{b.project.undoAction();return null;});await(()->idle(b)&&same(c,b)&&c.revision==5,"partner has an independent undo stack");
   check(edt(()->c.accepted.keySet().stream().anyMatch(k->k.contains("AND Gate"))&&c.accepted.keySet().stream().noneMatch(k->k.contains("OR Gate"))),"partner undo preserves own edit");
   check(notifications.isEmpty(),"no unexpected connection or history dialogs");System.out.println("PASS: "+checks+" saved connection assertions, one-click reconnect and personal undo across desktop reopening");
  }finally{final Collaboration c=reopened;edt(()->{for(Collaboration item:new Collaboration[]{a,b,c})if(item!=null){item.leave();item.project.getSimulator().shutDown();}return null;});server.shutdown();ownNode.removeNode();partnerNode.removeNode();Preferences.userRoot().flush();}
 }
}
