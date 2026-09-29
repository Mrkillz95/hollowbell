#!/usr/bin/env python3
"""Makes the Hollowbell's music and the sounds of his ground, from scratch (no recordings from anywhere), as .ogg
files in src/main/resources/assets/hollowbell/sounds/:

  music/fight.ogg        his fight theme: stereo, 120 s, loops. Tolling bells, a choir, glass, a hollow echo.
  music/ground.ogg       the Bell Hollows' own music: calmer and sparser, 90 s, loops.
  ambient/ground.ogg     the ground's background loop (air through glass, a low hum, far-off drips), 32 s.
  ambient/mood.ogg       a deep toll a long way off, with its echo (the ground's "mood" sound).
  ambient/chime1-3.ogg   little glass chimes now and then (the ground's "additions").
  egg_wind.ogg           the egg rain's wind-up: the clumps rattle and a glassy shiver rises
  egg_splat.ogg          an egg clump hitting the ground
  egg_hatch.ogg          a Belling cracking out of its egg

Everything is seeded, so it makes the same files every time. The loops are made "round": every note that runs
off the end carries on at the start, and the echo is worked out round the loop too, so there is no seam.

    pip install numpy scipy soundfile
    python3 tools/sounds/make_music.py && python3 tools/sounds/check_sounds.py && python3 tools/sounds.py
"""
import os
import numpy as np
import soundfile as sf
from scipy import signal

ROOT = os.path.join(os.path.dirname(__file__), '..', '..')
OUT = os.path.join(ROOT, 'src/main/resources/assets/hollowbell/sounds')
SR = 32000
rng = np.random.default_rng(20260929)


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def t_(sec):
    return np.arange(int(sec * SR)) / SR


def env_adsr(n, a, d, s, r, sus_len=None):
    """attack / decay / sustain level / release, in seconds; n samples total"""
    e = np.zeros(n)
    A, D, R = int(a * SR), int(d * SR), int(r * SR)
    A = max(1, min(A, n)); D = max(1, min(D, n - A))
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
    return signal.lfilter(b, a, x)


def highpass(x, fc, order=2):
    b, a = signal.butter(order, fc / (SR / 2), 'high')
    return signal.lfilter(b, a, x)


def bandpass(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), min(hi, SR * 0.45) / (SR / 2)], 'band')
    return signal.lfilter(b, a, x)


def peak(x, f, q):
    b, a = signal.iirpeak(f / (SR / 2), q)
    return signal.lfilter(b, a, x)


# ------------------------------------------------------------------ instruments (mono)

# a church bell's partials (hum, prime, tierce, quint, nominal, ...) and how long each rings, as a share
BELL = [(0.5, 0.38, 1.0), (1.0, 0.7, 0.8), (1.19, 0.5, 0.55), (1.5, 0.3, 0.45), (2.0, 0.85, 0.5),
        (2.51, 0.3, 0.3), (3.0, 0.3, 0.22), (4.07, 0.22, 0.14), (5.4, 0.12, 0.09), (6.8, 0.07, 0.06)]


def bell(f, dur=7.0, bright=1.0):
    t = t_(dur)
    x = np.zeros_like(t)
    for ratio, amp, life in BELL:
        fr = f * ratio
        if fr > SR * 0.42: continue
        tau = dur * life * 0.45
        a = amp * (bright if ratio > 2.2 else 1.0)
        # each partial is really two, a hair apart: the slow beating a big bell has
        beat = 0.15 + 0.35 * rng.random()
        ph = rng.random() * 6.28
        x += a * np.exp(-t / tau) * (np.sin(2 * np.pi * fr * t + ph) + 0.6 * np.sin(2 * np.pi * (fr + beat) * t + ph * 1.3))
    # the strike
    n = int(0.03 * SR)
    hit = rng.standard_normal(n) * np.exp(-np.arange(n) / (0.006 * SR))
    x[:n] += 0.4 * bright * bandpass(hit, 1500, 6000)
    x[:int(0.004 * SR)] *= np.linspace(0, 1, int(0.004 * SR))
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


