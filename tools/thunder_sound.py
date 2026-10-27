"""Synthesizes Another Weather Mod's thunder for assets/anotherweathermod/sounds/thunder/:

- distant_1..3: far-off thunder, a low roll that swells in over half a second or so and grumbles away in a few
  bumps over six to eight seconds, no crack at all
- near_1..2: a strike close by: a tearing crack, then the same rolling rumble, heavier

    pip install numpy soundfile
    python tools/thunder_sound.py

Mono (Minecraft only places mono sounds in the world). StormLightning plays them from the storm's direction.
"""
from pathlib import Path

import numpy as np
import soundfile as sf

RATE = 44100
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/anotherweathermod/sounds/thunder"


def lowpass(x, cutoff):
    """A gentle FFT low-pass (a Gaussian roll-off above `cutoff` Hz)."""
    spectrum = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(x.size, 1 / RATE)
    spectrum *= np.exp(-(np.maximum(freqs - cutoff, 0) / (cutoff * 0.6)) ** 2)
    return np.fft.irfft(spectrum, x.size)


def band(x, low, high):
    spectrum = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(x.size, 1 / RATE)
    spectrum *= np.exp(-(np.maximum(low - freqs, 0) / (low * 0.5 + 1)) ** 2)
    spectrum *= np.exp(-(np.maximum(freqs - high, 0) / (high * 0.5)) ** 2)
    return np.fft.irfft(spectrum, x.size)


def rumble(rng, seconds, attack, cutoff=140.0):
    """Rolling thunder: brown noise, low-passed, under an envelope of a soft swell and a few decaying rolls."""
    n = int(RATE * seconds)
    t = np.arange(n) / RATE
    brown = np.cumsum(rng.standard_normal(n))
    brown -= np.convolve(brown, np.ones(4410) / 4410, mode="same")  # Keep it from wandering off zero.
    low = lowpass(brown, cutoff)
    mid = band(rng.standard_normal(n), 120, 420) * 0.25  # A little grit on top.
    body = low / np.max(np.abs(low)) + mid / np.max(np.abs(mid)) * 0.25
    # The rolls: a handful of soft bumps, each a little later and fainter, the way echoes off the cloud base come in.
    rolls = np.zeros(n)
    start = attack * 0.6
    for k in range(rng.integers(3, 6)):
        centre = start + k * rng.uniform(0.6, 1.4)
        width = rng.uniform(0.35, 0.9)
        rolls += np.exp(-((t - centre) / width) ** 2) * (0.85 ** k) * rng.uniform(0.6, 1.0)
    swell = 1 - np.exp(-t / attack)
    decay = np.exp(-t / (seconds * 0.38))
    env = swell * decay * (0.35 + rolls)
    # A slow flutter in the loudness, the rumble breaking up as it rolls.
    env *= 0.8 + 0.2 * np.sin(2 * np.pi * rng.uniform(1.5, 3.5) * t + rng.uniform(0, 6))
    wave = body * env
    fade = np.minimum(1, (n - np.arange(n)) / (RATE * 0.6))  # End in silence.
    return wave * fade


def crack(rng, seconds):
    """A near strike's tearing crack: a sharp broadband burst and a few rattling after-cracks."""
    n = int(RATE * seconds)
    t = np.arange(n) / RATE
    noise = band(rng.standard_normal(n), 300, 6000)
    env = np.exp(-t / 0.06) + 0.5 * np.exp(-((t - 0.12) / 0.05) ** 2) + 0.3 * np.exp(-((t - 0.27) / 0.07) ** 2)
    env *= 1 - np.exp(-t / 0.003)
    return noise / np.max(np.abs(noise)) * env


def write(name, wave, peak):
    wave = wave / np.max(np.abs(wave)) * peak
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / f"{name}.ogg"
    sf.write(path, wave.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    print("wrote", path)


def main():
    rng = np.random.default_rng(1883)
    for i in range(1, 4):
        seconds = rng.uniform(6.0, 8.0)
        write(f"distant_{i}", rumble(rng, seconds, attack=rng.uniform(0.45, 0.9)), 0.8)
    for i in range(1, 3):
        seconds = rng.uniform(6.5, 8.5)
        roll = rumble(rng, seconds, attack=0.25, cutoff=190.0)
        hit = crack(rng, seconds)
        write(f"near_{i}", roll / np.max(np.abs(roll)) * 0.8 + hit * 0.55, 0.9)


if __name__ == "__main__":
    main()
