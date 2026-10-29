# Giants Performance: the bridge (protocol 1)

The Giants Guide has a **Giants Performance** screen (`/giants performance`, `/giants effects`, a key in
Controls, or the **Performance** button in the Guide). It lists effect, detail and feature settings with
presets: Highest, High, Medium, Low and Very Low, plus each player's own.

The guide never touches a giant's code. It talks to each giant's existing `GiantsBridge` by name, the same way
`/giants` does, with two new action strings. A giant mod that doesn't know them returns `null` (as it already
does for any unknown action), and the guide leaves it out of the list.

**What each giant mod adds:** one class, `GiantsPerf`, plus two lines in `GiantsBridge.giants(...)`. Then its fx
code reads the levels from `GiantsPerf`. There is a full example for Gloamwick in
`perf/gloamwick-giants-perf.patch`.

## The rule: Highest changes nothing

Every level sits on top of the giant's own config. It is a share (0..1) that multiplies his own number, or a
switch that can only turn his own setting off. The default is always 1, true or choice 0, and the default must
change nothing. So:

- without the guide, or before it has said anything, every level is its default;
- at Highest the guide sends every default;
- `bigEffects * f("fx.bigImpacts")` is `bigEffects` at Highest; `screenShake && on(...)` is `screenShake`.

## 1. `perf`: the giant's list

`GiantsBridge.giants(server, "perf", "1")`. The argument is the guide's protocol version. `server` can be null,
because the client asks too. The giant returns a `List<String>`, one JSON object per line.

**Line 1, the header:**

```json
{"perf":1,"giant":"gloamwick","uses":["fx.particles","fx.bigImpacts","detail.renderDistance","feature.meetings"]}
```

- `perf`: the protocol version the mod speaks. If it is newer than the guide's, the guide leaves the mod out.
- `uses`: the guide's shared settings (below) that this mod actually reads. The screen shows "Read by: ..." for
  each one, and leaves out any that no installed giant reads. **Only list ids your code reads.**

**Then one line per setting of the giant's own** (at most 64):

| field     | meaning |
|-----------|---------|
| `id`      | `giant.name`, e.g. `gloamwick.hush`. The guide puts `giant.` in front if it's missing. Letters, digits, `_ . -`, 64 at most. |
| `section` | `effects`, `detail`, `features`, or `giant` (his own section on the screen). |
| `kind`    | `toggle`, `slider` or `choice`. |
| `min`, `max`, `step` | sliders only. |
| `options` | choices only, 2 to 8 names, best first (`["Full","Half","Off"]`). If the last one starts with "Off", choosing it counts as removing. |
| `default` | the value at Highest: `true`/`false`, a number, or a choice index. |
| `levels`  | 5 values: Highest, High, Medium, Low, Very Low. The first is always replaced by `default`. Leave it out to keep the default in every preset. |
| `cost`    | `low`, `med` or `high`: what it costs to keep up. |
| `side`    | `client` (each player sets it for themselves) or `server` (the whole world). |
| `ops`     | `true` for `server` settings. The guide makes every `server` setting operators-only either way. |
| `name`, `what`, `loses` | plain words: the name (48 characters), one line on what it is, and what lowering or turning it off loses. For a `server` setting, say exactly what is taken away. |
| `unit`    | sliders: `%` (0..1 shown as a percentage), `x`, `blocks`, or empty. |

The guide checks everything. A line it can't use is dropped, and the rest still show. Values are clamped and
snapped to `step`.

## 2. `perfset`: the values

`GiantsBridge.giants(server, "perfset", json)` with:

```json
{"perf":1,"side":"client","values":{"fx.particles":0.5,"detail.farSight":true,"detail.renderDistance":0.85,"gloamwick.waxDrips":0.75}}
```

- **side `client`** is sent on each player's own game (with `server` null) when the game starts, and again each
  time they change something. Every client setting the giant should know about is included: the shared ones,
  his own, and his own copy of the per-giant ones (the guide's `detail.renderDistance.gloamwick` arrives as
  plain `detail.renderDistance`).
- **side `server`** is sent on the server when it starts, and each time an operator changes a feature. It holds
  every world setting.
- Toggles come as `true`/`false`, sliders as numbers, and choices as their index. Every value is sent, with
  defaults included, so the giant never has to guess. Ignore ids you don't know.
- Return any non-null list (for example `["ok"]`). Returning `null` means "didn't take it".

Single player runs both sides in one game, so keep the two sides in two separate maps.

## The guide's shared settings

All of these are `client` unless marked as the world's. A share of 1 changes nothing.

