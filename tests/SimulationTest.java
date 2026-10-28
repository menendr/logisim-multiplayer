package com.logisim.multiplayer;
import com.cburch.logisim.circuit.*;
import com.cburch.logisim.comp.*;
import com.cburch.logisim.data.*;
import com.cburch.logisim.file.*;
import com.cburch.logisim.proj.*;
import com.cburch.logisim.std.wiring.Pin;
import com.google.gson.*;
import java.io.*;
/** Truth-table check before/after standard .circ serialization, using the original simulator. */
public class SimulationTest {
 static Component component(LogisimFile f,String type,int x,int y,JsonObject attrs){JsonObject d=new JsonObject();d.addProperty("circuit","main");d.addProperty("type",type);d.addProperty("x",x);d.addProperty("y",y);d.add("attrs",attrs);return Model.component(f,d);}
 static void truth(LogisimFile file){
  Project p=new Project(file);p.getSimulator().setIsRunning(false);Circuit c=file.getMainCircuit();CircuitState state=p.getCircuitState();
  Component[] inputs=c.getNonWires().stream().filter(x->x.getFactory()==Pin.FACTORY&&!x.getAttributeSet().getValue(Pin.ATTR_TYPE)).sorted((a,b)->Integer.compare(a.getLocation().getY(),b.getLocation().getY())).toArray(Component[]::new);
  Component output=c.getNonWires().stream().filter(x->x.getFactory()==Pin.FACTORY&&x.getAttributeSet().getValue(Pin.ATTR_TYPE)).findFirst().orElseThrow();
  try{for(int a=0;a<2;a++)for(int b=0;b<2;b++){
   Pin.FACTORY.setValue(state.getInstanceState(inputs[0]),a==1?Value.TRUE:Value.FALSE);Pin.FACTORY.setValue(state.getInstanceState(inputs[1]),b==1?Value.TRUE:Value.FALSE);
   state.markComponentAsDirty(inputs[0]);state.markComponentAsDirty(inputs[1]);state.getPropagator().propagate();
   Value result=state.getValue(output.getLocation());if(result!=(a==1&&b==1?Value.TRUE:Value.FALSE))throw new AssertionError("AND truth table failed: "+a+","+b+" -> "+result);
  }}finally{p.getSimulator().shutDown();}
 }
 public static void main(String[] args)throws Exception{
  LogisimFile f=Model.blank();JsonObject ga=new JsonObject();ga.addProperty("inputs","2");Component gate=component(f,"Gates/AND Gate",200,100,ga);Component ia=component(f,"Wiring/Pin",100,gate.getEnd(1).getLocation().getY(),new JsonObject()),ib=component(f,"Wiring/Pin",100,gate.getEnd(2).getLocation().getY(),new JsonObject());JsonObject oa=new JsonObject();oa.addProperty("output","true");Component out=component(f,"Wiring/Pin",260,100,oa);
  CircuitMutation m=new CircuitMutation(f.getMainCircuit());m.add(gate);m.add(ia);m.add(ib);m.add(out);m.add(Wire.create(ia.getLocation(),gate.getEnd(1).getLocation()));m.add(Wire.create(ib.getLocation(),gate.getEnd(2).getLocation()));m.add(Wire.create(gate.getEnd(0).getLocation(),out.getLocation()));m.execute();
  truth(f);JsonObject canonical=Model.capture(f);LogisimFile replay=Model.materialize(canonical);truth(replay);
  byte[] circ=ProjectIO.write(replay);java.nio.file.Path artifact=java.nio.file.Path.of("build/and-truth-table.circ");java.nio.file.Files.createDirectories(artifact.getParent());java.nio.file.Files.write(artifact,circ);LogisimFile vanilla=LogisimFile.load(new ByteArrayInputStream(circ),new Model.StrictLoader());truth(vanilla);
  if(!Model.capture(vanilla).equals(canonical))throw new AssertionError("Export changed model");
  System.out.println("PASS: AND truth table (4 combinations) in original, replayed and vanilla-reopened models");
 }
}
