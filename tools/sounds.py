#!/usr/bin/env python3
"""Writes src/main/resources/assets/hollowbell/sounds.json: each of his sounds, with its subtitle.

   python3 tools/sounds.py

Sounds are the game's own, pitched and mixed for him (checked against the game's sound index, so a wrong name
stops the script), plus the ones made for him from scratch by tools/make_sounds.py (in assets/hollowbell/sounds/).
Nothing is taken from other games or mods.
"""
import glob, json, os, sys

ROOT = os.path.join(os.path.dirname(__file__), '..')
OUT = os.path.join(ROOT, 'src/main/resources/assets/hollowbell/sounds.json')
OWN = os.path.join(ROOT, 'src/main/resources/assets/hollowbell/sounds')

def v(path, pitch=1.0, volume=1.0, weight=1, n=None, attenuation=None, stream=False):
    """a game sound (path under minecraft/sounds/), n = how many numbered ones (path1..pathN)"""
    names = [f'{path}{i}' for i in range(1, n + 1)] if n else [path]
    out = []
    for nm in names:
        e = {'name': 'minecraft:' + nm, 'pitch': pitch, 'volume': volume}
        if weight != 1: e['weight'] = weight
        if attenuation: e['attenuation_distance'] = attenuation
        if stream: e['stream'] = True
        out.append(e)
    return out

def own(name, pitch=1.0, volume=1.0, attenuation=None, stream=False):
    """one of his own, made by tools/make_sounds.py"""
    e = {'name': 'hollowbell:' + name, 'pitch': pitch, 'volume': volume}
    if attenuation: e['attenuation_distance'] = attenuation
    if stream: e['stream'] = True
    return [e]

