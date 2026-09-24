# Furrowmaw (Java mod) — version 1.4.0

Your KillzAI Furrowmaw build turned into a real boss for Minecraft Java 1.21.1 with Fabric. He's a blind, ringed burrower about 600 blocks long at full size. He swims through the ground, feels you walking around on top of him, and comes up under you.

His head, maw and five hook mandibles are cut straight out of your build. So are his rings: the real ring shells with their thickness and the orange and copper bands, several different ones so they don't repeat. The parts your build had buried under the mud (the rest of the body, the legs under it, the thin end of the tail and the two tail pincers) were rebuilt to match.

## What's new in 1.4

- **His body is a real chain now.** Every ring stays exactly its own length from the next, always: nothing stretches, nothing pulls apart, in any move. A ring can only bend so far against the one before it (20 degrees), so a sharp turn, a cliff edge or a rear is taken as a smooth curve instead of a kink. He never passes through himself: where the front of him crosses the rest, it rides up over it, and a coil piles up on itself like a snake's.
- **He walks any terrain.** Rings out in the open rest on the ground under them, and a step or a bank ahead lifts the rings before it on an arc so he takes it as a slope. Steep banks he slows for and climbs; his head no longer drives into hillsides.
- **You are him.** *Come and carry me* in the book now works like the Mountain's: the view swings out behind him, the mouse says which way, **W** pushes him on, **A/D** bend him, **S** holds him still, **Sneak takes him under the ground and Jump brings him back up**, the number keys and Z X C V B N M set off his moves (the list sits down the left of the screen with a clock on each line), the scroll wheel or [ and ] move the view in and out, and **G** gets you off. You come to no harm from the ground he swims through or from anything he does himself.
- The **Safe list** page in the book: who he leaves alone while you hold it (players by name, or whole kinds of creature).
- The *tear rings off* and *heal* lines are gone from the book (the commands are still there).
- Fixes: he can't be killed into a server crash before his first tick, a dead one stops ticking, riding never counted as being swallowed, hold-still and follow survive a relog, a death survives a chunk unload.

## What's new in 1.3

- **The Furrowmaw Codex.** A book to control him, laid out the same way as the Mountain's. Craft it from a hush bristle over chitin, book, chitin, with a chitin plate under (see *The book* below). Whoever holds it is his master: he won't hunt you and doesn't hear you. Right-click to open it. Four pages: **Orders** (come, crawl where you look, hunt what you look at, hold still, follow, carry you, burrow, surface, breach, open the ground, where is he), **Attacks** (every move, greyed out while it's cooling down), **Him** (mood, forgive, ground breaking, tear rings off, heal) and a **Safe list** of players and kinds of creature he leaves alone while you hold the book. Above the pages: how far away he is, health, mood, what he's doing, and two bars for health and rings.
- **You can ride him.** *Come and carry me* seats you on his head behind the mandibles. Look where you want him to go and he crawls there. *Get off* or sneak to leave.
- **Three big attacks:**
  - **Roll.** He throws his whole length over sideways, crushing everything under the rings, and comes to rest a body's width over from where he was.
  - **Magma breath.** He rears, opens the mouth wide and sweeps a stream of magma across the ground in front of him for four seconds.
  - **Sinkhole.** He circles under you and the ground falls in: a bowl opens up under your feet, everything in it slides to the middle, and he bursts up out of the bottom.
- **He crawls over himself.** Where his path crosses his own body he climbs up over the rings and down the other side instead of passing through them.
- **His legs push him.** Each foot plants, stays put while the body moves over it, then swings ahead and lands in front, so it reads like the legs are doing the pushing. Feet that aren't on the ground (rings still in the ground, or hanging over an edge) don't try to walk; they hang and sway a little.
- **No more phasing through the ground.** On a slam, a swallow, the erupt or a roll his rings settle onto the ground rather than sinking into it, and the settling is eased so it never jumps. Falling lumps from his bursts don't pile up into towers any more either.
- **The mouth is clean:** the loose blocks that floated in the mouth are gone, and the rings are solid all the way through.
- `/furrowmaw do roll`, `erupt` and `sinkhole` for the new moves.

