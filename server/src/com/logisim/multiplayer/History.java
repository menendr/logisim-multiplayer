package com.logisim.multiplayer;
import com.google.gson.*;
import java.util.*;
/** Persistent per-client undo/redo. Partner touches invalidate entries, including ABA changes. */
public final class History {
 private History(){}
 static JsonObject all(JsonObject record){if(!record.has("history"))record.add("history",new JsonObject());return record.getAsJsonObject("history");}
 static JsonObject stacks(JsonObject record,String client){JsonObject h=all(record);if(!h.has(client)){JsonObject s=new JsonObject();s.add("undo",new JsonArray());s.add("redo",new JsonArray());h.add(client,s);}return h.getAsJsonObject(client);}
 static JsonObject top(JsonObject record,String client,String kind){JsonObject h=all(record);if(!h.has(client))return null;JsonArray a=h.getAsJsonObject(client).getAsJsonArray(kind);return a.isEmpty()?null:a.get(a.size()-1).getAsJsonObject();}
 static boolean blocked(JsonObject e){return e!=null&&e.has("blocked")&&e.get("blocked").getAsBoolean();}
 public static JsonArray changes(JsonObject record,String client,String kind){JsonObject entry=top(record,client,kind);if(entry==null)throw new IllegalArgumentException("Nothing to "+kind);if(blocked(entry))throw new IllegalArgumentException("Cannot "+kind+": your partner changed an affected object");JsonArray original=entry.getAsJsonArray("changes");if(kind.equals("redo"))return original.deepCopy();JsonArray inverse=new JsonArray();for(JsonElement e:original){JsonObject c=e.getAsJsonObject(),v=new JsonObject();v.add("key",c.get("key"));v.add("before",c.get("after"));v.add("after",c.get("before"));inverse.add(v);}return inverse;}
 public static void committed(JsonObject record,String client,String kind,JsonArray changes,long revision){
  Set<String> keys=new HashSet<>();changes.forEach(e->keys.add(Model.str(e.getAsJsonObject(),"key")));
  for(var owner:all(record).entrySet())if(!owner.getKey().equals(client))for(String stack:List.of("undo","redo"))for(JsonElement element:owner.getValue().getAsJsonObject().getAsJsonArray(stack)){
   JsonObject entry=element.getAsJsonObject();for(JsonElement c:entry.getAsJsonArray("changes"))if(keys.contains(Model.str(c.getAsJsonObject(),"key"))){entry.addProperty("blocked",true);break;}
  }
  JsonObject own=stacks(record,client);JsonObject entry;
  if(kind.equals("edit")){entry=new JsonObject();entry.add("changes",changes.deepCopy());own.add("redo",new JsonArray());}
  else {JsonArray source=own.getAsJsonArray(kind);entry=source.remove(source.size()-1).getAsJsonObject();}
  entry.addProperty("revision",revision);String destination=kind.equals("undo")?"redo":"undo";own.getAsJsonArray(destination).add(entry);
  while(all(record).toString().length()>4000000 || count(record)>100){
   JsonArray oldest=null;long min=Long.MAX_VALUE;
   for(var owner:all(record).entrySet())for(String stack:List.of("undo","redo")){
    JsonArray a=owner.getValue().getAsJsonObject().getAsJsonArray(stack);if(!a.isEmpty()&&a.get(0).getAsJsonObject().get("revision").getAsLong()<min){min=a.get(0).getAsJsonObject().get("revision").getAsLong();oldest=a;}
   }
   if(oldest==null)break;oldest.remove(0);
  }
  List<String> empty=new ArrayList<>();for(var e:all(record).entrySet()){JsonObject s=e.getValue().getAsJsonObject();if(s.getAsJsonArray("undo").isEmpty()&&s.getAsJsonArray("redo").isEmpty())empty.add(e.getKey());}empty.forEach(k->all(record).remove(k));
 }
 static int count(JsonObject record){int n=0;for(var e:all(record).entrySet())for(String kind:List.of("undo","redo"))n+=e.getValue().getAsJsonObject().getAsJsonArray(kind).size();return n;}
 public static JsonObject availability(JsonObject record){JsonObject result=new JsonObject();for(String client:all(record).keySet()){JsonObject flags=new JsonObject();for(String kind:List.of("undo","redo")){JsonObject e=top(record,client,kind);flags.addProperty(kind,e!=null&&!blocked(e));flags.addProperty(kind+"Reason",e==null?"Nothing to "+kind:blocked(e)?"Your partner changed an affected object":"");}result.add(client,flags);}return result;}
}
