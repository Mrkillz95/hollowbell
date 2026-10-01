# Copies each giant's newest released jar into run-mods/ without its own game tests, so the guide's
# test and picture runs (gradle ... -Pgiants) have the real giants loaded.  usage: python3 tools/giant_mods.py
import zipfile, json, glob, os, re
REPOS = {'pitchgut': 'pitchgut-', 'furrowmaw': 'furrowmaw-', 'cerberus': 'fire-ice-cerberus-',
         'hollowbell': 'hollowbell-', 'lanternwillow': 'lanternwillow-'}
def ver(p):
    m = re.search(r'-(\d+(?:\.\d+)*)\.jar$', p)
    return tuple(int(x) for x in m.group(1).split('.')) if m else ()
here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
out = os.path.join(here, 'run-mods')
os.makedirs(out, exist_ok=True)
for f in glob.glob(os.path.join(out, '*.jar')): os.remove(f)
for repo, pre in REPOS.items():
    js = [p for p in glob.glob(f'/home/user/{repo}/release/{pre}*.jar') if ver(p)]
    if not js: print(repo, 'none'); continue
    p = max(js, key=ver)
    with zipfile.ZipFile(p) as zin, zipfile.ZipFile(os.path.join(out, repo + '.jar'), 'w', zipfile.ZIP_DEFLATED) as zout:
        for it in zin.infolist():
            d = zin.read(it.filename)
            if it.filename == 'fabric.mod.json':
                j = json.loads(d); j.get('entrypoints', {}).pop('fabric-gametest', None); d = json.dumps(j).encode()
            zout.writestr(it, d)
    print(repo, os.path.basename(p))
