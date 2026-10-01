# Copies each giant's move names and move texts (his book's tooltips) from his own language file into the
# guide's, as guide.jj_giants.KEY.move.ID and .what. The guide shows his own text when his mod has the key,
# and these copies only when it doesn't (an older version). Run after a giant's moves change:
#   python3 tools/sync_moves.py
import json, os, re, collections
here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
lang_path = os.path.join(here, 'src/main/resources/assets/jj_giants/lang/en_us.json')
src = open(os.path.join(here, 'src/main/java/net/jj/giants/Giants.java')).read()
REPO = {'pitchgut': 'pitchgut', 'furrowmaw': 'furrowmaw', 'cerberus': 'cerberus', 'hollowbell': 'hollowbell', 'lanternwillow': 'lanternwillow'}
lang = json.load(open(lang_path), object_pairs_hook=collections.OrderedDict)
# each entry: new Giant("key", "modid", ... moves("namePrefix", "whatPrefix", {light}, {medium}, {heavy})
for m in re.finditer(r'new Giant\("(\w+)", "(\w+)".*?moves\("([\w.]+)", "([\w.]+)",(.*?)\}\),\s*Set\.of', src, re.S):
    key, mod, npre, wpre, body = m.groups()
    theirs = json.load(open(f'/home/user/{REPO[key]}/src/main/resources/assets/{mod}/lang/en_us.json'))
    for item in re.findall(r'"([\w=.]+)"', body):
        mid, _, own = item.partition('=')
        name_key, what_key = own or npre + mid, wpre + mid
        for k, ck in ((name_key, f'guide.jj_giants.{key}.move.{mid}'), (what_key, f'guide.jj_giants.{key}.move.{mid}.what')):
            if k not in theirs: raise SystemExit(f'{mod} has no {k}')
            lang[ck] = theirs[k]
json.dump(lang, open(lang_path, 'w'), indent=2, ensure_ascii=False)
open(lang_path, 'a').write('\n')
print('moves copied:', sum(1 for k in lang if '.move.' in k and not k.endswith('.what')))
