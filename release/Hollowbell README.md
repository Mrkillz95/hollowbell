# Hollowbell (Java mod) — version 1.1.2

Your KillzAI Hollowbell build turned into a real boss for Minecraft Java 1.21.1 with Fabric. He's a huge green jellyfish, about 200 blocks wide and 200 tall at full size, and he swims through the air.

He's made from your build block for block. Every block wears its real texture, and the glass is see-through in its own colours, so you can look into the hollow dome and see what he's caught.

## What's new in 1.1.2

- **He can't pick up the other giants.** His strands and arms won't grab, wrap or carry Pitchgut, the Cerberus, the Furrowmaw, any other boss (the Wither, the Ender Dragon, the Warden), or anything huge. He can still fight them. JJ's future boss mods are left alone too.

## What's new in 1.1.1

- **All the `/hollowbell` commands work again.** In 1.1.0 the new `detail` command stopped every other `/hollowbell` command from reaching the game.
- **Riding him:** the list of keys no longer runs off the screen. The moves are in two columns, and the whole list gets smaller if your window is small.
- **Wind and grudge bars** show while you ride him, like in the book.
- "Sit on his crown" is now just **Ride him**.

## What's new in 1.1

- **No more threads.** His pods hold him up now. Pop a third of them and he sinks right down for a while. Popped pods grow back.
- **He really flies.** He swims up and down like a jellyfish: the bell squeezes and shoots him up, and opens like a parachute when he sinks. He follows the ground, climbs after things up high and never scrapes his rim on the ground.
- **He moves like he has weight.** Arms and strands trail behind him, swing back when he stops, bundle up under him when he climbs and float up when he sinks. Nothing snaps from one pose to the next, even when a move gets cut off halfway. The bell leans into the way he's going.
- **Smooth on your screen too.** He glides between server updates instead of stepping.
- **What he grabs stays on the end of the strand** holding it, all the way up into the dome.
- **22 moves** (13 new), split into light, medium and heavy. Heavy moves are huge, hit a big area and come with a loud warning and a red message first. After one he's worn out for a few seconds.
- **He kills things now.** In 1.0 he hardly hurt creatures. Now his hits are much bigger, they land even right after another hit, hunting and guardian ones go after hostile mobs, and what he takes into the dome dies in there.
- **His own sounds:** a low hum, a wet drifting sound, the whoosh of each pulse, a deep toll, a heartbeat when you're inside him, and a sound for every move. Bigger ones sound deeper.
- **`/hollowbell detail on/off`** to choose whether he's drawn simpler far away.
- **A bit tidier:** fewer egg clumps (in small groups), fewer speckles, no floating bits.
- **A fresh one comes down out of the sky** instead of popping in.
- **He doesn't freeze** when you watch him from far off.
- **New commands:** `height`, `goto`, `stay`, `detail`. The thread commands are gone.
- Runs lighter: parts of him you can't see aren't drawn.

## Installing

1. You already have Fabric for 1.21.1 and Fabric API from the Mountain.
2. Put `hollowbell-1.1.2.jar` in `.minecraft\mods`. Take the old Hollowbell jar out first. It sits fine next to Pitchgut and Furrowmaw.
3. For your server, put the same jar in `mountain-server\mods` too. Anyone joining needs it in their own mods folder as well.

## Getting him

- **Spawn eggs** (creative, Spawn Eggs tab): Calm, Hunting, Guardian (full size) and Small (hunting, a fifth of the size). There's a Belling egg too.
- **Command:** `/hollowbell summon hunting` makes a full-size one in front of you. He comes down out of the sky. Add a size for a smaller or bigger one, like `/hollowbell summon calm 0.3`. Anything from 0.03 to 2 works.

### The moods

- **Calm** leaves you alone unless you hit him. Hit him and he'll come after you for a minute.
- **Hunting** goes after any player he can find.
- **Guardian** stays near the spot he was put down and goes after anything that comes near it, hostile mobs too.

Hunting ones go after hostile mobs as well when there's no player about.

## What he's like

- **He swims.** He drifts low over the land with his strands hanging under him, and the strand ends drag along the ground and flatten plants. He goes up hills and down into valleys.
- **His bell pulses.** The dome squeezes in and lets go, and a ripple runs round the rim. Each pulse pushes him along. His arms swing with it.
- **Climbing**, the bell squeezes hard and quick and shoots him up, with his arms and strands pulled in under him. **Sinking**, the bell opens wide and his strands float up and spread out.
- **He goes after things up high.** Fly near him or build up a pillar and he'll climb until you're among his strands.
- **His strands sway** and trail behind him when he moves, and swing back when he stops. The pods bob and the egg clumps wobble.
- **At night** the crown, the glowing spots and the froglight in his strands glow, and the glow pulses with him.
- **Touching a strand stings:** poison and slowness.

## His moves

