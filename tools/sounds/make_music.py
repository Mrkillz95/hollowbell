#!/usr/bin/env python3
"""Makes the Hollowbell's music and the sounds of his ground, from scratch (no recordings from anywhere), as .ogg
files in src/main/resources/assets/hollowbell/sounds/:

  music/fight.ogg         his fight theme: stereo, about 2 min 15 s, loops. A tolling intro, a driving main part,
                          a quiet tense break, a huge climax, and back round. Bells, an airy choir, brass, strings,
                          glass and big drums, in D minor at 100 beats a minute.
  music/ground.ogg        the Bell Hollows' own music: calm, slow choir and strings with soft bells, 105 s, loops.
  ambient/ground.ogg      the ground's background loop (soft air through glass, a low hum), 64 s, loops.
  ambient/mood1-5.ogg     a bell a long way off, five ways (the ground's "mood" sound).
  ambient/chime1-5.ogg    soft glass chimes now and then (the ground's "additions").
  egg_wind.ogg            the egg rain's wind-up: the clumps rattle and a glassy shiver rises
  egg_splat.ogg           an egg clump hitting the ground
  egg_hatch.ogg           a Belling cracking out of its egg

Everything is seeded, so it makes the same files every time. The loops are made "round": every note that runs
off the end carries on at the start, and the echo, the compressors and the filters are worked out round the loop
too, so there is no seam. The music is levelled by its loudness (LUFS, as check_sounds.py measures it).

    pip install numpy scipy soundfile pillow
    python3 tools/sounds/make_music.py [names...] && python3 tools/sounds/check_sounds.py && python3 tools/sounds.py

With names (fight, ground, ambient, mood, chimes, eggs) it makes only those.
"""
import os
import sys
import numpy as np
import soundfile as sf
from scipy import signal

sys.path.insert(0, os.path.dirname(__file__))
from check_sounds import lufs  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
OUT = os.path.join(ROOT, 'src/main/resources/assets/hollowbell/sounds')
SR = 32000
rng = np.random.default_rng(20260929)


def seed(name):
    """each piece its own random numbers, so changing one leaves the others as they were"""
    global rng
    rng = np.random.default_rng(abs(hash_name(name)))


def hash_name(s):
    h = 2166136261
    for ch in s.encode():
        h = ((h ^ ch) * 16777619) & 0xffffffff
    return h


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def t_(sec):
    return np.arange(int(sec * SR)) / SR


def env_adsr(n, a, d, s, r):
    """attack / decay / sustain level / release, in seconds; n samples total"""
    e = np.zeros(n)
    A, D, R = int(a * SR), int(d * SR), int(r * SR)
    A = max(1, min(A, n))
    D = max(1, min(D, n - A))
    e[:A] = np.linspace(0, 1, A)
    e[A:A + D] = np.linspace(1, s, D)
    rest = n - A - D
    if rest > 0:
        e[A + D:] = s
        R = min(R, rest)
        e[n - R:] *= np.linspace(1, 0, R) ** 1.5
    return e


def lowpass(x, fc, order=2):
    b, a = signal.butter(order, min(fc, SR * 0.45) / (SR / 2), 'low')
    return signal.lfilter(b, a, x, axis=0)


def highpass(x, fc, order=2):
    b, a = signal.butter(order, fc / (SR / 2), 'high')
    return signal.lfilter(b, a, x, axis=0)


def bandpass(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), min(hi, SR * 0.45) / (SR / 2)], 'band')
    return signal.lfilter(b, a, x, axis=0)


def peak(x, f, q):
    b, a = signal.iirpeak(f / (SR / 2), q)
    return signal.lfilter(b, a, x, axis=0)


def circ(fn, x, pad=4.0):
    """runs a filter round a loop: the end goes in first, so the start hears what came before it"""
    p = min(len(x), int(pad * SR))
    return fn(np.concatenate([x[-p:], x]))[p:]


# instruments (mono)

# a church bell's partials (hum, prime, tierce, quint, nominal, ...) and how long each rings, as a share
BELL = [(0.5, 0.38, 1.0), (1.0, 0.7, 0.8), (1.19, 0.5, 0.55), (1.5, 0.3, 0.45), (2.0, 0.85, 0.5),
        (2.51, 0.3, 0.3), (3.0, 0.3, 0.22), (4.07, 0.22, 0.14), (5.4, 0.12, 0.09), (6.8, 0.07, 0.06)]


def bell(f, dur=7.0, bright=1.0, strike=1.0):
    t = t_(dur)
    x = np.zeros_like(t)
    for ratio, amp, life in BELL:
        fr = f * ratio
        if fr > SR * 0.42:
            continue
        tau = dur * life * 0.45
        a = amp * (bright if ratio > 2.2 else 1.0)
        # each partial is really two, a hair apart: the slow beating a big bell has
        beat = 0.15 + 0.35 * rng.random()
        ph = rng.random() * 6.28
        x += a * np.exp(-t / tau) * (np.sin(2 * np.pi * fr * t + ph)
                                     + 0.6 * np.sin(2 * np.pi * (fr + beat) * t + ph * 1.3))
    n = int(0.03 * SR)
    hit = rng.standard_normal(n) * np.exp(-np.arange(n) / (0.006 * SR))
    x[:n] += 0.4 * bright * strike * bandpass(hit, 1500, 6000)
    a = int(0.004 * SR)
    x[:a] *= np.linspace(0, 1, a)
    return x / 3.0


def glass(f, dur=2.0, hard=1.0):
    """a struck glass: bright FM ping with a clear ring"""
    t = t_(dur)
    idx = 2.2 * hard * np.exp(-t * 7)
    x = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * 3.51 * t))
    x += 0.35 * np.sin(2 * np.pi * f * 2.756 * t) * np.exp(-t * 5)
    x *= np.exp(-t * (1.6 + 0.6 / max(0.2, hard)))
    a = int(0.003 * SR)
    x[:a] *= np.linspace(0, 1, a)
    return x * 0.5


VOWELS = {'a': [(730, 9), (1090, 10), (2440, 14)], 'o': [(470, 8), (820, 9), (2600, 14)],
          'u': [(330, 7), (740, 8), (2400, 13)], 'e': [(530, 8), (1840, 12), (2480, 14)]}


def choir(freqs, dur, vowel='a', attack=0.35, release=0.9, vib=0.0035, air=0.08, voices=5):
    """a choir chord: each note several slightly different voices, shaped by the vowel"""
    t = t_(dur)
    raw = np.zeros_like(t)
    for f in freqs:
        for k in range(voices):
            det = 1 + (k - (voices - 1) / 2) * 0.0035 + rng.normal(0, 0.0008)
            vr = 4.6 + rng.random() * 1.2
            vdel = np.clip((t - 0.25) / 0.6, 0, 1)
            fm = f * det * (1 + vib * vdel * np.sin(2 * np.pi * vr * t + rng.random() * 6.28))
            ph = 2 * np.pi * np.cumsum(fm) / SR + rng.random() * 6.28
            raw += signal.sawtooth(ph) * (0.8 + 0.4 * rng.random())
    raw = lowpass(raw, 4200, 2)
    y = np.zeros_like(raw)
    for fc, q in VOWELS[vowel]:
        y += peak(raw, fc, q) * (1.0 if fc < 1500 else 0.55)
    y += air * bandpass(rng.standard_normal(len(t)), 800, 3000) * 0.5
    e = env_adsr(len(t), attack, 0.3, 0.85, release)
    return y * e / (len(freqs) * voices) * 2.4