| id | kind | what the giant does with it | Highest / High / Medium / Low / Very Low |
|----|------|-----------------------------|-------------------------------------------|
| `fx.particles` | share | times every particle count (drips, dust, sparks, spores) | 1 / .75 / .5 / .3 / .15 |
| `fx.bigImpacts` | share | times `bigEffects` | 1 / .8 / .6 / .35 / 0 |
| `fx.smokeTrails` | share | smoke plumes, swipe trails, projectile trails | 1 / 1 / .6 / .3 / 0 |
| `fx.flashes` | share | flashes, glowing parts, moving light blocks | 1 / 1 / .75 / .5 / .25 |
| `fx.screenShake` | share | times the shake (0 = off, even if `screenShake` is on) | 1 / 1 / 1 / .75 / .5 |
| `fx.screenEffects` | share | vignettes, darkening, sky and fog colour | 1 / 1 / 1 / .75 / .5 |
| `fx.ambient` | share | biome ambient particles | 1 / .75 / .5 / .25 / 0 |
| `fx.fog` | switch | biome fog and the fog round him | on, on, on, on, off |
| `detail.model` | share .25..1 | at 1 (≥ .999) always the full model, at every distance; under 1, times `simpleFarAwayAt`, and the simpler model even if `simpleFarAway` is off | 1 / .85 / .65 / .45 / .3 |
| `detail.animation` | choice | 0 Full, 1 Simpler far off, 2 Simpler (fewer secondary-motion updates) | 0 / 0 / 1 / 1 / 2 |
| `detail.farSight` | switch | far-sight skyline stand-ins | on, on, on, on, off |
| `detail.farSightDistance` | share .25..1 | times `farSightBlocks` | 1 / 1 / .75 / .5 / .25 |
| `detail.lootBeams` | choice | 0 All, 1 Near only (none from far-sight), 2 Off | 0 / 0 / 0 / 1 / 2 |
| `detail.drawing` | choice | 0 Classic: drawn exactly as before (the old drawing, for anyone who sees a problem with Fast); 1 Fast (the default since 1.6.7): the same picture with less work for the graphics card. Presets never change it (built-in or a player's own, and not "reset to preset"): each player picks it. A giant that hasn't heard from the guide draws Fast | 1 in every preset (not applied) |
| `detail.sounds` | share | times `soundVolume` for sounds played on the client | 1 in every preset |
| `detail.music` | share | fight music volume; 0 = none | 1 in every preset |
| `detail.renderDistance` | share .25..1 | times `renderDistance` (one slider per giant on the screen) | 1 / 1 / .85 / .65 / .45 |
| `feature.meetings` | world switch | off: no meetings (as if `meetings` were off) | on, on, on, on, off |
| `feature.summons` | world share | times how many helpers a summoning move calls (round, at least 1) | 1 / 1 / 1 / .6 / .3 |
| `feature.keepAwake` | world share | times the keep-awake ticket radius; 0 = no ticket | 1 / 1 / 1 / .6 / .3 |
| `feature.footPrecision` | world choice | 0 every tick, 1 every 2nd, 2 every 4th tick for server foot placement | 0 / 0 / 0 / 1 / 2 |
| `feature.hitSampling` | world choice | 0 Full, 1 Half, 2 Quarter of the hit-zone sample points | 0 / 0 / 0 / 1 / 2 |
| `feature.landmarks` | world share | times landmark chances in **new** chunks of his ground | 1 / 1 / 1 / .6 / .3 |

Features (the world's) are operators only. A player's game sends a request, and the server checks
`hasPermissions(2)` (or that it's the owner's own single-player world) before it changes anything. The values are
saved with the world (`data/jj_giants_performance.dat`). Presets only change the world's features when an
operator turns on "Presets set world features".

## The class to add: `GiantsPerf`

Put it in your main package (common code, so both sides can read it). See the patch for the whole class. In
short:

```java
public final class GiantsPerf {
    public static final int VERSION = 1;
    private static final String[] USES = {"fx.particles", "fx.bigImpacts", /* only what you read */};
    private static final String[] OWN = { /* your own setting lines, JSON */ };
    private static volatile Map<String, Double> client = Map.of(), server = Map.of();

    public static List<String> describe() { /* header line + OWN */ }
    public static List<String> receive(String arg) { /* parse, store by side; null if unreadable */ }

    public static float f(String id)        // client share, 1 by default
    public static boolean on(String id)     // client switch, true by default
    public static float world(String id)    // server share
    public static boolean worldOn(String id)
}
```

In `GiantsBridge.giants(...)`, before the `switch`:

```java
if (action.equals("perf")) return GiantsPerf.describe();
if (action.equals("perfset")) return GiantsPerf.receive(arg);
```

Then read it where the effects are made, always **on top of** the config:

```java
BigFxClient.init(
        () -> GloamConfig.V.screenShake && GiantsPerf.f("fx.screenShake") > 0f,
        () -> GloamConfig.V.soundVolume * GiantsPerf.f("detail.sounds"),
        () -> GloamConfig.V.simpleFarAway || GiantsPerf.f("detail.model") < 1f,
        () -> GloamConfig.V.bigEffects * GiantsPerf.f("fx.bigImpacts"));
```

## Checklist for each giant

1. Add `GiantsPerf` with `USES` listing only what you read, and your own settings in `OWN`. Add per-move
   toggles where a move is heavy (for example `gloamwick.eclipseSky`).
2. Add the two lines in `GiantsBridge`.
3. Wire each id you list. With every value at its default the code must take exactly the old path.
4. Server features (`side: server`) go in the `features` section with a `loses` line that says exactly what is
   lost. Read them with `world(...)` / `worldOn(...)`.
5. A gametest: `giants(null, "perf", "1")` starts with a `{"perf":1,...}` header, and after `perfset` with every
   default, the fx numbers are the same as without it.
