#!/usr/bin/env python3
"""Makes Hollowbell's own sounds from scratch (no recordings from anywhere), as mono .ogg files in
src/main/resources/assets/hollowbell/sounds/:

  hum        a low, slowly breathing drone (loops)
  drift      wet drifting: soft water noise with bubbles now and then (loops)
  pulse      the whoosh of the bell squeezing
  heartbeat  a muffled heartbeat, heard from inside the dome
  echo       the hollow ring of the dome around you
  toll       a deep bell toll, made from a bell's own overtones

    pip install numpy soundfile
    python3 tools/make_sounds.py && python3 tools/sounds.py
"""
import os
import numpy as np
import soundfile as sf

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/hollowbell/sounds')
rng = np.random.default_rng(20260925)


def t_(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def lowpass(x, cutoff):
    """one-pole low-pass, cutoff in Hz (a number or one per sample)"""
    c = np.broadcast_to(np.asarray(cutoff, dtype=float), x.shape)
    a = 1.0 - np.exp(-2.0 * np.pi * c / RATE)
    y = np.empty_like(x)
    s = 0.0
    for i in range(len(x)):
        s += a[i] * (x[i] - s)
        y[i] = s
    return y


def bandpass(x, centre, q=2.0):
    """two-pole resonant band-pass (a state-variable filter), centre in Hz (a number or one per sample)"""
    c = np.broadcast_to(np.asarray(centre, dtype=float), x.shape)
    f = 2.0 * np.sin(np.pi * np.minimum(c, RATE / 6) / RATE)
    damp = 1.0 / q
    low = band = 0.0
    y = np.empty_like(x)
    for i in range(len(x)):
        high = x[i] - low - damp * band
        band += f[i] * high
        low += f[i] * band
        y[i] = band
    return y


def fft_filter(x, lo, hi):
    """keeps only lo..hi Hz (for looping sounds: a filter in the frequency domain wraps round cleanly)"""
    X = np.fft.rfft(x)
    fr = np.fft.rfftfreq(len(x), 1 / RATE)
    X[(fr < lo) | (fr > hi)] = 0
    return np.fft.irfft(X, len(x))


def reverb(x, seconds=1.2, wet=0.35, damp=0.4):
    """a simple room: a handful of feedback delays, darkened"""
    n = len(x) + int(seconds * RATE)
    y = np.zeros(n)
    y[:len(x)] = x
    out = y.copy()
    for d_ms, g in ((29.7, 0.62), (37.1, 0.58), (41.1, 0.55), (43.7, 0.52)):
        d = int(d_ms * RATE / 1000)
        buf = np.zeros(n)
        for i in range(d, n):
            buf[i] = y[i - d] + g * buf[i - d]
        out += wet * 0.25 * lowpass(buf, 2500 * (1 - damp) + 300)
    return out


def normalise(x, peak=0.9):
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def fade(x, a=0.01, b=0.05):
    n = len(x)
    e = np.ones(n)
    ia, ib = int(a * RATE), int(b * RATE)
    if ia: e[:ia] = np.linspace(0, 1, ia)
    if ib: e[-ib:] = np.linspace(1, 0, ib)
    return x * e


def save(name, x):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + '.ogg')
    sf.write(path, normalise(x).astype(np.float32), RATE, format='OGG', subtype='VORBIS')
    print(f'{name}.ogg  {len(x) / RATE:.1f} s  {os.path.getsize(path) // 1024} KB')


def hum():
    # 8 seconds that loop: every tone makes a whole number of cycles in 8 s
    T = 8.0
    t = t_(T)
    x = (np.sin(2 * np.pi * 41.25 * t) + 0.8 * np.sin(2 * np.pi * 41.5 * t)          # a slow beat between two
         + 0.45 * np.sin(2 * np.pi * 82.5 * t + 0.3) + 0.2 * np.sin(2 * np.pi * 123.75 * t + 1.1)
         + 0.12 * np.sin(2 * np.pi * 165.0 * t + 2.0))
    # breathing: swells twice in the loop
    breath = 0.75 + 0.25 * np.sin(2 * np.pi * t / 4.0)
    air = fft_filter(rng.standard_normal(len(t)), 60, 300) * 0.25
    return (x * breath + air * (0.6 + 0.4 * breath))


