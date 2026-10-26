#!/usr/bin/env python3
"""
Every giant's body, skin and motion, its spawn egg, and the mod menu icon: one description of each
giant, written out as the model Pandorical builds, the sheet it is painted on and the animations that
move it, so none of them can disagree.

There is a giant for each Variety in Variety.java, and each is built its own way: a hunched old
mossy one, a lanky crag giant bristling with shards, a peak giant like a summit on legs, a squat
shore giant shelled like a turtle, and a frost giant bearded with icicles. Each is a list of parts
and the cubes on them, in the game's model units (sixteen to a block, y down, the feet at 24),
written where each cube sits on the standing giant rather than relative to its part, so they read as
the giant they draw. Sixteen units to a block is meant literally: every body is modelled at its full
size, and drawn down by the scale its attribute puts back on, so its skin is the game's own stone at
the game's own size, the same blocks the outcrop it sleeps as is made of.

The parts carry the same six names on every giant - body, head, both arms, both legs - so one way of
writing an animation fits them all, and each giant's animations are written to its own proportions:
how far it crouches to rise is how long its legs are, how it walks is how it is built.

The face is painted on as the game paints its own mobs' faces, a few texels of pixel art on the
front of the head: whites with a coloured pupil, as a villager has; a heavy brow, as an iron golem
has; and a nose, a moss beard or icicles that are cubes of their own where the game would use one.

Usage: python3 generate_textures.py
"""

import io
import json
import re
import zipfile
import zlib
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image

HERE = Path(__file__).parent
MOD_ID = "stone-giants-justfatlard"
ASSETS = HERE / "src/main/resources/assets" / MOD_ID
MODELS = ASSETS / "pandorical/entity_models"
SKINS = ASSETS / "textures/entity"
EGGS = ASSETS / "textures/item"
ANIMATIONS = ASSETS / "animations"
ICON = ASSETS / "icon.png"
JAVA = HERE / "src/main/java/justfatlard/stone_giants"


def minecraft_jar():
    """The cached jar for the version this mod targets, which is where the stone and the egg's
    outline come from."""
    version = None
    for line in (HERE / "gradle.properties").read_text().splitlines():
        key, sep, value = line.partition("=")
        if sep and key.strip() == "minecraft_version":
            version = value.strip()
    jar = Path.home() / f".gradle/caches/fabric-loom/{version}/minecraft-merged.jar"
    if not jar.exists():
        raise SystemExit(f"no cached Minecraft jar at {jar}: build the mod once first")
    return jar


def vanilla(path):
    with zipfile.ZipFile(minecraft_jar()) as jar:
        image = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + path))).convert("RGBA")
        image.load()
        return image


def java_constant(name):
    """A number StoneGiant.java holds, so a timing written here is the one the server keeps."""
    source = (JAVA / "StoneGiant.java").read_text()
    return int(re.search(rf"{name} = (\d+);", source).group(1))


# --- The varieties, from Variety.java ----------------------------------------------------------

# Block ids whose texture is not named after them.
TEXTURE_OF = {"snow_block": "snow"}


@dataclass
class Variety:
    id: str
    type_id: str
    name: str
    body: list   # (texture, weight)
    tops: list


def varieties():
    """Each variety's ids and name, and the weights of the blocks its body and its top are laid in."""
    source = (JAVA / "Variety.java").read_text()
    out = []
    for match in re.finditer(r'^\t[A-Z]+\("(\w+)", "(\w+)", "([^"]+)",.*\n(\t\t.*\n)(\t\t.*\n)', source, re.M):
        mixes = [[(TEXTURE_OF.get(block.lower(), block.lower()), int(weight))
                  for block, weight in re.findall(r"Blocks\.(\w+), (\d+)", line)]
                 for line in match.groups()[3:]]
        out.append(Variety(*match.groups()[:3], *mixes))
    if not out:
        raise SystemExit("no varieties found in Variety.java")
    return out


VARIETIES = varieties()


# --- The shapes --------------------------------------------------------------------------------

@dataclass
class Cube:
    role: str
    origin: tuple   # where its least corner is on the standing giant
    size: tuple
    # A block this cube is made of whatever the variety: the snow on a frost giant's head.
    material: "str | None" = None
    # The cube on the other side this one copies: same place on the sheet, drawn mirrored.
    twin: "Cube | None" = None
    uv: tuple = None

    @property
    def lo(self):
        return self.origin

    @property
    def hi(self):
        return tuple(o + s for o, s in zip(self.origin, self.size))


@dataclass
class Part:
    name: str
    pivot: tuple
    cubes: list = field(default_factory=list)