There are three kinds. **Light** moves are quick and he uses them a lot. **Medium** moves have a short warning and hit hard: most normal mobs die from one. **Heavy** moves are rare and huge: they hit tens of blocks round him at full size, kill everything normal in the way and badly hurt a player in netherite. Before a heavy move you hear a loud warning sound, a red message comes up and his glow flares, so you have 1.5 to 4.5 seconds to get away. After one he's worn out for 5 seconds: slow, drooping and not attacking.

Bigger ones hit harder and wider, smaller ones less.

### Light

| Move | What it does |
|---|---|
| Grab and lift | A strand wraps round you and slowly pulls you up into the dome. Hit that strand four times and it lets go. |
| Harvest | He does it to anything: cows, villagers, even trees get pulled up and into the dome, where you can see them through the glass. Creatures die in there. |
| Sting volley | The strand ends nearest you flick three rounds of stingers at you. Poison and slowness. |
| Strand lash | One strand winds back and whips sideways through everything on that side of him. |
| Glow flash | Everything that glows on him flares at once: blindness and darkness, worse at night. |

### Medium

| Move | What it does |
|---|---|
| Curtain | The strands close in round you like a cage and pull tight. |
| Sweep | All his strands swing across the ground in a wide arc. |
| Arm slam | One of his eight arms lifts up and slaps down on the ground. Everything under the arm gets hit. |
| Arm wrap | An arm curls round you and squeezes. Hit that arm four times and it lets go. |
| Pulse wave | A hard pulse sends out a shock wave: knockback and nausea, and it knocks flyers out of the air. |
| Shed | Egg clumps break off and hatch into **Bellings**, small jellies that drift after you and sting. |
| Spore cloud | Egg clumps burst into clouds of poison that drift after you. |
| Pod burst | Every pod swells and sprays poison goo straight down. |
| Egg rain | Egg clumps drop off and burst where they land. Some hatch into Bellings. |

### Heavy

| Move | What it does |
|---|---|
| Drop | The bell lifts and glows, then the whole of him slams down on the ground. He stays down for a while, then rises back up slowly. |
| Whirlpool | He spins, and his strands drag everything in a wide ring in under him, then crush it. |
| Sky dive | He climbs about 70 blocks, turns over and dives bell first. The shock wave reaches about 100 blocks out. |
| Deep toll | Three tolls, louder each time, then a massive shock ring. It throws everything far and breaks glass and leaves. |
| Arm storm | All eight arms go up, then crash down one after another all round him. |
| Stinger storm | Every strand flicks stingers up, and they rain down over a wide area. |
| Sun lances | A beam of burning light comes out of each glowing spot and sweeps over the ground. One hunts you. |
| Undertow | His strands spread wide and drag everything round him in, then slam it down. |

## Inside the dome

