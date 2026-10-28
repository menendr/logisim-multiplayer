import concurrent.futures, hashlib, json, pathlib, re, urllib.request
BASE='https://svn.code.sf.net/p/circuit/code/!svn/bc/247/tags/2.7.1/'
ROOT=pathlib.Path('client')
def fetch(path):
    url=BASE+path+'?p=256'
    for attempt in range(4):
        try:
            data=urllib.request.urlopen(url,timeout=45).read(); break
        except Exception:
            if attempt==3: raise
    if path.endswith('/') or not path:
        links=re.findall(r'href="([^"]+)"',data.decode())
        return [path+x.split('?')[0] for x in links if not x.startswith(('..','http','?','/'))]
    target=ROOT/path; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(data)
    return []
if __name__=='__main__':
    if ROOT.exists() and any(ROOT.iterdir()): raise SystemExit('Refusing to overwrite an existing client tree; export into a fresh checkout instead')
    pending=['src/','libs/','scripts/','notes/']; count=0
    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
        while pending:
            results=list(pool.map(fetch,pending)); count+=len(pending)
            pending=[x for group in results for x in group]
            print('Retrieved',count,'entries; pending',len(pending),flush=True)
    manifest={str(p.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in ROOT.rglob('*') if p.is_file()}
    pathlib.Path('docs/upstream-sha256.json').write_text(json.dumps(manifest,indent=2))

