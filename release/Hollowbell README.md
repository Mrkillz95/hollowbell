# Hollowbell (Java mod) — version 1.0.0

Your KillzAI Hollowbell build turned into a real boss for Minecraft Java 1.21.1 with Fabric. He's a huge green jellyfish, about 200 blocks wide and 200 tall at full size, and 128 threads run up out of his dome into the sky above him.

He's made from your build block for block. Every block wears its real texture, and the glass is see-through in its own colours, so you can look into the hollow dome and see what he's caught.

## Installing

1. You already have Fabric for 1.21.1 and Fabric API from the Mountain.
2. Put `hollowbell-1.0.0.jar` in `.minecraft\mods`. It sits fine next to Pitchgut and Furrowmaw.
3. For your server, put the same jar in `mountain-server\mods` too. Anyone joining needs it in their own mods folder as well.

## Getting him

- **Spawn eggs** (creative, Spawn Eggs tab): Calm, Hunting, Guardian (full size) and Small (hunting, a fifth of the size). There's a Belling egg too.
- **Command:** `/hollowbell summon hunting` makes a full-size one in front of you. Add a size for a smaller or bigger one, like `/hollowbell summon calm 0.3`. Anything from 0.03 to 2 works.

### The moods

- **Calm** leaves you alone unless you hit him. Hit him and he'll come after you for a minute.
- **Hunting** goes after any player he can find.
- **Guardian** stays near the spot he was put down and only goes after things near it.

## What he's like

- **He drifts.** He floats slowly over the land with his strands hanging under him. The strand ends drag along the ground and flatten plants.
- **His bell pulses.** Every few seconds the dome squeezes in and lets go, and each pulse pushes him along. His arms swing with it like he's swimming.
- **His strands sway** and trail behind him when he moves, and swing back when he stops. The pods and egg clumps jiggle on them.
- **His threads** sway gently and lean the way he's drifting, like he's hanging from them. They fade out about 100 blocks above his crown.
- **At night** the crown, the glowing spots and the froglight in his strands glow.
- **Touching a strand stings:** poison and slowness.

## His moves

| Move | What it does |
|---|---|
| Grab and lift | A strand wraps round you and slowly pulls you up into the dome. Hit that strand four times and it lets go. |
| Harvest | He does it to anything: cows, villagers, even trees get pulled up and hang inside the dome, where you can see them through the glass. |
| Curtain | The strands close in round you like a cage and pull tight. |
| Sweep | All his strands swing across the ground in a wide arc. |
| Arm slam | One of his eight arms lifts up and slaps down on the ground. |
| Arm wrap | An arm curls round you and squeezes. Hit that arm four times and it lets go. |
| Pulse wave | A hard pulse sends out a shock wave: knockback and nausea, and it knocks flying players out of the air. |
| Drop | He stops floating and the whole bell slams down on the ground. He stays down for a while, then rises back up slowly. |
| Shed | Egg clumps break off and hatch into **Bellings**, small jellies that drift after you and sting. |

## Inside the dome

If a strand pulls you all the way up, you're inside the dome with whatever else he's caught. It hurts in there (poison and damage over time), but the glowing spots are right over your head. **Hit them from inside for extra damage.** Hurt him enough from inside and he lets you go (you float down slowly).

A very small Hollowbell has no room inside: he squeezes you and drops you instead.

## Fighting him

| Where you hit him | How much it counts |
|---|---|
| The crown | 3 times (4 from inside) |
| The five glowing spots (the four yellow balls under the dome and the glowing vase in the middle) | 2.5 times (3.5 from inside) |
| A pod | full, and the pod pops |
| An egg clump | a little |
| His threads | a little, and the thread gets cut |
| The copper and bone (dome, rim, arms, strands) | very little |

- **Pods** hang among the strands and can be reached from the ground. Each popped pod takes a good piece of his health, drops a pod, and stays dark.
- **Threads** can be hit and cut (arrows work too). Each cut thread makes him hang lower and lean toward that side. Cut enough (about 6 in 10) and he sinks right down and can't rise until they grow back. A cut thread starts growing back after 45 seconds.
- While he's down (after a drop, or with his threads cut), everything hits him a bit harder, and his dome and rim are in reach from the ground. The crown and spots are high up, so bring a bow.
- **Boss bars:** his health, how many pods are left, and how many threads are still holding.
- He has 6,000 health at full size. Smaller ones have less, and he gets more when several players fight him.
- **Below half health** the lime loops on his arms turn red and he pulses more often.
- **When he dies** he stops floating, the bell sinks onto the strands, the strands buckle, and he folds down onto the ground. Then he sinks away and drops his loot.

### What he drops

| Drop | What it's for |
|---|---|
| Stinger | A whip-like sword, a bit stronger than netherite. What it hits is poisoned and slowed. |
| Bell Glass | Craft it into bell glass armor (like diamond, with more toughness) and into the book. |
| Hollowbell Pods | For the book, and a pod in a glass bottle makes a poison potion. |
| Hollowbell Crown | A glowing trophy block to set down. |
| Oxidized copper, bone blocks, verdant froglight | From his body. |