def choir(freqs, dur, vowel='a', attack=0.35, release=0.9, vib=0.0035, air=0.08):
    """a choir chord: each note three slightly different voices, shaped by the vowel"""
    F = {'a': [(730, 9), (1090, 10), (2440, 14)], 'o': [(470, 8), (820, 9), (2600, 14)],
         'u': [(330, 7), (740, 8), (2400, 13)], 'e': [(530, 8), (1840, 12), (2480, 14)]}[vowel]
    t = t_(dur)
    raw = np.zeros_like(t)
    for f in freqs:
        for k in range(3):
            det = 1 + (k - 1) * 0.0045 + rng.normal(0, 0.0008)
            vr = 4.6 + rng.random() * 1.2
            vdel = np.clip((t - 0.25) / 0.6, 0, 1)
            fm = f * det * (1 + vib * vdel * np.sin(2 * np.pi * vr * t + rng.random() * 6.28))
            ph = 2 * np.pi * np.cumsum(fm) / SR + rng.random() * 6.28
            raw += signal.sawtooth(ph) * (0.8 + 0.4 * rng.random())
    raw = lowpass(raw, 4200, 2)
    y = np.zeros_like(raw)
    for fc, q in F:
        y += peak(raw, fc, q) * (1.0 if fc < 1500 else 0.55)
    # breath
    y += air * bandpass(rng.standard_normal(len(t)), 800, 3000) * 0.5
    e = env_adsr(len(t), attack, 0.3, 0.85, release)
    return y * e / (len(freqs) * 3) * 1.6


def bass(f, dur, drive=1.6):
    t = t_(dur)
    x = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(2 * np.pi * 2 * f * t + 0.3) + 0.12 * np.sin(2 * np.pi * 3 * f * t)
    x = np.tanh(drive * x) / np.tanh(drive)
    return x * env_adsr(len(t), 0.02, 0.2, 0.75, 0.3) * 0.5


def taiko(dur=1.3, pitch=1.0):
    t = t_(dur)
    f = (42 + 70 * np.exp(-t * 16)) * pitch
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 4.2)
    n = rng.standard_normal(len(t)) * np.exp(-t * 28)
    x += 0.6 * lowpass(n, 350) + 0.15 * bandpass(n, 900, 3000) * np.exp(-t * 40)
    return x * 0.9


def hollow_drum(dur=0.6):
    """a frame drum with a hollow ring, like knocking on his dome"""
    t = t_(dur)
    x = (np.sin(2 * np.pi * 118 * t) * np.exp(-t * 9) + 0.6 * np.sin(2 * np.pi * 187 * t) * np.exp(-t * 12)
         + 0.3 * np.sin(2 * np.pi * 263 * t) * np.exp(-t * 16))
    n = rng.standard_normal(len(t)) * np.exp(-t * 45)
    x += 0.5 * bandpass(n, 300, 2500)
    return x * 0.5


def tick(dur=0.12):
    t = t_(dur)
    x = bandpass(rng.standard_normal(len(t)), 5000, 11000) * np.exp(-t * 60)
    x += 0.4 * np.sin(2 * np.pi * 3150 * t) * np.exp(-t * 40)
    return x * 0.35


# ------------------------------------------------------------------ a round buffer (the end runs into the start)

