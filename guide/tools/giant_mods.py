# Copies each giant's newest released jar into run-mods/ without its own game tests, so the guide's
# test and picture runs (gradle ... -Pgiants) have the real giants loaded.
# usage: python3 tools/giant_mods.py [folder holding the repos, default /home/user] [repo=folder ...]
# (repo=folder: that giant's jar from another folder, its build/libs/ or else its release/, e.g. an unreleased
#  branch's worktree: tickmoor=C:/Users/jeria/giants/tickmoor-wt-duel)
import zipfile, json, glob, os, re, sys
REPOS = {'pitchgut': 'pitchgut-', 'furrowmaw': 'furrowmaw-', 'cerberus': 'fire-ice-cerberus-',
         'hollowbell': 'hollowbell-', 'lanternwillow': 'lanternwillow-', 'wreckback': 'wreckback-', 'swarmforge': 'swarmforge-',
         'gloamwick': 'gloamwick-', 'hearthstride': 'hearthstride-', 'tickmoor': 'tickmoor-'}
# (a giant whose repo folder isn't his name)
FOLDER = {'tickmoor': 'tickmoor-mod'}
def ver(p):
    m = re.search(r'-(\d+(?:\.\d+)*)\.jar$', p)
    return tuple(int(x) for x in m.group(1).split('.')) if m else ()
root = sys.argv[1] if len(sys.argv) > 1 and '=' not in sys.argv[1] else '/home/user'
OVER = dict(a.split('=', 1) for a in sys.argv[1:] if '=' in a)
here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
out = os.path.join(here, 'run-mods')
os.makedirs(out, exist_ok=True)
for f in glob.glob(os.path.join(out, '*.jar')): os.remove(f)
for repo, pre in REPOS.items():
    if repo in OVER:
        # (its build/libs/ first: a worktree's release/ holds the last released jar, often the same version number)
        for d in (os.path.join('build', 'libs'), 'release'):
            js = [p for p in glob.glob(os.path.join(OVER[repo], d, pre + '*.jar'))
                  if ver(p) and not p.endswith(('-sources.jar', '-dev.jar'))]
            if js: break
    else:
        js = [p for p in glob.glob(os.path.join(root, FOLDER.get(repo, repo), 'release', pre + '*.jar')) if ver(p)]
    if not js: print(repo, 'none'); continue
    p = max(js, key=ver)
    with zipfile.ZipFile(p) as zin, zipfile.ZipFile(os.path.join(out, repo + '.jar'), 'w', zipfile.ZIP_DEFLATED) as zout:
        for it in zin.infolist():
            d = zin.read(it.filename)
            if it.filename == 'fabric.mod.json':
                j = json.loads(d); j.get('entrypoints', {}).pop('fabric-gametest', None); d = json.dumps(j).encode()
            zout.writestr(it, d)
    print(repo, os.path.basename(p))