def drift():
    T = 6.0
    t = t_(T)
    water = fft_filter(rng.standard_normal(len(t)), 80, 900)
    swell = 0.7 + 0.3 * np.sin(2 * np.pi * t / 3.0) * np.sin(2 * np.pi * t / 2.0)
    x = water * swell
    # a few bubbles: short rising blips
    for start in (0.4, 1.3, 1.45, 2.9, 3.6, 4.8, 5.1):
        n = int(0.09 * RATE)
        tt = np.arange(n) / RATE
        f = 350 + 2600 * tt
        blip = np.sin(2 * np.pi * np.cumsum(f) / RATE) * np.exp(-tt * 40)
        i = int(start * RATE)
        x[i:i + n] += 1.8 * blip[:len(x) - i]
    # the ends meet: cross-fade the last half second over the start
    k = int(0.5 * RATE)
    w = np.linspace(0, 1, k)
    x[:k] = x[:k] * w + x[-k:] * (1 - w)
    return x[:-k]


def pulse():
    t = t_(1.8)
    noise = rng.standard_normal(len(t))
    # the squeeze: air rushing, the band sweeping up then down
    centre = 180 + 700 * np.sin(np.pi * np.clip(t / 1.2, 0, 1)) ** 1.5
    whoosh = bandpass(noise, centre, q=1.4)
    env = np.clip(t / 0.25, 0, 1) ** 2 * np.exp(-np.clip(t - 0.25, 0, None) * 2.6)
    thump = np.sin(2 * np.pi * (48 - 12 * t) * t) * np.exp(-t * 5)
    x = whoosh * env * 1.6 + thump * 0.8
    return fade(reverb(x, 0.8, 0.3), 0.005, 0.2)


def heartbeat():
    t = t_(1.3)
    def beat(at, f, k):
        tt = np.clip(t - at, 0, None)
        return (t >= at) * np.sin(2 * np.pi * f * tt) * np.exp(-tt * k) * np.clip(tt / 0.01, 0, 1)
    x = beat(0.0, 52, 11) * 1.0 + beat(0.26, 44, 13) * 0.7
    x = lowpass(x + 0.02 * rng.standard_normal(len(t)), 180)        # muffled, through his body
    return fade(reverb(x, 1.0, 0.45, 0.7), 0.002, 0.3)


def echo():
    t = t_(0.25)
    burst = rng.standard_normal(len(t)) * np.exp(-t * 30)
    x = bandpass(burst, 420, 6) + 0.6 * bandpass(burst, 610, 8)
    x = reverb(x, 3.0, 0.9, 0.5)
    return fade(lowpass(x, 1400), 0.002, 0.6)


def toll():
    # a bell's overtones (hum, prime, tierce, quint, nominal and up), each dying away at its own rate
    T = 7.0
    t = t_(T)
    base = 110.0
    partials = ((0.5, 1.0, 0.5), (1.0, 0.8, 0.8), (1.183, 0.55, 1.1), (1.506, 0.35, 1.4), (2.0, 0.6, 1.2),
                (2.514, 0.25, 2.2), (2.662, 0.2, 2.6), (3.011, 0.15, 3.0), (4.166, 0.1, 4.0))
    x = np.zeros(len(t))
    for ratio, amp, decay in partials:
        f = base * ratio
        # a slow wobble on each, like a real bell's beating
        x += amp * np.sin(2 * np.pi * f * t + 0.3 * np.sin(2 * np.pi * 0.7 * ratio * t)) * np.exp(-t * decay * 0.55)
    strike = bandpass(rng.standard_normal(len(t)), 1800, 1.5) * np.exp(-t * 60) * 0.6
    x = (x + strike) * np.clip(t / 0.004, 0, 1)
    return fade(reverb(x, 2.0, 0.3), 0.0, 0.5)


if __name__ == '__main__':
    save('hum', hum())
    save('drift', drift())
    save('pulse', pulse())
    save('heartbeat', heartbeat())
    save('echo', echo())
    save('toll', toll())