class Loop:
    def __init__(self, seconds):
        self.n = int(seconds * SR)
        self.buf = np.zeros((self.n, 2))
        self.send = np.zeros((self.n, 2))

    def add(self, x, at, pan=0.0, gain=1.0, wet=0.3):
        """a mono sound at `at` seconds, panned -1..1; whatever runs past the end comes in at the start"""
        th = (pan + 1) * np.pi / 4
        lr = np.array([np.cos(th), np.sin(th)]) * gain
        s = int(round(at * SR)) % self.n
        pos, i = s, 0
        while i < len(x):
            m = min(len(x) - i, self.n - pos)
            seg = x[i:i + m, None] * lr[None, :]
            self.buf[pos:pos + m] += seg * (1 - wet * 0.5)
            self.send[pos:pos + m] += seg * wet
            i += m
            pos = 0

    def render(self, ir):
        # the echo, worked out round the loop (so it runs over the seam as it would in the middle)
        out = self.buf.copy()
        for c in range(2):
            X = np.fft.rfft(self.send[:, c])
            H = np.fft.rfft(ir[:, c], n=self.n)
            out[:, c] += np.fft.irfft(X * H, n=self.n)
        return out


def reverb_ir(seconds=4.0, rt60=3.4, echo=None, bright=0.5):
    """a big hollow room: a burst of early reflections, a long soft tail, and (if asked) a clear echo or two"""
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = np.zeros((n, 2))
    for c in range(2):
        noise = rng.standard_normal(n)
        dark = lowpass(noise, 2500)
        lite = highpass(noise, 2500)
        tail = (dark * np.exp(-6.9 * t / rt60) + bright * lite * np.exp(-6.9 * t / (rt60 * 0.45)))
        pre = int(0.022 * SR)
        tail[:pre] = 0
        tail[pre:pre + 400] *= np.linspace(0, 1, 400)
        ir[:, c] = tail * 0.05
        for k in range(10):
            d = int((0.008 + 0.05 * rng.random()) * SR)
            ir[d, c] += (0.5 - 0.035 * k) * (1 if rng.random() > 0.3 else -1)
        if echo:
            for k, (d, g) in enumerate(echo):
                j = int(d * SR) + c * 37
                if j < n:
                    burst = lowpass(rng.standard_normal(300), 3000) * np.hanning(300)
                    ir[j:j + 300, c] += burst * g * 0.12
    return ir


def finish(x, target_db=-1.5):
    """no DC, nothing below 28 Hz, peak at the target"""
    x = x - x.mean(axis=0)
    # zero-phase and round, so the loop stays seamless
    X = np.fft.rfft(x, axis=0)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= (1 / np.sqrt(1 + (28 / np.maximum(f, 1e-3)) ** 8))[:, None]
    x = np.fft.irfft(X, n=len(x), axis=0)
    # a gentle limiter, then the peak
    pk = np.max(np.abs(x))
    x = x / pk
    x = np.tanh(1.4 * x) / np.tanh(1.4)
    return x * 10 ** (target_db / 20) / np.max(np.abs(x))


def write(name, x, quality=0.4, sr=SR):
    path = os.path.join(OUT, name + '.ogg')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    x = x.astype(np.float32)
    ch = 1 if x.ndim == 1 else x.shape[1]
    # written a block at a time (the Vorbis writer falls over on one huge write)
    with sf.SoundFile(path, 'w', sr, ch, format='OGG', subtype='VORBIS', compression_level=1 - quality) as f:
        for i in range(0, len(x), 4096):
            f.write(x[i:i + 4096])
    print(f'{name}.ogg  {len(x) / sr:.1f} s  {os.path.getsize(path) // 1024} KB')


# ------------------------------------------------------------------ the fight theme

CH = {'Dm': [50, 53, 57, 62], 'Bb': [46, 53, 58, 62], 'Gm': [43, 50, 55, 58], 'A': [45, 52, 57, 61],
      'F': [41, 48, 53, 57], 'C': [48, 55, 60, 64], 'Dm/F': [41, 50, 57, 62]}
ROOT_ = {'Dm': 38, 'Bb': 34, 'Gm': 31, 'A': 33, 'F': 29, 'C': 36, 'Dm/F': 29}


