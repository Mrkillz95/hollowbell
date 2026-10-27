"""From a duel sweep, how much stronger or weaker each giant is overall, and the giantArmor change that evens it out.

usage: python -I tools/duel_fit.py build/duels/base.csv [--skip P-H,WB-S] [--cap 0.35] [--escape P=1,H=5,...]
       [--per 0.1] [--armor P=0.36,...]

For each pair the edge is ln(rate X drains Y's health / rate Y drains X's). Giving X's incoming giant damage a factor
k_X moves every edge of X by -ln k_X, so least squares over the pairs gives one strength s_X per giant (mean 0), and
k_X = exp(s_X) (a strong giant takes more from giant blows, a weak one less), kept within 1 +- cap. Pairs passed in
--skip (ones broken in code, which numbers shouldn't paper over) are left out of the fit.

--escape gives each giant an escape score (1 = can't get away from a fight it is losing, 5 = gets away at will, as the
Hollowbell flies off). JJ: a giant that can escape easily may lose more head-on; one that can't should be stronger. The
target is then not an even edge but ln edge_XY = -per * (E_X - E_Y): each escape point is worth `per` of head-on edge.
--armor: the giantArmor values the sweep ran with (default: the released ones), to print the new ones from.
"""
import csv, math, sys
from collections import defaultdict

ORDER = ['P', 'F', 'C', 'H', 'W', 'WB', 'S', 'G']
ARMOR = {'P': 0.55, 'F': 0.62, 'C': 0.80, 'H': 0.78, 'W': 0.52, 'WB': 0.6, 'S': 0.48, 'G': 0.6}


def rates(rows):
    agg = defaultdict(lambda: [0.0, 0.0, 0.0, 0.0, 0.0])  # dmg x->y, dmg y->x, secs, max x, max y
    for r in rows:
        x, y = sorted((r['a'], r['b']), key=ORDER.index)
        a = agg[(x, y)]
        if r['a'] == x:
            a[0] += float(r['dmg_a_to_b']); a[1] += float(r['dmg_b_to_a']); a[3] = float(r['max_a']); a[4] = float(r['max_b'])
        else:
            a[0] += float(r['dmg_b_to_a']); a[1] += float(r['dmg_a_to_b']); a[3] = float(r['max_b']); a[4] = float(r['max_a'])
        a[2] += float(r['seconds'])
    out = {}
    for k, (dx, dy, s, mx, my) in agg.items():
        # (a floor of 0.2 % of the other's health a minute, so a side that did nothing gives a big, finite edge)
        rx = max(dx / my / s * 6000, 0.2)
        ry = max(dy / mx / s * 6000, 0.2)
        out[k] = math.log(rx / ry)
    return out


def fit(edges, skip, target=None):
    n = len(ORDER)
    s = [0.0] * n
    use = {k: e for k, e in edges.items() if f'{k[0]}-{k[1]}' not in skip and f'{k[1]}-{k[0]}' not in skip}
    # (fit to the distance from the target edge, so a strength of 0 means "where its escape says it should be")
    if target:
        use = {k: e - target(k[0], k[1]) for k, e in use.items()}
    # Gauss-Seidel on the normal equations of sum (edge_XY - s_X + s_Y)^2: s_X = mean over X's pairs of (edge seen
    # from X + s_Y)
    for _ in range(500):
        for i, g in enumerate(ORDER):
            terms = []
            for (x, y), e in use.items():
                if x == g:
                    terms.append(e + s[ORDER.index(y)])
                elif y == g:
                    terms.append(-e + s[ORDER.index(x)])
            if terms:
                s[i] = sum(terms) / len(terms)
        m = sum(s) / n
        s = [v - m for v in s]
    res = sum((e - s[ORDER.index(x)] + s[ORDER.index(y)]) ** 2 for (x, y), e in use.items())
    return s, use, res


def main():
    path = sys.argv[1]
    skip = set()
    cap = 0.35
    args = sys.argv[2:]
    for i, a in enumerate(args):
        if a == '--skip':
            skip = set(args[i + 1].split(','))
        if a == '--cap':
            cap = float(args[i + 1])
    esc, per, armor = {}, 0.1, dict(ARMOR)
    for i, a in enumerate(args):
        if a == '--escape':
            esc = {kv.split('=')[0]: float(kv.split('=')[1]) for kv in args[i + 1].split(',')}
        if a == '--per':
            per = float(args[i + 1])
        if a == '--armor':
            armor.update({kv.split('=')[0]: float(kv.split('=')[1]) for kv in args[i + 1].split(',')})
    target = (lambda x, y: -per * (esc[x] - esc[y])) if esc else None
    rows = list(csv.DictReader(open(path, encoding='utf-8')))
    edges = rates(rows)
    s, use, res = fit(edges, skip, target)
    if esc:
        mean = sum(esc.values()) / len(esc)
        print('| Giant | Escape | Target head-on edge vs an average giant |')
        print('|---|---|---|')
        for g in ORDER:
            print(f'| {g} | {esc[g]:.0f} | {math.exp(-per * (esc[g] - mean)):.2f} |')
        print()
    # s_X > 0: X is stronger than its rivals by that log-edge. Incoming x k_X = exp(s_X) moves the pair's edge by
    # -(s_X - s_Y) when both change.
    print('| Giant | Strength (log edge) | Incoming x | giantArmor now -> proposed |')
    print('|---|---|---|---|')
    for g, v in zip(ORDER, s):
        k = min(1 + cap, max(1 - cap, math.exp(v)))
        print(f'| {g} | {v:+.2f} | x{k:.2f} | {armor[g]:.2f} -> {armor[g] * k:.2f} |')
    print()
    print('Pair edges in the fit (off target, if --escape), now and if the factors were exact (no cap):')
    for (x, y), e in sorted(use.items(), key=lambda k: -abs(k[1])):
        after = e - s[ORDER.index(x)] + s[ORDER.index(y)]
        print(f'  {x}-{y}: {math.exp(abs(e)):.2f} -> {math.exp(abs(after)):.2f}')
    print(f'left out: {sorted(skip)}')


if __name__ == '__main__':
    main()