def saws(f, t, n, spread, vib=0.0, vib_rate=5.5, scoop=0.0):
    """n sawtooth voices around f, spread in cents, with a little vibrato and an optional scoop up into the note"""
    x = np.zeros_like(t)
    for k in range(n):
        cents = (k - (n - 1) / 2) * spread / max(1, n - 1) * 2 + rng.normal(0, 1.5)
        fk = f * 2 ** (cents / 1200)
        bend = 1 - scoop * np.exp(-t * 25)
        wob = 1 + vib * np.clip((t - 0.3) / 0.5, 0, 1) * np.sin(2 * np.pi * vib_rate * t + rng.random() * 6.28)
        ph = 2 * np.pi * np.cumsum(fk * bend * wob) / SR + rng.random() * 6.28
        x += signal.sawtooth(ph)
    return x / n


def strings(f, dur, attack=0.25, release=0.5, bright=1.0, vib=0.004):
    """a bowed string section note"""
    t = t_(dur)
    x = saws(f, t, 5, 9, vib=vib)
    x = lowpass(x, min(9000, 1800 * bright + f * 2), 2)
    x = highpass(x, 90)
    x += 0.04 * bandpass(rng.standard_normal(len(t)), 2000, 6000)
    return x * env_adsr(len(t), attack, 0.2, 0.9, release) * 0.6


def spiccato(f, dur=0.2, accent=1.0):
    """a short bounced string note, for the running figures"""
    t = t_(dur)
    x = saws(f, t, 3, 7)
    x = lowpass(x, 1500 + 1500 * accent + f * 1.5, 2)
    x = highpass(x, 120)
    n = int(0.012 * SR)
    x[:n] += 0.3 * accent * bandpass(rng.standard_normal(n), 1500, 5000) * np.linspace(1, 0, n)
    return x * env_adsr(len(t), 0.006, 0.06, 0.45, 0.07) * (0.5 + 0.5 * accent) * 0.7


def brass(f, dur, bright=1.0, attack=0.04, release=0.25):
    """a brass section: the tone opens up as it's blown, with a small scoop into the note"""
    t = t_(dur)
    x = saws(f, t, 4, 8, vib=0.003, scoop=0.012)
    dark = lowpass(x, f * 2.5 + 200, 2)
    lit = lowpass(x, min(7000, f * (5 + 5 * bright) + 600), 2)
    op = np.clip(t / 0.09, 0, 1) * (0.75 + 0.25 * np.exp(-t * 3))
    y = dark * (1 - op) + lit * op
    y += 0.5 * peak(y, 1150, 4)
    y = np.tanh(1.8 * y) / np.tanh(1.8)
    y = highpass(y, 70)
    return y * env_adsr(len(t), attack, 0.15, 0.8, release) * 0.55


def bass(f, dur, drive=1.6, cutoff=380):
    t = t_(dur)
    x = 0.6 * saws(f, t, 2, 6) + np.sin(2 * np.pi * f * t)
    x = lowpass(np.tanh(drive * x) / np.tanh(drive), cutoff, 2)
    return x * env_adsr(len(t), 0.008, 0.15, 0.8, 0.12) * 0.6


def kick(dur=0.5, hi=160, lo=48):
    t = t_(dur)
    f = lo + (hi - lo) * np.exp(-t * 32)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 10)
    n = int(0.008 * SR)
    x[:n] += 0.35 * bandpass(rng.standard_normal(n), 1200, 5000) * np.linspace(1, 0, n)
    return np.tanh(1.6 * x) * 0.8


def boom(dur=2.4, pitch=1.0):
    """a big low cinematic hit: a taiko body, a long sub under it, and a thump of air"""
    t = t_(dur)
    f = (34 + 60 * np.exp(-t * 9)) * pitch
    sub = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 3.2)
    body_f = (70 + 90 * np.exp(-t * 18)) * pitch
    body = np.sin(2 * np.pi * np.cumsum(body_f) / SR) * np.exp(-t * 6)
    air = lowpass(rng.standard_normal(len(t)), 500) * np.exp(-t * 10)
    return np.tanh(1.3 * (0.75 * sub + 0.7 * body + 0.5 * air)) * 0.85


def tom(pitch=1.0, dur=0.8):
    t = t_(dur)
    f = (85 + 75 * np.exp(-t * 20)) * pitch
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 7)
    x += 0.4 * np.sin(2 * np.pi * np.cumsum(f * 1.5) / SR) * np.exp(-t * 11)
    x += 0.35 * bandpass(rng.standard_normal(len(t)), 200, 2500) * np.exp(-t * 35)
    return x * 0.6


def snare(dur=0.45, clap=False):
    t = t_(dur)
    body = np.sin(2 * np.pi * 190 * t) * np.exp(-t * 22) + 0.5 * np.sin(2 * np.pi * 330 * t) * np.exp(-t * 30)
    rattle = bandpass(rng.standard_normal(len(t)), 1500, 8000) * np.exp(-t * 13)
    x = 0.6 * body + rattle
    if clap:
        # a few hands, a hair apart, then the room
        c = np.zeros_like(t)
        for k, d in enumerate((0.0, 0.011, 0.019, 0.03)):
            s = int(d * SR)
            m = int(0.012 * SR)
            c[s:s + m] += bandpass(rng.standard_normal(m), 900, 4500) * np.exp(-np.arange(m) / (0.003 * SR))
        c += 0.4 * bandpass(rng.standard_normal(len(t)), 900, 4000) * np.exp(-t * 16)
        x = 0.6 * x + c
    return x * 0.45


def hat(open_=False):
    dur = 0.35 if open_ else 0.07
    t = t_(dur)
    x = highpass(bandpass(rng.standard_normal(len(t)), 6000, 13000), 6000)
    x *= np.exp(-t * (11 if open_ else 70))
    return x * 0.35


def shaker():
    t = t_(0.09)
    x = bandpass(rng.standard_normal(len(t)), 3500, 9000)
    return x * np.sin(np.pi * np.clip(t / 0.09, 0, 1)) ** 2 * 0.25


