#!/usr/bin/env python3
"""
Regenerate data/encumbered/data_maps/item/item_weights.json.

Whitelist model: scan every installed mod jar + Encumbered's bundled vanilla
item list, set every known item to a low default weight, then layer a small
"heavy materials" whitelist on top (ores, raw metals, ingots, stone family,
logs, storage blocks, anvils, buckets).

Why: Encumbered ships every item at 1.0. With a 200-weight cap, that means
torches and string punish travel as much as iron. Most items in modded
inventories are decoration/food/tools — they should be effectively weightless;
only raw cargo materials should compete for cap.

Run:  python3 scripts/gen-item-weights.py
Then: ./scripts/apply-config.sh && ./gradlew build && ./deploy.sh
"""
import os, sys, json, zipfile, re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'encumbered',
                   'data_maps', 'item', 'item_weights.json')

# Where to find the running Prism instance — used to scan mod jars and
# Encumbered's own bundled vanilla item list. Override with CAERO_INSTANCE.
INSTANCE = os.environ.get(
    'CAERO_INSTANCE',
    os.path.expanduser('~/.local/share/PrismLauncher/instances/1.21.1/minecraft'),
)

DEFAULT_WEIGHT = 0.05  # everything we don't have an opinion on

# Patterns are matched against the *path* portion (after the colon).
# First match wins. Anchored to the full path with re.fullmatch.
PATTERN_RULES = [
    # --- VERY LIGHT (override default for tiny / bulk-cheap items) ---
    (r'.*_seeds?', 0.01),
    (r'.*_sapling', 0.05),
    (r'.*sapling', 0.05),
    (r'.*_propagule', 0.05),
    (r'(red|soul|)_?torch', 0.02),
    (r'redstone_torch', 0.02),

    # --- WOOD FAMILY ---
    # Logs / stems / wood-blocks (all 6-faced log-shaped blocks)
    (r'(stripped_)?[a-z_]+_log',     0.4),
    (r'(stripped_)?[a-z_]+_wood',    0.4),
    (r'(stripped_)?[a-z_]+_stem',    0.4),
    (r'(stripped_)?[a-z_]+_hyphae',  0.4),
    # RU "branches" / "beards" cut from logs — same density as logs
    (r'[a-z_]+_branch_from_[a-z_]+_log',   0.4),
    (r'[a-z_]+_beard_from_[a-z_]+_log',    0.4),
    # Planks (thinner, lighter)
    (r'[a-z_]+_planks',  0.15),
    (r'painted_planks',  0.15),

    # --- STONE FAMILY (cobble, granite, diorite, andesite, tuff, basalt, blackstone, deepslate, sandstone, end_stone, netherrack) ---
    # Cobbled / regular stone-tier
    (r'(cobblestone|stone|granite|diorite|andesite|tuff|sandstone|red_sandstone|end_stone|smooth_stone|smooth_sandstone|smooth_red_sandstone)',                 1.0),
    (r'(polished_)?(granite|diorite|andesite|tuff|blackstone|deepslate)',                                                                                       1.0),
    (r'(chiseled_|cracked_|smooth_)?(stone_bricks|sandstone|tuff_bricks|deepslate_bricks|deepslate_tiles|nether_bricks|red_nether_bricks)',                     1.0),
    (r'mossy_(cobblestone|stone_bricks)',                                                                                                                       1.0),
    (r'(cobbled_)?deepslate',                                                                                                                                   1.25),
    (r'(polished_)?(blackstone|basalt|smooth_basalt)',                                                                                                          1.25),
    (r'(crying_)?obsidian',                                                                                                                                     2.5),
    (r'netherrack',                                                                                                                                             0.75),

    # Earth blocks — light bulk
    (r'(coarse_|rooted_)?dirt',           0.25),
    (r'grass_block|podzol|mycelium',      0.25),
    (r'(red_)?sand',                      0.25),
    (r'gravel|suspicious_gravel',         0.25),
    (r'clay|packed_mud|mud_bricks?|mud',  0.35),

    # --- ORE BLOCKS ---
    # Vanilla & modded raw ore blocks
    (r'(deepslate_)?(coal|iron|copper|gold|redstone|lapis|diamond|emerald)_ore', 1.5),
    (r'(nether_gold_ore|nether_quartz_ore|ancient_debris)',                      2.0),

    # --- RAW METALS / INGOTS ---
    (r'raw_iron|raw_copper|raw_gold',                            2.0),
    (r'(iron|copper|gold)_ingot',                                1.5),
    (r'netherite_ingot',                                         3.0),
    (r'netherite_scrap',                                         2.5),
    (r'brass_ingot|zinc_ingot|andesite_alloy',                   1.5),  # Create

    # --- STORAGE BLOCKS (compacted ingots/metals) ---
    # Specific-density first (regex first-match-wins).
    (r'netherite_block',                                                                                  5.0),
    (r'raw_(iron|copper|gold)_block',                                                                     3.5),
    (r'(iron|copper|gold|emerald|diamond|brass|zinc|amethyst|lapis|redstone|coal)_block',                 3.0),

    # --- TOOLS / WEAPONS / ARMOUR ---
    # 5.0 each — same band as anvil; carrying a full kit eats meaningful cap.
    # Broad suffix patterns also catch modded tools/armour with the same naming.
    (r'[a-z0-9_]*_(pickaxe|axe|shovel|hoe)',           5.0),
    (r'[a-z0-9_]*_sword',                              5.0),
    (r'bow|crossbow|mace|trident',                     5.0),
    (r'[a-z0-9_]*_(helmet|chestplate|leggings|boots)', 5.0),
    (r'turtle_helmet',                                 5.0),

    # --- HEAVY UTILITY ---
    (r'(chipped_|damaged_)?anvil',     5.0),
    (r'heavy_core',                    4.0),
    (r'lava_bucket',                   1.25),
    (r'water_bucket|milk_bucket|powder_snow_bucket|axolotl_bucket|cod_bucket|salmon_bucket|pufferfish_bucket|tropical_fish_bucket|tadpole_bucket', 1.0),
    (r'bucket',                        0.25),
]