def fight():
    bpm = 96
    beat = 60 / bpm
    bar = 4 * beat
    A = ['Dm', 'Dm', 'Bb', 'Bb', 'Gm', 'Gm', 'A', 'A'] * 2
    B = ['Dm', 'Bb', 'F', 'C', 'Gm', 'Bb', 'A', 'A'] * 2
    C = ['Bb', 'Bb', 'Gm', 'Gm', 'Dm', 'Dm', 'A', 'A']
    D = ['Dm', 'Bb', 'F', 'C', 'Gm', 'Bb', 'A', 'A']
    bars = A + B + C + D
    sec = ['A'] * 16 + ['B'] * 16 + ['C'] * 8 + ['D'] * 8
    L = Loop(len(bars) * bar)
    # the melody over the eight bars of B and D: (bar, beat, beats long, note)
    mel = [(0, 0, 2, 69), (0, 2, 2, 74), (1, 0, 1.5, 72), (1, 1.5, 0.5, 70), (1, 2, 2, 69),
           (2, 0, 1, 69), (2, 1, 1, 72), (2, 2, 2, 77), (3, 0, 2, 76), (3, 2, 1, 74), (3, 3, 1, 72),
           (4, 0, 3, 74), (4, 3, 1, 70), (5, 0, 1, 72), (5, 1, 1, 74), (5, 2, 2, 77),
           (6, 0, 2, 76), (6, 2, 2, 73), (7, 0, 4, 69)]
    mel2 = [(0, 0, 1, 74), (0, 1, 1, 77), (0, 2, 2, 81), (1, 0, 2, 82), (1, 2, 2, 77),
            (2, 0, 1, 77), (2, 1, 1, 79), (2, 2, 2, 81), (3, 0, 2, 79), (3, 2, 1, 76), (3, 3, 1, 72),
            (4, 0, 2, 74), (4, 2, 2, 79), (5, 0, 1, 77), (5, 1, 1, 74), (5, 2, 2, 70),
            (6, 0, 2, 73), (6, 2, 1, 76), (6, 3, 1, 79), (7, 0, 4, 81)]
    for i, (ch, s) in enumerate(zip(bars, sec)):
        t0 = i * bar
        notes = CH[ch]
        root = ROOT_[ch]
        # the choir pad under everything
        pad_gain = {'A': 0.5, 'B': 0.75, 'C': 0.65, 'D': 0.9}[s]
        L.add(choir([midi(n) for n in notes], bar + 0.9, 'o', attack=0.5, release=0.9), t0, pan=-0.25, gain=pad_gain, wet=0.45)
        L.add(choir([midi(n + 12) for n in notes[1:]], bar + 0.9, 'u', attack=0.6, release=0.9), t0, pan=0.3, gain=pad_gain * 0.45, wet=0.5)
        # bass
        if s != 'C':
            for b in range(4):
                if s == 'A' and b % 2: continue
                L.add(bass(midi(root), beat * (2 if s == 'A' else 1) * 0.95), t0 + b * beat, gain=0.4)
        else:
            L.add(bass(midi(root), bar * 0.98, drive=1.1), t0, gain=0.4)
        # the bells: a toll on the first beat, and in the big parts an answer on the third
        if s in 'ABD' or i % 2 == 0:
            L.add(bell(midi(root + 12), 7.5, bright=0.9), t0, pan=0.1, gain=0.8 if s != 'C' else 1.0, wet=0.55)
        if s == 'D':
            L.add(bell(midi(notes[2] + 12), 5.0, bright=1.1), t0 + 2 * beat, pan=-0.35, gain=0.45, wet=0.5)
        # glass running up and down the chord
        if s in 'ABD':
            pat = [0, 1, 2, 3, 2, 1, 2, 3] if s != 'A' else [0, 2, 3, 2, 1, 2, 3, 2]
            for k in range(8):
                n = notes[pat[k]] + 24
                acc = 1.0 if k % 2 == 0 else 0.7
                L.add(glass(midi(n), 1.6, hard=acc), t0 + k * beat / 2, pan=(-0.6 if k % 2 else 0.6) * 0.8,
                      gain=(0.19 if s == 'A' else 0.24) * acc, wet=0.45)
        elif i % 2 == 1:
            for k, n in enumerate([notes[3] + 24, notes[2] + 24, notes[1] + 24]):
                L.add(glass(midi(n), 2.5, hard=0.6), t0 + k * beat * 1.5, pan=0.5 - k * 0.5, gain=0.15, wet=0.7)
        # drums
        if s == 'A':
            L.add(taiko(), t0, gain=0.9, wet=0.25); L.add(taiko(pitch=1.1), t0 + 2 * beat, gain=0.7, wet=0.25)
            L.add(hollow_drum(), t0 + beat, pan=0.3, gain=0.35); L.add(hollow_drum(), t0 + 3 * beat, pan=-0.3, gain=0.35)
            if i % 4 == 3: L.add(taiko(pitch=1.25), t0 + 3.5 * beat, gain=0.55, wet=0.25)
        elif s in 'BD':
            for b, g in [(0, 1.0), (1.5, 0.6), (2, 0.85), (3.5, 0.5)]:
                L.add(taiko(pitch=1.0 + 0.1 * (b % 2)), t0 + b * beat, gain=g, wet=0.25)
            for b in (1, 3):
                L.add(hollow_drum(), t0 + b * beat, pan=0.2, gain=0.5)
            for k in range(8):
                L.add(tick(), t0 + k * beat / 2 + (0.02 if k % 2 else 0), pan=0.5 if k % 2 else -0.4, gain=0.45 if k % 2 == 0 else 0.3, wet=0.2)
            if s == 'D' and i == len(bars) - 1:
                for k in range(8):   # a roll into the top of the loop
                    L.add(taiko(0.7, pitch=1.1 + 0.03 * k), t0 + 2 * beat + k * beat / 4, gain=0.35 + 0.07 * k, wet=0.25)
        else:
            # the break: only a heartbeat under the bells
            L.add(taiko(pitch=0.9), t0, gain=0.7, wet=0.35); L.add(taiko(pitch=0.9), t0 + 0.3 * beat, gain=0.45, wet=0.35)
        # the melody, sung
        if s in 'BD':
            j = (i - 16) % 8 if s == 'B' else i - 40
            which = mel if (s == 'B' and i < 24) else mel2 if s == 'D' else mel
            for (mb, bt, ln, n) in which:
                if mb != j: continue
                L.add(choir([midi(n)], ln * beat + 0.3, 'a', attack=0.12, release=0.35, vib=0.006, air=0.12),
                      t0 + bt * beat, pan=0.0, gain=0.55 if s == 'B' else 0.6, wet=0.45)
                if s == 'D':
                    L.add(glass(midi(n + 12), 1.2, hard=0.8), t0 + bt * beat, pan=0.25, gain=0.12, wet=0.5)
    ir = reverb_ir(4.2, 3.6, echo=[(beat * 1.5, 0.9), (beat * 3, 0.55), (beat * 4.5, 0.3)])
    return finish(L.render(ir))