def riser(dur, top=1.0):
    """noise and a cluster of tones rising, louder and brighter, into the next part"""
    t = t_(dur)
    k = t / dur
    n = rng.standard_normal(len(t))
    y = np.zeros_like(t)
    steps = 16
    edges = np.linspace(0, len(t), steps + 1).astype(int)
    for i in range(steps):
        a, b = edges[i], edges[i + 1]
        fc = 300 * (20 ** (i / (steps - 1)))
        seg = bandpass(n[max(0, a - 2000):b], fc * 0.6, fc * 1.6)[-(b - a):]
        y[a:b] = seg
    tones = np.zeros_like(t)
    for f0 in (146.8, 220.0, 293.7, 349.2):
        f = f0 * (1 + 1.0 * k ** 2)
        tones += np.sin(2 * np.pi * np.cumsum(f) / SR + rng.random() * 6)
    out = (0.7 * y + 0.12 * tones) * k ** 2.2 * top
    out[-int(0.03 * SR):] *= np.linspace(1, 0, int(0.03 * SR))
    return out * 0.5


def swell(x):
    """a sound played backwards: it grows out of nothing and stops dead on the beat"""
    return x[::-1].copy()


def crash():
    t = t_(2.5)
    x = highpass(rng.standard_normal(len(t)), 3000) * np.exp(-t * 2.2)
    x = lowpass(x, 11000)
    return x * 0.25


# a round mix: buses that share one echo, and whatever runs off the end comes in at the start

class Mix:
    def __init__(self, seconds):
        self.n = int(round(seconds * SR))
        self.bus = {}
        self.send = np.zeros((self.n, 2))

    def _put(self, buf, x2, at):
        s = int(round(at * SR)) % self.n
        pos, i = s, 0
        while i < len(x2):
            m = min(len(x2) - i, self.n - pos)
            buf[pos:pos + m] += x2[i:i + m]
            i += m
            pos = 0

    def add(self, bus, x, at, pan=0.0, gain=1.0, wet=0.3):
        """a mono sound at `at` seconds on a bus, panned -1..1"""
        th = (pan + 1) * np.pi / 4
        x2 = x[:, None] * (np.array([np.cos(th), np.sin(th)]) * gain * np.sqrt(2))[None, :]
        self.stereo(bus, x2, at, wet)

    def wide(self, bus, xl, xr, at, gain=1.0, wet=0.3):
        """two takes of the same part, one each side"""
        m = min(len(xl), len(xr))
        self.stereo(bus, np.stack([xl[:m], xr[:m]], 1) * gain, at, wet)

    def stereo(self, bus, x2, at, wet):
        if bus not in self.bus:
            self.bus[bus] = np.zeros((self.n, 2))
        self._put(self.bus[bus], x2 * (1 - wet * 0.4), at)
        self._put(self.send, x2 * wet, at)


def reverb_ir(seconds=4.0, rt60=3.4, echo=None, bright=0.5):
    """a big hollow room: early reflections, a long soft tail, and (if asked) a clear echo or two"""
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = np.zeros((n, 2))
    for c in range(2):
        nz = rng.standard_normal(n)
        tail = lowpass(nz, 2500) * np.exp(-6.9 * t / rt60)
        tail += bright * highpass(nz, 2500) * np.exp(-6.9 * t / (rt60 * 0.45))
        pre = int(0.022 * SR)
        tail[:pre] = 0
        tail[pre:pre + 400] *= np.linspace(0, 1, 400)
        ir[:, c] = tail * 0.05
        for k in range(10):
            d = int((0.008 + 0.05 * rng.random()) * SR)
            ir[d, c] += (0.5 - 0.035 * k) * (1 if rng.random() > 0.3 else -1)
        if echo:
            for d, g in echo:
                j = int(d * SR) + c * 37
                if j < n - 300:
                    burst = lowpass(rng.standard_normal(300), 3000) * np.hanning(300)
                    ir[j:j + 300, c] += burst * g * 0.12
    return ir


def round_conv(x, ir):
    """convolution round the loop"""
    n = len(x)
    out = np.zeros_like(x)
    for c in range(x.shape[1]):
        out[:, c] = np.fft.irfft(np.fft.rfft(x[:, c]) * np.fft.rfft(ir[:, c], n=n), n=n)
    return out


def round_eq(x, lo=None, hi=None, order=4):
    """a zero-phase high/low cut done on the whole loop at once, so it stays round"""
    X = np.fft.rfft(x, axis=0)
    f = np.maximum(np.fft.rfftfreq(len(x), 1 / SR), 1e-3)
    g = np.ones_like(f)
    if lo:
        g /= np.sqrt(1 + (lo / f) ** (2 * order))
    if hi:
        g /= np.sqrt(1 + (f / hi) ** (2 * order))
    return np.fft.irfft(X * g[:, None] if x.ndim > 1 else X * g, n=len(x), axis=0)


def smooth_round(g, att, rel):
    """attack/release smoothing of a gain curve (in dB, per block), round the loop: two passes so the start
    carries on from the end"""
    out = np.empty_like(g)
    s = g[-1]
    for _ in range(2):
        for i, v in enumerate(g):
            s = s + (v - s) * (att if v < s else rel)
            out[i] = s
    return out


def compress(x, thr_db, ratio, att_ms=8, rel_ms=180, block_ms=5, knee=6.0):
    """a stereo-linked compressor, worked out on short blocks, round the loop; returns the squeezed signal"""
    blk = int(block_ms / 1000 * SR)
    nb = len(x) // blk
    pw = (x[:nb * blk] ** 2).mean(axis=1).reshape(nb, blk).mean(axis=1)
    lvl = 10 * np.log10(pw + 1e-12) + 3.0
    over = lvl - thr_db
    gr = np.where(over <= -knee / 2, 0.0,
                  np.where(over >= knee / 2, over * (1 - 1 / ratio),
                           (1 - 1 / ratio) * (over + knee / 2) ** 2 / (2 * knee)))
    a = 1 - np.exp(-block_ms / att_ms)
    r = 1 - np.exp(-block_ms / rel_ms)
    g = smooth_round(-gr, a, r)
    gain = np.interp(np.arange(len(x)), (np.arange(nb) + 0.5) * blk, 10 ** (g / 20), period=len(x))
    return x * gain[:, None]


def limit(x, ceiling_db=-1.6, rel_ms=60):
    """a look-ahead peak limiter, round the loop"""
    blk = int(0.0025 * SR)
    nb = int(np.ceil(len(x) / blk))
    pad = np.zeros((nb * blk, 2))
    pad[:len(x)] = x
    pk = np.abs(pad).max(axis=1).reshape(nb, blk).max(axis=1)
    c = 10 ** (ceiling_db / 20)
    need = 20 * np.log10(np.minimum(1.0, c / np.maximum(pk, 1e-9)))
    # look ahead and behind two blocks, so the gain is already down when the peak comes
    need = np.minimum.reduce([np.roll(need, k) for k in (-2, -1, 0, 1, 2)])
    g = smooth_round(need, 1.0, 1 - np.exp(-2.5 / rel_ms))
    g = np.minimum.reduce([np.roll(g, k) for k in (-1, 0, 1)])
    gain = np.interp(np.arange(len(x)), (np.arange(nb) + 0.5) * blk, 10 ** (g / 20), period=len(x))
    y = x * gain[:, None]
    # the odd sample still over (between blocks) is cut
    return np.clip(y, -c, c)


