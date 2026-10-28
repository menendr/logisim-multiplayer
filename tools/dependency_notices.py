import pathlib,zipfile,hashlib,json,urllib.request
pathlib.Path('licenses').mkdir(exist_ok=True)
urls={'Apache-2.0.txt':'https://raw.githubusercontent.com/google/gson/gson-parent-2.11.0/LICENSE','Java-WebSocket-MIT.txt':'https://raw.githubusercontent.com/TooTallNate/Java-WebSocket/v1.5.7/LICENSE','SLF4J-MIT.txt':'https://raw.githubusercontent.com/qos-ch/slf4j/v_2.0.16/LICENSE.txt'}
for name,url in urls.items():
 try:pathlib.Path('licenses',name).write_bytes(urllib.request.urlopen(url,timeout=30).read())
 except Exception as e:print(name,type(e).__name__)
manifest={p.as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for base in ['lib','client/libs'] for p in pathlib.Path(base).glob('*.jar')}
pathlib.Path('docs/dependency-sha256.json').write_text(json.dumps(manifest,indent=2))
lines=['# Dependency archive inventory','', 'Exact checksums: dependency-sha256.json. Added binaries come from Maven Central via tools/fetch_dependencies.py. Dependency source archives are available from the same Maven coordinates with -sources.jar.', '']
for p in pathlib.Path('client/libs').glob('*.jar'):
 with zipfile.ZipFile(p) as z:
  notices=[n for n in z.namelist() if any(s in n.lower() for s in ['license','copying','notice','readme'])]
  lines.extend(['## '+p.name,'',*['- '+n for n in notices],''])
  for n in notices:
   if not n.endswith('/'):pathlib.Path('licenses',p.stem+'-'+pathlib.Path(n).name).write_bytes(z.read(n))
pathlib.Path('docs/dependencies.md').write_text('\n'.join(lines))
