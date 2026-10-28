import concurrent.futures,json,pathlib,hashlib
from fetch_upstream import fetch
pending=['doc/'];count=0
with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:
 while pending:
  results=list(pool.map(fetch,pending));count+=len(pending);pending=[p for group in results for p in group];print('Documentation entries',count,'pending',len(pending),flush=True)
p=pathlib.Path('docs/upstream-sha256.json');manifest=json.loads(p.read_text())
for f in pathlib.Path('client/doc').rglob('*'):
 if f.is_file():manifest[f.relative_to('client').as_posix()]=hashlib.sha256(f.read_bytes()).hexdigest()
p.write_text(json.dumps(manifest,indent=2))