def master(x, target_lufs, ceiling_db=-1.6):
    """no DC or rumble, levelled to the target loudness, peaks held under the ceiling"""
    x = round_eq(x - x.mean(axis=0), lo=28)
    for _ in range(6):
        x = x * 10 ** ((target_lufs - lufs(x, SR)[0]) / 20)
        x = limit(x, ceiling_db)
    return x


def write(name, x, quality=0.4, sr=SR):
    path = os.path.join(OUT, name + '.ogg')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    x = x.astype(np.float32)
    ch = 1 if x.ndim == 1 else x.shape[1]
    # written a block at a time (the Vorbis writer falls over on one huge write)
    with sf.SoundFile(path, 'w', sr, ch, format='OGG', subtype='VORBIS', compression_level=1 - quality) as f:
        for i in range(0, len(x), 4096):
            f.write(x[i:i + 4096])
    print(f'{name}.ogg  {len(x) / sr:.1f} s  {os.path.getsize(path) // 1024} KB  {lufs(x, sr)[0]:.1f} LUFS')


# the fight theme

CH = {'Dm': [50, 53, 57, 62], 'Bb': [46, 53, 58, 62], 'Gm': [43, 50, 55, 58], 'A': [45, 52, 57, 61],
      'F': [41, 48, 53, 57], 'C': [48, 55, 60, 64], 'Dm/F': [41, 50, 57, 62], 'Am': [45, 52, 57, 60],
      'Gm/D': [38, 50, 55, 58]}
ROOT_ = {'Dm': 38, 'Bb': 34, 'Gm': 31, 'A': 33, 'F': 29, 'C': 36, 'Dm/F': 29, 'Am': 33, 'Gm/D': 38}

# the tune, over eight bars of Dm Bb F C Gm Bb A A: (bar, beat, beats long, note)
MEL = [(0, 0, 2, 69), (0, 2, 2, 74), (1, 0, 1.5, 72), (1, 1.5, 0.5, 70), (1, 2, 2, 69),
       (2, 0, 1, 69), (2, 1, 1, 72), (2, 2, 2, 77), (3, 0, 2, 76), (3, 2, 1, 74), (3, 3, 1, 72),
       (4, 0, 3, 74), (4, 3, 1, 70), (5, 0, 1, 72), (5, 1, 1, 74), (5, 2, 2, 77),
       (6, 0, 2, 76), (6, 2, 2, 73), (7, 0, 4, 69)]
# the same tune, higher and grander, for the climax
MEL2 = [(0, 0, 1, 74), (0, 1, 1, 77), (0, 2, 2, 81), (1, 0, 2, 82), (1, 2, 2, 77),
        (2, 0, 1, 77), (2, 1, 1, 79), (2, 2, 2, 81), (3, 0, 2, 79), (3, 2, 1, 76), (3, 3, 1, 72),
        (4, 0, 2, 74), (4, 2, 2, 79), (5, 0, 1, 77), (5, 1, 1, 74), (5, 2, 2, 70),
        (6, 0, 2, 73), (6, 2, 1, 76), (6, 3, 1, 79), (7, 0, 4, 81)]

TUNE_CHORDS = ['Dm', 'Bb', 'F', 'C', 'Gm', 'Bb', 'A', 'A']


def fight_plan():
    """the bars: (chord, part). I intro, A main, B main with the tune, K the break, C the climax, O the way back"""
    plan = [(c, 'I') for c in ['Dm', 'Dm', 'Bb', 'A']]
    plan += [(c, 'A') for c in ['Dm', 'Dm', 'Bb', 'Bb', 'Gm', 'Gm', 'A', 'A'] * 2]
    plan += [(c, 'B') for c in TUNE_CHORDS]
    plan += [(c, 'K') for c in ['Gm/D', 'Gm/D', 'Dm', 'Dm', 'Bb', 'Bb', 'A', 'A']]
    plan += [(c, 'C') for c in TUNE_CHORDS * 2]
    plan += [(c, 'O') for c in ['Dm', 'Bb', 'Gm', 'A']]
    return plan