# Explicit overrides — same shape as the JSON output. Win over patterns.
EXPLICIT = {
    # Numismatics coins — you should never feel coin weight
    'numismatics:spur':     0.005,
    'numismatics:bevel':    0.005,
    'numismatics:sun':      0.01,
    'numismatics:cog':      0.01,
    'numismatics:sprocket': 0.015,
    'numismatics:crown':    0.02,

    # Currency-ish small valuables
    'minecraft:diamond':       0.25,
    'minecraft:emerald':       0.25,
    'minecraft:amethyst_shard':0.15,
    'minecraft:quartz':        0.2,
    'minecraft:lapis_lazuli':  0.2,
    'minecraft:redstone':      0.05,
    'minecraft:coal':          0.25,
    'minecraft:charcoal':      0.2,

    # Bamboo (light hollow material)
    'minecraft:bamboo':        0.05,
    'minecraft:bamboo_block':  0.25,
    'minecraft:stick':         0.025,
    'minecraft:feather':       0.025,
    'minecraft:string':        0.05,
    'minecraft:paper':         0.025,

    # Pumpkins / melons (large but soft cargo)
    'minecraft:pumpkin':       0.75,
    'minecraft:melon':         0.75,

    # Hybrid Aquatic raft (vehicle)
    'hybrid-aquatic:raft':     2.5,

    # Shield — heavy plated wood/metal slab strapped to the arm
    'minecraft:shield':        10.0,
}


def collect_modded_items() -> set[str]:
    """Pull every item.<modid>.<name> / block.<modid>.<name> key from
    every mod jar's en_us.json. Strict: ignores tooltip / sub-key entries."""
    out = set()
    mods_dir = os.path.join(INSTANCE, 'mods')
    if not os.path.isdir(mods_dir):
        print(f'WARN: {mods_dir} not found — modded items will be missing', file=sys.stderr)
        return out
    pat = re.compile(r'^(item|block)\.([a-z0-9_-]+)\.([a-z0-9_]+)$')
    for jar in sorted(os.listdir(mods_dir)):
        if not jar.endswith('.jar'):
            continue
        try:
            with zipfile.ZipFile(os.path.join(mods_dir, jar)) as z:
                for n in z.namelist():
                    if n.startswith('assets/') and n.endswith('/lang/en_us.json'):
                        try:
                            data = json.loads(z.read(n).decode('utf-8', errors='replace'))
                        except Exception:
                            continue
                        for k in data:
                            m = pat.match(k)
                            if m:
                                out.add(f'{m.group(2)}:{m.group(3)}')
        except Exception:
            pass
    return out


def collect_vanilla_items() -> set[str]:
    """Read Encumbered's bundled item_weights.json — it lists every vanilla
    item with weight 1.0. We use it as the canonical 'minecraft:*' set."""
    enc_jar = None
    mods_dir = os.path.join(INSTANCE, 'mods')
    if os.path.isdir(mods_dir):
        for jar in os.listdir(mods_dir):
            if jar.startswith('encumbered-') and jar.endswith('.jar'):
                enc_jar = os.path.join(mods_dir, jar)
                break
    if not enc_jar:
        print('WARN: encumbered jar not found — vanilla items will be missing', file=sys.stderr)
        return set()
    with zipfile.ZipFile(enc_jar) as z:
        data = json.loads(z.read('data/encumbered/data_maps/item/item_weights.json'))
    return set(data.get('values', {}).keys())


def weight_for(item_id: str) -> float:
    if item_id in EXPLICIT:
        return EXPLICIT[item_id]
    path = item_id.split(':', 1)[1]
    for pat, w in PATTERN_RULES:
        if re.fullmatch(pat, path):
            return w
    return DEFAULT_WEIGHT


def main():
    items = collect_modded_items() | collect_vanilla_items()
    if not items:
        print('ERROR: zero items found — check INSTANCE path', file=sys.stderr)
        sys.exit(1)

    values = {}
    bucket_count = {}
    for it in sorted(items):
        w = weight_for(it)
        values[it] = {'replace': True, 'value': {'weight': w}}
        bucket_count[w] = bucket_count.get(w, 0) + 1

    out = {
        '_comment': (
            'Generated by scripts/gen-item-weights.py. Whitelist model: every '
            'known item is set to a light default; a small heavy-materials '
            'whitelist (ores, ingots, stone, logs, storage blocks, anvils) '
            'overrides. With Encumbered threshold2=200, a stack of stone '
            '(64 * 1.0) + a stack of raw iron (64 * 2.0) ≈ 192 fills you. '
            'Run gen-item-weights.py to regenerate after adding mods.'
        ),
        'replace': True,  # wipe Encumbered's bundled 1.0-everywhere stub
        'values': values,
    }

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w') as f:
        json.dump(out, f, indent=2)

    print(f'wrote {len(values)} items → {os.path.relpath(OUT, ROOT)}')
    for w in sorted(bucket_count):
        print(f'  weight {w:>5}: {bucket_count[w]:>5} items')


if __name__ == '__main__':
    main()
