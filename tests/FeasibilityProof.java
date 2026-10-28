package com.logisim.multiplayer;
import com.cburch.logisim.circuit.*;
import com.cburch.logisim.comp.*;
import com.cburch.logisim.data.*;
import com.cburch.logisim.file.*;
import com.cburch.logisim.proj.*;
import com.cburch.logisim.tools.*;
import java.util.*;
import javax.swing.SwingUtilities;
/** Executable experiment against unmodified 2.7.1 classes. GPL-2.0-or-later. */
public final class FeasibilityProof {
 static void check(boolean b,String s) { if(!b) throw new AssertionError(s); }
 public static void main(String[] args) throws Exception {
  SwingUtilities.invokeAndWait(() -> {
   Loader loader=new Loader(null);
   LogisimFile fa=LogisimFile.createNew(loader), fb=LogisimFile.createNew(new Loader(null));
   Project a=new Project(fa), b=new Project(fb);
   Library gates=loader.loadLibrary("#Gates"); fa.addLibrary(gates); fb.addLibrary(fb.getLoader().loadLibrary("#Gates"));
   ComponentFactory factory=((AddTool)gates.getTool("AND Gate")).getFactory();
   List<String> captured=new ArrayList<>();
   a.addProjectListener(e -> { if(e.getAction()==ProjectEvent.ACTION_COMPLETE) {
    for(Component c:fa.getMainCircuit().getNonWires()) captured.add(c.getFactory().getName()+":"+c.getLocation());
   }});
   CircuitMutation edit=new CircuitMutation(fa.getMainCircuit());
   edit.add(factory.createComponent(Location.create(120,100),factory.createAttributeSet()));
   a.doAction(edit.toAction(null));
   check(captured.equals(Arrays.asList("AND Gate:(120,100)")),"capture after original action");
   Component original=fa.getMainCircuit().getNonWires().iterator().next();
   ComponentFactory remote=((AddTool)fb.getLibraries().get(0).getTool(original.getFactory().getName())).getFactory();
   CircuitMutation replay=new CircuitMutation(fb.getMainCircuit());
   replay.add(remote.createComponent(Location.create(120,100),(AttributeSet)original.getAttributeSet().clone()));
   b.doAction(replay.toAction(null));
   check(fb.getMainCircuit().getNonWires().size()==1,"remote gate");
   try { check(Arrays.equals(ProjectIO.write(fa),ProjectIO.write(fb)),"serialized convergence"); }
   catch(Exception e) {throw new RuntimeException(e);}
   a.undoAction(); check(fa.getMainCircuit().getNonWires().isEmpty(),"original inverse transaction");
   System.out.println("PASS: original action capture, independent replay, serialized convergence, inverse transaction");
   a.getSimulator().shutDown(); b.getSimulator().shutDown();
  });
 }
}