def fight():
    seed('fight')
    bpm = 100
    beat = 60 / bpm
    bar = 4 * beat
    plan = fight_plan()
    M = Mix(len(plan) * bar)
    first = {}
    for i, (_, part) in enumerate(plan):
        first.setdefault(part, i)
    arp = [0, 1, 2, 3, 2, 1, 2, 3, 0, 1, 2, 3, 2, 3, 2, 1]
    for i, (ch, part) in enumerate(plan):
        t0 = i * bar
        notes = CH[ch]
        root = ROOT_[ch]
        j = i - first[part]
        last_of_part = i + 1 == len(plan) or plan[i + 1][1] != part
        big = part in 'C'
        drive = part in 'ABC'

        # the choir: airy and high in the intro and the break, full in the main parts and the climax
        if part in 'IK':
            v = 'u'
            g = 0.3 if part == 'I' else 0.26
            hi = [n + 12 for n in notes[1:]]
            M.wide('pads', choir([midi(n) for n in hi], bar + 1.2, v, attack=1.2, release=1.2, vib=0.004),
                   choir([midi(n) for n in hi], bar + 1.2, v, attack=1.2, release=1.2, vib=0.004), t0, g, wet=0.7)
        else:
            g = {'A': 0.5, 'B': 0.52, 'C': 0.62, 'O': 0.42}[part]
            v = 'a' if big else 'o'
            M.wide('pads', choir([midi(n) for n in notes], bar + 0.8, v, attack=0.3, release=0.8),
                   choir([midi(n) for n in notes], bar + 0.8, v, attack=0.3, release=0.8), t0, g, wet=0.5)
            if big:
                hi = [n + 12 for n in notes[1:]]
                M.wide('pads', choir([midi(n) for n in hi], bar + 0.8, 'a', attack=0.3, release=0.8),
                       choir([midi(n) for n in hi], bar + 0.8, 'a', attack=0.3, release=0.8), t0, 0.32, wet=0.55)

        # low strings hold the chord under the main parts; in the break they shiver, high and quiet
        if part in 'ABCO':
            left = strings(midi(notes[0] + 12), bar + 0.3, 0.08, 0.4) + strings(midi(notes[2]), bar + 0.3, 0.08, 0.4)
            right = strings(midi(notes[1] + 12), bar + 0.3, 0.08, 0.4) + strings(midi(notes[3]), bar + 0.3, 0.08, 0.4)
            M.wide('strings', left, right, t0, 0.28 if not big else 0.36, wet=0.35)
        if part == 'K':
            trem = 0.6 + 0.4 * np.sin(2 * np.pi * 11 * t_(bar + 0.5))
            hi = strings(midi(notes[3] + 24), bar + 0.5, 0.4, 0.4, bright=1.3) * trem
            lo = strings(midi(notes[0]), bar + 0.5, 0.6, 0.5, bright=0.6)
            M.wide('strings', hi + lo, strings(midi(notes[2] + 24), bar + 0.5, 0.4, 0.4, bright=1.3) * trem + lo,
                   t0, 0.16 + 0.02 * j, wet=0.55)

        # the running figure: bounced strings on the chord, sixteenths
        if drive:
            for k in range(16):
                n = notes[arp[k]] + 12 + (12 if big and k % 4 == 2 else 0)
                acc = 1.0 if k % 4 == 0 else 0.65 if k % 2 == 0 else 0.45
                M.add('strings', spiccato(midi(n), 0.2, acc), t0 + k * beat / 4,
                      pan=0.45 if k % 2 else -0.35, gain=0.42 if not big else 0.45, wet=0.3)

        # bass
        if drive:
            for k in range(8):
                n = root + (12 if k in (3, 7) else 0)
                M.add('bass', bass(midi(n), beat / 2 * 0.9), t0 + k * beat / 2,
                      gain=0.55 if k % 2 == 0 else 0.42, wet=0.0)
        elif part == 'O':
            M.add('bass', bass(midi(root), bar * 0.97, drive=1.2, cutoff=260), t0, gain=0.5, wet=0.05)
        elif part == 'K':
            M.add('bass', bass(midi(root), beat * 0.9, drive=1.1, cutoff=200), t0, gain=0.5, wet=0.05)
            M.add('bass', bass(midi(root), beat * 0.9, drive=1.1, cutoff=200), t0 + 2 * beat, gain=0.35, wet=0.05)
        elif part == 'I' and j >= 2:
            M.add('bass', bass(midi(root), bar * 0.97, drive=1.1, cutoff=220), t0, gain=0.25, wet=0.1)

        # the bells: his voice. A toll every bar of the intro and the break, two a bar in the climax
        if part in 'IKO' or (part in 'AB' and j % 2 == 0):
            M.add('bells', bell(midi(root + 12), 7.0, bright=0.9), t0, pan=0.15,
                  gain=0.45 if part in 'IK' else 0.6, wet=0.6)
        if big:
            M.add('bells', bell(midi(root + 12), 6.0, bright=1.0), t0, pan=-0.15, gain=0.6, wet=0.55)
            M.add('bells', bell(midi(notes[2] + 12), 4.5, bright=1.1), t0 + 2 * beat, pan=0.35, gain=0.4, wet=0.55)
        # glass running high over the top
        if part in 'BC' or (part in 'IK' and j % 2 == 1):
            pat = [3, 2, 1, 2, 3, 2, 1, 0] if part != 'K' else [3, 1, 2, 0]
            step = beat / 2 if part in 'BC' else beat
            for k, p in enumerate(pat):
                M.add('bells', glass(midi(notes[p] + 24), 1.4, hard=0.7 if k % 2 == 0 else 0.5), t0 + k * step,
                      pan=(-0.7 if k % 2 else 0.7), gain=0.13, wet=0.55)

        # brass: stabs that push the main parts on, long calls in the intro and on the way back, the tune at the top
        if part == 'A':
            for b, ln in ((0, 0.5), (1.5, 0.25), (2.5, 0.5)) if j % 2 == 0 else ((0, 0.25), (3, 0.5)):
                for n in notes[:3]:
                    M.add('brass', brass(midi(n), ln * beat + 0.15, bright=0.8), t0 + b * beat,
                          pan=-0.2 + 0.2 * (n % 3), gain=0.38, wet=0.35)
        if part == 'I' and j in (1, 3):
            for n in (notes[0], notes[2]):
                M.add('brass', brass(midi(n), bar * 0.9, bright=0.5, attack=0.4, release=0.8), t0, gain=0.22, wet=0.5)
        if part == 'O':
            for n in notes[:3]:
                M.add('brass', brass(midi(n), bar * 0.95, bright=0.6 - 0.1 * j, attack=0.15, release=1.0), t0,
                      pan=-0.3 + 0.3 * (n % 3), gain=0.32 - 0.04 * j, wet=0.5)
        if part in 'BC':
            tune = MEL if part == 'B' else MEL2
            jj = j % 8
            for (mb, bt, ln, n) in tune:
                if mb != jj:
                    continue
                d = ln * beat + 0.15
                at = t0 + bt * beat
                if part == 'B':
                    M.add('lead', brass(midi(n - 12), d, bright=0.55, attack=0.06, release=0.3), at,
                          pan=-0.1, gain=0.42, wet=0.4)
                    M.add('lead', choir([midi(n)], d + 0.2, 'a', attack=0.08, release=0.35, vib=0.006, voices=4), at,
                          pan=0.1, gain=0.42, wet=0.45)
                else:
                    M.add('lead', brass(midi(n - 12), d, bright=0.9, attack=0.04, release=0.3), at,
                          pan=-0.15, gain=0.5, wet=0.4)
                    M.add('lead', brass(midi(n - 24), d, bright=0.7, attack=0.05, release=0.3), at,
                          pan=0.15, gain=0.32, wet=0.4)
                    M.add('lead', choir([midi(n)], d + 0.2, 'a', attack=0.06, release=0.35, vib=0.006, voices=4), at,
                          gain=0.5, wet=0.45)
                    if j >= 8:
                        # second time round the choir sings a third under it too
                        M.add('lead', choir([midi(n - 4)], d + 0.2, 'o', attack=0.06, release=0.35, voices=4), at,
                              pan=-0.3, gain=0.28, wet=0.45)

        # drums
        if part == 'A' or part == 'B':
            for b, g in ((0, 1.0), (1.5, 0.7), (2, 0.85), (3.75, 0.5)):
                M.add('drums', kick(), t0 + b * beat, gain=g * 0.85, wet=0.05)
            for b in (1, 3):
                M.add('drums', snare(clap=True), t0 + b * beat, pan=0.05, gain=0.6, wet=0.2)
            for k in range(16):
                M.add('drums', hat(), t0 + k * beat / 4 + (0.012 if k % 2 else 0), pan=0.35,
                      gain=0.35 if k % 4 == 0 else 0.22 if k % 2 == 0 else 0.14, wet=0.08)
            for k in range(8):
                M.add('drums', shaker(), t0 + k * beat / 2 + beat / 4, pan=-0.45, gain=0.3, wet=0.1)
            if j % 4 == 0:
                M.add('drums', boom(), t0, gain=0.8, wet=0.25)
            if j % 4 == 3:
                for k, p in enumerate((1.3, 1.15, 1.0, 0.85)):
                    M.add('drums', tom(p), t0 + 3 * beat + k * beat / 4, pan=0.4 - 0.25 * k, gain=0.7, wet=0.2)
        if big:
            for b, g in ((0, 1.0), (0.75, 0.6), (1.5, 0.75), (2, 0.95), (2.75, 0.6), (3.5, 0.75)):
                M.add('drums', kick(), t0 + b * beat, gain=g * 0.85, wet=0.05)
            for b in (1, 3):
                M.add('drums', snare(clap=True), t0 + b * beat, gain=0.65, wet=0.22)
                M.add('drums', tom(0.9), t0 + b * beat, pan=-0.3, gain=0.45, wet=0.2)
                M.add('drums', tom(1.2), t0 + b * beat, pan=0.3, gain=0.4, wet=0.2)
            for k in range(16):
                M.add('drums', hat(open_=(k % 8 == 6)), t0 + k * beat / 4 + (0.012 if k % 2 else 0), pan=0.3,
                      gain=0.36 if k % 4 == 0 else 0.24 if k % 2 == 0 else 0.16, wet=0.08)
            M.add('drums', boom(pitch=1.05), t0, gain=0.75 if j % 2 == 0 else 0.5, wet=0.25)
            if j % 4 == 0:
                M.add('fx', crash(), t0, pan=0.25, gain=0.5, wet=0.4)
            if j % 8 == 7:
                for k in range(8):
                    M.add('drums', tom(1.35 - 0.07 * k), t0 + 2 * beat + k * beat / 4, pan=0.5 - 0.14 * k,
                          gain=0.55 + 0.04 * k, wet=0.2)
        if part == 'K':
            # a heartbeat, and a pulse of low toms
            M.add('drums', boom(1.6, pitch=0.9), t0, gain=0.45, wet=0.35)
            M.add('drums', boom(1.2, pitch=0.9), t0 + 0.3 * beat, gain=0.28, wet=0.35)
            if j >= 4:
                for k in range(4):
                    M.add('drums', tom(0.8), t0 + k * beat + 0.5 * beat, pan=-0.3, gain=0.25 + 0.03 * j, wet=0.3)
        if part == 'I':
            if j >= 2:
                M.add('drums', boom(2.0, pitch=0.95), t0, gain=0.42, wet=0.4)
            if j == 3:
                for k in range(16):
                    M.add('drums', snare(0.25), t0 + 2 * beat + k * beat / 8, pan=0.1, gain=0.12 + 0.025 * k, wet=0.3)
        if part == 'O':
            M.add('drums', boom(2.2), t0, gain=0.7 - 0.08 * j, wet=0.35)
            M.add('drums', tom(0.85), t0 + 2 * beat, gain=0.4, wet=0.3)

        # risers into the parts that start big, an impact when they land, and the bells swelling backwards into them
        if last_of_part and i + 1 < len(plan):
            nxt = plan[i + 1][1]
            if nxt in 'AC':
                ln = bar * (2 if nxt == 'C' else 1)
                M.add('fx', riser(ln, 1.0 if nxt == 'C' else 0.7), t0 + bar - ln, gain=0.55, wet=0.4)
                rb = swell(bell(midi(ROOT_[plan[i + 1][0]] + 12), 3.0, bright=1.2))
                M.add('fx', rb, t0 + bar - len(rb) / SR, pan=0.0, gain=0.45, wet=0.3)
            if nxt == 'K':
                M.add('fx', boom(3.0, pitch=0.85), t0 + bar, gain=0.7, wet=0.5)
        if j == 0 and part in 'AC':
            M.add('fx', boom(3.0, pitch=0.8), t0, gain=0.95 if part == 'C' else 0.75, wet=0.45)
            M.add('fx', crash(), t0, pan=-0.2, gain=0.6, wet=0.5)
        if i == len(plan) - 1:
            # back round to the top: the first toll of the intro grows out of this
            rb = swell(bell(midi(38 + 12), 3.0, bright=1.0))
            M.add('fx', rb, t0 + bar - len(rb) / SR, gain=0.4, wet=0.3)

    ir = reverb_ir(4.2, 3.4, echo=[(beat * 1.5, 0.7), (beat * 3, 0.4)])
    return mixdown(M, ir, {
        'drums': dict(gain=0.8, lo=38, comp=(-14, 3.0, 5, 120)),
        'bass': dict(gain=0.55, lo=35, hi=900, comp=(-16, 3.0, 10, 150)),
        'pads': dict(gain=2.4, lo=160, comp=(-20, 2.0, 30, 300)),
        'strings': dict(gain=0.9, lo=150),
        'brass': dict(gain=1.0, lo=120),
        'lead': dict(gain=1.0, lo=120, comp=(-18, 2.5, 15, 200)),
        'bells': dict(gain=0.9, lo=140),
        'fx': dict(gain=0.9, lo=30),
    }, target=-14.0, duck=('drums', 3.0))