## The book

The **Hollowbell Codex** controls him, laid out like the Mountain's and Furrowmaw's. Craft it like this (P = pod, G = bell glass, B = book):

```
 P
GBG
 G
```

Carry it anywhere on you and the nearest Hollowbell within 600 blocks is yours: he leaves you alone, drifts where you point and grabs what you point at. Right-click to open it. Right-click a creature with it to send him after that creature.

- **Orders:** come to me, drift where I look, grab what I look at, leave it, hold still / let him drift, sit on his crown / get off, let go of everything, where is he, and boxes to type an X and Z to send him to.
- **Moves:** every move he has. A line goes grey with a count while that move is cooling down.
- **Him:** calm / hunting / guardian, forget his grudges, whether he breaks blocks, whether he harvests.
- **Safe list:** *Spare who I look at* puts the player you're looking at on your list (or, if it's a creature, every one of that kind). *Spare everyone near me* adds every player within 48 blocks. Each name has a ✕ to take it off. The list only counts while you're holding the book.

At the top it says how far away he is, his health, his mood and what he's doing, with bars for his wind and his grudge. Like the Mountain, every order costs him some wind, and if you push him too hard he starts to resent you: first he takes his time, then he ignores some orders, then he picks his own targets, and in the end he turns on you. Hit him five times while the book keeps him off you and he stops listening to it.

## Being him

*Sit on his crown* in the book: he drifts over to you, a strand picks you up and puts you on his crown, and the view swings out behind him, like the Mountain's.

| Key | What it does |
|---|---|
| Mouse | which way |
| W / A / S / D | drift him that way |
| 1 to 9 | his moves (the list sits down the left of the screen with a clock on each line) |
| Scroll, or [ and ] | move the view in and out |
| G | get off (he sets you down beside him) |

## All the commands (cheats on)

| Command | What it does |
|---|---|
| `/hollowbell summon [calm/hunting/guardian] [size]` | makes one in front of you |
| `/hollowbell do <move>` | makes the nearest one do a move at you: `grab`, `harvest`, `curtain`, `sweep`, `arm_slam`, `arm_wrap`, `pulse_wave`, `drop`, `shed` |
| `/hollowbell list` | where they all are, their health, pods and threads |
| `/hollowbell mood <mood>` | changes the nearest one's mood |
| `/hollowbell size <size>` | resizes the nearest one |
| `/hollowbell hurt <amount>` | takes that much off the nearest one |
| `/hollowbell sethealth <n>` | sets the nearest one's health |
| `/hollowbell heal` | heals the nearest one fully and grows his threads back |
| `/hollowbell popped <n>` | pops that many pods (for testing) |
| `/hollowbell cut <n>` | cuts that many threads (for testing) |
| `/hollowbell mend` | grows all his threads back |
| `/hollowbell ride` | puts you straight on his crown (or takes you off) |
| `/hollowbell kill` | kills them (he still folds down and sinks) |
| `/hollowbell remove` | removes them straight away |
| `/hollowbell health <n>` | full-size health for new ones |
| `/hollowbell damage <x>` | multiplies how hard he hits |
| `/hollowbell griefing true/false` | whether he pulls up trees and flattens plants |
| `/hollowbell shake true/false` | screen shake |
| `/hollowbell reload` | reads the settings file again |

## Settings

`config\hollowbell.json`, made the first time you start the game:

- `spawnEggScale` is how big the egg ones are (1.0 is full size), and `smallEggScale` is how big the small egg is (0.2)
- `health`, `damageMultiplier`, `mobDamage`
- `griefing` covers pulling up trees, flattening plants and cracking the ground. It also needs the mobGriefing gamerule on.
- `harvest`: whether he pulls up creatures and trees on his own
- `insideDome`: off and he squeezes what he lifts and drops it instead of taking it inside
- `threadsCanBeCut`, `threadRegrowSeconds`
- `shedCount`: how many egg clumps he sheds at a time (0 = never)
- `maxHollowbells`: how many the world holds at once (0 = no limit). Past that, the oldest one goes.
- `bookCosts`, `windSeconds`, `freeHits`, `grudgeRate`, `bookRange`: how the book works, the same as the Mountain's
- `screenShake`, `bossBar`, `soundVolume`, `renderDistance`
- `simpleFarAway` / `simpleFarAwayAt`: far away he's drawn with bigger blocks so he runs faster

## Tips

- Pop the pods first. They're low down among the strands and each one is a big chunk of his health.
- If a strand grabs you, hit that strand. If you'd rather go up, hit the glowing spots once you're inside.
- Cut his threads with arrows to bring him down, then hit him while he can't rise.
- Watch for the bell squeezing hard: that's the pulse wave. Don't be flying near him when it goes.
- Use a render distance of 12 or more for the full-size one.
