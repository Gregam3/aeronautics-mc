#!/usr/bin/env python3
"""
Regenerate generated-JSON config from config.json.

Reads config.json, writes:
  - src/main/resources/data/caero_rings/worldgen/placed_feature/ore_*.json
  - src/main/resources/data/caero_rings/neoforge/biome_modifier/ore_*.json
  - src/main/resources/data/caero_rings/neoforge/biome_modifier/boost_bic_*.json

Run after editing config.json, before ./gradlew build.
"""
import json, os, sys, glob

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CFG = os.path.join(ROOT, 'config.json')
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'caero_rings')
PF_DIR = os.path.join(DATA, 'worldgen', 'placed_feature')
BM_DIR = os.path.join(DATA, 'neoforge', 'biome_modifier')
VANILLA_SRC = '/tmp/vanilla-ores'

# Default BiC base spawn weights (from BiC's biome_modifier JSONs, verified 2026-04-24)
BIC_SPAWNS_BASE = [
    ('decrepit_skeleton', 20, 1, 1),
    ('decaying_zombie', 30, 1, 1),
    ('baby_skeleton', 25, 1, 2),
    ('skeleton_demoman', 12, 1, 1),
    ('skeleton_thrasher', 8, 1, 1),
    ('siamese_skeletons', 16, 1, 1),
    ('barrel_zombie', 25, 1, 1),
    ('door_knight', 13, 1, 1),
    ('zombie_bruiser', 16, 1, 1),
    ('zombie_lumberjack', 25, 1, 1),
    ('zombie_clown', 8, 1, 1),
    ('zombie_fisherman', 30, 1, 1),
    ('swarmer', 11, 1, 1),
    ('spirit_guide', 16, 1, 1),
    ('spirit_guide_assistant', 9, 2, 2),
    ('bonescaller', 11, 1, 1),
    ('supreme_bonescaller', 1, 1, 1),
    ('fallen_chaos_knight', 5, 1, 1),
    ('lifestealer', 3, 1, 1),
    ('missioner', 4, 1, 1),
    ('nightmare_stalker', 4, 1, 1),
    ('restless_spirit', 20, 1, 1),
    ('dark_vortex', 14, 1, 1),
    ('phantom_creeper', 8, 1, 1),
    ('spiritof_chaos', 8, 1, 1),
    ('baby_spider', 16, 3, 5),
    ('mother_spider', 13, 1, 1),
    ('corpse_fly', 12, 1, 3),
    ('bloody_gadfly', 15, 1, 1),
    ('dread_hound', 15, 3, 5),
    ('dire_hound_leader', 4, 1, 1),
    ('corpse_fish', 7, 3, 5),
    ('glutton_fish', 2, 1, 1),
    ('thornshell_crab', 8, 1, 1),
    ('krampus_henchman', 9, 2, 3),
    ('krampus', 2, 1, 1),
]

def format_factor(f):
    """Turn 0.75 into '0_75x'."""
    return str(f).replace('.', '_').replace('_0', '') + 'x' if '.' in str(f) and not str(f).endswith('.0') \
           else f'{int(f)}_0x' if f == int(f) else str(f).replace('.', '_') + 'x'

def load_vanilla_ore(name):
    path = os.path.join(VANILLA_SRC, name + '.json')
    if not os.path.exists(path):
        print(f"ERROR: vanilla ore {path} not found — extract first from server jar", file=sys.stderr)
        sys.exit(1)
    return json.load(open(path))

def main():
    cfg = json.load(open(CFG))
    ores = cfg['ore_list']['ores']
    bias = cfg['ore_bias']
    bic = cfg['bic_spawn_boost']

    # Wipe generated placed_features and ore biome modifiers
    for f in glob.glob(os.path.join(PF_DIR, 'ore_*.json')):
        os.remove(f)
    for f in glob.glob(os.path.join(BM_DIR, 'ore_*.json')):
        os.remove(f)
    for f in glob.glob(os.path.join(BM_DIR, 'boost_bic_*.json')):
        os.remove(f)

    os.makedirs(PF_DIR, exist_ok=True)
    os.makedirs(BM_DIR, exist_ok=True)

    # Generate ore placed_features per non-1.0 tier
    needed_factors = set()
    for tier in ('easy', 'medium', 'hard'):
        factor = bias[tier]
        if abs(factor - 1.0) < 1e-6:
            continue
        needed_factors.add(factor)

    for factor in needed_factors:
        suffix = format_factor(factor)
        for ore in ores:
            data = load_vanilla_ore(ore)
            # Strip biome placement filter (breaks against tier biome substitution)
            data['placement'] = [p for p in data['placement'] if p.get('type') != 'minecraft:biome']
            count_idx = next((i for i, p in enumerate(data['placement'])
                              if p.get('type') == 'minecraft:count' and isinstance(p.get('count'), int)), None)
            if count_idx is None:
                continue
            orig = data['placement'][count_idx]['count']
            data['placement'][count_idx]['count'] = max(1, round(orig * factor))
            with open(os.path.join(PF_DIR, f"{ore}_{suffix}.json"), 'w') as f:
                json.dump(data, f, indent=2)

    # Generate ore biome modifiers per non-1.0 tier
    for tier in ('easy', 'medium', 'hard'):
        factor = bias[tier]
        if abs(factor - 1.0) < 1e-6:
            continue
        suffix = format_factor(factor)
        remove = {
            "type": "neoforge:remove_features",
            "biomes": f"#caero_rings:tier_{tier}",
            "features": [f"minecraft:{o}" for o in ores],
            "steps": "underground_ores",
        }
        add = {
            "type": "neoforge:add_features",
            "biomes": f"#caero_rings:tier_{tier}",
            "features": [f"caero_rings:{o}_{suffix}" for o in ores],
            "step": "underground_ores",
        }
        json.dump(remove, open(os.path.join(BM_DIR, f"ore_{tier}_remove.json"), 'w'), indent=2)
        json.dump(add, open(os.path.join(BM_DIR, f"ore_{tier}_add.json"), 'w'), indent=2)

    # Generate BiC spawn boosters
    for tier, boost in bic.items():
        if tier.startswith('_') or boost <= 0:
            continue
        spawners = []
        for name, weight, mn, mx in BIC_SPAWNS_BASE:
            spawners.append({
                "type": f"born_in_chaos_v1:{name}",
                "weight": max(1, round(weight * boost)),
                "minCount": mn,
                "maxCount": mx,
            })
        mod = {
            "type": "neoforge:add_spawns",
            "biomes": f"#caero_rings:tier_{tier}",
            "spawners": spawners,
        }
        json.dump(mod, open(os.path.join(BM_DIR, f"boost_bic_{tier}.json"), 'w'), indent=2)

    # Report
    pf_count = len(glob.glob(os.path.join(PF_DIR, '*.json')))
    bm_count = len(glob.glob(os.path.join(BM_DIR, '*.json')))
    print(f"  ore_bias: easy={bias['easy']} medium={bias['medium']} hard={bias['hard']}")
    print(f"  bic_spawn_boost: medium=+{bic['medium']}x hard=+{bic['hard']}x")
    print(f"  wrote {pf_count} placed_features, {bm_count} biome_modifiers")

if __name__ == '__main__':
    main()
