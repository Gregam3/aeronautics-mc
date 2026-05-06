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
# In-repo source for non-vanilla placed features (e.g. create:zinc_ore).
# Layout: scripts/feature_sources/<namespace>/<name>.json
MOD_SRC = os.path.join(HERE, 'feature_sources')

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

# BiC mobs we never actively boost via our biome modifier. Two reasons:
#   1. Bosses / mini-bosses (gameplay decision: no bosses in the world).
#      The boss gamerules (e.g. serPumpkinheadSpawn, lifestealerSpawn) are
#      flipped off by data/caero_rings/function/disable_bic_bosses.mcfunction
#      on every world load, but those gamerules do not exist for every
#      mini-boss — listing them here also excludes them from `add_spawns`
#      so even if BiC's own ambient rules let them spawn, we are not
#      amplifying them.
#   2. Tower-bound entities (Lord Pumpkinhead, Sir variants). With the dark
#      and observation towers disabled (biomes:[] override), these would
#      otherwise have nowhere natural to spawn from anyway.
BIC_BOSS_EXCLUDE = {
    'sir_pumpkinhead',
    'lifestealer',
    'spiritof_chaos',
    'mother_spider',
    'fallen_chaos_knight',
    'nightmare_stalker',
    'krampus',
    'supreme_bonescaller',
    'dire_hound_leader',
    'dark_vortex',
    # BiC's aquatic mobs spawn at water-surface and clip onto land — don't boost.
    'corpse_fish',
    'glutton_fish',
    'thornshell_crab',
}

def format_factor(f):
    """0.3 -> '0_3x', 0.75 -> '0_75x', 1.0 -> '1_0x', 1.5 -> '1_5x', 2.0 -> '2_0x'."""
    s = f"{f:.10g}"  # trim trailing zeros
    if '.' not in s:
        s += '.0'
    return s.replace('.', '_') + 'x'

def normalize_ore_entry(entry):
    """Accepts a bare string ('ore_iron_upper' → minecraft) or {namespace,name}.
    Returns (namespace, name)."""
    if isinstance(entry, str):
        return ('minecraft', entry)
    return (entry['namespace'], entry['name'])

def load_source_feature(namespace, name):
    """Load a placed_feature JSON to clone into our scaled variants.
    minecraft features live in /tmp/vanilla-ores/<name>.json (extracted out-of-band
    from the server jar). Modded features ship in scripts/feature_sources/<ns>/<name>.json
    so we don't depend on /tmp for non-vanilla namespaces."""
    if namespace == 'minecraft':
        path = os.path.join(VANILLA_SRC, name + '.json')
        if not os.path.exists(path):
            print(f"ERROR: vanilla placed_feature {path} not found — extract first from server jar", file=sys.stderr)
            sys.exit(1)
        return json.load(open(path))
    path = os.path.join(MOD_SRC, namespace, name + '.json')
    if not os.path.exists(path):
        print(f"ERROR: mod placed_feature {path} not found — copy from the mod jar into scripts/feature_sources/{namespace}/", file=sys.stderr)
        sys.exit(1)
    return json.load(open(path))

def ore_family(ore_name):
    """'ore_diamond_buried' -> 'diamond'. 'ore_iron_upper' -> 'iron'.
    'zinc_ore' -> 'zinc' (no 'ore_' prefix to strip; first underscore-segment wins)."""
    stem = ore_name.replace('ore_', '')
    return stem.split('_')[0]

def factor_for(ore_name, tier, bias):
    overrides = bias.get('overrides', {}) or {}
    fam = ore_family(ore_name)
    if fam in overrides and tier in overrides[fam]:
        return overrides[fam][tier]
    return bias[tier]

def variant_id(namespace, name, factor):
    """ID for our generated scaled placed_feature. We always namespace it under
    caero_rings:, but encode the source's name + namespace prefix for uniqueness
    when the same family lives in two namespaces (none today, future-proofing)."""
    if namespace == 'minecraft':
        stem = name
    else:
        stem = f"{namespace}_{name}"
    return ('caero_rings', f"{stem}_{format_factor(factor)}")

def biome_tier_lookup():
    """Return {biome_id: tier} by reading the tier tag JSONs."""
    tag_dir = os.path.join(DATA, 'tags', 'worldgen', 'biome')
    out = {}
    for tier in ('easy', 'medium', 'hard'):
        tag_path = os.path.join(tag_dir, f"tier_{tier}.json")
        if not os.path.exists(tag_path):
            continue
        d = json.load(open(tag_path))
        for v in d.get('values', []):
            if isinstance(v, str):
                out[v] = tier
            elif isinstance(v, dict) and 'id' in v:
                out[v['id']] = tier
    return out