def mixdown(M, ir, buses, target, duck=None, ret=0.2, ret_lo=200, ret_hi=9000, glue=(-12, 1.8, 20, 250)):
    """each bus cleaned and squeezed, the shared echo added, the whole glued, then levelled and limited"""
    out = np.zeros((M.n, 2))
    duck_by = None
    if duck:
        # the pads and strings dip a little under each drum hit, so the low end stays clear
        d = M.bus.get(duck[0])
        if d is not None:
            env = circ(lambda v: lowpass(np.abs(v), 8, 1), d.mean(axis=1), 2.0)
            env = env / (np.percentile(env, 99) + 1e-9)
            duck_by = 10 ** (-duck[1] * np.clip(env, 0, 1) / 20)
    for name, x in M.bus.items():
        o = buses.get(name, {})
        y = round_eq(x, lo=o.get('lo'), hi=o.get('hi'))
        if 'comp' in o:
            y = compress(y, *o['comp'])
        if duck_by is not None and name in ('pads', 'strings'):
            y = y * duck_by[:, None]
        out += y * o.get('gain', 1.0)
    wet = round_eq(round_conv(M.send, ir), lo=ret_lo, hi=ret_hi) * ret
    out += wet
    out = compress(out, *glue)
    return master(out, target)


# the ground's music