## What's new in 1.2

- **You can't see through him any more.** The rings cut from your build were hollow tubes, so every bend showed daylight through the gap between two rings. They're solid now, and each ring has a plug reaching into the next one, so bends stay closed.
- **He's much longer:** 50 rings, about 600 blocks at full size (he was 28 rings and 350).
- **The red dome is his mouth.** It splits into four petals that hinge open like a flower over a glowing throat. It opens wide to shriek, swallow and spit, half-opens to vent, snaps for a bite, and breathes a little the rest of the time.
- **The rears each have their own shape.** The slam goes up tall and crashes down; the swallow comes up lower with the head bent down at you and lunges down and forward; the spit is a cobra's lift of the front few rings with the head thrown up; the shriek goes up and curls back over itself with the head thrown back; listening is a low, swaying lift.
- **Smoother.** His speed eases up and down instead of jumping between stopped and full crawl, the height he holds his head at is smoothed over the ground ahead so tree trunks and bumps don't make him bob, and he no longer stops and starts on the edge of his reach. The thrash is a slower, wider wave and his feet grip the ground through it instead of skittering.
- **Damage commands:** `/furrowmaw hurt <amount>`, `/furrowmaw sethealth <n>`, `/furrowmaw heal`.

## What's new in 1.1

- **He doesn't go dark any more.** He was lighting himself from points along his spine, and any point inside a hill or under the ground read as pitch black. Now he takes his light from the surface above each point.
- **No more snapping.** Every move slides in and out of his real body shape instead of jumping to it, and his head no longer writes itself into his trail while he's rearing (that was what made his body tweak out after an attack).
- **His feet are planted.** Each foot finds the ground under it, stays put while his body moves over it, and steps ahead when it's stretched. On hills the legs reach down or fold up to meet the slope. Rings crawling on the surface also ride over small bumps beside his path instead of clipping through them.
- **Nine new moves** (see below): bite, charge, shriek, magma vent, tail whip, tail flick, tremor, coil and dust.
- **Health is 6,000** at full size, the same as the Mountain.
- He leaves the Mountain and Cerberus alone: nothing that big counts as food to him.

## Installing

1. You already have Fabric for 1.21.1 and Fabric API from the Mountain.
2. Put `furrowmaw-1.4.0.jar` in `.minecraft\mods`. It sits fine next to the Mountain and Cerberus. Only one Furrowmaw jar can be in the folder at a time (the older ones there have been turned into blank stubs that do nothing).
3. For your server, put the same jar in `mountain-server\mods` too. Anyone joining needs it in their own mods folder as well, or the server turns them away.

## Getting him

- **Spawn eggs** (creative, Spawn Eggs tab): Calm, Hunting, Guardian (full size) and Small (hunting, a fifth of the size). There's a Furrowling egg too.
- He doesn't just appear. He comes up out of the ground where the egg lands, head first, and the rest of him follows out of the same hole.
- **Command:** `/furrowmaw summon hunting` makes a full-size one in front of you. Add a size for a smaller or bigger one, like `/furrowmaw summon calm 0.3`. Anything from 0.03 to 2.5 works.

### The moods

- **Calm** leaves you alone unless you hit him. Hit him and he'll hunt you down for over a minute.
- **Hunting** goes after anything he can feel moving.
- **Guardian** stays around the spot he came up and only hunts things near it.

## He's blind

He has no eyes. Everything he knows comes up through the ground:

- walking, and running even more
- jumping and landing
- breaking and placing blocks
- fighting, shooting, arrows landing
- explosions (very loud to him)

**Sneaking makes no sound to him.** Stand still or sneak and he loses you. If he hears something he can't place, he rears up partway and listens.

**Hush Bristle:** hold one in your off hand and nothing you do reaches him, not even digging.

You can see where he is while he's under the ground. The ground above his head heaves and spits dirt, and it shakes under you when he passes close.

## What he does

