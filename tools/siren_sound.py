"""Synthesizes the tornado siren's tone for assets/anotherweathermod/sounds/tornado_siren.ogg.

    pip install numpy soundfile
    python tools/siren_sound.py

A rotor siren at full speed: a buzzy tone with strong odd harmonics a little below 500 Hz and a second port a fifth
up, as two-tone sirens have. It is a steady two-second loop that repeats without a seam (every partial and every
flutter completes whole cycles in it). Everything that changes is done live by SirenSound: the pitch climbing from
half to full as the rotor winds up, the loudness, and the swell as the turning horn sweeps past. Mono, as Minecraft
only places mono sounds in the world. Swap in a real recording any time, as long as it loops cleanly and is the
siren at full speed.
"""
from pathlib import Path

import numpy as np
import soundfile as sf

RATE = 44100
SECONDS = 2.0
BASE = 470.0  # Hz at full speed; 940 cycles in the loop
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/anotherweathermod/sounds/tornado_siren.ogg"


def main():
    n = int(RATE * SECONDS)
    t = np.arange(n) / RATE
    # The rotor hunts a little: a pitch wobble of whole cycles in the loop (2 and 3 per loop), integrated so the phase
    # still closes.
    wobble = 4 * np.sin(2 * np.pi * 2 * t / SECONDS) + 2.5 * np.sin(2 * np.pi * 3 * t / SECONDS + 1)
    phase = 2 * np.pi * np.cumsum(BASE + wobble) / RATE
    phase *= (2 * np.pi * BASE * SECONDS) / phase[-1]  # Exactly whole cycles.
    tone = sum(a * np.sin(k * phase) for k, a in ((1, 1.0), (2, 0.16), (3, 0.42), (5, 0.22), (7, 0.1), (9, 0.05)))
    tone += 0.35 * sum(a * np.sin(k * 1.5 * phase) for k, a in ((1, 1.0), (3, 0.3)))
    # Ports chopping the air: a fast flutter, 40 cycles a loop.
    tone *= 0.9 + 0.1 * np.sin(2 * np.pi * 40 * t / SECONDS)
    # Rushing air, filtered in a circle so it loops too.
    rng = np.random.default_rng(7)
    spectrum = np.fft.rfft(rng.standard_normal(n))
    freqs = np.fft.rfftfreq(n, 1 / RATE)
    spectrum *= np.exp(-((freqs - 900) / 700) ** 2)
    air = np.fft.irfft(spectrum, n)
    air *= 0.06 / np.max(np.abs(air))
    wave = tone + air
    wave *= 0.85 / np.max(np.abs(wave))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sf.write(OUT, wave.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
