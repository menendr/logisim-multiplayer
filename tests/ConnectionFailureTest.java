package com.logisim.multiplayer;
import com.cburch.logisim.proj.Project;
import java.net.*;
/** Regression: connection failure must explain the endpoint and preserve the local project. */
public class ConnectionFailureTest extends DesktopTest {
 public static void main(String[] args)throws Exception{
  int unused;try(java.net.ServerSocket port=new java.net.ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){unused=port.getLocalPort();}
  Collaboration c=client(unused,null);
  try{
   await(()->c.socket==null&&c.status.equals("Connection failed")&&!notifications.isEmpty(),"failed connection must be visible");
   check(edt(()->c.sessionId==null&&!c.ready),"no invite for an uncreated session");
   check(edt(()->notifications.size()==1&&notifications.get(0).contains(":"+unused)&&notifications.get(0).contains("shared project could not be opened")),"one actionable failure containing endpoint");
   check(edt(()->Model.capture(c.project.getLogisimFile()).equals(c.accepted)),"failed connection preserves project");
   System.out.println("PASS: initial connection failure reports endpoint and preserves local project");
  }finally{edt(()->{c.leave();c.project.getSimulator().shutDown();return null;});}
 }
}