- **Swims through the ground.** Under the ground his legs fold back flat and he moves fast. His whole body follows the head through the same tunnel.
- **Crawls on top.** On the surface he walks on a pair of legs per ring. Each foot plants on the ground and stays there until it has to step, so the ripple running down his legs matches the ground he's covering. He churns a trail of coarse dirt where he crawls and flattens plants.
- **His legs rake you.** Standing next to him while he crawls is a bad idea, and his body shoves anything it runs into.

## His moves

Front end:
- **Bite.** A quick lunge and snap of the mandibles at whatever's right in front of the maw. Short cooldown, and the bite leaves you withering for a few seconds.
- **Slam.** The front of him rises up like in your build, sways, then comes crashing down in front of him. Anything under it is thrown.
- **Swallow.** He rears up with his mandibles wide open and comes down on you. If you get caught you're inside his head: the screen goes red, you take damage every second, and sneaking won't get you out. **Hit him from the inside** and it hurts him two and a half times as much. After enough hits he gags and spits you out and is stunned for a few seconds. Sit there and he spits you out after half a minute anyway.
- **Charge.** He drops his head and comes at you at three times his crawling speed. Whatever he hits when he gets there is flung.
- **Spit.** He rears and lobs lumps of magma from his maw. They splash, set things alight and slow you down.
- **Shriek.** A scream from the maw. Everything near him is shoved back and blinded by darkness, and everything alive within his hearing **glows** for twelve seconds: he can feel you now even if you're sneaking.
- **Magma vent.** He arches his front into a hump and the cracks on his rings spit magma straight up, to rain down all around him. Don't stand near him while he's venting.
- **Dust.** He shudders and throws up a cloud of dirt round his head. Anyone in it is blinded and slowed, and he slips away under the ground behind it. He does this when he's badly hurt.

Whole body:
- **Bursts up under you.** From under the ground he comes straight up under whatever he's hunting, throws everything nearby into the air, arches right over and dives back in. At the top of the arch he sometimes spits magma down at you.
- **Tremor.** He presses his whole length flat against the ground and a ring of broken ground runs out from him along the surface. Anyone standing on the ground when it reaches them is knocked into the air and slowed.
- **Coil.** He crawls round you in a circle and pulls the ring tight, dragging you toward the middle, then squeezes.
- **Thrash.** Get too close to his body and he whips his whole length side to side.

