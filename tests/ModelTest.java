package com.logisim.multiplayer;
import com.google.gson.*;
import com.cburch.logisim.file.*;
import com.cburch.logisim.circuit.*;
import com.cburch.logisim.comp.*;
import com.cburch.logisim.data.*;
import java.io.*;
public class ModelTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception{
  LogisimFile f=Model.blank(); JsonObject base=Model.capture(f); System.out.println(base);
  JsonObject gate=new JsonObject();gate.addProperty("circuit","main");gate.addProperty("type","Gates/AND Gate");gate.addProperty("x",120);gate.addProperty("y",100);gate.add("attrs",new JsonObject());
  CircuitMutation m=new CircuitMutation(f.getMainCircuit());m.add(Model.component(f,gate));m.execute();
  JsonObject after=Model.capture(f);JsonArray delta=Model.diff(base,after);
  JsonObject next=Model.patched(base,delta);LogisimFile b=Model.materialize(next);check(Model.capture(b).equals(after),"replay");
  byte[] circ=ProjectIO.write(b); LogisimFile reopened=LogisimFile.load(new ByteArrayInputStream(circ),new Model.StrictLoader());check(Model.capture(reopened).equals(after),"vanilla round trip");
  Model.apply(b,base);check(Model.capture(b).equals(base),"reconcile");
  try{Model.patched(next,delta);throw new AssertionError("duplicate must conflict");}catch(IllegalArgumentException expected){}
  System.out.println("PASS: model delta, replay, vanilla .circ round trip, reconciliation, conflict");
 }
}