If a strand pulls you all the way up, you're inside the dome with whatever else he's caught. You hear his heartbeat and the dome echoing. It hurts in there (poison and damage over time, and creatures don't last long), but the glowing spots are right over your head. **Hit them from inside for extra damage.** Hurt him enough from inside and he lets you go (you float down slowly).

A very small Hollowbell has no room inside: he squeezes you and drops you instead.

## Fighting him

| Where you hit him | How much it counts |
|---|---|
| The crown (the pale patch right on top of his dome) | 3 times (4 from inside) |
| The five glowing spots (the four yellow balls under the dome and the glowing vase in the middle) | 2.5 times (3.5 from inside) |
| A pod | full, and the pod pops |
| An egg clump | a little |
| The copper and bone (dome, rim, arms, strands) | very little |

- **Pods** hang among the strands and can be reached from the ground. Each popped pod takes a good piece of his health and drops a pod.
- **The pods hold him up.** Each popped pod makes him hang a little lower and lean toward that side. Pop a third of them (8 of 23 at full size) and he sinks right down for at least 30 seconds, until enough have grown back. A popped pod starts growing back after 90 seconds.
- While he's down (after a drop, or with his pods popped), everything hits him a bit harder, and his dome and rim are in reach from the ground. The crown and spots are high up, so bring a bow.
- **Boss bars:** his health, and how many pods are left (it turns purple while he's down).
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

- **Orders:** come to me, drift where I look, grab what I look at, leave it, hold still / let him drift, ride him / get off, let go of everything, where is he, and boxes to type an X and Z to send him to.
- **Moves:** every move he has, on three tabs: light, medium and heavy. A line goes grey with a count while that move is cooling down. Heavy moves cost a lot more wind.
- **Him:** calm / hunting / guardian, forget his grudges, whether he breaks blocks, whether he harvests.
- **Safe list:** *Spare who I look at* puts the player you're looking at on your list (or, if it's a creature, every one of that kind). *Spare everyone near me* adds every player within 48 blocks. Each name has a ✕ to take it off. The list only counts while you're holding the book.

At the top it says how far away he is, his health, his mood and what he's doing, with bars for his wind and his grudge. Like the Mountain, every order costs him some wind, and if you push him too hard he starts to resent you: first he takes his time, then he ignores some orders, then he picks his own targets, and in the end he turns on you. Hit him five times while the book keeps him off you and he stops listening to it.

## Being him

*Ride him* in the book: he drifts over to you, a strand picks you up and sets you on top of his dome, and the view swings out behind him, like the Mountain's.

| Key | What it does |
|---|---|
| Mouse | which way |
| W / A / S / D | drift him that way |
| Jump / Sneak | take him up / down |
| 1 to 0, then Z X C V B N M, then R H J K U | his 22 moves, light ones first (the list sits top left with a clock on each line) |
| Scroll, or [ and ] | move the view in and out |
| G | get off (he sets you down beside him) |

Top left you also see his health, and his **wind** and **grudge** bars, the same as in the book. Every move costs him some wind. Push him too hard and the grudge grows.

## All the commands (cheats on)

| Command | What it does |
|---|---|
| `/hollowbell summon [calm/hunting/guardian] [size]` | makes one in front of you |
| `/hollowbell do <move>` | makes the nearest one do a move at you. Light: `grab`, `harvest`, `sting_volley`, `strand_lash`, `glow_flash`. Medium: `curtain`, `sweep`, `arm_slam`, `arm_wrap`, `pulse_wave`, `shed`, `spore_cloud`, `pod_burst`, `egg_rain`. Heavy: `drop`, `whirlpool`, `sky_dive`, `deep_toll`, `arm_storm`, `stinger_storm`, `sun_lances`, `undertow` |
| `/hollowbell list` | where they all are, their health and pods |
| `/hollowbell mood <mood>` | changes the nearest one's mood |
| `/hollowbell size <size>` | resizes the nearest one |
| `/hollowbell hurt <amount>` | takes that much off the nearest one |
| `/hollowbell sethealth <n>` | sets the nearest one's health |
| `/hollowbell heal` | heals the nearest one fully and grows his pods back |
| `/hollowbell popped <n>` | pops that many pods (for testing) |
| `/hollowbell height <blocks>` | how high the nearest one drifts over the ground |
| `/hollowbell goto <x> <z>` | sends the nearest one there |
| `/hollowbell stay true/false` | makes the nearest one hold still where he is, or drift again |
| `/hollowbell ride` | puts you straight on top of him to ride him (or takes you off) |
| `/hollowbell kill` | kills them (he still folds down and sinks) |
| `/hollowbell remove` | removes them straight away |
| `/hollowbell health <n>` | full-size health for new ones |
| `/hollowbell damage <x>` | multiplies how hard he hits |
| `/hollowbell griefing true/false` | whether he pulls up trees and flattens plants |
| `/hollowbell shake true/false` | screen shake |
| `/hollowbell reload` | reads the settings file again |
| `/hollowbell detail on/off` | on (the default): he's drawn simpler far away so the game runs faster. Off: always full detail. On its own it says which. This one works without cheats and only changes your own game. |

## Settings

`config\hollowbell.json`, made the first time you start the game:

- `spawnEggScale` is how big the egg ones are (1.0 is full size), and `smallEggScale` is how big the small egg is (0.2)
- `health`, `damageMultiplier`, and `mobDamage` (2.5): how much harder he hits creatures than players
- `griefing` covers pulling up trees, flattening plants and cracking the ground. It also needs the mobGriefing gamerule on.
- `harvest`: whether he pulls up creatures and trees on his own
- `insideDome`: off and he squeezes what he lifts and drops it instead of taking it inside
- `podRegrowSeconds` (90): how long a popped pod takes to start growing back
- `podsToSink` (0.34): how many of his pods have to be popped before he sinks, and `sunkSeconds` (30): how long he stays down at least
- `shedCount`: how many egg clumps he sheds at a time (0 = never)
- `maxHollowbells`: how many the world holds at once (0 = no limit). Past that, the oldest one goes.
- `bookCosts`, `windSeconds`, `freeHits`, `grudgeRate`, `bookRange`: how the book works, the same as the Mountain's
- `screenShake`, `bossBar`, `soundVolume`, `renderDistance`
- `ambientSounds`: his hum and drifting sound (off keeps the rest)
- `chunkLoading`: keeps the ground under him loaded and him moving while a player is near him
- `simpleFarAway` / `simpleFarAwayAt`: far away he's drawn with bigger blocks so he runs faster (`/hollowbell detail` changes the first one)

## Tips

- Pop the pods first. They're low down among the strands and each one is a big chunk of his health.
- If a strand grabs you, hit that strand. If you'd rather go up, hit the glowing spots once you're inside.
- Pop a third of his pods to bring him down, then hit him while he can't rise.
- Watch for the bell squeezing hard: that's the pulse wave. Don't be flying near him when it goes.
- When the red message comes up, run. Heavy moves reach a long way, so get well out from under him, or get behind something solid.
- Right after a heavy move he's worn out for a few seconds. That's the time to hit him.
- Use a render distance of 12 or more for the full-size one.
