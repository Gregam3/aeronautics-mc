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
    ('bone_imp', 25, 1, 2),
    ('firelight', 15, 1, 3),
    ('mr_pumpkin', 15, 1, 3),
    ('mrs_pumpkin', 13, 1, 1),
    ('pumpkin_bruiser', 10, 1, 1),
    ('pumpkin_dunce', 16, 3, 3),
    ('seared_spirit', 5, 1, 1),
    ('senor_pumpkin', 10, 1, 2),
    ('sir_pumpkinhead', 4, 1, 1),
]

def format_factor(f):
    """0.3 -> '0_3x', 0.75 -> '0_75x', 1.0 -> '1_0x', 1.5 -> '1_5x', 2.0 -> '2_0x'."""
    s = f"{f:.10g}"  # trim trailing zeros
    if '.' not in s:
        s += '.0'
    return s.replace('.', '_') + 'x'

def load_vanilla_ore(name):
    path = os.path.join(VANILLA_SRC, name + '.json')
    if not os.path.exists(path):
        print(f"ERROR: vanilla ore {path} not found — extract first from server jar", file=sys.stderr)
        sys.exit(1)
    return json.load(open(path))

def ore_family(ore_name):
    """'ore_diamond_buried' -> 'diamond'. 'ore_iron_upper' -> 'iron'."""
    stem = ore_name.replace('ore_', '')
    return stem.split('_')[0]

def factor_for(ore_name, tier, bias):
    overrides = bias.get('overrides', {}) or {}
    fam = ore_family(ore_name)
    if fam in overrides and tier in overrides[fam]:
        return overrides[fam][tier]
    return bias[tier]

