# Stone Giants

Giants of living stone, asleep in the mountains. Asleep, a giant looks like any other outcrop of the rock around it, with a bit of ore showing on its front. Start mining it and it stands up.

## What This Mod Does

The high country has goats, the odd stray, and nothing that makes you think twice before you dig into a hillside. This puts something up there that is worth thinking twice about: rocks that are not rocks, and do not like being mined.

Server-side; Pandorical carries the client's half.

## The Giants

Each kind of country has its own giant: made of its own rock, built its own way, sleeping as its own shape of outcrop, and fighting its own way.

| Giant | Sleeps in | Stands | Built | Fights |
|---|---|---|---|---|
| **Mossy Giant** | meadows, windswept forests | 5 blocks | hunched and old, a hump of shoulder over a sunken head, long arms and boulder fists, moss for a beard | **brawls**: both fists down on you, and you go up as well as back |
| **Crag Giant** | windswept hills, windswept gravelly hills | 7½ blocks | lanky and broken, shards standing up off its shoulders and back, a jutting brow | **throws rock**: from six to twenty-four blocks off it tears off a chunk and lobs it at you; nearer, it punches |
| **Peak Giant** | stony peaks | 8½ blocks | a summit on legs: an enormous chest, crags on its shoulders, a peak behind its head, calcite for a brow | **stamps**: get within five blocks and it lifts a knee and brings it down, and everything near is hurt and thrown up and away |
| **Shore Giant** | stony shores | 3½ blocks | squat and shelled like a turtle, barnacles on its back, claws, beady eyes under a heavy brow and a turtle's sullen beak | **shoves**: quick, with lighter blows that send you a long way back, often into the sea |
| **Frost Giant** | frozen peaks, jagged peaks, snowy slopes, groves | 8½ blocks | bulked against the cold, snow on its head and shoulders, an ice crest down its back, a beard of icicles | **throws ice**: lobs chunks of packed ice that freeze and slow what they hit; nearer, it punches |

Rock does not smother them: one woken in a hillside gets up out of it unhurt.

Every throw and stamp has a wind-up you can see: the arm goes back over its head, or the knee comes up, a good half second before anything lets go.

Each is about as much stone as the outcrop it sleeps as: a peak giant's tor is sixty-four blocks, and so, near enough, is the peak giant.

| | Shore | Mossy | Crag | Peak | Frost | Iron golem, for comparison |
|---|---|---|---|---|---|---|
| Health | 82 | 120 | 177 | 202 | 196 | 100 |
| Blow, on normal | 11.5 | 14 | 17 | 18 | 18 | 7.5 to 21.5 |
| Armor | 8 | 8 | 8 | 8 | 8 | 0 |
| Speed | quick | slow | slow | slowest | slow | slow |

A thrown chunk does six tenths of a blow, and a stamp seven tenths, to everything it reaches. None of them can be knocked back, and none takes fall damage.

## Finding One

About one giant for every ten chunks of the biomes above, where the ground leaves room for one. Each is a one-off: they are laid down with the world and never spawn again.

A sleeping giant is an outcrop of the rock it is made of, with **one ore showing low on its front**. That ore is the bait.

| Giant | Its outcrop | Made of | Wears |
|---|---|---|---|
| Mossy | a mossy hump, 5 across, 3 deep, 3 high: 27 blocks | stone, cobblestone, andesite; moss on top | coal, iron, copper, now and then gold |
| Crag | a broken crag with a spire, 5 across, 5 high: 29 blocks | andesite, stone, cobblestone | coal, iron, copper, now and then emerald |
| Peak | a stepped tor, 7 across, 5 deep, 5 high: 64 blocks | stone, calcite, andesite, tuff | iron, coal, gold, emerald, copper |
| Shore | a low worn shelf, 5 across, 3 deep, 2 high: 22 blocks | stone, andesite, cobblestone, smooth stone | coal, copper, iron |
| Frost | a great snow drift, 7 across, 4 deep, 4 high: 72 blocks | stone, packed ice; snow on top | iron, coal, emerald |

**It gives itself away**, if you are patient. Now and then a sleeping giant breathes: a trickle of grit, sand or snow falls off its back and there is a low grind, for anyone within ten blocks to hear. A rock should not do that.

## Waking One

**The first swing of a tool at any of its stones wakes it.** Not just the ore: any block of it. The stone does not break; the giant gets up. The whole outcrop crumbles away and the giant pushes itself up out of it on its fists, which takes it two seconds. It neither moves nor strikes until it is up, and then it comes for whoever woke it.

Anything else that takes one of its stones wakes it too: an explosion, a piston, an enderman. Woken that way it has nobody in particular in mind, but it is awake, and will go for any player near.

On **peaceful** a woken giant stands up, looks at you, wanders a while and goes back to sleep. Iron golems fight giants, and giants fight back.

## Going Back To Sleep

A giant left **a minute** with nobody to fight, and with **no player within sixteen blocks**, lies down wherever it is and is an outcrop again, **fully healed**, of the same rock and with the same ore on its front, however far it has wandered from home. It needs open ground to lie on and room for its stones; where there is none it keeps looking. So a giant you ran from is still out there, somewhere near where you left it, looking like a rock.

## Killing One

It drops **six to twelve cobblestone, the ore block it was wearing, and one to four of its own stone**: mossy cobblestone, andesite, calcite, smooth stone or packed ice. Waking one earns **Let Sleeping Stones Lie**.

Each giant has a **spawn egg** with the other spawn eggs, which sets it down **asleep**, as an outcrop, where there is room for one. `/summon stone-giants-justfatlard:crag_giant` (or `mossy_giant`, `peak_giant`, `shore_giant`, `frost_giant`) brings one **awake**, and adding `{Asleep:true}` lays one down asleep.

## Their Own Bodies

Each giant is its own entity, with its own body, built of the game's own stone at the game's own size, a block's worth of texture to a block of giant, so it looks made of the same rock it slept as, and built of enough of it to be about as much rock as its outcrop was. Their faces are drawn the way the game draws its own mobs' faces, a few pixels on the front of the head: the mossy giant's green villager eyes under a mossy brow, over a nose and a moss beard; the crag giant's scowl and ember eyes; the peak giant's deep-set gold eyes under a band of calcite; the shore giant's beady eyes and sullen turtle's beak; the frost giant's ice-blue eyes, white brows and icicle beard.

Each walks its own way, a long stride or a short scuttle, breathes slowly at rest, strikes its own way, reels back when you land a blow (the shore giant pulls its head into its shell), and when it wakes it pushes itself up off the ground on its fists and shakes its head.

## Pandorical

Pandorical 15.26 or later is required on the server, and on a player's client to see a giant as a giant; the mod will not load without it on the server.

**Asleep, a giant is real blocks**, so everybody sees the disguise, Pandorical or not. **Awake, without Pandorical client-side**, a player sees it as an iron golem at the giant's size, and it still hits like a giant; thrown rock and ice show as what they are to everybody.

## Development

Installing, the art pipeline and the tests are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
