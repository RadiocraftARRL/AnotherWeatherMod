"""Outdoor warning sirens (2026-11-11, from the maintainer's photos): two blocks tall, the siren head alone (no pole:
it stands on whatever is under it, a fence post or a log, by a collar at its foot).

  omni_siren   a stacked omnidirectional siren: seven grey cells, each a squat disc with a dark mouth under its lip,
               on a diamond mounting plate
  horn_siren   a horn array: four square black horns stacked on each of the four sides of a galvanized column, on a
               grey junction box

Each also comes on a pole (2026-11-11): <name>_pole is 12 blocks tall, a steel control cabinet at the foot (it turns
the siren on from redstone or its key switch; its lamp lights while it sounds), nine blocks of wooden pole with a
conduit up the side, and the siren head on top. siren_pole.png has the cabinet and the pole.

Each head is drawn 32 units tall and split at y 16 into <name>_lower and <name>_upper (the block's two halves; faces cut
by the split keep their share of the texture); the item model is the whole thing, shrunk. Vanilla model features only
(y rotations of 45 degrees), so it loads on 1.21.1 as is.

Writes, under src/main/resources/assets/anotherweathermod/:
  textures/block/<name>.png                     64 x 64, 4 px a model unit
  models/block/<name>_lower.json, _upper.json, models/item/<name>.json, models/item/<name>_pole.json
  textures/block/siren_pole.png, models/block/siren_cabinet.json, siren_cabinet_on.json, siren_pole.json
  blockstates/<name>.json, <name>_pole.json
Run: python tools/siren_models.py [--preview DIR]
"""
import copy
import json
import os
import random
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "anotherweathermod")
SIDES = ("north", "east", "south", "west")


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def noisy(d, box, colour, spread, seed):
    """Fills box (x0, y0, x1, y1 inclusive) with colour, each pixel a little lighter or darker."""
    rnd = random.Random(seed)
    for y in range(box[1], box[3] + 1):
        for x in range(box[0], box[2] + 1):
            k = rnd.randint(-spread, spread)
            d.point((x, y), fill=tuple(max(0, min(255, c + k)) for c in colour[:3]) + (255,))


def uvs(px):
    return [round(v / 4.0, 4) for v in px]  # 64 px texture: UV 0 to 16


def element(name, frm, to, faces, rotation=None):
    """faces: face name -> tile (x0, y0, x1, y1 texture px)."""
    e = {"name": name, "from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to],
         "faces": {f: {"uv": uvs(t), "texture": "#0"} for f, t in faces.items()}}
    if rotation:
        e["rotation"] = rotation
    return e


def all_faces(side, top=None, bottom=None):
    out = {f: side for f in SIDES}
    out["up"] = top or side
    out["down"] = bottom or top or side
    return out


def y45(cx=8.0, cy=8.0, cz=8.0):
    return {"angle": 45, "axis": "y", "origin": [cx, cy, cz]}


def clip(elements, lo, hi):
    """The elements' parts between y lo and hi, moved down by lo. A side face cut short keeps the matching part of its
    UV (v runs top to bottom), so a cell's lip stays where it was drawn."""
    out = []
    for e in elements:
        y0, y1 = e["from"][1], e["to"][1]
        a, b = max(y0, lo), min(y1, hi)
        if b - a <= 1e-6:
            continue
        e = copy.deepcopy(e)
        for f, face in e["faces"].items():
            if f in SIDES and (a > y0 or b < y1):
                u0, v0, u1, v1 = face["uv"]
                top = v0 + (v1 - v0) * (y1 - b) / (y1 - y0)
                bottom = v0 + (v1 - v0) * (y1 - a) / (y1 - y0)
                face["uv"] = [u0, round(top, 4), u1, round(bottom, 4)]
        if a > y0:
            e["faces"].pop("down", None)  # The cut faces are inside the other half's piece.
        if b < y1:
            e["faces"].pop("up", None)
        e["from"][1], e["to"][1] = round(a - lo, 4), round(b - lo, 4)
        if "rotation" in e:
            e["rotation"]["origin"][1] = round(e["rotation"]["origin"][1] - lo, 4)
        out.append(e)
    return out