def mirrored(cube: Cube) -> Cube:
    """The same cube on the giant's other side, sharing its paint."""
    (x, y, z), (w, h, d) = cube.origin, cube.size
    return Cube(cube.role, (-x - w, y, z), cube.size, cube.material, twin=cube)


def pair(name, pivot, cubes):
    """Right and left from the right-hand one: the left mirrors it. Right is -x, as the game has it."""
    return [Part("right_" + name, pivot, cubes),
            Part("left_" + name, (-pivot[0], pivot[1], pivot[2]), [mirrored(c) for c in cubes])]


@dataclass
class Face:
    """Pixel art for the front of one cube, from its top left, centred across it; '.' is bare stone."""
    rows: list
    down: int = 0


@dataclass
class Body:
    parts: list
    faces: dict          # cube role -> Face
    pupil: tuple         # the colour of the eyes, or the glow in them
    leg: int             # how long its legs are, which is how far down it crouches to rise
    stride: float        # how far its legs swing, in degrees
    step: float          # how long a stride takes, in seconds
    blow: str            # "both", "right" or "pinch"


def rgb(hex_):
    return tuple(int(hex_[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


# Every giant is built facing -z. Its parts carry the same six names, so the same kind of
# animation fits all of them.
BODIES = {
    # Hunched and old: a torso broader than it is deep, a hump of shoulder rising behind a head sunk
    # low and forward, arms longer than its legs ending in fists like boulders. Moss for eyebrows and
    # a beard, the villager's nose and the villager's green eyes.
    "mossy": Body(
        parts=[
            Part("body", (0, -2, 0), [
                Cube("torso", (-18, -40, -10), (36, 38, 24)),
                Cube("hump", (-20, -52, -4), (40, 12, 20)),
                Cube("hips", (-15, -4, -8), (30, 6, 18)),
            ]),
            Part("head", (0, -40, -8), [
                Cube("head", (-9, -50, -21), (18, 16, 16)),
                Cube("jaw", (-8, -36, -22), (16, 5, 13)),
                Cube("nose", (-2, -44, -24), (4, 7, 3)),
            ]),
            *pair("arm", (-22, -46, 0), [
                Cube("arm", (-32, -48, -7), (14, 34, 14)),
                Cube("fist", (-34, -14, -9), (18, 16, 18)),
            ]),
            *pair("leg", (-10, -2, 0), [Cube("leg", (-17, -2, -7), (14, 26, 14))]),
        ],
        faces={
            "head": Face([
                "GGGGGGGGGGGGGGGGGG",
                "GG.GGGG.GGG.GGG.GG",
                ".G..G..........G..",
                "..................",
                "...BBBBB..BBBBB...",
                "..SBBBBS..SBBBBS..",
                "....WP......PW....",
                "....SS......SS....",
                "..................",
                "..................",
                "..................",
                "..................",
                "..................",
                "......MMMMMM......",
            ]),
            "jaw": Face([
                "G.GGGGGGGGGGGG.G",
                "GGGG.GGGGGG.GGGG",
                ".GG.GGG..GGG.GG.",
                "..G..G....G..G..",
            ]),
        },
        pupil=rgb("#3e8948"), leg=26, stride=22, step=1.6, blow="both"),

    # Lanky and broken: a narrow torso on long legs, shards standing up off its shoulders and back,
    # long arms and knuckly fists. A jutting brow ledge over narrowed amber eyes, scowling, and a
    # jagged mouth.
    "craggy": Body(
        parts=[
            Part("body", (0, -8, 0), [
                Cube("torso", (-14, -44, -8), (28, 36, 16)),
                Cube("hips", (-12, -10, -7), (24, 4, 14)),
                Cube("shard", (-19, -56, -3), (6, 14, 6)),
                Cube("shard", (12, -60, -1), (6, 18, 6)),
                Cube("shard", (-7, -52, 5), (5, 10, 5)),
                Cube("shard", (3, -55, 4), (5, 13, 5)),
            ]),
            Part("head", (0, -44, -4), [
                Cube("head", (-8, -58, -12), (16, 14, 14)),
                Cube("brow", (-9, -55, -14), (18, 2, 3)),
            ]),
            *pair("arm", (-19, -42, 0), [
                Cube("arm", (-24, -44, -5), (10, 34, 10)),
                Cube("fist", (-25, -10, -6), (12, 12, 12)),
            ]),
            *pair("leg", (-8, -8, 0), [Cube("leg", (-13, -8, -5), (9, 32, 10))]),
        ],
        faces={
            "head": Face([
                "................",
                "..S..........S..",
                "................",
                "................",
                "................",
                "..BB........BB..",
                "...BBB....BBB...",
                "...KWPK..KPWK...",
                "....SS....SS....",
                "................",
                ".......SS.......",
                "................",
                "..MM.MM..MM.MM..",
                "...MM.MM.MM.MM..",
            ]),
            "brow": Face([
                "BBBBBBBBBBBBBBBBBB",
                "SBBSSBBBSSBBBSSBBS",
            ]),
        },
        pupil=rgb("#d65a1e"), leg=32, stride=28, step=1.4, blow="right"),

    # A summit on legs: an enormous chest and shoulders over a belly and thick column legs, crags
    # standing up off both shoulders and a peak behind its head, which is small for it and set low
    # in its chest. A band of calcite for a brow, deep-set gold eyes, and a great flat nose.
    "peak": Body(
        parts=[
            Part("body", (0, -10, 0), [
                Cube("hips", (-20, -14, -10), (40, 6, 20)),
                Cube("belly", (-22, -34, -12), (44, 22, 24)),
                Cube("chest", (-28, -64, -14), (56, 32, 28)),
                Cube("crag", (-27, -76, -6), (14, 12, 14)),
                Cube("crag", (13, -74, -6), (14, 10, 14)),
                Cube("summit", (-8, -88, -2), (16, 24, 14)),
            ]),
            Part("head", (0, -64, -14), [
                Cube("head", (-10, -74, -26), (20, 18, 14)),
                Cube("nose", (-3, -66, -29), (6, 6, 3)),
            ]),
            *pair("arm", (-32, -58, 0), [
                Cube("arm", (-44, -62, -10), (16, 40, 20)),
                Cube("fist", (-48, -22, -13), (24, 22, 26)),
            ]),
            *pair("leg", (-12, -10, 0), [Cube("leg", (-20, -10, -9), (16, 34, 18))]),
        ],
        faces={
            "head": Face([
                "CCCCCCCCCCCCCCCCCCCC",
                "CCCCCCCCCCCCCCCCCCCC",
                "....................",
                "...BBBBBBBBBBBBBB...",
                "...SSSS......SSSS...",
                "...KKKK......KKKK...",
                "...KWPK......KPWK...",
                "...SSSS......SSSS...",
                "....................",
                "....................",
                "....................",
                "....................",
                "....................",
                "....................",
                "....................",
                "......MMMMMMMM......",
                "....................",
                ".....SSSSSSSSSS.....",
            ]),
        },
        pupil=rgb("#e6a028"), leg=34, stride=16, step=2.0, blow="both"),

    # Squat and shelled, like a turtle hauled up on the shingle: a wide flat body under a shell that
    # overhangs it, barnacles on the shell, stubby legs splayed wide, short arms ending in claws, and
    # its head thrust out low in front. Small beady eyes under a heavy brow, and a turtle's sullen beak.
    "shore": Body(
        parts=[
            Part("body", (0, 10, 0), [
                Cube("belly", (-20, -12, -14), (40, 22, 30)),
                Cube("shell", (-23, -18, -16), (46, 6, 34)),
                Cube("barnacle", (-12, -22, -6), (6, 4, 6)),
                Cube("barnacle", (6, -21, 2), (5, 3, 5)),
                Cube("barnacle", (-4, -21, 8), (4, 3, 4)),
            ]),
            Part("head", (0, -2, -14), [
                Cube("head", (-9, -10, -26), (18, 14, 12)),
            ]),
            *pair("arm", (-22, -6, -4), [
                Cube("arm", (-28, -8, -10), (8, 18, 10)),
                Cube("claw", (-31, 8, -16), (12, 8, 16)),
            ]),
            *pair("leg", (-11, 10, 0), [Cube("leg", (-16, 10, -6), (10, 14, 12))]),
        ],
        faces={
            "head": Face([
                "..................",
                "..BBBB......BBBB..",
                "..KPKK......KKPK..",
                "...KK........KK...",
                "..................",
                "..................",
                ".......K..K.......",
                "..................",
                "..SSSSSSSSSSSSSS..",
                "...MMMMMMMMMMMM...",
                "..M............M..",
                "....R.............",
            ]),
        },
        pupil=rgb("#16424a"), leg=14, stride=30, step=0.8, blow="pinch"),

    # Big and bulked against the cold: a broad torso, snow banked on its back and on its shoulders,
    # a snow cap on its head, an ice crest down its spine, and a beard of icicles hanging off its
    # chin. White frost for brows and a moustache, and eyes that glow like ice with light behind it.
    "frost": Body(
        parts=[
            Part("body", (0, -6, 0), [
                Cube("torso", (-22, -50, -12), (44, 44, 26)),
                Cube("snowback", (-20, -54, -6), (40, 4, 20), "snow"),
                Cube("crest", (-4, -62, 4), (8, 12, 8), "packed_ice"),
            ]),
            Part("head", (0, -50, -6), [
                Cube("head", (-10, -70, -18), (20, 20, 18)),
                Cube("cap", (-11, -76, -19), (22, 6, 20), "snow"),
                Cube("icicle", (-8, -50, -19), (3, 8, 2), "packed_ice"),
                Cube("icicle", (-3, -50, -19), (3, 11, 2), "packed_ice"),
                Cube("icicle", (2, -50, -19), (3, 9, 2), "packed_ice"),
                Cube("icicle", (6, -50, -19), (2, 6, 2), "packed_ice"),
            ]),
            *pair("arm", (-27, -46, 0), [
                Cube("pad", (-35, -54, -11), (16, 8, 22), "snow"),
                Cube("arm", (-33, -48, -8), (12, 38, 16)),
                Cube("fist", (-35, -10, -10), (16, 14, 20)),
            ]),
            *pair("leg", (-10, -6, 0), [Cube("leg", (-17, -6, -8), (14, 30, 16))]),
        ],
        faces={
            "head": Face([
                "....................",
                "....................",
                "....................",
                "....................",
                "...NNNNN....NNNNN...",
                "....NNNN....NNNN....",
                "....KEEK....KEEK....",
                "....KKKK....KKKK....",
                "....................",
                ".........SS.........",
                "........SSSS........",
                "....................",
                "....................",
                ".......MMMMMM.......",
                "....................",
                "....N..N..N..N..N...",
                "...NNNNNNNNNNNNNN...",
                "....NNNNNNNNNNNN....",
            ]),
        },
        pupil=rgb("#a8e8ff"), leg=30, stride=20, step=1.8, blow="both"),
}

# Which cubes the rain falls on: their tops are the variety's tops.
WEATHERED = {"hump", "head", "arm", "fist", "torso", "shard", "crag", "summit", "shell", "chest",
             "barnacle", "claw", "brow"}


# --- The paint ---------------------------------------------------------------------------------
#
# Block-sized tiles of the game's own stone, each tile one block's texture, chosen per tile so the
# body is a patchwork of the rock its variety is made of, by the same weights its outcrop is laid
# with: those are read out of Variety.java, so a giant and the rock it sleeps as cannot disagree.
# The tops of the parts the weather reaches, and the top row of tiles on their sides now and then,
# are the variety's tops instead. Every tile is edged a shade darker, the seam between two blocks.

SOCKET = (26, 26, 28, 255)
EYE_WHITE = (232, 232, 222, 255)
MOUTH = (40, 36, 34, 255)
BARNACLE = (206, 204, 190, 255)
SEAM = 0.8
SHADE_UNDER = 0.72

STONES = {name: vanilla(f"block/{name}.png")
          for name in {n for v in VARIETIES for n, _ in v.body + v.tops}
          | {"moss_block", "calcite", "snow", "packed_ice", "mossy_cobblestone"}}


def roll(*key):
    """A number from 0 to 99 that is always the same for the same key."""
    return zlib.crc32(repr(key).encode()) % 100


def pick(mix, r):
    """The block a roll of 0 to 99 lands on, by weight."""
    total = sum(weight for _, weight in mix)
    at = r * total // 100
    for name, weight in mix:
        at -= weight
        if at < 0:
            return name
    return mix[-1][0]


def material(variety, cube, face, ti, tj):
    if cube.material:
        return cube.material
    r = roll(cube.role, cube.origin, face, ti, tj)
    if face == "up" and cube.role in WEATHERED:
        return pick(variety.tops, r)
    if face in ("north", "south", "east", "west") and tj == 0 and cube.role in WEATHERED and r < 35:
        return pick(variety.tops, roll(cube.role, cube.origin, face, ti, tj, "top"))
    return pick(variety.body, r)


def on_face(cube, face, x, y, z):
    """Where a texel is across and down its face, from the face's own top left corner."""
    (x0, y0, z0), (x1, y1, z1) = cube.lo, cube.hi
    if face == "north":
        return x - x0, y - y0
    if face == "south":
        return x1 - x, y - y0
    if face == "west":
        return z1 - z, y - y0
    if face == "east":
        return z - z0, y - y0
    return x - x0, z - z0


def darker(colour, by):
    r, g, b, a = colour
    return int(r * by), int(g * by), int(b * by), a


def lighter(colour, by):
    r, g, b, a = colour
    return min(255, int(r * by)), min(255, int(g * by)), min(255, int(b * by)), a


def stonework(variety, cube, face, x, y, z):
    across, down = on_face(cube, face, x, y, z)
    # Each cube's blocks are laid from a corner of their own, so the seams of two cubes never line up.
    shift = roll(cube.role, cube.origin, "shift") % 16
    a, b = int(across + shift), int(down + (shift * 7) % 16)
    ti, tj = a // 16, b // 16
    u, v = a % 16, b % 16
    colour = STONES[material(variety, cube, face, ti, tj)].getpixel((u, v))
    if u == 0 or v == 0:
        colour = darker(colour, SEAM)
    if face == "down":
        colour = darker(colour, SHADE_UNDER)
    return colour


def face_mark(body, cube, x, y):
    """The pixel art's mark at this texel of a cube's front, or None for bare stone."""
    art = body.faces.get(cube.role)
    if art is None:
        return None
    across = (int(cube.size[0]) - len(art.rows[0])) // 2
    col, row = int(x - cube.lo[0]) - across, int(y - cube.lo[1]) - art.down
    if not (0 <= row < len(art.rows) and 0 <= col < len(art.rows[row])):
        return None
    mark = art.rows[row][col]
    return None if mark == "." else mark


def painter(variety):
    """How a giant of this variety is painted, texel by texel."""
    body = BODIES[variety.id]

    def paint_giant(cube, face, x, y, z):
        stone = stonework(variety, cube, face, x, y, z)
        if face == "north" and cube.twin is None:
            mark = face_mark(body, cube, x, y)
            if mark is not None:
                u, v = int(x) % 16, int(y) % 16
                return {
                    "B": darker(stone, 0.5),
                    "S": darker(stone, 0.72),
                    "H": lighter(stone, 1.15),
                    "K": SOCKET,
                    "W": EYE_WHITE,
                    "P": body.pupil,
                    "E": body.pupil,
                    "M": MOUTH,
                    "R": BARNACLE,
                    "G": STONES["moss_block"].getpixel((u, v)),
                    "C": STONES["calcite"].getpixel((u, v)),
                    "N": STONES["snow"].getpixel((u, v)),
                    "I": STONES["packed_ice"].getpixel((u, v)),
                }[mark]
        if cube.role == "nose" and face != "up":
            return darker(stone, 0.88)
        return stone

    return paint_giant


# --- The sheet ---------------------------------------------------------------------------------

def footprint(cube):
    """A cube's rectangle on the sheet, in the game's box layout: two rows, the top and bottom
    above the four sides."""
    w, h, d = cube.size
    return int(2 * (d + w)), int(d + h)


def place(parts):
    """Each cube that has paint of its own, given a free spot on the smallest sheet that holds them
    all, biggest first; the sheet's size."""
    own = [c for part in parts for c in part.cubes if c.twin is None]
    for sheet in ((256, 256), (512, 256), (512, 512), (1024, 512)):
        width, height = sheet
        taken = [[False] * width for _ in range(height)]
        spots = {}
        for cube in sorted(own, key=lambda c: (-footprint(c)[1], -footprint(c)[0])):
            fw, fh = footprint(cube)
            spot = next(((u, v) for v in range(height - fh + 1) for u in range(width - fw + 1)
                         if not taken[v][u] and not taken[v][u + fw - 1]
                         and all(not taken[v + j][u + i] for j in range(fh) for i in range(fw))), None)
            if spot is None:
                break
            for j in range(fh):
                for i in range(fw):
                    taken[spot[1] + j][spot[0] + i] = True
            spots[id(cube)] = spot
        else:
            for cube in own:
                cube.uv = spots[id(cube)]
            for part in parts:
                for c in part.cubes:
                    if c.twin is not None:
                        c.uv = c.twin.uv
            return sheet
    raise SystemExit("no sheet big enough for these cubes")


def faces(cube):
    """Every texel of a cube's faces: where on the sheet, which face, and where its middle is on the
    standing giant. The layout is the game's own: in the top row the top then the bottom, in the row
    under it the giant's right side, its front, its left side and its back."""
    u, v = cube.uv
    w, h, d = (int(s) for s in cube.size)
    (x0, y0, z0), (x1, y1, z1) = cube.lo, cube.hi
    for i in range(w):
        for j in range(d):
            yield (u + d + i, v + j), "up", (x0 + i + .5, y0, z1 - j - .5)
            yield (u + d + w + i, v + j), "down", (x0 + i + .5, y1, z1 - j - .5)
    for k in range(h):
        y = y0 + k + .5
        for i in range(d):
            yield (u + i, v + d + k), "west", (x0, y, z1 - i - .5)
            yield (u + d + w + i, v + d + k), "east", (x1, y, z0 + i + .5)
        for i in range(w):
            yield (u + d + i, v + d + k), "north", (x0 + i + .5, y, z0)
            yield (u + 2 * d + w + i, v + d + k), "south", (x1 - i - .5, y, z1)


def paint(parts, sheet, painter):
    image = Image.new("RGBA", sheet, (0, 0, 0, 0))
    pixels = image.load()
    for part in parts:
        for cube in part.cubes:
            if cube.twin is not None:
                continue
            for (px, py), face, (x, y, z) in faces(cube):
                pixels[px, py] = painter(cube, face, x, y, z)
    return image


# --- The motion --------------------------------------------------------------------------------
#
# Positions are in model units, y down as the model has it; rotations in degrees, and a limb
# hanging from its pivot swings forward on a negative x. Every bone that rides on the torso is moved
# with it, because the parts are not nested: a torso that rose without its head and arms would leave
# them behind.

def frame(time, value, smooth=True):
    out = {"time": time, "value": list(value)}
    if smooth:
        out["interpolation"] = "catmullrom"
    return out


def channel(target, *frames):
    return {"target": target, "keyframes": list(frames)}


UPPER = ("body", "head", "right_arm", "left_arm")


def walk(body):
    """Legs swinging against each other, arms against the legs, the upper body dropping onto each
    footfall: as far and as long a stride as the giant is built for."""
    t, swing = body.step, body.stride
    bones = {"body": [], "head": []}
    for side, sign in (("right", 1), ("left", -1)):
        bones[f"{side}_leg"] = [channel("rotation",
            frame(0, (-swing * sign, 0, 0)), frame(t / 2, (swing * sign, 0, 0)), frame(t, (-swing * sign, 0, 0)))]
        arm = swing * 0.6 * sign
        bones[f"{side}_arm"] = [channel("rotation", frame(0, (arm, 0, 0)), frame(t / 2, (-arm, 0, 0)), frame(t, (arm, 0, 0)))]
    bob = body.leg * 0.06
    for bone in UPPER:
        bones[bone].append(channel("position",
            frame(0, (0, bob, 0)), frame(t / 4, (0, 0, 0)), frame(t / 2, (0, bob, 0)),
            frame(3 * t / 4, (0, 0, 0)), frame(t, (0, bob, 0))))
    bones["body"].append(channel("rotation", frame(0, (0, 0, 2)), frame(t / 2, (0, 0, -2)), frame(t, (0, 0, 2))))
    return {"length": t, "looping": True, "bones": bones}


def idle(body):
    """Breathing, slowly, the way a hillside would."""
    bones = {bone: [channel("position", frame(0, (0, 0, 0)), frame(2, (0, 0.8, 0)), frame(4, (0, 0, 0)))]
             for bone in UPPER}
    bones["body"].append(channel("rotation", frame(0, (0, 0, 0)), frame(2, (2, 0, 0)), frame(4, (0, 0, 0))))
    return {"length": 4, "looping": True, "bones": bones}


def blow(body):
    """Its strike: both fists over its head and down, one great hook of an arm, or a pinch of claws."""
    if body.blow == "pinch":
        def claw(sign):
            return channel("rotation",
                frame(0, (0, 0, 0)), frame(0.25, (-40, 0, 35 * sign)), frame(0.45, (-55, 0, -10 * sign)), frame(0.8, (0, 0, 0)))
        return {"length": 0.8, "looping": False, "bones": {"right_arm": [claw(1)], "left_arm": [claw(-1)]}}
    if body.blow == "right":
        hook = channel("rotation",
            frame(0, (0, 0, 0)), frame(0.35, (-120, -30, 40)), frame(0.55, (-60, 40, -20)), frame(1.0, (0, 0, 0)))
        twist = channel("rotation", frame(0, (0, 0, 0)), frame(0.35, (0, 20, 0)), frame(0.55, (6, -25, 0)), frame(1.0, (0, 0, 0)))
        return {"length": 1.0, "looping": False, "bones": {"right_arm": [hook], "body": [twist]}}
    overhead = channel("rotation",
        frame(0, (0, 0, 0)), frame(0.4, (-165, 0, 0)), frame(0.6, (-20, 0, 0)), frame(1.0, (0, 0, 0)))
    lean = channel("rotation",
        frame(0, (0, 0, 0)), frame(0.4, (-8, 0, 0)), frame(0.6, (14, 0, 0)), frame(1.0, (0, 0, 0)))
    return {"length": 1.0, "looping": False, "bones": {"right_arm": [overhead], "left_arm": [overhead], "body": [lean]}}


def rise(body, seconds):
    """Up out of the rock: from hunched on its haunches with its fists on the ground, a push up on
    its arms, a shake of the head, and stood. It crouches as low as its legs are long."""
    t = seconds
    drop = body.leg * 0.85
    crouched = {"body": (0, drop, 0), "head": (0, drop + 4, -4), "right_arm": (0, drop - 2, -4), "left_arm": (0, drop - 2, -4)}
    bones = {}
    for bone, low in crouched.items():
        bones[bone] = [channel("position",
            frame(0, low), frame(t * 0.45, tuple(v * 0.6 for v in low)), frame(t * 0.8, (0, 0, 0)), frame(t, (0, 0, 0)))]
    bones["body"].append(channel("rotation",
        frame(0, (38, 0, 0)), frame(t * 0.45, (30, 0, 0)), frame(t * 0.8, (-4, 0, 0)), frame(t, (0, 0, 0))))
    for side in ("right_arm", "left_arm"):
        bones[side].append(channel("rotation",
            frame(0, (-35, 0, 0)), frame(t * 0.45, (-55, 0, 0)), frame(t * 0.8, (-10, 0, 0)), frame(t, (0, 0, 0))))
    for side in ("right_leg", "left_leg"):
        # The legs come down with the hips they hang from, and stick out in front of it, sat.
        bones[side] = [channel("position",
            frame(0, (0, drop, 0)), frame(t * 0.45, (0, drop * 0.6, 0)), frame(t * 0.8, (0, 0, 0)), frame(t, (0, 0, 0))),
            channel("rotation",
            frame(0, (-70, 0, 0)), frame(t * 0.45, (-40, 0, 0)), frame(t * 0.8, (0, 0, 0)), frame(t, (0, 0, 0)))]
    bones["head"].append(channel("rotation",
        frame(0, (-30, 0, 0)), frame(t * 0.8, (0, 0, 0)), frame(t * 0.88, (0, 0, 9)),
        frame(t * 0.94, (0, 0, -9)), frame(t, (0, 0, 0))))
    return {"length": t, "looping": False, "bones": bones}


def throw(body, seconds):
    """A chunk torn off and thrown overarm: the arm up, cocked back behind the head, and whipped
    forward as the wind-up runs out, which is when the server lets the chunk go."""
    t = seconds
    return {"length": t, "looping": False, "bones": {
        "right_arm": [channel("rotation",
            frame(0, (0, 0, 0)), frame(t * 0.5, (-160, 0, 10)), frame(t * 0.8, (-205, 0, 10)), frame(t, (-70, 0, 0)))],
        "left_arm": [channel("rotation", frame(0, (0, 0, 0)), frame(t * 0.8, (-30, 0, -15)), frame(t, (10, 0, 0)))],
        "body": [channel("rotation",
            frame(0, (0, 0, 0)), frame(t * 0.8, (-10, 25, 0)), frame(t, (12, -15, 0)))],
    }}


def stomp(body, seconds):
    """A knee up, the arms up with it, and the foot brought down as the wind-up runs out, which is
    when the server shakes the ground."""
    t = seconds
    return {"length": t, "looping": False, "bones": {
        "right_leg": [channel("rotation", frame(0, (0, 0, 0)), frame(t * 0.7, (-55, 0, 0)), frame(t, (0, 0, 0)))],
        "right_arm": [channel("rotation", frame(0, (0, 0, 0)), frame(t * 0.7, (-60, 0, 20)), frame(t, (15, 0, 0)))],
        "left_arm": [channel("rotation", frame(0, (0, 0, 0)), frame(t * 0.7, (-60, 0, -20)), frame(t, (15, 0, 0)))],
        "body": [channel("rotation", frame(0, (0, 0, 0)), frame(t * 0.7, (-8, 0, 0)), frame(t, (10, 0, 0)))],
    }}


def animations(body):
    return {
        "walk": walk(body),
        "idle": idle(body),
        "blow": blow(body),
        "rise": rise(body, java_constant("RISING_TICKS") / 20),
        "throw": throw(body, java_constant("THROW_WINDUP") / 20),
        "stomp": stomp(body, java_constant("STOMP_WINDUP") / 20),
    }


def model(variety, parts, sheet):
    """The model as Pandorical reads it: each cube relative to its part's pivot, and how it moves."""
    out = {}
    for part in parts:
        px, py, pz = part.pivot
        cubes = []
        for c in part.cubes:
            x, y, z = c.origin
            cube = {"origin": [x - px, y - py, z - pz], "size": list(c.size), "uv": list(c.uv)}
            if c.twin is not None:
                cube["mirror"] = True
            cubes.append(cube)
        out[part.name] = {"pivot": list(part.pivot), "cubes": cubes}

    def animation(name):
        return f"{MOD_ID}:{variety.type_id}_{name}"

    return {
        "texture": f"{MOD_ID}:textures/entity/{variety.type_id}.png",
        "texture_size": list(sheet),
        "animation": {
            "look": "head",
            "idle": animation("idle"),
            "moving": {"animation": animation("walk"), "pace": 2, "strength": 2.5},
            "attack": animation("blow"),
        },
        "parts": out,
    }


def tidy(value):
    if isinstance(value, dict):
        return {k: tidy(v) for k, v in value.items()}
    if isinstance(value, list):
        return [tidy(v) for v in value]
    if isinstance(value, float):
        value = round(value, 3)
        return int(value) if value.is_integer() else value
    return value


def compact(data):
    """JSON a person can read: one line per cube, and per keyframe."""
    text = json.dumps(tidy(data), indent="\t")
    text = re.sub(r"\[\s+([-\d.,\s]+?)\s+\]", lambda m: "[" + ", ".join(m.group(1).split()).replace(",,", ",") + "]", text)
    text = re.sub(r"\{\s+(\"(?:origin|time)\"[^{}]*?)\s+\}", lambda m: "{" + " ".join(m.group(1).split()) + "}", text)
    return text + "\n"


# --- The eggs and the icon ---------------------------------------------------------------------

def egg(variety):
    """The spawn egg's outline, from the iron golem's egg, filled with the variety's commonest stone
    lit from the top left, its top over the crown, and its two eyes."""
    shape = vanilla("item/iron_golem_spawn_egg.png")
    filled = {(x, y) for y in range(shape.height) for x in range(shape.width) if shape.getpixel((x, y))[3]}
    out = Image.new("RGBA", shape.size, (0, 0, 0, 0))
    stone = STONES[max(variety.body, key=lambda b: b[1])[0]]
    crown = STONES[max(variety.tops, key=lambda b: b[1])[0]]
    top = min(y for _, y in filled)
    for x, y in filled:
        outline = any((x + dx, y + dy) not in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        colour = crown.getpixel((x, y)) if y < top + 3 else stone.getpixel((x, y))
        lit = x + y
        if outline:
            colour = darker(colour, 0.5)
        elif lit > 19:
            colour = darker(colour, 0.75)
        elif lit < 9:
            colour = lighter(colour, 1.12)
        out.putpixel((x, y), colour)
    pupil = BODIES[variety.id].pupil
    for (x, y), colour in {(5, 7): EYE_WHITE, (6, 7): pupil, (9, 7): pupil, (10, 7): EYE_WHITE}.items():
        if (x, y) in filled:
            out.putpixel((x, y), colour)
    return out


def front_view(parts, painter):
    """The giant seen square from the front, standing still: every cube's front face, the
    furthest drawn first. One pixel to a texel."""
    cubes = [c for part in parts for c in part.cubes]
    left = min(c.lo[0] for c in cubes)
    top = min(c.lo[1] for c in cubes)
    width = int(max(c.hi[0] for c in cubes) - left)
    height = int(max(c.hi[1] for c in cubes) - top)
    view = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    for cube in sorted(cubes, key=lambda c: -c.lo[2]):
        source = cube.twin or cube
        w, h = int(cube.size[0]), int(cube.size[1])
        for i in range(w):
            for k in range(h):
                # A mirrored cube shows its twin's paint flipped, as the game draws it.
                si = w - 1 - i if cube.twin else i
                x = source.lo[0] + si + .5
                y = cube.lo[1] + k + .5
                colour = painter(source, "north", x, y, source.lo[2])
                view.putpixel((int(cube.lo[0] - left + i), int(cube.lo[1] - top + k)), colour)
    return view


def icon(variety):
    """A giant from the front on mossy cobblestone, darkened, as a 128 square."""
    figure = front_view(BODIES[variety.id].parts, painter(variety))
    scale = max(1, min(120 // figure.width, 120 // figure.height))
    figure = figure.resize((figure.width * scale, figure.height * scale), Image.NEAREST)
    block = STONES["mossy_cobblestone"].point(lambda c: c * 2 // 5).resize((128, 128), Image.NEAREST)
    block.putalpha(255)
    block.alpha_composite(figure, ((128 - figure.width) // 2, (128 - figure.height) // 2))
    return block


def main() -> None:
    for path in (MODELS, SKINS, EGGS, ANIMATIONS):
        path.mkdir(parents=True, exist_ok=True)
    for variety in VARIETIES:
        body = BODIES[variety.id]
        sheet = place(body.parts)
        paint(body.parts, sheet, painter(variety)).save(SKINS / f"{variety.type_id}.png")
        (MODELS / f"{variety.type_id}.json").write_text(compact(model(variety, body.parts, sheet)))
        for name, built in animations(body).items():
            (ANIMATIONS / f"{variety.type_id}_{name}.json").write_text(compact(built))
        egg(variety).save(EGGS / f"{variety.type_id}_spawn_egg.png")
        tall = max(-c.lo[1] for p in body.parts for c in p.cubes) + 24
        print(f"{variety.type_id}: {sum(len(p.cubes) for p in body.parts)} cubes, {tall / 16:.1f} blocks tall,"
              f" on a {sheet[0]}x{sheet[1]} sheet")
    picture = icon(VARIETIES[0])
    picture.save(ICON)
    picture.save(HERE / "icon.png")


if __name__ == "__main__":
    main()