Tail end (only once his tail is out of the ground and you're near it):
- **Tail whip.** The tail lifts and lashes sideways through you. Anything it catches is flung a long way.
- **Tail flick.** The tail curls up over his back and flings lumps of ground at you.

And when he doesn't know where you are but has heard something, he rears up partway and **listens**.

## The book

The **Furrowmaw Codex** is how you control him, like the Mountain's codex. Craft it like this (H = hush bristle, C = chitin plate, B = book):

```
 H
CBC
 C
```

Hold it and the nearest Furrowmaw within 600 blocks is yours: he won't hunt you and he can't hear you while you're holding it. Right-click to open it.

- **Orders:** come to me, crawl where I look, hunt what I look at, leave it, hold still / let him go, follow me / stop following, come and carry me / get off, go under, come up, burst up where you are, open the ground where I look, where is he?
- **Attacks:** every move he has. A line goes grey with a count while that move is cooling down, or says *busy* while he's in the middle of something.
- **Him:** calm / hunting / guardian, forget his grudges, whether he breaks blocks.
- **Safe list:** who he is to leave alone, the same as the Mountain's. *Spare who I look at* puts the player you're looking at on your list (or, if you're looking at a creature, that whole kind: every cow, every villager). *Spare everyone near me* adds every player within 48 blocks. Each name on the list is a line with a ✕: press it to take them off. He doesn't hunt anyone on the list and doesn't hear them either. The list is yours, and it only counts while you're holding the book: put the book down and they're fair game again until you pick it up. The line at the bottom says whether it's in force and how many are on it.

At the top it says how far away he is, how much health he has left, his mood and what he's doing, with a bar for health and one for how many of his rings he still has.

**Being him.** *Come and carry me* puts you on his head and the view goes out behind him, like the Mountain's. The mouse says which way. **W** pushes him on, **A/D** bend him, **S** holds him still. **Sneak** takes him under the ground and **Jump** brings him back up, and you go with him: the ground can't hurt you while you're on him, and neither can his own magma. The keys down the left of the screen set off his moves (1 to 0, then Z X C V B N M; N is burst up, M opens the ground where you're looking), each with its own clock. Scroll, or [ and ], to move the view in and out. **G** gets you off (he sets you down on the surface if he's under).

## Fighting him

- Every ring is a hitbox, and so is his head. **The head takes 30% more damage.**
- He has 6,000 health at full size, the same as the Mountain. Smaller ones have less.
- **He loses his tail.** At two thirds and one third health, six rings tear off the back of him and turn into **Furrowlings**, small ring crawlers that bite. He gets shorter each time.
- Below half health he gets angry: faster, he hits harder, and his maw spits embers.
- **When he dies** he thrashes one last time, curls up on his back with his legs in the air and slowly sinks into the ground.

### What he drops

- **Mandible Blade:** a sword, a bit stronger than netherite, that sets what it hits on fire.
- **Chitin Plates:** craft them into chitin armor, which is as strong as netherite, doesn't burn and resists knockback.
- **Hush Bristles**
- netherite scrap (from a big one), magma cream and a lot of XP

Furrowlings sometimes drop chitin plates, and rarely a hush bristle.

## All the commands (cheats on)

| Command | What it does |
|---|---|
| `/furrowmaw summon [calm/hunting/guardian] [size]` | makes one in front of you |
| `/furrowmaw do <move>` | makes the nearest one do something to you: `bite`, `slam`, `swallow`, `charge`, `spit`, `shriek`, `vent`, `dust`, `breach`, `tremor`, `coil`, `thrash`, `tail_whip`, `tail_flick`, `listen`, `roll`, `erupt`, `sinkhole`, `dive`, `surface`, `shed` |
| `/furrowmaw list` | where they all are, their health, how many rings |
| `/furrowmaw mood <mood>` | changes the nearest one's mood |
| `/furrowmaw size <size>` | resizes the nearest one |
| `/furrowmaw hurt <amount>` | takes that much off the nearest one |
| `/furrowmaw sethealth <n>` | sets the nearest one's health |
| `/furrowmaw heal` | heals the nearest one fully |
| `/furrowmaw kill` | kills them (he still curls up and sinks) |
| `/furrowmaw remove` | removes them straight away |
| `/furrowmaw health <n>` | full-size health for new ones |
| `/furrowmaw damage <x>` | multiplies how hard he hits |
| `/furrowmaw hearing <x>` | how far he can feel you (1 = normal) |
| `/furrowmaw griefing true/false` | whether he breaks the ground |
| `/furrowmaw shake true/false` | screen shake |
| `/furrowmaw reload` | re-reads the settings file |

## Settings

`config\furrowmaw.json`, made the first time you start the game:

- `spawnEggScale` is how big the egg ones are (1.0 is full size), and `smallEggScale` is how big the small egg is (0.2)
- `health`, `damageMultiplier`, `hearing`
- `griefing` covers the holes he bursts and the dirt he churns up. It also needs the mobGriefing gamerule on. `furrows` is the dirt trail on its own.
- `shedsRings` sets whether he loses his tail rings
- `chunkLoading` keeps the ground around his head loaded while players are near
- `screenShake`, `bossBar`, `renderDistance`
- `simpleFarAway` / `simpleFarAwayAt`: far away he's drawn with bigger blocks so he runs faster
- `maxFurrowmaws`: how many the world holds at once (0 = no limit). Past that, the oldest one goes.

## Tips

- If he's hunting you, **sneak**. If you need to dig, keep a hush bristle in your off hand.
- When the ground starts heaving under you, **move**. He's about to come up.
- Get swallowed on purpose if you're brave: hits from the inside are worth two and a half of the ones from outside.
- Use a render distance of 12 or more for the full-size one.
- Craft the book early. With it in your hand he leaves you alone, and you can put your friends on the safe list before he finds them.
