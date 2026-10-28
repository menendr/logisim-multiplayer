package com.logisim.multiplayer;
import java.util.*;
import java.util.prefs.*;
import java.nio.file.*;
import java.util.concurrent.*;
/** Persistent profile verification across an actual separate Java process. */
public class SavedServersTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception{
  if(args.length>0){SavedServers reopened=new SavedServers(Preferences.userRoot().node(args[0]));var last=reopened.last();check(last!=null&&last.clientId().equals(args[1])&&last.name().equals("Alex")&&last.address().equals("ws://127.0.0.1:18080"),"profile/identity survives fresh JVM");System.out.println("PASS: saved server and name restored in a fresh JVM");return;}
  Preferences node=Preferences.userRoot().node("com/logisim/multiplayer-tests/"+UUID.randomUUID());
  try{
   node.put("server","127.0.0.1:18080");node.put("nickname","Alex");SavedServers store=new SavedServers(node);var original=store.last();check(original!=null,"legacy settings migrated");
   var same=store.remember("ws://127.0.0.1:18080/","Alex");check(same.clientId().equals(original.clientId())&&store.list().size()==1,"normalized address deduplicates and retains history identity");
   var partner=store.remember("127.0.0.1:18080","Partner");check(!partner.clientId().equals(original.clientId()),"different name gets separate personal history");check(store.last().equals(partner),"last used profile remembered");
   store.remember(original.address(),"Alex");String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
   Process child=new ProcessBuilder(executable,"-cp",System.getProperty("java.class.path"),SavedServersTest.class.getName(),node.absolutePath(),original.clientId()).inheritIO().start();check(child.waitFor(15,TimeUnit.SECONDS)&&child.exitValue()==0,"separate process persistence check");
   store.forget(original);check(store.last().equals(partner),"forget chooses remaining connection");store.forget(partner);check(store.list().isEmpty()&&new SavedServers(node).last()==null,"forget last stays forgotten after reopening");
   for(int i=0;i<12;i++)store.remember("127.0.0.1:"+(19000+i),"Alex");check(store.list().size()==8&&store.last().address().endsWith(":19011"),"recent servers bounded and ordered");
   node.put(SavedServers.LIST,"not-json");check(new SavedServers(node).list().isEmpty(),"corrupt local settings cannot break application startup");
   System.out.println("PASS: saved-server migration, deduplication, separate users, last-used, forgetting, bounds and corrupt settings");
  }finally{node.removeNode();Preferences.userRoot().flush();}
 }
}
