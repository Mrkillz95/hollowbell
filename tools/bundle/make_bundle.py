# Makes the all-in-one jar: the Giants Guide mod (jj_giants, built in guide/) with all five of JJ's boss mods
# nested inside it (Fabric jar-in-jar). Each giant keeps its own id inside, so old worlds keep working.
# usage: python3 make_bundle.py <bundle-version> <out-dir> [--guide <guide jar>] [giant jar ...]
#   default guide jar: the newest guide/build/libs/jj_giants-<ver>.jar; default giants: newest jar in each repo's release/
import zipfile, json, glob, re, sys, os
REPOS = ['pitchgut', 'furrowmaw', 'cerberus', 'hollowbell', 'lanternwillow']
HERE = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
def ver(p):
    m = re.search(r'-(\d+(?:\.\d+)*)\.jar$', p)
    return tuple(int(x) for x in m.group(1).split('.')) if m else ()
args = sys.argv[1:]
bver, out = args[0], args[1]
rest = args[2:]
guide = None
if '--guide' in rest:
    i = rest.index('--guide'); guide = rest[i + 1]; del rest[i:i + 2]
if guide is None:
    guide = max((p for p in glob.glob(os.path.join(HERE, 'guide/build/libs/jj_giants-*.jar')) if ver(p)), key=ver)
# a giant's release folder can hold an older all-in-one jar too: only take that giant's own jars
jars = rest or [max((p for p in glob.glob(f'/home/user/{r}/release/*.jar') if ver(p) and not os.path.basename(p).startswith('giants-all-')), key=ver)
                for r in REPOS]
ids = []
for p in jars:
    with zipfile.ZipFile(p) as z:
        fmj = json.loads(z.read('fabric.mod.json'))
    ids.append((fmj['id'], fmj['version'], fmj.get('name', fmj['id'])))
with zipfile.ZipFile(guide) as gz:
    fmj = json.loads(gz.read('fabric.mod.json'))
    if fmj.get('id') != 'jj_giants': raise SystemExit(f'{guide} is not the Giants Guide')
    fmj['version'] = bver
    fmj['name'] = "JJ's Giants (all five)"
    fmj['description'] = ('All five giants in one jar, and the Giants Guide: ' + ', '.join(f'{n} {v}' for _, v, n in ids) + '.')
    fmj.get('entrypoints', {}).pop('fabric-gametest', None)  # the guide's own tests stay out of the jar people play with
    fmj['jars'] = [{'file': 'META-INF/jars/' + os.path.basename(p)} for p in jars]
    os.makedirs(out, exist_ok=True)
    dst = os.path.join(out, f'giants-all-{bver}.jar')
    with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('fabric.mod.json', json.dumps(fmj, indent=2))
        for it in gz.infolist():
            if it.filename == 'fabric.mod.json' or it.filename.startswith('net/jj/giants/test/'): continue
            z.writestr(it, gz.read(it.filename))
        for p in jars:  # nested jars are already compressed: store them as they are
            z.write(p, 'META-INF/jars/' + os.path.basename(p), compress_type=zipfile.ZIP_STORED)
print(dst, os.path.getsize(dst))
print('  guide', os.path.basename(guide))
for i in ids: print(' ', *i)