def main():
    cfg = json.load(open(CFG))
    ores = cfg['ore_list']['ores']
    bias = cfg['ore_bias']
    bic = cfg['bic_spawn_boost']
    chest_mass = cfg.get('chest_mass') or {}
    player_mass = cfg.get('player_mass') or {}
    water_discount = cfg.get('water_discount') or {}

    # Wipe generated placed_features and ore biome modifiers
    for f in glob.glob(os.path.join(PF_DIR, 'ore_*.json')):
        os.remove(f)
    for f in glob.glob(os.path.join(BM_DIR, 'ore_*.json')):
        os.remove(f)
    for f in glob.glob(os.path.join(BM_DIR, 'boost_bic_*.json')):
        os.remove(f)

    os.makedirs(PF_DIR, exist_ok=True)
    os.makedirs(BM_DIR, exist_ok=True)

    # Generate placed_features for each (ore, factor) pair we actually need
    needed = set()  # set of (ore, factor)
    for tier in ('easy', 'medium', 'hard'):
        for ore in ores:
            factor = factor_for(ore, tier, bias)
            if abs(factor - 1.0) < 1e-6:
                continue
            needed.add((ore, factor))

    for ore, factor in needed:
        data = load_vanilla_ore(ore)
        data['placement'] = [p for p in data['placement'] if p.get('type') != 'minecraft:biome']
        count_idx = next((i for i, p in enumerate(data['placement'])
                          if p.get('type') == 'minecraft:count' and isinstance(p.get('count'), int)), None)
        if count_idx is None:
            continue
        orig = data['placement'][count_idx]['count']
        data['placement'][count_idx]['count'] = max(1, round(orig * factor))
        suffix = format_factor(factor)
        with open(os.path.join(PF_DIR, f"{ore}_{suffix}.json"), 'w') as f:
            json.dump(data, f, indent=2)

    # Generate ore biome modifiers per tier (only ores with non-1.0 factor for that tier)
    for tier in ('easy', 'medium', 'hard'):
        tier_ores = [(o, factor_for(o, tier, bias)) for o in ores]
        scaled = [(o, f) for (o, f) in tier_ores if abs(f - 1.0) >= 1e-6]
        if not scaled:
            continue
        remove = {
            "type": "neoforge:remove_features",
            "biomes": f"#caero_rings:tier_{tier}",
            "features": [f"minecraft:{o}" for (o, f) in scaled],
            "steps": "underground_ores",
        }
        add = {
            "type": "neoforge:add_features",
            "biomes": f"#caero_rings:tier_{tier}",
            "features": [f"caero_rings:{o}_{format_factor(f)}" for (o, f) in scaled],
            "step": "underground_ores",
        }
        json.dump(remove, open(os.path.join(BM_DIR, f"ore_{tier}_remove.json"), 'w'), indent=2)
        json.dump(add, open(os.path.join(BM_DIR, f"ore_{tier}_add.json"), 'w'), indent=2)

    # Generate BiC spawn boosters (medium / hard get MORE BiC mobs).
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

    # Strip ALL BiC spawns from easy-tier biomes — BiC's own datapack adds its
    # mobs to `neoforge:any`, which leaks into easy biomes near spawn. Easy
    # biomes should feel vanilla at night.
    bic_remove = {
        "type": "neoforge:remove_spawns",
        "biomes": "#caero_rings:tier_easy",
        "entity_types": "#caero_rings:bic_mobs",
    }
    json.dump(bic_remove, open(os.path.join(BM_DIR, "bic_remove_easy.json"), 'w'), indent=2)
    # Generate the entity-type tag that the remove modifier references.
    bic_tag_dir = os.path.join(DATA, 'tags', 'entity_type')
    os.makedirs(bic_tag_dir, exist_ok=True)
    bic_tag = {
        "replace": False,
        "values": [f"born_in_chaos_v1:{name}" for name, *_ in BIC_SPAWNS_BASE],
    }
    json.dump(bic_tag, open(os.path.join(bic_tag_dir, 'bic_mobs.json'), 'w'), indent=2)

    # Generate chest_mass runtime config (read at first ChestMass.bonusFor call)
    chest_mass_out = {
        "mass_per_weight_unit": chest_mass.get('mass_per_weight_unit', 0.1),
        "max_bonus_per_block": chest_mass.get('max_bonus_per_block', 200.0),
    }
    runtime_cfg_dir = os.path.join(ROOT, 'src', 'main', 'resources', 'caero_rings')
    os.makedirs(runtime_cfg_dir, exist_ok=True)
    with open(os.path.join(runtime_cfg_dir, 'chest_mass.json'), 'w') as f:
        json.dump(chest_mass_out, f, indent=2)

    # Generate player_mass runtime config
    player_mass_out = {
        "inventory_multiplier": player_mass.get('inventory_multiplier', 0.02),
        "base_mass": player_mass.get('base_mass', 0.0),
        "max_per_player": player_mass.get('max_per_player', 0.0),
    }
    with open(os.path.join(runtime_cfg_dir, 'player_mass.json'), 'w') as f:
        json.dump(player_mass_out, f, indent=2)

    # Generate water_discount runtime config
    water_discount_out = {
        "enabled": water_discount.get('enabled', True),
        "discount": water_discount.get('discount', 0.7),
    }
    with open(os.path.join(runtime_cfg_dir, 'water_discount.json'), 'w') as f:
        json.dump(water_discount_out, f, indent=2)

    # Report
    pf_count = len(glob.glob(os.path.join(PF_DIR, 'ore_*.json')))
    bm_count = len([f for f in glob.glob(os.path.join(BM_DIR, '*.json'))
                    if 'ore_' in os.path.basename(f) or 'boost_bic_' in os.path.basename(f)])
    print(f"  ore_bias default: easy={bias['easy']} medium={bias['medium']} hard={bias['hard']}")
    for fam, ov in (bias.get('overrides') or {}).items():
        print(f"  ore_bias override [{fam}]: easy={ov.get('easy','-')} medium={ov.get('medium','-')} hard={ov.get('hard','-')}")
    print(f"  bic_spawn_boost: medium=+{bic['medium']}x hard=+{bic['hard']}x")
    cap_desc = f"cap {chest_mass_out['max_bonus_per_block']}/container" if chest_mass_out['max_bonus_per_block'] > 0 else "no cap (linear)"
    print(f"  chest_mass: {chest_mass_out['mass_per_weight_unit']}/item × encumbered weight, {cap_desc}")
    pcap_desc = f"cap {player_mass_out['max_per_player']}/player" if player_mass_out['max_per_player'] > 0 else "no cap (linear)"
    print(f"  player_mass: {player_mass_out['inventory_multiplier']} × encumbered weight, base {player_mass_out['base_mass']}, {pcap_desc}")
    if water_discount_out['enabled'] and water_discount_out['discount'] > 0:
        print(f"  water_discount: -{int(water_discount_out['discount']*100)}% to chest+player bonuses while on water")
    else:
        print(f"  water_discount: disabled")
    print(f"  wrote {pf_count} placed_features, {bm_count} biome_modifiers")

if __name__ == '__main__':
    main()
