// Bedrock block names from the build -> Java 1.21.1 block ids. Anything not in here
// makes the converter stop with an error: nothing is ever quietly turned into stone.

const COLOURS = {
  white: 'white', orange: 'orange', magenta: 'magenta', light_blue: 'light_blue', yellow: 'yellow',
  lime: 'lime', pink: 'pink', gray: 'gray', silver: 'light_gray', cyan: 'cyan', purple: 'purple',
  blue: 'blue', brown: 'brown', green: 'green', red: 'red', black: 'black',
};

// Same name on both editions.
const SAME = new Set([
  'bone_block', 'calcite', 'glass', 'tinted_glass', 'glowstone', 'sea_lantern', 'shroomlight',
  'honeycomb_block', 'dripstone_block', 'moss_block', 'end_stone',
  'verdant_froglight', 'ochre_froglight', 'pearlescent_froglight',
  'copper_block', 'exposed_copper', 'weathered_copper', 'oxidized_copper',
  'cut_copper', 'exposed_cut_copper', 'weathered_cut_copper', 'oxidized_cut_copper',
  'amethyst_block', 'prismarine',
]);

// Different name on Java.
const RENAMED = {
  end_bricks: 'end_stone_bricks',
};

function javaName(bedrock) {
  const ax = bedrock.match(/^([a-z_]+)\[axis=([xz])\]$/);
  if (ax) return javaName(ax[1]) + `[axis=${ax[2]}]`;     // a pillar block lying on its side
  const m = bedrock.match(/^([a-z_]+)(?:\[color=([a-z_]+)\])?$/);
  if (!m) throw new Error('cannot read block name: ' + bedrock);
  const [, base, colour] = m;
  if (colour !== undefined) {
    const c = COLOURS[colour];
    if (!c) throw new Error(`unknown colour "${colour}" in ${bedrock}`);
    switch (base) {
      case 'stained_glass': return `minecraft:${c}_stained_glass`;
      case 'concrete': return `minecraft:${c}_concrete`;
      case 'concrete_powder': return `minecraft:${c}_concrete_powder`;
      case 'wool': return `minecraft:${c}_wool`;
      case 'stained_hardened_clay': return `minecraft:${c}_terracotta`;
      case 'stained_glass_pane': return `minecraft:${c}_stained_glass_pane`;
      default: throw new Error('no Java name for ' + bedrock);
    }
  }
  if (SAME.has(base)) return 'minecraft:' + base;
  if (RENAMED[base]) return 'minecraft:' + RENAMED[base];
  throw new Error('no Java name for ' + bedrock);
}

/** true for blocks you can see through (their faces don't hide the faces behind them) */
function isGlass(java) { return /glass/.test(java); }

module.exports = { javaName, isGlass };