# ------------------------------------------------------------------ the ground's music

def ground():
    step = 7.5
    prog = [('Dm', 'u'), ('Bb', 'o'), ('Gm', 'u'), ('A', 'o'), ('Dm', 'u'), ('F', 'o'), ('C', 'u'), ('A', 'o'),
            ('Bb', 'u'), ('Gm', 'o'), ('Dm/F', 'u'), ('A', 'u')]
    L = Loop(len(prog) * step)
    penta = [62, 65, 67, 69, 72, 74, 77, 79, 81]
    for i, (ch, v) in enumerate(prog):
        t0 = i * step
        notes = CH[ch]
        L.add(choir([midi(n) for n in notes], step + 2.5, v, attack=2.0, release=2.5, vib=0.002, air=0.05), t0, pan=-0.2, gain=0.55, wet=0.6)
        L.add(choir([midi(n + 12) for n in notes[2:]], step + 2.0, 'u', attack=2.5, release=2.0, vib=0.002), t0 + 0.8, pan=0.35, gain=0.25, wet=0.7)
        L.add(bass(midi(ROOT_[ch]), step * 0.98, drive=1.05) * env_adsr(int((step * 0.98) * SR), 1.5, 0.5, 0.8, 2.0), t0, gain=0.35, wet=0.3)
        if i % 2 == 0:
            L.add(lowpass(bell(midi(ROOT_[ch] + 12), 9.0, bright=0.6), 2500), t0 + 0.2, pan=0.4 * (1 if i % 4 else -1), gain=0.55, wet=0.8)
        # a few glass notes, now and then
        for k in range(rng.integers(2, 5)):
            at = t0 + rng.random() * step
            L.add(glass(midi(int(rng.choice(penta)) + 12), 2.8, hard=0.4), at, pan=rng.uniform(-0.8, 0.8), gain=0.12, wet=0.8)
    ir = reverb_ir(5.5, 4.8, echo=[(0.62, 0.8), (1.24, 0.5), (1.86, 0.3)], bright=0.4)
    return finish(L.render(ir), -2.0)