def model(credit, texture, elements, **extra):
    data = {"credit": credit, "parent": "block/block", "texture_size": [64, 64],
            "textures": {"0": "anotherweathermod:block/" + texture, "particle": "anotherweathermod:block/" + texture},
            "elements": elements}
    data.update(extra)
    return data


ITEM_DISPLAY = {  # The whole two-block siren, shrunk to an item.
    "gui": {"rotation": [30, 225, 0], "translation": [0, -2.5, 0], "scale": [0.42, 0.42, 0.42]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.18, 0.18, 0.18]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 1], "scale": [0.25, 0.25, 0.25]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 1], "scale": [0.25, 0.25, 0.25]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -1, 0], "scale": [0.25, 0.25, 0.25]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -1, 0], "scale": [0.25, 0.25, 0.25]},
}

# ---- The omnidirectional siren -------------------------------------------------------------------------------------
GREY = (168, 168, 158)
GREY_LIGHT = (196, 196, 186)
GREY_DARK = (128, 128, 120)
MOUTH = (44, 44, 42)
GALV = (150, 156, 160)

O_SIDE = (0, 0, 32, 15)      # a cell's side: lip at the top, the body, a shadow at the bottom (15 px: 3.7 units)
O_TOP = (32, 0, 48, 16)
O_BOTTOM = (48, 0, 64, 16)
O_NECK = (0, 16, 16, 18)     # the dark mouth between cells
O_MOUNT = (16, 16, 32, 32)   # the mounting plate and collar
O_PLATE_SIDE = (32, 16, 48, 20)

CELLS = 7
CELL_BASE = 2.5
CELL_BODY = 3.7
CELL_NECK = 0.5


def omni_texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    noisy(d, (0, 0, 31, 14), GREY, 4, 1)
    noisy(d, (0, 0, 31, 2), GREY_LIGHT, 3, 2)     # the lip
    d.line([0, 3, 31, 3], fill=GREY_DARK + (255,))  # its edge
    noisy(d, (0, 12, 31, 14), GREY_DARK, 3, 3)    # shadow towards the mouth below
    for x in (3, 13, 23):
        d.point((x, 1), fill=(110, 110, 104, 255))  # rivets on the lip
    noisy(d, (32, 0, 47, 15), GREY_LIGHT, 3, 4)
    noisy(d, (48, 0, 63, 15), GREY_DARK, 3, 5)
    noisy(d, (0, 16, 15, 17), MOUTH, 3, 6)
    noisy(d, (16, 16, 31, 31), GALV, 6, 7)
    for x, y in ((18, 18), (29, 18), (18, 29), (29, 29)):
        d.point((x, y), fill=(96, 100, 104, 255))  # bolts
    noisy(d, (32, 16, 47, 19), GALV, 6, 8)
    return img


def omni_elements():
    els = [
        element("collar", (5.0, 0.0, 5.0), (11.0, 1.5, 11.0), all_faces(O_PLATE_SIDE, O_MOUNT)),
        element("plate", (3.5, 1.5, 3.5), (12.5, 2.5, 12.5), all_faces(O_PLATE_SIDE, O_MOUNT), y45(8, 2, 8)),
    ]
    for i in range(CELLS):
        y0 = CELL_BASE + i * (CELL_BODY + CELL_NECK)
        y1 = y0 + CELL_BODY
        faces = all_faces(O_SIDE, O_TOP, O_BOTTOM)
        # A round disc from a cross and a turned square, each a hair taller than the last so no tops share a plane.
        els.append(element("cell%d_a" % i, (2.5, y0, 4.5), (13.5, y1, 11.5), faces))
        els.append(element("cell%d_b" % i, (4.5, y0 - 0.01, 2.5), (11.5, y1 + 0.01, 13.5), faces))
        els.append(element("cell%d_c" % i, (4.0, y0 - 0.02, 4.0), (12.0, y1 + 0.02, 12.0), faces, y45(8, y0, 8)))
        if i < CELLS - 1:
            els.append(element("neck%d" % i, (4.0, y1, 4.0), (12.0, y1 + CELL_NECK, 12.0), all_faces(O_NECK, O_BOTTOM)))
    return els