# event: (subtitle, [sounds]). Far-carrying sounds get a long attenuation distance: he is huge.
EVENTS = {
    # his body
    'hum': ("Hollowbell hums", own('hum', 1.0, 1.0, 96, True)),
    'drift': ("Hollowbell drifts", own('drift', 1.0, 0.8, 64, True)),
    'pulse': ("Hollowbell's bell squeezes", own('pulse', 1.0, 1.0, 96) + own('pulse', 0.85, 1.0, 96)),
    'pulse_water': ("Water rushes", v('liquid/swim', 0.5, 0.8, n=5, attenuation=48)),
    'ripple': ("The rim ripples", v('ambient/underwater/enter', 0.55, 0.7, n=3, attenuation=48)),
    'hurt': ("Hollowbell rings", v('block/amethyst/break', 0.5, 1.0, n=4, attenuation=48) + v('mob/guardian/elder_hit', 0.6, 0.8, n=4, attenuation=48)),
    'hurt_heavy': ("Hollowbell cries out", v('mob/guardian/elder_hit', 0.42, 1.0, n=4, attenuation=96)),
    'death': ("Hollowbell dies", v('mob/guardian/elder_death', 0.45, 1.0, attenuation=160)),
    'death_fall': ("Hollowbell's bell folds", own('toll', 0.6, 1.0, 160)),
    'tired': ("Hollowbell sags", v('mob/warden/listening_', 0.5, 0.9, n=5, attenuation=64)),
    # strands and parts
    'strand': ("Strands slide", v('block/honeyblock/slide', 0.6, 0.9, n=4, attenuation=32)),
    'grab': ("A strand grabs", v('mob/slime/big', 0.5, 1.0, n=4, attenuation=32)),
    'sting': ("A strand stings", v('mob/bee/sting', 0.6, 1.0, attenuation=24)),
    'pod_pop': ("A pod pops", v('block/frogspawn/break', 0.5, 1.0, n=4, attenuation=48) + v('block/honeyblock/break', 0.55, 1.0, n=5, attenuation=48)),
    'egg_burst': ("An egg clump bursts", v('block/frogspawn/hatch', 0.6, 1.0, n=5, attenuation=40)),
    'goo': ("Goo splatters", v('mob/slime/attack', 0.5, 1.0, n=2, attenuation=40)),
    'spores': ("Spores burst", v('block/sculk/spread', 0.6, 1.0, n=5, attenuation=48)),
    # Bellings
    'belling': ("Belling chirps", v('mob/allay/idle_without_item', 1.35, 0.6, n=4, attenuation=16)),
    'belling_hurt': ("Belling hurts", v('mob/allay/hurt', 1.3, 0.8, n=2, attenuation=16)),
    'belling_death': ("Belling pops", v('mob/allay/death', 1.3, 0.8, n=2, attenuation=16)),
    # inside the dome
    'heartbeat': ("A heartbeat thuds", own('heartbeat', 1.0, 1.0, 24)),
    'echo': ("The dome echoes", own('echo', 1.0, 0.8, 24)),
    # the moves
    'toll': ("Hollowbell tolls", own('toll', 1.0, 1.0, 192)),
    'toll_big': ("Hollowbell tolls deep", own('toll', 0.7, 1.0, 256) + own('toll', 0.75, 1.0, 256)),
    'slam': ("Something slams down", v('random/explode', 0.5, 1.0, n=4, attenuation=96)),
    'shock': ("A shock wave booms", v('mob/warden/sonic_boom', 0.5, 1.0, n=4, attenuation=160)),
    'flash': ("Hollowbell flares", v('block/amethyst/shimmer', 0.7, 1.0, attenuation=64) + v('block/beacon/activate', 1.4, 0.8, attenuation=64)),
    'whip': ("A strand whips", v('entity/player/attack/sweep', 0.5, 1.0, n=7, attenuation=40)),
    'volley': ("Stingers fly", v('item/trident/throw', 1.4, 0.9, n=2, attenuation=40)),
    'beam': ("A beam of light hums", v('block/beacon/ambient', 0.7, 1.0, attenuation=64)),
    'splash': ("A great splash", v('liquid/heavy_splash', 0.5, 1.0, attenuation=96)),
    'churn': ("Water churns", v('block/bubble_column/whirlpool_ambient', 0.5, 1.0, n=5, attenuation=96)),
    'swoop': ("Hollowbell dives", v('mob/phantom/swoop', 0.45, 1.0, n=4, attenuation=128)),
    'click': ("Stingers rattle", v('mob/warden/tendril_clicks_', 0.7, 1.0, n=6, attenuation=32)),
    # the warning before each heavy move
    'warn_drop': ("Hollowbell gathers himself", v('mob/warden/sonic_charge', 0.5, 1.0, n=4, attenuation=160)),
    'warn_whirlpool': ("A whirlpool stirs", v('block/bubble_column/whirlpool_ambient', 0.35, 1.0, n=5, attenuation=160)),
    'warn_dive': ("Hollowbell screams high above", v('mob/phantom/swoop', 0.35, 1.0, n=4, attenuation=192) + v('mob/warden/roar_', 0.6, 1.0, n=5, attenuation=192)),
    'warn_toll': ("A deep toll builds", own('toll', 0.55, 1.0, 256)),
    'warn_arms': ("Hollowbell raises every arm", v('mob/ravager/roar', 0.5, 1.0, n=4, attenuation=160)),
    'warn_stingers': ("Stingers rattle all over", v('mob/breeze/charge', 0.6, 1.0, n=3, attenuation=160)),
    'warn_lances': ("The glowing spots hum", v('block/beacon/power', 0.5, 1.0, n=3, attenuation=160)),
    'warn_undertow': ("An undertow sucks in", v('block/bubble_column/upwards_inside', 0.45, 1.0, attenuation=160)),
}

def index():
    for f in glob.glob(os.path.expanduser('~/.gradle/caches/fabric-loom/assets/indexes/*.json')):
        return set(k[len('minecraft/sounds/'):-4] for k in json.load(open(f))['objects'] if k.startswith('minecraft/sounds/'))
    return None

def main():
    game = index()
    bad = []
    out = {}
    for ev, (sub, sounds) in EVENTS.items():
        for s in sounds:
            ns, path = s['name'].split(':')
            if ns == 'minecraft' and game is not None and path not in game: bad.append(f'{ev}: {path}')
            if ns == 'hollowbell' and not os.path.exists(os.path.join(OWN, path + '.ogg')): bad.append(f'{ev}: own sound {path}.ogg not made yet')
        out[ev] = {'subtitle': f'subtitles.hollowbell.{ev}', 'sounds': sounds}
    if bad:
        print('missing sounds:\n  ' + '\n  '.join(bad)); sys.exit(1)
    if game is None: print('(no game sound index found: the game sounds were not checked)')
    json.dump(out, open(OUT, 'w'), indent=2)
    # the subtitles go in the language file
    lang = os.path.join(ROOT, 'src/main/resources/assets/hollowbell/lang/en_us.json')
    L = json.load(open(lang))
    for ev, (sub, _) in EVENTS.items(): L[f'subtitles.hollowbell.{ev}'] = sub
    json.dump(L, open(lang, 'w'), indent=2, ensure_ascii=False)
    print(f'wrote sounds.json: {len(out)} sounds')

if __name__ == '__main__':
    main()
