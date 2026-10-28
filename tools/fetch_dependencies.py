import urllib.request,pathlib
libs={'gson-2.11.0.jar':'com/google/code/gson/gson/2.11.0/gson-2.11.0.jar','Java-WebSocket-1.5.7.jar':'org/java-websocket/Java-WebSocket/1.5.7/Java-WebSocket-1.5.7.jar','slf4j-api-2.0.16.jar':'org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar','slf4j-simple-2.0.16.jar':'org/slf4j/slf4j-simple/2.0.16/slf4j-simple-2.0.16.jar'}
for name,path in libs.items():
 urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/'+path,pathlib.Path('lib')/name)
