package com.logisim.multiplayer;
import com.cburch.logisim.gui.main.*;
import com.cburch.logisim.proj.*;
import com.cburch.logisim.file.*;
import com.cburch.logisim.circuit.*;
import com.cburch.logisim.comp.*;
import com.cburch.logisim.data.*;
import com.cburch.logisim.instance.StdAttr;
import java.nio.file.*;
/** Real Swing frames and original selection/clipboard actions; requires a graphical desktop. */
public class GuiTest extends DesktopTest {
 static void frames(Collaboration a,Collaboration b)throws Exception{edt(()->{
  for(Collaboration c:new Collaboration[]{a,b}){
   Frame f=new Frame(c.project);c.project.setFrame(f);c.project.getLogisimFile().getLoader().setParent(f);f.setSize(800,600);f.setVisible(true);
  }
  a.project.getFrame().setLocation(20,50);b.project.getFrame().setLocation(840,50);return null;
 });}
 static Component first(Collaboration c){return c.project.getCurrentCircuit().getNonWires().iterator().next();}
 public static void main(String[] args)throws Exception{
  Server s=IntegrationTest.start(Files.createTempDirectory("logisim-gui-test-"));Collaboration a=client(s.getPort(),null);await(()->idle(a),"create");Collaboration b=client(s.getPort(),edt(()->a.sessionId));await(()->idle(b),"join");frames(a,b);
  try{
   edt(()->{gate(a,"Gates/AND Gate",120,100);return null;});await(()->idle(a)&&same(a,b),"gate in both frames");
   edt(()->{var edit=a.project.getFrame().getJMenuBar().getMenu(1);check(edit.getItem(0).isEnabled()&&edit.getItem(0).getAccelerator().getKeyCode()==java.awt.event.KeyEvent.VK_Z,"undo menu and Ctrl+Z enabled");edit.getItem(0).doClick();return null;});await(()->idle(a)&&same(a,b)&&a.project.getCurrentCircuit().getNonWires().isEmpty(),"Edit menu undo synchronized");
   edt(()->{var edit=a.project.getFrame().getJMenuBar().getMenu(1);check(edit.getItem(1).isEnabled()&&edit.getItem(1).getAccelerator().getKeyCode()==java.awt.event.KeyEvent.VK_Y,"redo menu and Ctrl+Y enabled");edit.getItem(1).doClick();return null;});await(()->idle(a)&&same(a,b)&&a.project.getCurrentCircuit().getNonWires().size()==1,"Edit menu redo synchronized");
   edt(()->{Selection sel=a.project.getFrame().getCanvas().getSelection();sel.add(first(a));a.project.doAction(SelectionActions.duplicate(sel));a.project.doAction(SelectionActions.translate(sel,150,0,null));return null;});await(()->idle(a)&&same(a,b)&&a.project.getCurrentCircuit().getNonWires().size()==2,"duplicate via original selection actions");
   edt(()->{Selection sel=b.project.getFrame().getCanvas().getSelection();sel.add(first(b));b.project.doAction(SelectionActions.copy(sel));b.project.doAction(SelectionActions.pasteMaybe(b.project,sel));b.project.doAction(SelectionActions.translate(sel,0,150,null));return null;});await(()->idle(b)&&same(a,b)&&b.project.getCurrentCircuit().getNonWires().size()==3,"copy paste via original clipboard actions");
   edt(()->{Selection sel=a.project.getFrame().getCanvas().getSelection();a.project.doAction(SelectionActions.translate(sel,20,20,null));return null;});await(()->idle(a)&&same(a,b),"move existing component");
   edt(()->{com.cburch.logisim.tools.SetAttributeAction action=new com.cburch.logisim.tools.SetAttributeAction(b.project.getCurrentCircuit(),()->"label");action.set(first(b),StdAttr.LABEL,"shared");b.project.doAction(action);return null;});await(()->idle(b)&&same(a,b),"original attribute action");
   edt(()->{Selection sel=a.project.getFrame().getCanvas().getSelection();a.project.doAction(SelectionActions.dropAll(sel));sel.add(first(a));a.project.doAction(SelectionActions.clear(sel));return null;});await(()->idle(a)&&same(a,b)&&a.project.getCurrentCircuit().getNonWires().size()==2,"original selection deletion");
   edt(()->{a.project.doAction(LogisimFileActions.addCircuit(new Circuit("unit")));return null;});await(()->idle(a)&&same(a,b)&&a.project.getLogisimFile().getCircuitCount()==2,"original add circuit action");
   check(notifications.isEmpty(),"no GUI synchronization errors: "+notifications);
   System.out.println("PASS: real Swing frames, original duplicate/copy/paste/move/delete/attribute/subcircuit actions; "+checks+" assertions");
   if(args.length>0&&args[0].equals("--hold")){System.out.println("GUI inspection ready");Thread.sleep(60000);}
  }finally{
   edt(()->{a.leave();b.leave();a.project.getSimulator().shutDown();b.project.getSimulator().shutDown();a.project.getFrame().dispose();b.project.getFrame().dispose();return null;});s.shutdown();
  }
 }
}