def main():
    cfg = json.load(open(CFG))
    ores_raw = cfg['ore_list']['ores']
    ores = [normalize_ore_entry(e) for e in ores_raw]  # list of (namespace, name)
    remove_only = cfg['ore_list'].get('remove_only', [])
    bias = cfg['ore_bias']
    biome_overrides_raw = cfg.get('biome_ore_overrides', {}) or {}
    bic = cfg['bic_spawn_boost']
    chest_mass = cfg.get('chest_mass') or {}
    player_mass = cfg.get('player_mass') or {}
    water_discount = cfg.get('water_discount') or {}

    # Strip _comment / _families / _note keys from biome overrides
    biome_overrides = {}
    for biome, fams in biome_overrides_raw.items():
        if biome.startswith('_'):
            continue
        biome_overrides[biome] = {k: v for k, v in fams.items() if not k.startswith('_')}

    # Validate every overridden biome's family multipliers:
    #  (a) every value is in [MIN, MAX] (placement count cap is 256 in vanilla;
    #      our largest source count is ore_iron_upper=90, so 2.0× is safely under),
    #  (b) every family in FAMILIES is supplied,
    #  (c) the average matches the tier midpoint.
    biome_tier = biome_tier_lookup()
    FAMILIES = {ore_family(name) for (_ns, name) in ores}
    tier_midpoint = {'easy': bias['easy'], 'medium': bias['medium'], 'hard': bias['hard']}
    MIN_MULT = float(biome_overrides_raw.get('_min_multiplier', 0.2))
    MAX_MULT = float(biome_overrides_raw.get('_max_multiplier', 2.0))
    for biome, fams in biome_overrides.items():
        tier = biome_tier.get(biome)
        if tier is None:
            print(f"ERROR: biome_ore_overrides[{biome}] is not in any tier_easy/medium/hard tag — would have no tier baseline to displace; add it to a tier tag or remove the override", file=sys.stderr)
            sys.exit(1)
        missing = FAMILIES - set(fams.keys())
        if missing:
            print(f"ERROR: biome_ore_overrides[{biome}] is missing families {sorted(missing)} — every family must be specified for the average to be meaningful", file=sys.stderr)
            sys.exit(1)
        extra = set(fams.keys()) - FAMILIES
        if extra:
            print(f"ERROR: biome_ore_overrides[{biome}] has unknown families {sorted(extra)} — must be one of {sorted(FAMILIES)}", file=sys.stderr)
            sys.exit(1)
        out_of_range = {k: v for k, v in fams.items() if v < MIN_MULT or v > MAX_MULT}
        if out_of_range:
            print(f"ERROR: biome_ore_overrides[{biome}] values out of range [{MIN_MULT}, {MAX_MULT}]: {out_of_range}", file=sys.stderr)
            sys.exit(1)
        avg = sum(fams.values()) / len(fams)
        target = tier_midpoint[tier]
        if abs(avg - target) > 0.05:  # 5% tolerance for hand-rounded values
            print(f"ERROR: biome_ore_overrides[{biome}] (tier={tier}) family avg = {avg:.3f}, expected ~{target:.3f}; rebalance so {biome} sits on the tier curve", file=sys.stderr)
            sys.exit(1)

    # Wipe generated placed_features (anything with a `_<digits>_<digits>x.json` factor suffix).
    import re
    factor_re = re.compile(r'_\d+_\d+x\.json$')
    if os.path.isdir(PF_DIR):
        for f in os.listdir(PF_DIR):
            if factor_re.search(f):
                os.remove(os.path.join(PF_DIR, f))
    for f in glob.glob(os.path.join(BM_DIR, 'ore_*.json')):
        os.remove(f)
    for f in glob.glob(os.path.join(BM_DIR, 'biome_ore_*.json')):
        os.remove(f)
    for f in glob.glob(os.path.join(BM_DIR, 'boost_bic_*.json')):
        os.remove(f)

    os.makedirs(PF_DIR, exist_ok=True)
    os.makedirs(BM_DIR, exist_ok=True)

    # Collect every (ns, name, factor) we need across tier defaults AND biome overrides.
    # For biome overrides we ALWAYS need a placed_feature even if factor==1.0, because
    # the tier remove strips vanilla and we need a 1.0x replacement to put back.
    needed = set()  # set of (namespace, name, factor)
    for tier in ('easy', 'medium', 'hard'):
        for ns, name in ores:
            factor = factor_for(name, tier, bias)
            if abs(factor - 1.0) < 1e-6:
                continue
            needed.add((ns, name, factor))
    for biome, fams in biome_overrides.items():
        for ns, name in ores:
            fam = ore_family(name)
            factor = fams[fam]  # required by validation above
            needed.add((ns, name, factor))

    for ns, name, factor in needed:
        data = load_source_feature(ns, name)
        # Keep the `minecraft:biome` placement modifier — it filters placements
        # to positions whose biome contains the feature. With biome modifiers,
        # the feature is added to every targeted biome's feature list, so
        # the filter passes there and skips elsewhere. Stripping it caused
        # multi-biome chunks to run the feature once per overlapping biome
        # (a chunk straddling plains + forest got 2× the placements).
        count_idx = next((i for i, p in enumerate(data['placement'])
                          if p.get('type') == 'minecraft:count' and isinstance(p.get('count'), int)), None)
        if count_idx is None:
            continue
        orig = data['placement'][count_idx]['count']
        data['placement'][count_idx]['count'] = max(1, round(orig * factor))
        _, vname = variant_id(ns, name, factor)
        with open(os.path.join(PF_DIR, f"{vname}.json"), 'w') as f:
            json.dump(data, f, indent=2)

    # Generate ore biome modifiers per tier.
    #
    # REMOVE: emit as `caero_rings:id_remove_features` (custom modifier in
    # IdRemoveFeatures.kt). NeoForge's built-in `neoforge:remove_features`
    # silently no-ops when targeting `minecraft:*` features in this mod stack
    # — the pre-baked Holder.References in vanilla biomes don't compare equal
    # to the modifier's freshly-resolved holders, so `removeIf(holderSet::contains)`
    # never matches. The custom modifier compares by ResourceLocation instead.
    # JSON differs from `neoforge:remove_features`: uses `feature_ids` (list of
    # plain ID strings) instead of `features` (HolderSet codec).
    #
    # ADD: keep as `neoforge:add_features` (verified working — caero_rings:* features
    # round-trip through the modifier engine fine since they share registry identity
    # with the resolved holders).
    #
    # The remove list always includes `remove_only` (stripped from every tier
    # so they don't stack on the scaled replacements); the add list is empty
    # for those entries.
    for tier in ('easy', 'medium', 'hard'):
        tier_ores = [(ns, name, factor_for(name, tier, bias)) for (ns, name) in ores]
        scaled = [(ns, name, f) for (ns, name, f) in tier_ores if abs(f - 1.0) >= 1e-6]
        if not scaled and not remove_only:
            continue
        remove_feature_ids = [f"{ns}:{name}" for (ns, name, _) in scaled] \
                           + [f"minecraft:{o}" for o in remove_only]
        remove = {
            "type": "caero_rings:id_remove_features",
            "biomes": f"#caero_rings:tier_{tier}",
            "feature_ids": remove_feature_ids,
            "steps": ["underground_ores"],
        }
        json.dump(remove, open(os.path.join(BM_DIR, f"ore_{tier}_remove.json"), 'w'), indent=2)
        if scaled:
            add_features = []
            for ns, name, f in scaled:
                vns, vname = variant_id(ns, name, f)
                add_features.append(f"{vns}:{vname}")
            add = {
                "type": "neoforge:add_features",
                "biomes": f"#caero_rings:tier_{tier}",
                "features": add_features,
                "step": "underground_ores",
            }
            json.dump(add, open(os.path.join(BM_DIR, f"ore_{tier}_add.json"), 'w'), indent=2)

    # Per-biome overrides. Run AFTER tier modifiers conceptually — but biome
    # modifier phase order is: ADD → REMOVE → MODIFY. So both ADDs run first
    # (tier-scaled gets added, biome-scaled gets added), then both REMOVEs run
    # (tier-remove drops vanilla, biome-remove drops the tier-scaled variants
    # that are NOT what we want for this biome). End state: only the biome-scaled
    # variants remain in the targeted biome's underground_ores step.
    #
    # We always emit a 1.0x variant as a placed_feature when an override calls
    # for it (factor==1.0 means "vanilla rate" but vanilla is being stripped by
    # the tier modifier, so we need a fresh same-count copy to add back).
    for biome, fams in sorted(biome_overrides.items()):
        biome_tier_name = biome_tier[biome]  # 'easy' | 'medium' | 'hard'
        # Build the add list. Skip families where the biome's override factor
        # equals the tier-default factor — the tier ADD modifier already adds
        # that variant for this biome, so adding again would double-place.
        # Special case: if the tier factor is 1.0×, the tier ADD doesn't run
        # (we skip 1.0× generation in tier modifiers). In that case we DO need
        # the per-biome add to put back what the tier REMOVE will strip.
        add_features = []
        # Track which (ns, name) we still need to ensure the biome has *some*
        # variant for. If we skip the per-biome add for a family because
        # tier-add covers it, the biome already has it — fine. If the biome
        # override factor is 1.0× and the tier factor is also 1.0×, no add
        # happens and the tier REMOVE leaves vanilla in place — fine.
        skipped_due_to_tier_match = set()
        for ns, name in ores:
            fam = ore_family(name)
            biome_factor = fams[fam]
            tier_factor = factor_for(name, biome_tier_name, bias)
            if abs(biome_factor - tier_factor) < 1e-6:
                # Tier modifier already places this exact variant on this biome.
                skipped_due_to_tier_match.add((ns, name))
                continue
            vns, vname = variant_id(ns, name, biome_factor)
            add_features.append(f"{vns}:{vname}")
        add_set = set(add_features)
        # Build the remove list:
        # - Vanilla feature IDs (the tier REMOVE does this too if the biome is
        #   in a tier, but listing here is harmless because removeIf is a no-op
        #   on already-absent features). Also skip vanilla IDs we still want
        #   (when biome_factor == 1.0 and tier_factor == 1.0, vanilla IS what
        #   the tier modifier left in place, and we don't want to strip it).
        # - Every tier-scaled variant we ever generated for any tier — EXCEPT
        #   variants the tier modifier *added* on this biome at this biome's
        #   factor (those are tracked via skipped_due_to_tier_match — keep them).
        remove_ids = []
        for ns, name in ores:
            fam = ore_family(name)
            biome_factor = fams[fam]
            tier_factor = factor_for(name, biome_tier_name, bias)
            keep_vanilla = (abs(biome_factor - 1.0) < 1e-6 and abs(tier_factor - 1.0) < 1e-6)
            vid = f"{ns}:{name}"
            if not keep_vanilla:
                remove_ids.append(vid)
            for tier in ('easy', 'medium', 'hard'):
                tf = factor_for(name, tier, bias)
                if abs(tf - 1.0) < 1e-6:
                    continue  # tier modifier never generated a 1.0× variant
                vns, vname = variant_id(ns, name, tf)
                full = f"{vns}:{vname}"
                # Keep this variant on the biome iff (ns,name) was skipped due
                # to tier-match AND this variant matches the biome's factor.
                if (ns, name) in skipped_due_to_tier_match and abs(tf - biome_factor) < 1e-6:
                    continue
                remove_ids.append(full)
        # De-dupe while preserving order.
        seen = set()
        dedup_remove_ids = []
        for r in remove_ids:
            if r not in seen:
                seen.add(r)
                dedup_remove_ids.append(r)

        slug = biome.replace(':', '__')
        remove = {
            "type": "caero_rings:id_remove_features",
            "biomes": biome,
            "feature_ids": dedup_remove_ids,
            "steps": ["underground_ores"],
        }
        json.dump(remove, open(os.path.join(BM_DIR, f"biome_ore_{slug}_remove.json"), 'w'), indent=2)
        add = {
            "type": "neoforge:add_features",
            "biomes": biome,
            "features": add_features,
            "step": "underground_ores",
        }
        json.dump(add, open(os.path.join(BM_DIR, f"biome_ore_{slug}_add.json"), 'w'), indent=2)

    # Generate BiC spawn boosters (medium / hard get MORE BiC mobs).
    for tier, boost in bic.items():
        if tier.startswith('_') or boost <= 0:
            continue
        spawners = []
        for name, weight, mn, mx in BIC_SPAWNS_BASE:
            if name in BIC_BOSS_EXCLUDE:
                continue
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

    # Generate horse_encumbrance runtime config
    horse_enc = cfg.get('horse_encumbrance') or {}
    horse_enc_out = {
        "enabled": horse_enc.get('enabled', True),
        "limit_multiplier": horse_enc.get('limit_multiplier', 2.0),
        "max_slow": horse_enc.get('max_slow', 0.6),
        "tick_interval": horse_enc.get('tick_interval', 20),
    }
    with open(os.path.join(runtime_cfg_dir, 'horse_encumbrance.json'), 'w') as f:
        json.dump(horse_enc_out, f, indent=2)

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