# ------------------------------------------------------------------ the ground's sounds

def round_band(n, lo, hi):
    """noise in a band that joins up with itself at the ends (made in the frequency domain)"""
    X = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    X *= 1 / np.sqrt(1 + (lo / np.maximum(f, 1e-3)) ** 4) / np.sqrt(1 + (f / hi) ** 4)
    x = np.fft.irfft(X, n=n)
    return x / np.std(x)


def ambient_loop():
    sec = 32.0
    L = Loop(sec)
    n = L.n
    t = np.arange(n) / SR
    # air moving through a huge glass: soft noise swells that loop round (the swell's speed divides the loop)
    for c, pan in ((0, -0.6), (1, 0.6)):
        swell = 0.55 + 0.45 * np.sin(2 * np.pi * t * (3 / sec) + c * 1.7) * np.sin(2 * np.pi * t * (2 / sec) + 0.4)
        air = round_band(n, 250, 1400) * swell * 0.25
        L.add(air, 0, pan=pan, gain=1.0, wet=0.3)
    # a low hum, whole cycles in the loop
    hum = sum(a * np.sin(2 * np.pi * f * t) for f, a in ((73.125, 0.5), (146.25, 0.25), (219.375, 0.12), (292.5, 0.06)))
    hum *= 0.7 + 0.3 * np.sin(2 * np.pi * t / sec * 4)
    L.add(hum * 0.35, 0, gain=1.0, wet=0.3)
    # far-off drips of glass
    for k in range(9):
        L.add(glass(midi(int(rng.choice([74, 77, 79, 81, 84, 86]))), 2.0, hard=0.3), rng.random() * sec,
              pan=rng.uniform(-0.9, 0.9), gain=0.08, wet=0.9)
    ir = reverb_ir(4.0, 3.5, bright=0.3)
    return finish(L.render(ir), -8.0)


def one_shot(x, tail=3.0, wet=0.5, rt=3.0, echo=None, target=-3.0, mono=True):
    """a sound on its own (not a loop), with its own echo after it"""
    n = len(x) + int(tail * SR)
    y = np.zeros(n)
    y[:len(x)] = x
    ir = reverb_ir(tail, rt, echo=echo)[:, 0]
    wetsig = signal.fftconvolve(y, ir)[:n]
    out = y * (1 - wet * 0.5) + wetsig * wet
    out = out - out.mean()
    fade = int(0.3 * SR)
    out[-fade:] *= np.linspace(1, 0, fade)
    out = highpass(out, 25)
    return out * 10 ** (target / 20) / np.max(np.abs(out))