def ground():
    seed('ground')
    step = 7.5
    prog = [('Dm', 'u'), ('Bb', 'o'), ('Gm', 'u'), ('A', 'o'), ('Dm', 'u'), ('F', 'o'), ('C', 'u'), ('Am', 'o'),
            ('Bb', 'u'), ('Gm', 'o'), ('Dm/F', 'u'), ('A', 'u'), ('Gm/D', 'o'), ('A', 'u')]
    M = Mix(len(prog) * step)
    penta = [62, 65, 67, 69, 72, 74, 77, 79, 81]
    # a slow, soft tune now and then, on a breathy glass flute
    phrases = {2: [(0.0, 2.0, 74), (2.0, 1.0, 77), (3.0, 3.0, 76)],
               6: [(0.0, 1.5, 79), (1.5, 1.5, 77), (3.0, 3.5, 72)],
               9: [(0.0, 2.0, 70), (2.0, 1.0, 72), (3.0, 3.5, 74)],
               12: [(0.0, 2.0, 69), (2.0, 2.0, 74), (4.0, 3.0, 73)]}
    for i, (ch, v) in enumerate(prog):
        t0 = i * step
        notes = CH[ch]
        M.wide('pads', choir([midi(n) for n in notes], step + 3.0, v, attack=2.5, release=3.0, vib=0.002, air=0.04),
               choir([midi(n) for n in notes], step + 3.0, v, attack=2.5, release=3.0, vib=0.002, air=0.04),
               t0, 0.45, wet=0.6)
        M.wide('strings', strings(midi(notes[1] + 12), step + 2.5, 2.5, 2.5, bright=0.5, vib=0.003),
               strings(midi(notes[3]), step + 2.5, 2.5, 2.5, bright=0.5, vib=0.003), t0 + 0.4, 0.3, wet=0.6)
        b = bass(midi(ROOT_[ch]), step * 0.98, drive=1.05, cutoff=200)
        M.add('bass', b * env_adsr(len(b), 1.8, 0.5, 0.8, 2.2), t0, gain=0.5, wet=0.2)
        if i % 2 == 0:
            M.add('bells', lowpass(bell(midi(ROOT_[ch] + 12), 9.0, bright=0.5, strike=0.3), 2200), t0 + 0.2,
                  pan=0.4 * (1 if i % 4 else -1), gain=0.42, wet=0.8)
        # soft glass notes, a few a chord
        for k in range(int(rng.integers(2, 4))):
            at = t0 + 0.5 + rng.random() * (step - 1)
            M.add('bells', lowpass(glass(midi(int(rng.choice(penta)) + 12), 3.0, hard=0.3), 4500), at,
                  pan=rng.uniform(-0.8, 0.8), gain=0.1, wet=0.8)
        for at, ln, n in phrases.get(i, []):
            M.add('lead', flute(midi(n), ln + 0.4), t0 + at, pan=0.15, gain=0.32, wet=0.6)
    ir = reverb_ir(5.5, 4.8, echo=[(0.62, 0.6), (1.24, 0.35)], bright=0.35)
    return mixdown(M, ir, {
        'pads': dict(gain=2.4, lo=140, hi=6000),
        'strings': dict(gain=1.0, lo=120, hi=5000),
        'bass': dict(gain=0.6, lo=35, hi=500),
        'bells': dict(gain=1.0, lo=100, hi=7000),
        'lead': dict(gain=1.0, lo=200, hi=7000),
    }, target=-18.0, ret=0.4, ret_lo=150, ret_hi=7000, glue=(-20, 1.5, 40, 400))


def flute(f, dur):
    t = t_(dur)
    vib = 1 + 0.004 * np.clip((t - 0.4) / 0.6, 0, 1) * np.sin(2 * np.pi * 4.8 * t)
    ph = 2 * np.pi * np.cumsum(f * vib) / SR
    x = np.sin(ph) + 0.18 * np.sin(2 * ph) + 0.06 * np.sin(3 * ph)
    x += 0.12 * bandpass(rng.standard_normal(len(t)), f * 0.8, f * 3) * (0.6 + 0.4 * np.exp(-t * 3))
    return x * env_adsr(len(t), 0.18, 0.2, 0.85, 0.5) * 0.5


# the ground's sounds

def round_band(n, lo, hi):
    """noise in a band that joins up with itself at the ends (made in the frequency domain)"""
    X = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    X *= 1 / np.sqrt(1 + (lo / np.maximum(f, 1e-3)) ** 4) / np.sqrt(1 + (f / hi) ** 4)
    x = np.fft.irfft(X, n=n)
    return x / np.std(x)


def ambient_loop():
    seed('ambient')
    sec = 64.0
    M = Mix(sec)
    n = M.n
    t = np.arange(n) / SR
    # air moving through a huge glass: soft noise swells, each speed a whole number of times round the loop
    for c, pan in ((0, -0.7), (1, 0.7)):
        sw = 0.6 + 0.25 * np.sin(2 * np.pi * t * (3 / sec) + c * 1.7)
        sw += 0.15 * np.sin(2 * np.pi * t * (7 / sec) + 0.4 + c)
        M.add('air', round_band(n, 180, 1100) * sw * 0.25, 0, pan=pan, wet=0.3)
    # a low hum, whole cycles in the loop, breathing slowly
    hum = sum(a * np.sin(2 * np.pi * f * t) for f, a in ((73.125, 0.5), (146.25, 0.22), (219.375, 0.08)))
    hum *= 0.75 + 0.25 * np.sin(2 * np.pi * t / sec * 4)
    M.add('hum', hum * 0.3, 0, wet=0.3)
    # very soft glass far away, no strike: they swell in and ring out
    for k in range(12):
        g = lowpass(glass(midi(int(rng.choice([74, 77, 79, 81, 84]))), 3.0, hard=0.2), 3000)
        a = int(0.04 * SR)
        g[:a] *= np.linspace(0, 1, a) ** 2
        M.add('air', g, (k + rng.random() * 0.6) * sec / 12, pan=rng.uniform(-0.9, 0.9), gain=0.05, wet=0.9)
    ir = reverb_ir(4.0, 3.5, bright=0.2)
    x = mixdown(M, ir, {'air': dict(hi=6500), 'hum': dict(lo=40)}, target=-24.0, ret=0.4, ret_lo=150, ret_hi=6000,
                glue=(-30, 1.2, 50, 500))
    return round_eq(x, hi=7000)


def one_shot(x, tail=3.0, wet=0.5, rt=3.0, echo=None, target=-27.0, hi=7000):
    """a mono sound on its own (not a loop), with its own echo after it, levelled by loudness"""
    n = len(x) + int(tail * SR)
    y = np.zeros(n)
    y[:len(x)] = x
    ir = reverb_ir(tail, rt, echo=echo)[:, 0]
    out = y * (1 - wet * 0.5) + signal.fftconvolve(y, ir)[:n] * wet
    out = out - out.mean()
    fade = int(0.4 * SR)
    out[-fade:] *= np.linspace(1, 0, fade) ** 2
    out = lowpass(highpass(out, 30), hi, 4)
    out *= 10 ** ((target - lufs(out, SR)[0]) / 20)
    pk = np.max(np.abs(out))
    return out if pk < 0.7 else out * 0.7 / pk


def soft(x, ms=25):
    a = int(ms / 1000 * SR)
    x[:a] *= np.linspace(0, 1, a) ** 2
    return x


