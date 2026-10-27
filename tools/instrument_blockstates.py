"""Writes the weather instruments' blockstates, item models, loot tables and mining tag for Another Weather Mod.

    python tools/instrument_blockstates.py

The models come from weather_instruments_model.py. The wind vane arrow and the siren horn turn on 16 points from four
model frames, drawn at element angles 0, 22.5, 45 and -22.5 degrees, plus the blockstate's y rotation. An element's
y angle turns counter-clockwise seen from above and the blockstate's y turns clockwise, so frame f under rotation r
points (r - FRAMES[f]) degrees clockwise from north. Wall instruments are drawn facing south.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
ASSETS = ROOT / "assets/anotherweathermod"
DATA = ROOT / "data/anotherweathermod"
NS = "anotherweathermod:block/"
FRAMES = (0.0, 22.5, 45.0, -22.5)
FACING_Y = {"south": 0, "west": 90, "north": 180, "east": 270}
INSTRUMENTS = ("wind_vane", "barometer", "thermometer", "rain_gauge", "solar_detector", "tornado_sensor", "tornado_siren")


def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def frame_for(point):
    """The model frame and y rotation that point 22.5 * point degrees clockwise from north."""
    for f, angle in enumerate(FRAMES):
        for r in (0, 90, 180, 270):
            if round((r - angle) % 360 / 22.5) % 16 == point:
                return f, r
    raise ValueError(point)


def apply(model, y=0):
    a = {"model": NS + model}
    if y:
        a["y"] = y
    return a


def turning(base_model, part):
    parts = [{"apply": apply(base_model)}]
    for point in range(16):
        f, r = frame_for(point)
        parts.append({"when": {"direction": str(point)}, "apply": apply(f"{part}_{f}", r)})
    return {"multipart": parts}


def wall(base, part, prop, count):
    parts = []
    for facing, y in FACING_Y.items():
        parts.append({"when": {"facing": facing}, "apply": apply(base, y)})
        for i in range(count):
            parts.append({"when": {"facing": facing, prop: str(i)}, "apply": apply(f"{part}_{i}", y)})
    return {"multipart": parts}


def blockstates():
    bs = ASSETS / "blockstates"
    save(bs / "wind_vane.json", turning("wind_vane_base", "wind_vane_arrow"))
    save(bs / "tornado_siren.json", {"multipart": [{"when": {"half": "lower"}, "apply": apply("tornado_siren_lower")}]
         + [{"when": {"half": "upper", "direction": str(p)}, "apply": apply(f"tornado_siren_upper_{frame_for(p)[0]}",
                                                                               frame_for(p)[1])} for p in range(16)]})
    save(bs / "barometer.json", wall("barometer_base", "barometer_needle", "needle", 5))
    save(bs / "thermometer.json", wall("thermometer_base", "thermometer_column", "column", 5))
    water = [{"when": {"fill": "|".join(str(f) for f in range(4 * level - 3, min(15, 4 * level) + 1))},
              "apply": apply(f"rain_gauge_water_{level}")} for level in range(1, 5)]
    save(bs / "rain_gauge.json", {"multipart": [{"apply": apply("rain_gauge")}] + water})
    save(bs / "solar_detector.json", {"variants": {"": apply("solar_detector")}})
    save(bs / "tornado_sensor.json", {"variants": {"": apply("tornado_sensor")}})


def item_models():
    for name in INSTRUMENTS:
        model = {"parent": NS + name}
        if name == "tornado_siren":  # Two blocks tall: shrink it to fit.
            model["display"] = {
                "gui": {"rotation": [30, 225, 0], "translation": [0, -3, 0], "scale": [0.4, 0.4, 0.4]},
                "ground": {"translation": [0, 2, 0], "scale": [0.2, 0.2, 0.2]},
                "fixed": {"translation": [0, -3, 0], "scale": [0.4, 0.4, 0.4]},
                "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1, 0], "scale": [0.25, 0.25, 0.25]},
                "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
                "head": {"translation": [0, 8, 0], "scale": [0.5, 0.5, 0.5]},
            }
        save(ASSETS / "models/item" / f"{name}.json", model)


def loot_tables():
    for name in INSTRUMENTS:
        entry = {"type": "minecraft:item", "name": f"anotherweathermod:{name}"}
        conditions = [{"condition": "minecraft:survives_explosion"}]
        if name == "tornado_siren":
            conditions.append({"condition": "minecraft:block_state_property", "block": "anotherweathermod:tornado_siren",
                               "properties": {"half": "lower"}})
        save(DATA / "loot_table/blocks" / f"{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [entry], "conditions": conditions}],
            "random_sequence": f"anotherweathermod:blocks/{name}"})


def tags():
    save(ROOT / "data/minecraft/tags/block/mineable/pickaxe.json",
         {"replace": False, "values": [f"anotherweathermod:{name}" for name in INSTRUMENTS]})


if __name__ == "__main__":
    blockstates()
    item_models()
    loot_tables()
    tags()
