# Development

## Building

```
./gradlew build
```

Compiles against Pandorical's working tree as a subproject.

## Installing

Server-side. Drop the jar in the server's `mods/` alongside Pandorical 15.21 or later, which is the version that lets a mod add an entity with a body of its own. Players need Pandorical client-side to see an awake giant as a giant; without it they are shown an iron golem at the giant's size.

## How it fits together

- `StoneGiant` extends `AbstractGolem`, the iron golem's parent, and adds no synced data, which is what lets a vanilla client be sent an iron golem in its place. Everything about sleeping is the server's: whether it is asleep, the stones it lies in and the ore it wears are saved as `Asleep`, `Body` and `Ore`.
- **Asleep**, it lies in the `SLEEPING` pose, which every client sizes at a fifth of a block times its scale, invisible, without AI or gravity, and unhurtable short of `/kill`. That keeps it inside the block at its feet, where nothing can hit, shoot or bump into it. Pose and invisibility are not saved with an entity, so a sleeping one is laid down again as it loads.
- `Variety` is which giant it is: its entity type, name, size on the scale attribute (health and blow in step with it), its outcrop's `Shape`, the weights of the stones its body and top are laid in, its ores, and its `Attack`. `StoneGiants` registers an entity type and a spawn egg for each, all running the one class, and the class asks its type which giant it is, so nothing about the variety is saved. Worldgen picks the variety by biome.
- **Fighting** is a melee goal for all of them, plus `StoneGiant.fight` each tick for the rest: a thrower starts a throw at a target it can see between `THROW_FROM` and `THROW_TO`, a stamper a stamp at one within `STOMP_FROM`; the animation plays through the wind-up and the throw or stamp happens as it runs out, if the target is still there. A thrown chunk is a `Boulder`, a vanilla snowball carrying a block item, so every client draws it as that block flying with nothing new to send; its damage and the frost on ice are server-side and not saved. A shore giant's melee blow shoves rather than lifts.
- `Outcrop` is the shape it sleeps as, laid in real blocks of its variety's rock: the same for worldgen, the spawn egg and a giant lying down again. Its stones are remembered by place and block, so it knows its own from whatever was built against it later.
- **Waking** is `AttackBlockCallback`, which fires on the server at the first swing, before the stone can break, and `PlayerBlockBreakEvents.BEFORE` for anything that breaks a block without a swing first. A sleeping giant also checks its stones once a second and wakes if any is gone.
- **Rising** plays its type's `_rise` animation through `PandoricalApi.animations()` and holds its movement still for `RISING_TICKS`; the animation is generated to the same length, as the throw and stamp are to `THROW_WINDUP` and `STOMP_WINDUP`. The rise is timed by the client's clock from when it arrives, so a screenshot of it has to be taken straight away: `Pictures.shoot` settles the world first, and by then the giant is up.
- `SleepingGiantFeature` is a feature type; the feature and its placement are data under `data/stone-giants-justfatlard/worldgen/`, so a datapack can change how rare giants are. It goes in at `LOCAL_MODIFICATIONS`, where vanilla sets down its mossy boulders, and may take the place of the hillside's own rock and soil, so on a slope it lies half into the hill.

## The art

```
python3 generate_textures.py
```

For each giant, writes its body (`pandorical/entity_models/<type>.json`), paints its skin (`textures/entity/<type>.png`) and its spawn egg, and writes its six animations in `animations/` - walk, idle, blow, rise, throw and stomp - all from its own table of boxes in `BODIES`, so a box cannot move on the sheet without its paint moving with it; then paints the icon (both copies). Needs Pillow, and the cached Minecraft jar from one build. Idempotent; it needs no game running.

Each body is modelled at full size, sixteen units to a block, and registered with a `drawScale` of one over its variety's scale: the scale attribute makes the hitbox, and the draw scale takes it back off the drawing, so the body is drawn at the size it was modelled and its skin is the game's stone at the game's size. Each skin is painted in block-sized tiles of the vanilla textures of the blocks its variety is laid in, by the same weights, read out of `Variety.java`, so a giant and its outcrop cannot be made of different rock; each tile is edged a shade darker.

Every body has the same six top-level parts - `body`, `head`, `right_arm`, `left_arm`, `right_leg`, `left_leg` - with whatever else it has (shards, crags, icicles, a shell) as cubes on them, so one way of writing each animation fits every giant, scaled to its own legs and stride. The parts are not nested, so the animations move the head and arms with the body wherever the body moves.

The faces are pixel art in `BODIES`, one character to a texel, painted on the front of the head (and of the jaw or brow, where a giant has one) over its stone: `W` an eye's white and `P` its pupil, `K` a socket, `B` and `S` brow and shade, `M` mouth, `E` a glowing eye, and `G`, `C`, `N`, `I` moss, calcite, snow and ice. A nose, a beard of icicles or a brow ledge is a cube of its own, as the game's mobs have them.

`StoneGiantPictured` photographs a sleeping mossy giant, a standing one from the front and from the quarter, one early in its rise, and every giant in a row, up and then asleep, into `build/run/clientGameTest/screenshots/` (and `screenshots/` with `-Ppictures`): the way to see a change to the body.

## Tests

```
./gradlew runClientGameTest -Ptest.level=full
```

Runs the smoke test, `StoneGiantPictured`, and `StoneGiantBehaviour`, which walls in a stone floor in the sky and holds the giant to the readme: every giant lies down in its own shape, at its own size and health, under its own name, and is the one for each of its biomes; the mossy egg lays one asleep in real blocks with one ore, hidden, too small to hit and nothing for an iron golem to hunt; a swing at its ore wakes it, its stones all go, and it stands full size, after its miner, still while it rises; a sleeping giant with a stone taken away wakes by itself; one left alone and unwatched for a minute lies down again, healed; a dead one drops its ore, cobblestone and its own stone; the worldgen feature lays one asleep and is in every variety's biomes but not the plains; a crag giant hits a villager twelve blocks off with a thrown rock, a frost giant freezes and slows one with ice, a peak giant's stamp hurts one beside it, a shore giant's blow sends one further back than a mossy giant's, which sends it up; and it is drawn by a Pandorical client from its own body and sent to a vanilla one as an iron golem. It takes a little over a minute longer than most, waiting out the giant's minute.

On a machine with no display, `xvfb-run -a -s "-screen 0 1920x1080x24"` in front of it works.

## What to check by hand

- **Finding one in a new world**: `/locate biome minecraft:stony_peaks`, then look for a mossy outcrop with ore on its front.
- **The rise and the fight look right**: mine one of each in survival, let it hit you, then run more than sixteen blocks and wait a minute. The throws and the stamp are worth watching from both ends: they should be readable before they land.
