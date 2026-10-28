package com.logisim.multiplayer;

import com.google.gson.*;
import com.cburch.logisim.circuit.*;
import com.cburch.logisim.comp.*;
import com.cburch.logisim.data.*;
import com.cburch.logisim.file.*;
import com.cburch.logisim.tools.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Model adapter for classic 2.7.1. GPL-2.0-or-later. No Swing or Java object deserialization. */
public final class Model {
 public static final int MAX_ENTRIES=20000, MAX_CHANGES=4000;
 public static final Gson JSON=new Gson();
 private Model() {}
 public static class StrictLoader extends Loader {
  public StrictLoader(){super(null);}
  @Override public void showError(String msg){throw new IllegalArgumentException("Invalid Logisim project");}
  @Override public Library loadLibrary(String desc){
   if(!desc.matches("#(Base|Gates|Wiring|Plexers|Arithmetic|Memory|Input/Output|I/O|IO)")) throw new IllegalArgumentException("External libraries are disabled in collaboration");
   return super.loadLibrary(desc);
  }
 }
 public static LogisimFile blank(){
  Loader l=new StrictLoader(); LogisimFile f=LogisimFile.createNew(l);
  for(Library lib:l.getBuiltin().getLibraries()) f.addLibrary(l.loadLibrary("#"+lib.getName()));
  return f;
 }
 @SuppressWarnings({"rawtypes","unchecked"})
 public static JsonObject attrs(AttributeSet set){
  JsonObject out=new JsonObject(); TreeMap<String,String> sorted=new TreeMap<>();
  for(Attribute attr:set.getAttributes()) {
   Object v=set.getValue(attr);
   if(v!=null && set.isToSave(attr)) sorted.put(attr.getName(),attr.toStandardString(v));
  }
  sorted.forEach(out::addProperty); return out;
 }
 public static String circuitKey(String name){return "c/"+Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));}
 public static String factoryName(LogisimFile f,ComponentFactory factory){
  for(Circuit c:f.getCircuits()) if(c.getSubcircuitFactory()==factory) return "@"+c.getName();
  for(Library lib:f.getLoader().getBuiltin().getLibraries()) for(Tool t:lib.getTools())
   if(t instanceof AddTool && ((AddTool)t).getFactory()==factory) return lib.getName()+"/"+factory.getName();
  throw new IllegalArgumentException("External component libraries are unsupported");
 }
 public static JsonObject describe(LogisimFile f,String circuit,Component c){
  JsonObject d=new JsonObject(); d.addProperty("circuit",circuit);
  if(c instanceof Wire){
   Wire w=(Wire)c; d.addProperty("type","wire"); d.addProperty("x",w.getEnd0().getX()); d.addProperty("y",w.getEnd0().getY()); d.addProperty("x2",w.getEnd1().getX()); d.addProperty("y2",w.getEnd1().getY());
  } else {
   d.addProperty("type",factoryName(f,c.getFactory())); d.addProperty("x",c.getLocation().getX()); d.addProperty("y",c.getLocation().getY()); d.add("attrs",attrs(c.getAttributeSet()));
  }
  return d;
 }
 public static String objectKey(JsonObject d){
  String p=circuitKey(str(d,"circuit"))+"/"+str(d,"type")+"/"+integer(d,"x")+","+integer(d,"y");
  return str(d,"type").equals("wire")?p+"/"+integer(d,"x2")+","+integer(d,"y2"):p;
 }
 public static JsonObject capture(LogisimFile f){
  JsonObject s=new JsonObject(); s.addProperty("main",f.getMainCircuit().getName());
  for(Library lib:f.getLibraries()) if(!f.getLoader().getBuiltin().getLibraries().contains(lib)) throw new IllegalArgumentException("External libraries are unsupported");
  for(Circuit c:f.getCircuits()) {
   if(!c.getAppearance().isDefaultAppearance()) throw new IllegalArgumentException("Custom circuit appearances are unsupported in collaboration");
   JsonObject d=new JsonObject(); d.addProperty("name",c.getName()); d.add("attrs",attrs(c.getStaticAttributes())); s.add(circuitKey(c.getName()),d);
   for(Component comp:c.getNonWires()){JsonObject o=describe(f,c.getName(),comp); putUnique(s,objectKey(o),o);}
   for(Wire w:c.getWires()){JsonObject o=describe(f,c.getName(),w); putUnique(s,objectKey(o),o);}
  }
  return s;
 }
 static void putUnique(JsonObject s,String k,JsonObject d){if(s.has(k)) throw new IllegalArgumentException("Overlapping identical components are unsupported"); s.add(k,d);}
 public static JsonArray diff(JsonObject before,JsonObject after){
  TreeSet<String> keys=new TreeSet<>(before.keySet()); keys.addAll(after.keySet()); JsonArray changes=new JsonArray();
  for(String k:keys) if(!Objects.equals(before.get(k),after.get(k))){JsonObject c=new JsonObject(); c.addProperty("key",k); c.add("before",before.has(k)?before.get(k):JsonNull.INSTANCE); c.add("after",after.has(k)?after.get(k):JsonNull.INSTANCE); changes.add(c);}
  if(changes.size()>MAX_CHANGES) throw new IllegalArgumentException("Transaction too large"); return changes;
 }
 public static JsonObject patched(JsonObject state,JsonArray changes){
  if(changes.size()==0 || changes.size()>MAX_CHANGES) throw new IllegalArgumentException("Invalid transaction size");
  JsonObject next=state.deepCopy(); Set<String> seen=new HashSet<>();
  for(JsonElement e:changes){JsonObject c=e.getAsJsonObject(); String k=str(c,"key");
   if(k.length()>400 || !seen.add(k) || !c.has("before") || !c.has("after")) throw new IllegalArgumentException("Malformed transaction");
   JsonElement current=state.has(k)?state.get(k):JsonNull.INSTANCE;
   if(!current.equals(c.get("before"))) throw new IllegalArgumentException("Conflict: a target changed; your edit was not accepted");
   if(c.get("after").isJsonNull()) next.remove(k); else next.add(k,c.get("after").deepCopy());
  }
  validate(next); return next;
 }
 public static String str(JsonObject o,String k){JsonElement v=o.get(k); if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected string: "+k); return v.getAsString();}
 public static int integer(JsonObject o,String k){JsonElement v=o.get(k); if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer"); int n=v.getAsInt(); if(!v.getAsString().equals(Integer.toString(n)) || Math.abs((long)n)>100000) throw new IllegalArgumentException("Invalid coordinate"); return n;}
 static void name(String n){if(!n.matches("[A-Za-z][A-Za-z0-9_ -]{0,63}"))throw new IllegalArgumentException("Circuit names must use 1-64 ASCII letters, digits, spaces, underscores or hyphens, starting with a letter");}
 static void validateAttrs(JsonObject a){
  if(a.size()>100) throw new IllegalArgumentException("Too many attributes");
  for(String k:a.keySet()) {
   String value=str(a,k);if(k.length()>100 || value.length()>65536) throw new IllegalArgumentException("Attribute too large");
   if(k.equals("contents")){
    String header=value.split("\n",2)[0];String[] parts=header.split(" ");
    if(parts.length!=3 || !parts[0].equals("addr/data:") || !parts[1].matches("[0-9]{1,2}") || !parts[2].matches("[0-9]{1,2}") || Integer.parseInt(parts[1])<2 || Integer.parseInt(parts[1])>16 || Integer.parseInt(parts[2])<1 || Integer.parseInt(parts[2])>32)throw new IllegalArgumentException("Invalid or oversized ROM contents");
   }
   if(k.toLowerCase(java.util.Locale.ROOT).contains("addrwidth") && (!value.matches("[0-9]{1,2}") || Integer.parseInt(value)>16)) throw new IllegalArgumentException("Memory address width is limited to 16 bits in collaboration");
  }
 }
 public static void validate(JsonObject s){
  if(s.size()>MAX_ENTRIES || s.toString().getBytes(StandardCharsets.UTF_8).length>1500000) throw new IllegalArgumentException("Project too large");
  Map<String,Set<String>> graph=new HashMap<>();
  for(String k:s.keySet()) if(k.startsWith("c/") && !k.substring(2).contains("/")){
   JsonObject c=s.getAsJsonObject(k); String n=str(c,"name"); name(n); if(!k.equals(circuitKey(n)))throw new IllegalArgumentException("Circuit key mismatch");
   validateAttrs(c.getAsJsonObject("attrs")); if(!n.equals(str(c.getAsJsonObject("attrs"),"circuit")))throw new IllegalArgumentException("Circuit name mismatch"); graph.put(n,new HashSet<>());
  }
  if(graph.isEmpty() || graph.size()>64 || !graph.containsKey(str(s,"main")))throw new IllegalArgumentException("Missing main circuit");
  long wireLength=0;
  for(String k:s.keySet()) {
   if(k.equals("main") || graph.keySet().stream().anyMatch(n->k.equals(circuitKey(n))))continue;
   JsonObject d=s.getAsJsonObject(k); String n=str(d,"circuit"),type=str(d,"type");
   if(!graph.containsKey(n) || !k.equals(objectKey(d)))throw new IllegalArgumentException("Object key mismatch");
   int x=integer(d,"x"),y=integer(d,"y");
   if(type.equals("wire")){int x2=integer(d,"x2"),y2=integer(d,"y2"); if((x!=x2 && y!=y2)||(x==x2 && y==y2))throw new IllegalArgumentException("Wire must be nonzero and axis-aligned");long length=Math.abs((long)x-x2)+Math.abs((long)y-y2);wireLength+=length;if(length>20000||wireLength>500000)throw new IllegalArgumentException("Wire geometry exceeds resource limits");}
   else {validateAttrs(d.getAsJsonObject("attrs")); if(type.startsWith("@")){String dep=type.substring(1); if(!graph.containsKey(dep))throw new IllegalArgumentException("Missing subcircuit"); graph.get(n).add(dep);}}
  }
  Set<String> done=new HashSet<>(); for(String n:graph.keySet()) visit(n,graph,new HashSet<>(),done);
 }
 static void visit(String n,Map<String,Set<String>> graph,Set<String> path,Set<String> done){if(done.contains(n))return; if(!path.add(n))throw new IllegalArgumentException("Recursive subcircuit"); for(String dep:graph.get(n))visit(dep,graph,path,done); path.remove(n); done.add(n);}
 @SuppressWarnings({"unchecked","rawtypes"})
 static void setAttrs(AttributeSet set,JsonObject data){
  // Some attributes (such as number of inputs) change the attribute list. Two passes resolve these dependencies.
  Set<String> applied=new HashSet<>();
  for(int pass=0;pass<3;pass++)for(String k:data.keySet()){
   Attribute a=set.getAttribute(k); if(a!=null && !applied.contains(k)) {String text=str(data,k); Object v=a.parse(text); if(v==null)throw new IllegalArgumentException("Invalid attribute"); set.setValue(a,v); applied.add(k);}
  }
  if(applied.size()!=data.size())throw new IllegalArgumentException("Unknown attribute");
 }
 static ComponentFactory factory(LogisimFile f,String type){
  if(type.startsWith("@")){Circuit c=f.getCircuit(type.substring(1)); if(c==null)throw new IllegalArgumentException("Missing subcircuit"); return c.getSubcircuitFactory();}
  for(Library lib:f.getLoader().getBuiltin().getLibraries())for(Tool tool:lib.getTools())if(tool instanceof AddTool){ComponentFactory cf=((AddTool)tool).getFactory(); if((lib.getName()+"/"+cf.getName()).equals(type))return cf;}
  throw new IllegalArgumentException("Unknown component factory");
 }
 public static Component component(LogisimFile f,JsonObject d){
  if(str(d,"type").equals("wire")) return Wire.create(Location.create(integer(d,"x"),integer(d,"y")),Location.create(integer(d,"x2"),integer(d,"y2")));
  ComponentFactory cf=factory(f,str(d,"type")); AttributeSet a=cf.createAttributeSet(); setAttrs(a,d.getAsJsonObject("attrs")); return cf.createComponent(Location.create(integer(d,"x"),integer(d,"y")),a);
 }
 /** Apply a validated target through original CircuitMutation APIs; retain unchanged component identities. */
 public static void apply(LogisimFile f,JsonObject target){
  validate(target); JsonObject before=capture(f);
  for(String k:target.keySet())if(k.startsWith("c/")&&!k.substring(2).contains("/")){
   String n=str(target.getAsJsonObject(k),"name"); if(f.getCircuit(n)==null)f.addCircuit(new Circuit(n));
  }
  for(Circuit c:new ArrayList<>(f.getCircuits())){
   CircuitMutation m=new CircuitMutation(c); Map<String,Component> existing=new HashMap<>();
   for(Component comp:new ArrayList<>(c.getNonWires()))existing.put(objectKey(describe(f,c.getName(),comp)),comp);
   for(Wire w:new ArrayList<>(c.getWires()))existing.put(objectKey(describe(f,c.getName(),w)),w);
   for(Map.Entry<String,Component> e:existing.entrySet())if(!Objects.equals(before.get(e.getKey()),target.get(e.getKey()))){
    if(target.has(e.getKey()))m.replace(e.getValue(),component(f,target.getAsJsonObject(e.getKey())));else m.remove(e.getValue());
   }
   for(String k:target.keySet())if(target.get(k).isJsonObject()){
    JsonObject d=target.getAsJsonObject(k); if(d.has("type") && str(d,"circuit").equals(c.getName()) && !existing.containsKey(k) && !Objects.equals(before.get(k),d))m.add(component(f,d));
   }
   if(!m.isEmpty())m.execute();
   String ck=circuitKey(c.getName());
   if(target.has(ck)) {JsonObject a=target.getAsJsonObject(ck).getAsJsonObject("attrs"); if(!Objects.equals(before.get(ck),target.get(ck)))setAttrs(c.getStaticAttributes(),a);}
  }
  for(Circuit c:new ArrayList<>(f.getCircuits()))if(!target.has(circuitKey(c.getName())))f.removeCircuit(c);
  f.setMainCircuit(f.getCircuit(str(target,"main")));
  if(!capture(f).equals(target))throw new IllegalArgumentException("Logisim model did not reproduce transaction exactly");
 }
 public static LogisimFile materialize(JsonObject state){LogisimFile f=blank(); apply(f,state); return f;}
 public static void checkJsonDepth(String text){
  int depth=0;boolean quoted=false,escape=false;
  for(int i=0;i<text.length();i++){char ch=text.charAt(i);if(quoted){if(escape)escape=false;else if(ch==92)escape=true;else if(ch==34)quoted=false;}else if(ch==34)quoted=true;else if(ch==123||ch==91){if(++depth>32)throw new IllegalArgumentException("Excessive JSON nesting");}else if(ch==125||ch==93)depth--;}
 }
 public static String canonical(JsonElement e){
  if(e.isJsonObject()){TreeMap<String,JsonElement> sorted=new TreeMap<>(); e.getAsJsonObject().entrySet().forEach(x->sorted.put(x.getKey(),x.getValue())); StringJoiner j=new StringJoiner(",","{","}"); sorted.forEach((k,v)->j.add(JSON.toJson(k)+":"+canonical(v))); return j.toString();}
  if(e.isJsonArray()){StringJoiner j=new StringJoiner(",","[","]"); e.getAsJsonArray().forEach(v->j.add(canonical(v)));return j.toString();} return e.toString();
 }
 public static String hash(JsonElement e){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(e).getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