# ---- The horn array -------------------------------------------------------------------------------------------------
HORN = (34, 34, 36)
HORN_LIGHT = (66, 66, 70)
THROAT = (12, 12, 13)
COLUMN = (158, 162, 164)
BOX = (176, 178, 176)

H_MOUTH = (0, 0, 40, 27)     # a horn's mouth, 10 x 6.75 units
H_SIDE = (40, 0, 56, 16)
H_COLUMN = (0, 32, 16, 48)
H_BOX = (16, 32, 32, 48)
H_BOX_TOP = (32, 32, 48, 48)

HORNS = 4
HORN_BASE = 3.0
HORN_TALL = 6.75
HORN_GAP = 0.25


def horn_texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    noisy(d, (0, 0, 39, 26), HORN_LIGHT, 3, 11)  # the rim
    for k, colour in enumerate(((40, 40, 42), (28, 28, 30), (20, 20, 22), (14, 14, 15))):  # the flare going in
        inset = 2 + k * 3
        d.rectangle([inset, inset * 27 // 40 + 1, 39 - inset, 26 - inset * 27 // 40 - 1], fill=colour + (255,))
    d.rectangle([16, 9, 23, 17], fill=THROAT + (255,))
    d.ellipse([17, 10, 22, 16], outline=(150, 150, 150, 255))  # the driver's face plate
    d.point((19, 13), fill=(190, 190, 190, 255))
    d.point((20, 13), fill=(190, 190, 190, 255))
    noisy(d, (40, 0, 55, 15), HORN, 3, 12)
    d.line([40, 0, 55, 0], fill=HORN_LIGHT + (255,))
    noisy(d, (0, 32, 15, 47), COLUMN, 6, 13)
    d.line([0, 32, 0, 47], fill=(190, 194, 196, 255))
    noisy(d, (16, 32, 31, 47), BOX, 4, 14)
    d.rectangle([19, 36, 28, 41], fill=(222, 222, 214, 255))  # the rating plate
    d.line([20, 38, 27, 38], fill=(120, 120, 120, 255))
    noisy(d, (32, 32, 47, 47), BOX, 4, 15)
    for x, y in ((33, 33), (46, 33), (33, 46), (46, 46)):
        d.point((x, y), fill=(110, 112, 112, 255))
    return img


def turn(box, k):
    """box ((x0, y0, z0), (x1, y1, z1)) facing north, turned k quarter turns clockwise (seen from above)."""
    (x0, y0, z0), (x1, y1, z1) = box
    for _ in range(k):
        x0, z0, x1, z1 = 16 - z1, x0, 16 - z0, x1
    return (x0, y0, z0), (x1, y1, z1)


def horn_elements():
    els = [
        element("box", (4.5, 0.0, 4.5), (11.5, 3.0, 11.5), all_faces(H_BOX, H_BOX_TOP)),
        element("column", (5.0, 3.0, 5.0), (11.0, 31.0, 11.0), all_faces(H_COLUMN)),
        element("cap", (4.5, 31.0, 4.5), (11.5, 31.75, 11.5), all_faces(H_COLUMN, H_BOX_TOP)),
    ]
    for k, side in enumerate(SIDES):
        for i in range(HORNS):
            y0 = HORN_BASE + i * (HORN_TALL + HORN_GAP)
            y1 = y0 + HORN_TALL
            parts = [  # mouth, flare, throat: facing north before turning
                ("mouth", ((3.0, y0, 0.0), (13.0, y1, 1.0))),
                ("flare", ((4.0, y0 + 0.75, 1.0), (12.0, y1 - 0.75, 3.0))),
                ("throat", ((5.5, y0 + 1.75, 3.0), (10.5, y1 - 1.75, 5.0))),
            ]
            for name, box in parts:
                frm, to = turn(box, k)
                faces = all_faces(H_SIDE)
                faces[side] = H_MOUTH if name == "mouth" else H_SIDE
                els.append(element("%s_%s%d" % (side, name, i), frm, to, faces))
    return els


# ---- The pole and its control cabinet -------------------------------------------------------------------------------
CAB = (150, 156, 152)
CAB_LIGHT = (178, 184, 180)
CAB_DARK = (106, 112, 108)
WOOD = (134, 110, 82)
WOOD_DARK = (102, 82, 60)

P_FRONT = (0, 0, 42, 45)       # the cabinet door, 14 x 15 units
P_SIDE = (42, 0, 58, 16)
P_TOP = (42, 16, 58, 32)
P_FLANGE = (42, 32, 50, 36)
P_LAMP_OFF = (52, 34, 56, 38)
P_LAMP_ON = (58, 34, 62, 38)
P_WOOD = (0, 48, 16, 64)
P_WOOD_END = (16, 48, 32, 64)
P_CONDUIT = (32, 48, 36, 64)
P_STEP = (36, 48, 40, 52)


def pole_texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    noisy(d, (0, 0, 41, 44), CAB, 3, 21)
    d.rectangle([2, 2, 39, 42], outline=CAB_DARK + (255,))       # the door's edge
    d.line([3, 3, 38, 3], fill=CAB_LIGHT + (255,))
    d.rectangle([8, 6, 33, 12], fill=(226, 222, 206, 255))        # the label plate: SIREN CONTROL in tiny lines
    d.line([11, 8, 30, 8], fill=(150, 30, 26, 255))
    d.line([13, 10, 28, 10], fill=(70, 70, 70, 255))
    d.rectangle([33, 20, 36, 28], fill=(60, 62, 62, 255))         # the handle
    d.line([34, 21, 34, 27], fill=(120, 124, 124, 255))
    d.ellipse([7, 18, 13, 24], fill=(196, 198, 200, 255), outline=(90, 92, 94, 255))  # the key switch
    d.line([10, 19, 10, 23], fill=(50, 50, 50, 255))
    for y in range(31, 41, 2):                                   # louvres
        d.line([8, y, 28, y], fill=CAB_DARK + (255,))
    noisy(d, (42, 0, 57, 15), CAB, 3, 22)
    noisy(d, (42, 16, 57, 31), CAB_LIGHT, 3, 23)
    noisy(d, (42, 32, 49, 35), (120, 124, 126), 4, 24)
    d.rectangle([52, 34, 55, 37], fill=(90, 24, 20, 255))         # the lamp, dark
    d.rectangle([58, 34, 61, 37], fill=(255, 70, 50, 255))        # and lit
    d.point((59, 35), fill=(255, 190, 170, 255))
    rnd = random.Random(25)
    for x in range(0, 16):                                        # the pole: grain running up it, weathered
        base = WOOD if x % 5 else WOOD_DARK
        for y in range(48, 64):
            k = rnd.randint(-8, 8)
            d.point((x, y), fill=tuple(max(0, min(255, c + k)) for c in base) + (255,))
    noisy(d, (16, 48, 31, 63), (150, 124, 92), 6, 26)
    d.ellipse([18, 50, 29, 61], outline=WOOD_DARK + (255,))       # rings on the cut end
    noisy(d, (32, 48, 35, 63), (176, 180, 184), 4, 27)
    noisy(d, (36, 48, 39, 51), (60, 60, 62), 4, 28)
    return img


def cabinet_elements(lit):
    lamp = element("lamp", (11.0, 13.0, 1.6), (12.0, 14.0, 2.0), all_faces(P_LAMP_ON if lit else P_LAMP_OFF))
    if lit:  # Glows in the dark: NeoForge's per-element light, full bright and unshaded.
        lamp["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    faces = all_faces(P_SIDE, P_TOP)
    faces["north"] = P_FRONT
    return [
        element("cabinet", (1.0, 0.0, 2.0), (15.0, 15.0, 14.0), faces),
        element("flange", (5.0, 15.0, 5.0), (11.0, 16.0, 11.0), all_faces(P_FLANGE)),
        lamp,
    ]


def pole_elements():
    return [
        element("pole", (6.0, 0.0, 6.0), (10.0, 16.0, 10.0), all_faces(P_WOOD, P_WOOD_END)),
        element("conduit", (7.5, 0.0, 10.0), (8.5, 16.0, 11.0), all_faces(P_CONDUIT)),
        element("step_east", (10.0, 4.0, 7.5), (11.5, 4.5, 8.5), all_faces(P_STEP)),
        element("step_west", (4.5, 12.0, 7.5), (6.0, 12.5, 8.5), all_faces(P_STEP)),
    ]


ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
POLE_HEIGHT = 12


def blockstates(name, parts):
    """parts: list of (condition dict, model); every facing gets each, turned."""
    out = []
    for when, model_name in parts:
        for facing, y in ROT.items():
            apply = {"model": "anotherweathermod:block/" + model_name}
            if y:
                apply["y"] = y
            if "OR" in when:
                cond = {"OR": [dict(c, facing=facing) for c in when["OR"]]}
            else:
                cond = dict(when, facing=facing)
            out.append({"when": cond, "apply": apply})
    write_json(os.path.join(ROOT, "blockstates", name + ".json"), {"multipart": out})


def write_poles():
    img = pole_texture()
    img.save(os.path.join(ROOT, "textures", "block", "siren_pole.png"))
    credit = "Made with tools/siren_models.py (a siren's control cabinet and pole)"
    write_json(os.path.join(ROOT, "models", "block", "siren_cabinet.json"), model(credit, "siren_pole", cabinet_elements(False)))
    write_json(os.path.join(ROOT, "models", "block", "siren_cabinet_on.json"), model(credit, "siren_pole", cabinet_elements(True)))
    write_json(os.path.join(ROOT, "models", "block", "siren_pole.json"), model(credit, "siren_pole", pole_elements()))
    return img


SIRENS = {
    "omni_siren": ("an omnidirectional siren: seven stacked cells", omni_texture, omni_elements),
    "horn_siren": ("a horn array siren: four horns a side", horn_texture, horn_elements),
}


def preview(name, elements, img, out):
    """A rough front view: each element's north face, back to front, scaled 8 times."""
    scale = 8
    canvas = Image.new("RGBA", (16 * scale, 32 * scale), (120, 160, 210, 255))
    for e in sorted(elements, key=lambda e: -e["from"][2]):
        if "rotation" in e or "north" not in e["faces"]:
            continue
        u0, v0, u1, v1 = [v * 4 for v in e["faces"]["north"]["uv"]]
        tile = img.crop((int(u0), int(v0), max(int(u0) + 1, int(u1)), max(int(v0) + 1, int(v1))))
        x0, x1 = 16 - e["to"][0], 16 - e["from"][0]
        y0, y1 = 32 - e["to"][1], 32 - e["from"][1]
        w, h = int(round((x1 - x0) * scale)), int(round((y1 - y0) * scale))
        if w > 0 and h > 0:
            canvas.alpha_composite(tile.resize((w, h), Image.NEAREST), (int(round(x0 * scale)), int(round(y0 * scale))))
    canvas.save(os.path.join(out, name + "_front.png"))


def main():
    write_poles()
    poles = "|".join(str(i) for i in range(1, POLE_HEIGHT - 2))
    for name, (what, texture, elements) in SIRENS.items():
        blockstates(name, [({"segment": "0"}, name + "_lower"), ({"segment": "1"}, name + "_upper")])
        blockstates(name + "_pole", [
            ({"segment": "0", "powered": "false", "switched": "false"}, "siren_cabinet"),
            ({"OR": [{"segment": "0", "powered": "true"}, {"segment": "0", "switched": "true"}]}, "siren_cabinet_on"),
            ({"segment": poles}, "siren_pole"),
            ({"segment": str(POLE_HEIGHT - 2)}, name + "_lower"),
            ({"segment": str(POLE_HEIGHT - 1)}, name + "_upper"),
        ])
        write_json(os.path.join(ROOT, "models", "item", name + "_pole.json"), {"parent": "anotherweathermod:item/" + name})
        img = texture()
        img.save(os.path.join(ROOT, "textures", "block", name + ".png"))
        els = elements()
        credit = "Made with tools/siren_models.py (%s)" % what
        write_json(os.path.join(ROOT, "models", "block", name + "_lower.json"), model(credit, name, clip(els, 0, 16)))
        write_json(os.path.join(ROOT, "models", "block", name + "_upper.json"), model(credit, name, clip(els, 16, 32)))
        write_json(os.path.join(ROOT, "models", "item", name + ".json"), model(credit, name, els, display=ITEM_DISPLAY))
        print("wrote %s: %d elements" % (name, len(els)))
        if "--preview" in sys.argv:
            preview(name, els, img, sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