def mood(k):
    """a bell a long way off: five ways it can sound"""
    seed(f'mood{k}')
    if k == 0:
        x = soft(lowpass(bell(midi(38), 9.0, bright=0.4, strike=0.2), 1200))
        echo = [(0.9, 0.6), (1.8, 0.35)]
    elif k == 1:
        x = np.zeros(int(11 * SR))
        for at, n in ((0.0, 38), (3.2, 45)):
            b = soft(lowpass(bell(midi(n), 7.5, bright=0.4, strike=0.2), 1300))
            s = int(at * SR)
            x[s:s + len(b)] += b * (1.0 if at == 0 else 0.7)
        echo = [(1.1, 0.5)]
    elif k == 2:
        x = soft(lowpass(bell(midi(36), 9.0, bright=0.4, strike=0.2), 1100))
        c = choir([midi(50), midi(57)], 8.0, 'u', attack=3.0, release=3.0, vib=0.002, air=0.02, voices=3)
        x[:len(c)] += 0.5 * lowpass(c, 2000)
        echo = [(1.3, 0.4)]
    elif k == 3:
        t = t_(10.0)
        x = sum(a * np.sin(2 * np.pi * f * t) for f, a in ((73.4, 0.5), (110.0, 0.3), (146.8, 0.15)))
        x *= np.sin(np.pi * t / 10.0) ** 2
        for at, n in ((2.5, 81), (4.0, 77), (5.8, 74)):
            g = soft(lowpass(glass(midi(n), 3.0, hard=0.2), 3000), 40)
            s = int(at * SR)
            x[s:s + len(g)] += 0.15 * g[:len(x) - s]
        echo = None
    else:
        x = np.zeros(int(12 * SR))
        for i, (at, n) in enumerate(((0.0, 45), (2.6, 41), (5.2, 38))):
            b = soft(lowpass(bell(midi(n), 6.5, bright=0.35, strike=0.2), 1200))
            s = int(at * SR)
            x[s:s + len(b)] += b * (0.8 - 0.1 * i)
        echo = [(0.8, 0.4)]
    return one_shot(x, 5.0, wet=0.75, rt=4.5, echo=echo, target=-27.0, hi=3000)


def chime(k):
    """a few soft glass notes"""
    seed(f'chime{k}')
    notes = [[81, 84, 88], [79, 86, 91], [84, 81, 77, 74], [77, 81, 84], [86, 84, 79, 81]][k]
    gap = [0.11, 0.16, 0.13, 0.2, 0.12][k]
    x = np.zeros(int((2.0 + gap * len(notes)) * SR))
    for i, n in enumerate(notes):
        g = soft(lowpass(glass(midi(n), 1.8, hard=0.35), 5000), 6)
        s = int((gap * i + 0.03 * rng.random()) * SR)
        m = min(len(g), len(x) - s)
        x[s:s + m] += g[:m] * (1 - 0.12 * i)
    return one_shot(x, 2.5, wet=0.65, rt=2.5, target=-29.0, hi=6500)


# the egg rain's sounds (mono, placed in the world)

def egg_one_shot(x, tail, wet, rt, target):
    """the egg sounds keep their own peak levelling"""
    n = len(x) + int(tail * SR)
    y = np.zeros(n)
    y[:len(x)] = x
    ir = reverb_ir(tail, rt)[:, 0]
    out = y * (1 - wet * 0.5) + signal.fftconvolve(y, ir)[:n] * wet
    out = out - out.mean()
    fade = int(0.3 * SR)
    out[-fade:] *= np.linspace(1, 0, fade)
    out = highpass(out, 25)
    return out * 10 ** (target / 20) / np.max(np.abs(out))


def egg_wind():
    seed('egg_wind')
    dur = 2.3
    t = t_(dur)
    k = t / dur
    # the clumps rattling, faster and faster
    x = np.zeros_like(t)
    at = 0.0
    while at < dur - 0.05:
        s = int(at * SR)
        m = int(0.02 * SR)
        c = bandpass(rng.standard_normal(m), 1800, 7000) * np.exp(-np.arange(m) / (0.003 * SR))
        m = min(len(c), len(x) - s)
        x[s:s + m] += c[:m] * (0.3 + 0.7 * at / dur)
        at += 0.11 * (1 - 0.8 * at / dur) + 0.01 * rng.random()
    # a glassy shiver rising under it
    sh = np.zeros_like(t)
    for f0 in (420, 427, 633, 841):
        f = f0 * (1 + 1.6 * k ** 1.6)
        sh += np.sin(2 * np.pi * np.cumsum(f) / SR + rng.random() * 6)
    trem = 0.6 + 0.4 * np.sin(2 * np.pi * np.cumsum(6 + 18 * k) / SR)
    sh *= trem * (k ** 1.3) * 0.25
    air = lowpass(rng.standard_normal(len(t)), 600) * (k ** 2) * 0.6
    y = x * 0.9 + sh + air
    y[-int(0.08 * SR):] *= np.linspace(1, 0, int(0.08 * SR))
    return egg_one_shot(y, 1.2, 0.35, 1.5, -2.0)


def egg_splat():
    seed('egg_splat')
    dur = 0.9
    t = t_(dur)
    f = 38 + 110 * np.exp(-t * 22)
    thud = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 7)
    squelch = lowpass(rng.standard_normal(len(t)), 900) * np.exp(-t * 9) * (0.6 + 0.4 * np.sin(2 * np.pi * 23 * t))
    crack = bandpass(rng.standard_normal(len(t)), 2500, 9000) * np.exp(-t * 55)
    return egg_one_shot(thud + squelch * 0.9 + crack * 0.5, 0.8, 0.2, 0.9, -1.5)


def egg_hatch():
    seed('egg_hatch')
    dur = 0.9
    y = np.zeros(int(dur * SR))
    for i in range(6):
        s = int((0.03 + 0.05 * i + 0.02 * rng.random()) * SR)
        m = int(0.03 * SR)
        c = bandpass(rng.standard_normal(m), 1500, 8000) * np.exp(-np.arange(m) / (0.004 * SR))
        y[s:s + len(c)] += c * (0.5 + 0.1 * i)
    # the little glassy chirp of the Belling
    t = t_(0.35)
    f = 900 + 1400 * (t / 0.35) ** 0.7
    chirp = (np.sin(2 * np.pi * np.cumsum(f) / SR + 1.5 * np.sin(2 * np.pi * np.cumsum(f * 2.0) / SR))
             * np.sin(np.pi * t / 0.35) ** 0.5)
    s = int(0.38 * SR)
    y[s:s + len(chirp)] += chirp * 0.6
    return egg_one_shot(y, 0.8, 0.3, 0.8, -2.0)


def main(which):
    if 'fight' in which:
        write('music/fight', fight(), quality=0.25)
    if 'ground' in which:
        write('music/ground', ground(), quality=0.15)
    if 'ambient' in which:
        write('ambient/ground', ambient_loop(), quality=0.1)
    if 'mood' in which:
        for k in range(5):
            write(f'ambient/mood{k + 1}', mood(k), quality=0.2)
    if 'chimes' in which:
        for k in range(5):
            write(f'ambient/chime{k + 1}', chime(k), quality=0.3)
    if 'eggs' in which:
        write('egg_wind', egg_wind(), quality=0.4)
        write('egg_splat', egg_splat(), quality=0.4)
        write('egg_hatch', egg_hatch(), quality=0.4)


if __name__ == '__main__':
    main(set(sys.argv[1:]) or {'fight', 'ground', 'ambient', 'mood', 'chimes', 'eggs'})
