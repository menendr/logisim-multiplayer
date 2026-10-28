package com.logisim.multiplayer;
import com.google.gson.*;
import java.net.URI;
import java.util.*;
import java.util.prefs.*;
/** Local connection profiles. UUID identifies personal history, not an access credential. */
final class SavedServers {
 static final String LIST="savedServersV1", LAST="lastServerProfile";
 final Preferences prefs;
 record Profile(String address,String name,String clientId){
  String label(){return address.replaceFirst("^wss?://","")+" ("+name+")";}
 }
 SavedServers(Preferences prefs){
  this.prefs=prefs;
  if(prefs.get(LIST,null)==null&&prefs.get("server",null)!=null){
   try{remember(prefs.get("server",""),prefs.get("nickname","Student"));}catch(IllegalArgumentException ignored){/* An invalid old address is not imported. */}
  }
 }
 static String address(String text){
  try{URI u=Collaboration.serverUri(text);String path=u.getPath();if(path==null||path.equals("/"))path="";
   String value=new URI(u.getScheme(),null,u.getHost().toLowerCase(Locale.ROOT),u.getPort(),path,null,null).toString();
   if(value.length()>512)throw new IllegalArgumentException("Server address is too long");return value;
  }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Enter a valid server address",e);}
 }
 static String name(String value){String n=value.trim();if(n.isEmpty()||n.length()>40||n.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Name must contain 1-40 characters");return n;}
 List<Profile> list(){
  List<Profile> result=new ArrayList<>();
  try{String raw=prefs.get(LIST,"[]");if(raw.length()>Preferences.MAX_VALUE_LENGTH)return result;
   for(JsonElement e:JsonParser.parseString(raw).getAsJsonArray()){
    JsonObject o=e.getAsJsonObject();String a=address(Model.str(o,"address")),n=name(Model.str(o,"name")),id=Model.str(o,"clientId");
    if(!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))continue;
    if(result.stream().noneMatch(p->p.address().equals(a)&&p.name().equals(n)))result.add(new Profile(a,n,id));if(result.size()==8)break;
   }
  }catch(RuntimeException invalid){/* Keep any valid preceding profiles; never use malformed stored values. */}
  return result;
 }
 Profile last(){List<Profile> all=list();String id=prefs.get(LAST,"");return all.stream().filter(p->p.clientId().equals(id)).findFirst().orElse(all.isEmpty()?null:all.get(0));}
 Profile remember(String endpoint,String nickname){
  String a=address(endpoint),n=name(nickname);List<Profile> all=list();Profile profile=all.stream().filter(p->p.address().equals(a)&&p.name().equals(n)).findFirst().orElse(new Profile(a,n,UUID.randomUUID().toString()));
  all.removeIf(p->p.clientId().equals(profile.clientId()));all.add(0,profile);while(all.size()>8)all.remove(all.size()-1);
  write(all,profile);return profile;
 }
 void forget(Profile profile){List<Profile> all=list();all.removeIf(p->p.clientId().equals(profile.clientId()));Profile previous=last();Profile next=all.stream().filter(p->previous!=null&&p.clientId().equals(previous.clientId())).findFirst().orElse(all.isEmpty()?null:all.get(0));write(all,next);}
 void write(List<Profile> all,Profile last){
  JsonArray data=new JsonArray();for(Profile p:all){JsonObject o=new JsonObject();o.addProperty("address",p.address());o.addProperty("name",p.name());o.addProperty("clientId",p.clientId());data.add(o);}
  String json=data.toString();if(json.length()>Preferences.MAX_VALUE_LENGTH)throw new IllegalArgumentException("Saved server addresses are too long. Forget a server or shorten its address.");prefs.put(LIST,json);
  if(last!=null){prefs.put(LAST,last.clientId());prefs.put("server",last.address());prefs.put("nickname",last.name());}
  else{prefs.remove(LAST);prefs.remove("server");prefs.remove("nickname");}
  try{prefs.flush();}catch(BackingStoreException e){throw new IllegalStateException("Could not save server settings on this computer",e);}
 }
}
