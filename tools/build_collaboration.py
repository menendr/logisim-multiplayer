"""Build dependency-complete JARs using Python 3.9+ and JDK 17+. No Maven required."""
import pathlib,subprocess,os,zipfile,shutil,sys,re
root=pathlib.Path(__file__).resolve().parent.parent
os.chdir(root)
stage=root/'build/multiplayer-source';out=root/'build/multiplayer-classes'
if out.exists():shutil.rmtree(out)
out.mkdir(parents=True,exist_ok=True)
for p in pathlib.Path('client/src').rglob('*.java'):
 target=stage/p.relative_to('client/src');target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(p.read_bytes().removeprefix(b'\xef\xbb\xbf'))
sources=list(stage.rglob('*.java'))+list(pathlib.Path('shared/src').rglob('*.java'))+list(pathlib.Path('server/src').rglob('*.java'))+list(pathlib.Path('tests').glob('*.java'))+list(pathlib.Path('client/collaboration').rglob('*.java'))
pathlib.Path('build/multiplayer-sources.txt').write_text('\n'.join('"'+str(p.resolve()).replace('\\','/')+'"' for p in sources))
import hashlib,json
manifest=json.loads((root/'docs/dependency-sha256.json').read_text()) if (root/'docs/dependency-sha256.json').exists() else {}
for name,digest in manifest.items():
 if hashlib.sha256((root/name).read_bytes()).hexdigest()!=digest:raise SystemExit('Dependency checksum mismatch: '+name)
cp=os.pathsep.join([str(root/'client/libs/*'),str(root/'lib/*')])
subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',cp,'-d',str(out),'@build/multiplayer-sources.txt'],check=True)
helpdir=root/'build/help'
if helpdir.exists():shutil.rmtree(helpdir)
sys.path.insert(0,str(root/'client/scripts'))
import copy_doc
copy_doc.copy_files_all_locales(str(root/'client/doc'),str(helpdir))
copy_doc.build_contents(str(root/'client/doc'),str(helpdir))
copy_doc.build_map(str(root/'client/doc'),str(helpdir))
copy_doc.build_helpset(str(root/'client/doc'),str(helpdir))
for locale in copy_doc.get_doc_locales(str(root/'client/doc')):
 subprocess.run(['java','-jar',str(root/'client/scripts/data/javahelp/bin/jhindexer.jar'),'-db','search_lookup_'+locale,'-locale',locale,locale+'/html/guide',locale+'/html/libs'],cwd=helpdir,check=True,stdout=subprocess.DEVNULL)
pathlib.Path('dist').mkdir(exist_ok=True)
def package(name,main,server):
 with zipfile.ZipFile('dist/'+name,'w',zipfile.ZIP_DEFLATED) as jar:
  entries=set()
  def write(n,data):
   if n not in entries:jar.writestr(n,data);entries.add(n)
  write('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nMain-Class: '+main+'\n\n')
  for lib in list(pathlib.Path('client/libs').glob('*.jar'))+list(pathlib.Path('lib').glob('*.jar')):
   with zipfile.ZipFile(lib) as z:
    for n in z.namelist():
     if not n.endswith('/') and not n.upper().endswith(('.SF','.RSA','.DSA')) and n!='META-INF/MANIFEST.MF': write(n,z.read(n))
  for p in out.rglob('*.class'):
   n=p.relative_to(out).as_posix()
   if re.search(r'/(?:[A-Za-z]*Test|FeasibilityProof)(?:\$|\.class)',n):continue
   if not server and n in ['com/logisim/multiplayer/Server.class','com/logisim/multiplayer/Store.class']:continue
   write(n,p.read_bytes())
  for p in pathlib.Path('client/src/resources').rglob('*'):
   if p.is_file():write('resources/'+p.relative_to('client/src/resources').as_posix(),p.read_bytes())
  # Complete corresponding source, including modifications, travels with each executable.
  for base in ['client/src','client/collaboration','shared/src','server/src']:
   for p in pathlib.Path(base).rglob('*.java'):write('source/'+p.as_posix(),p.read_bytes())
  write('COPYING.TXT',pathlib.Path('client/scripts/data/COPYING.TXT').read_bytes())
  if pathlib.Path('THIRD_PARTY.md').exists():write('THIRD_PARTY.md',pathlib.Path('THIRD_PARTY.md').read_bytes())
  for base,prefix in [(helpdir,'doc/'),(root/'licenses','licenses/'),(root/'third_party','third_party/')]:
   for p in base.rglob('*'):
    if p.is_file():write(prefix+p.relative_to(base).as_posix(),p.read_bytes())
package('Logisim-Multiplayer.jar','com.cburch.logisim.Main',False)
package('Logisim-Multiplayer-Server.jar','com.logisim.multiplayer.Server',True)
print('Built dist/Logisim-Multiplayer.jar and dist/Logisim-Multiplayer-Server.jar')
