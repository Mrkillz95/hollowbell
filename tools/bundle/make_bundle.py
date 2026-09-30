# Makes the all-in-one jar: one jar holding all five of JJ's boss mods (Fabric jar-in-jar).
# Each mod keeps its own id inside, so old worlds keep working.
# usage: python3 make_bundle.py <bundle-version> <out-dir> [jar ...]   (default: newest jar in each repo's release/)
import zipfile, json, glob, re, sys, os, io
REPOS = ['pitchgut', 'furrowmaw', 'cerberus', 'hollowbell', 'lanternwillow']
def ver(p):
    m = re.search(r'-(\d+(?:\.\d+)*)\.jar$', p)
    return tuple(int(x) for x in m.group(1).split('.')) if m else ()
bver, out = sys.argv[1], sys.argv[2]
jars = sys.argv[3:] or [max((p for p in glob.glob(f'/home/user/{r}/release/*.jar') if ver(p)), key=ver) for r in REPOS]
nested, ids = [], []
for p in jars:
    with zipfile.ZipFile(p) as z:
        fmj = json.loads(z.read('fabric.mod.json'))
    ids.append((fmj['id'], fmj['version'], fmj.get('name', fmj['id'])))
    nested.append(p)
fmj = {
    'schemaVersion': 1, 'id': 'jj_giants', 'version': bver,
    'name': "JJ's Giants (all five)",
    'description': 'All five giants in one jar: ' + ', '.join(f'{n} {v}' for _, v, n in ids) + '.',
    'authors': ['JJ'], 'license': 'All rights reserved', 'environment': '*',
    'jars': [{'file': 'META-INF/jars/' + os.path.basename(p)} for p in nested],
    'depends': {'fabricloader': '>=0.19.5', 'minecraft': '~1.21.1', 'java': '>=21', 'fabric-api': '*'},
}
os.makedirs(out, exist_ok=True)
dst = os.path.join(out, f'giants-all-{bver}.jar')
with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\n\r\n')
    z.writestr('fabric.mod.json', json.dumps(fmj, indent=2))
    for p in nested:  # nested jars are already compressed: store them as they are
        z.write(p, 'META-INF/jars/' + os.path.basename(p), compress_type=zipfile.ZIP_STORED)
print(dst, os.path.getsize(dst))
for i in ids: print(' ', *i)