def mood():
    x = lowpass(bell(midi(38), 8.0, bright=0.5), 1400)
    return one_shot(x, 5.0, wet=0.8, rt=4.5, echo=[(0.9, 0.8), (1.8, 0.5), (2.7, 0.3)], target=-4.0)


def chime(k):
    x = np.zeros(int(1.5 * SR))
    notes = [[81, 84, 88], [79, 86, 91], [84, 81, 77, 74]][k]
    for i, n in enumerate(notes):
        g = glass(midi(n), 1.5, hard=0.5)
        s = int((0.09 * i + 0.03 * rng.random()) * SR)
        m = min(len(g), len(x) - s)
        x[s:s + m] += g[:m] * (1 - 0.15 * i)
    return one_shot(x, 2.5, wet=0.6, rt=2.5, target=-6.0)


# ------------------------------------------------------------------ the egg rain's sounds (mono, placed in the world)

def egg_wind():
    dur = 2.3
    t = t_(dur)
    k = t / dur
    # the clumps rattling, faster and faster
    x = np.zeros_like(t)
    at = 0.0
    while at < dur - 0.05:
        s = int(at * SR)
        c = bandpass(rng.standard_normal(int(0.02 * SR)), 1800, 7000) * np.exp(-np.arange(int(0.02 * SR)) / (0.003 * SR))
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
    # and a low swell of air
    air = lowpass(rng.standard_normal(len(t)), 600) * (k ** 2) * 0.6
    y = x * 0.9 + sh + air
    y[-int(0.08 * SR):] *= np.linspace(1, 0, int(0.08 * SR))
    return one_shot(y, 1.2, wet=0.35, rt=1.5, target=-2.0)


def egg_splat():
    dur = 0.9
    t = t_(dur)
    f = 38 + 110 * np.exp(-t * 22)
    thud = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 7)
    squelch = lowpass(rng.standard_normal(len(t)), 900) * np.exp(-t * 9) * (0.6 + 0.4 * np.sin(2 * np.pi * 23 * t))
    crack = bandpass(rng.standard_normal(len(t)), 2500, 9000) * np.exp(-t * 55)
    y = thud * 1.0 + squelch * 0.9 + crack * 0.5
    return one_shot(y, 0.8, wet=0.2, rt=0.9, target=-1.5)


def egg_hatch():
    dur = 0.9
    y = np.zeros(int(dur * SR))
    for i in range(6):
        s = int((0.03 + 0.05 * i + 0.02 * rng.random()) * SR)
        c = bandpass(rng.standard_normal(int(0.03 * SR)), 1500, 8000) * np.exp(-np.arange(int(0.03 * SR)) / (0.004 * SR))
        y[s:s + len(c)] += c * (0.5 + 0.1 * i)
    # the little glassy chirp of the Belling
    t = t_(0.35)
    f = 900 + 1400 * (t / 0.35) ** 0.7
    chirp = np.sin(2 * np.pi * np.cumsum(f) / SR + 1.5 * np.sin(2 * np.pi * np.cumsum(f * 2.0) / SR)) * np.sin(np.pi * t / 0.35) ** 0.5
    s = int(0.38 * SR)
    y[s:s + len(chirp)] += chirp * 0.6
    return one_shot(y, 0.8, wet=0.3, rt=0.8, target=-2.0)


def main():
    write('music/fight', fight(), quality=0.25)
    write('music/ground', ground(), quality=0.15)
    write('ambient/ground', ambient_loop(), quality=0.1)
    write('ambient/mood', mood(), quality=0.2)
    for k in range(3): write(f'ambient/chime{k + 1}', chime(k), quality=0.3)
    write('egg_wind', egg_wind(), quality=0.4)
    write('egg_splat', egg_splat(), quality=0.4)
    write('egg_hatch', egg_hatch(), quality=0.4)


if __name__ == '__main__':
    main()
