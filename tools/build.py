import os,pathlib,subprocess,zipfile,shutil
root=pathlib.Path(__file__).resolve().parent.parent
os.chdir(root)
out=root/'build/classes'; out.mkdir(parents=True,exist_ok=True)
stage=root/'build/source'; stage.mkdir(parents=True,exist_ok=True)
for p in (root/'client/src').rglob('*.java'):
    target=stage/p.relative_to(root/'client/src'); target.parent.mkdir(parents=True,exist_ok=True)
    original=root/'client/upstream-original'/p.relative_to(root/'client/src')
    target.write_bytes((original if original.exists() else p).read_bytes().removeprefix(b'\xef\xbb\xbf'))
sources=sorted(stage.rglob('*.java'))
(root/'build/sources.txt').write_text('\n'.join('"'+str(p).replace('\\','/')+'"' for p in sources))
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',os.pathsep.join(str(p) for p in (root/'client/libs').glob('*.jar')),'-d',str(out),'@build/sources.txt'],check=True)
with zipfile.ZipFile(root/'build/Logisim-Original-2.7.1.jar','w',zipfile.ZIP_DEFLATED) as jar:
    jar.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nMain-Class: com.cburch.logisim.Main\n\n')
    entries=set(['META-INF/MANIFEST.MF'])
    for lib in (root/'client/libs').glob('*.jar'):
        with zipfile.ZipFile(lib) as z:
            for n in z.namelist():
                if not n.startswith('META-INF/') and not n.endswith('/') and n not in entries:
                    jar.writestr(n,z.read(n)); entries.add(n)
    for base,prefix in [(out,''),(root/'client/src/resources','resources/'),(stage,'src/')]:
        for p in base.rglob('*'):
            if p.is_file():
                n=prefix+p.relative_to(base).as_posix()
                if n not in entries: jar.write(p,n); entries.add(n)
    jar.write(root/'client/scripts/data/COPYING.TXT','COPYING.TXT')
print('Built original JAR')
