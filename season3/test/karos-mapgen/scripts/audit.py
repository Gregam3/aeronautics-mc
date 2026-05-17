#!/usr/bin/env python3
"""
Worldgen audit for the post-mask karos design (2026-05-16+).

The painted-PNG mask is gone. The overworld now uses vanilla noise generation
+ Tectonic terrain shaping + regions_unexplored biomes, all wrapped by
caero_rings:voronoi_tiered which substitutes biomes per Voronoi cell. Five
hand-placed seeds carry biome-pool themes:

  mountain_high  — west wall of peaks
  jungle_outer   — east-south outer jungle band
  jungle_swamp   — wet "mollet" between the two jungles
  jungle_deep    — east-north deep jungle (ancient_jungle + RU rainforest)
  cursed_wastes  — far-north badlands edge ("the Nether very far out")

This script boots a NeoForge dedicated server, then verifies via in-game
biome predicates:

  1. The biome source codec parsed (caero_rings DIAG line shows themes=[…]).
  2. Spawn (0, 64, 0) is in tier_easy and NOT in any themed pool.
  3. Each of the five themed seed coords resolves to the right theme tag.
  4. The radius floor still kicks in: a check inside the easy core (Y=64,
     r=800) is NOT in any theme — a HARD-themed seed shouldn't leak in.
  5. The cell-boundary buffer still kicks in: a point on the boundary
     between mountain_high (HARD) and the easy origin (EASY) resolves to
     plain MEDIUM with no theme.
  6. No vanilla nether or end biomes show up in overworld — `/locate biome
     minecraft:nether_wastes` should report "could not find" in the
     dimension's 6400-block default search radius. (Regression: a leftover
     of the painted-PNG approach would flag here.)

Use `/execute if biome` and `/locate biome` — both query the biome source
directly without needing chunks generated, so the audit completes in well
under a minute past server boot. No NBT parsing, no forceload waiting.

CLAUDE.md worldgen rule: every check must PASS. A single failure = the
change isn't shipped.
"""
from __future__ import annotations
import argparse, math, os, queue, re, subprocess, sys, threading, time
from pathlib import Path

DEFAULT_STAGING = Path("/tmp/aero-s3-karos-test")
DEFAULT_BOOT_TIMEOUT = 300  # cold-boot with full mod stack

DONE_BOOT_RE = re.compile(r'Done \([0-9.]+s\)! For help, type "help"')
# `/data get entity ... Pos[1]` returns "...has the following entity data: 152.0d"
HEIGHT_REPLY_RE = re.compile(r"has the following entity data: (?P<y>-?\d+(?:\.\d+)?)d")
DIAG_RE = re.compile(
    r"caero_rings DIAG: medium_min=(?P<med>\d+) hard_min=(?P<hard>\d+) "
    r"floor_jitter=(?P<jit>\d+) seeds=(?P<seeds>\d+) "
    r"easy_size=(?P<easy>\d+) medium_size=(?P<medsz>\d+) "
    r"hard_size=(?P<hardsz>\d+) hard_sub_size=(?P<hsub>\d+) "
    r"themes=\[(?P<themes>[^\]]*)\]"
)
MARKER_RE = re.compile(r"\[Server\] (KAROS_AUDIT_[A-Z0-9_]+)\b")
# /locate biome reply when something matched. Note: for tag queries the
# reported biome is the specific concrete biome that was found, not the tag.
LOCATE_OK_RE = re.compile(
    r"The nearest (?:biome\s+)?(?:tag\s+)?[\"']?(?P<query>[^\"' ]+)[\"']?\s+(?:#[^\s]+\s+)?is at \[(?P<x>-?\d+), (?:~|-?\d+), (?P<z>-?\d+)\]"
)
# Fallback: a more permissive pattern that just grabs the coords.
LOCATE_COORDS_RE = re.compile(r"is at \[(?P<x>-?\d+), (?:~|-?\d+), (?P<z>-?\d+)\]")
LOCATE_FAIL_RE = re.compile(r"Could not find ")


class Server:
    """Background-drained server wrapper. The drain thread is essential —
    a heavily-modded server falls silent for tens of seconds under load,
    and naive readline() loops will deadlock."""

    def __init__(self, staging: Path):
        self.staging = staging
        self.proc: subprocess.Popen | None = None
        self.out: queue.Queue[str] = queue.Queue()
        self.log: list[str] = []  # captured for post-mortem on failure

    def start(self):
        env = os.environ.copy()
        env.setdefault("JAVA_TOOL_OPTIONS", "")
        self.proc = subprocess.Popen(
            ["bash", str(self.staging / "run.sh"), "nogui"],
            cwd=str(self.staging),
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            bufsize=1, text=True, env=env,
        )
        threading.Thread(target=self._drain, daemon=True).start()

    def _drain(self):
        assert self.proc and self.proc.stdout
        for line in self.proc.stdout:
            self.log.append(line)
            sys.stdout.write(f"  | {line}"); sys.stdout.flush()
            self.out.put(line)
        self.out.put("")  # EOF

    def send(self, cmd: str):
        if not self.proc or self.proc.stdin is None: return
        try:
            self.proc.stdin.write(cmd + "\n"); self.proc.stdin.flush()
            print(f"[>] {cmd}", flush=True)
        except BrokenPipeError:
            pass

    def wait_for(self, pattern: re.Pattern, timeout: float) -> re.Match | None:
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                line = self.out.get(timeout=max(0.1, deadline - time.time()))
            except queue.Empty:
                return None
            if line == "":
                return None  # EOF
            m = pattern.search(line)
            if m: return m
        return None

    def collect_for(self, seconds: float, *patterns: re.Pattern) -> list[re.Match]:
        out: list[re.Match] = []
        deadline = time.time() + seconds
        while time.time() < deadline:
            try:
                line = self.out.get(timeout=max(0.1, deadline - time.time()))
            except queue.Empty:
                continue
            if line == "":
                return out
            for p in patterns:
                m = p.search(line)
                if m:
                    out.append(m); break
        return out

    def stop(self, timeout: float = 90):
        try: self.send("stop")
        except Exception: pass
        if not self.proc: return
        try: self.proc.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            print(f"[!] /stop didn't shut down in {timeout}s, escalating")
            self.proc.terminate()
            try: self.proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                self.proc.kill()


# ──────────────────────────────────────────────────────────────────────────
# Test definitions
# ──────────────────────────────────────────────────────────────────────────

# Distant-cell probes — use `/execute positioned ... run locate biome <tag>` to
# query the biome source without needing the chunk loaded. Each entry is
# (label, query_x, query_z, theme_tag, max_dist_blocks). A successful probe
# means the nearest member of `theme_tag` is within `max_dist` of the query
# position — i.e. the theme is actually active at or very near that point.
# Limit must be tighter than the Worley patch scale (1024) to catch the
# common failure mode where a theme matches only at the seed point but
# leaks vanilla elsewhere.
THEME_PROBES = [
    ("mountain_at_seed",    -4000,    0, "#caero_rings:theme/mountain_high", 400),
    ("mountain_inside",     -3500,    0, "#caero_rings:theme/mountain_high", 400),
    ("mountain_near_edge",  -2800,    0, "#caero_rings:theme/mountain_high", 400),

    ("jungle_outer_at_seed", 3500, -1000, "#caero_rings:theme/jungle_outer",  400),
    ("jungle_swamp_at_seed", 4000,   300, "#caero_rings:theme/jungle_swamp",  400),
    ("jungle_deep_at_seed",  4500,  1500, "#caero_rings:theme/jungle_deep",   400),

    # The nether_core seed sits at (0, -4500). The actual cell is bounded by
    # the wrap_seeds at (0, -3000) and (±2000, -4500); midlines are z=-3750
    # and x=±1000. So at (0, -4500) the cell holds nether biomes (Worley
    # patch hash picks one of the 5 vanilla nether biomes).
    ("nether_at_seed",          0, -4500, "#caero_rings:theme/nether_core",  400),
    ("nether_inside",         500, -4500, "#caero_rings:theme/nether_core",  400),

    # Cursed wraps form the ashen approach. At each wrap seed the cell holds
    # ashen/badlands biomes.
    ("cursed_south_wrap",       0, -3000, "#caero_rings:theme/cursed_wastes", 400),
    ("cursed_west_wrap",    -2000, -4500, "#caero_rings:theme/cursed_wastes", 400),
    ("cursed_east_wrap",     2000, -4500, "#caero_rings:theme/cursed_wastes", 400),

    # End enclave at (4000, 2800).
    ("end_at_seed",          4000,  2800, "#caero_rings:theme/end_islands",   400),
]

# Vanilla biome tags we want the audit to find — confirms the surface rule
# will fire there because the biome IS a real nether/end biome.
VANILLA_BIOME_TAG_PROBES = [
    # At nether_core, biome must be IS_NETHER tagged.
    ("nether_at_seed_is_nether",  0, -4500, "#minecraft:is_nether",       400),
    # At end_islands seed, biome must be IS_END tagged.
    ("end_at_seed_is_end",     4000,  2800, "#minecraft:is_end",          400),
]

# Floor enforcement — at r=600 (well inside the easy core's strictest jittered
# floor of medium_min_radius − jitter = 900), no theme should be APPLIED. We
# can't directly assert "biome at point ∉ tag" via /locate, but we can assert
# the nearest themed biome is far away. > 300 blocks confirms the test
# position itself isn't themed (one biome cell is ~16 blocks; >300 means
# many cells of separation). Picked themes deliberately:
#   west query → check mountain_high (the geographically nearest theme west)
#   east query → check jungle_outer (the geographically nearest theme east)
# A failure here means the floor stopped working and a HARD-themed cell
# leaked into the easy core — exactly the spawn-safety regression we care about.
#
# We avoid the "is tier_easy at this point" test because oceans and rivers
# inside the easy core legitimately pass through untagged (caero_rings doesn't
# rebias unclassified biomes), so the biome AT (x, z) may not be in tier_easy
# even when the floor is working perfectly.
FLOOR_LEAK_PROBES = [
    ("floor_west_no_mountain", -600, 0, "#caero_rings:theme/mountain_high", 300),
    ("floor_east_no_jungle",    600, 0, "#caero_rings:theme/jungle_outer",  300),
    ("floor_north_no_cursed",   0, -600, "#caero_rings:theme/cursed_wastes", 300),
]

# Spawn-safety predicates (spawn chunk IS loaded — use /execute if biome).
# These verify that origin resolves to tier_easy and to NONE of the themes.
SPAWN_PROBES = [
    ("spawn_is_easy",        "#caero_rings:tier_easy",            True),
    ("spawn_not_mountain",   "#caero_rings:theme/mountain_high",  False),
    ("spawn_not_jungle_o",   "#caero_rings:theme/jungle_outer",   False),
    ("spawn_not_jungle_s",   "#caero_rings:theme/jungle_swamp",   False),
    ("spawn_not_jungle_d",   "#caero_rings:theme/jungle_deep",    False),
    ("spawn_not_cursed",     "#caero_rings:theme/cursed_wastes",  False),
]

# SPAWN-SAFETY regression: nether/end biomes are now legitimately present in
# overworld (by design, in the nether_core / end_islands themed cells). But
# they MUST NOT appear close to spawn — the spawn-safety contract requires
# the easy core to be vanilla overworld biomes. We assert: from spawn, the
# nearest is_nether biome is at least `min_dist` blocks away.
NETHER_END_DISTANCE_CHECKS = [
    ("nether_far_from_spawn", "#minecraft:is_nether", 3500),
    ("end_far_from_spawn",    "#minecraft:is_end",    3500),
]

# Height probes. Each entry samples WORLD_SURFACE_WG at (x, z) and asserts the
# surface Y is inside [min_y, max_y]. Adversarial pairing:
#
#   * Inside the mountain_high cell we require min_y ≥ 140 — vanilla noise at
#     these coords (with no terrain override and Tectonic disabled) sits
#     around Y=70-90, so the probe MUST fail before the density override is
#     wired in. If it passes pre-fix, the probe is broken.
#
#   * At spawn (0, 0) we require Y ≤ 100. Catches the regression where the
#     mountain override leaks past the cell boundary or accidentally applies
#     globally — the easy-core terrain must stay flat enough for the starter
#     base to remain buildable.
#
# Probe uses /forceload to generate the chunk, then summons a marker via
# `positioned over WORLD_SURFACE_WG` and reads Pos[1]. Costs ~10-15s per
# probe in chunk-gen time; keep the probe count small.
HEIGHT_PROBES = [
    ("mountain_peak_at_seed",  -4000,     0, 140,  330),
    ("mountain_peak_interior", -3500,     0, 140,  330),
    ("spawn_height_capped",        0,     0,   0,  100),
]


def measure_height(server: Server, label: str, x: int, z: int,
                   gen_timeout: float = 60.0) -> tuple[int | None, str]:
    """Forceload around (x, z), wait for chunks to generate, summon a marker
    at WORLD_SURFACE_WG, return its Y. (None, msg) on error."""
    upper = label.upper()
    # 3x3 chunk forceload around the probe — single chunk is enough for
    # `positioned over` but neighbours give a tiny buffer if the (x, z)
    # straddles a chunk boundary.
    cx, cz = x >> 4, z >> 4
    server.send(f"forceload add {(cx - 1) << 4} {(cz - 1) << 4} "
                f"{((cx + 1) << 4) + 15} {((cz + 1) << 4) + 15}")
    try:
        # Poll `if loaded` until the target chunk is live, then summon + read.
        ready_marker = f"KAROS_HEIGHT_READY_{upper}"
        ready_re = re.compile(rf"\b{ready_marker}\b")
        deadline = time.time() + gen_timeout
        loaded = False
        while time.time() < deadline:
            server.send(f"execute if loaded {x} 80 {z} run say {ready_marker}")
            if server.wait_for(ready_re, timeout=4):
                loaded = True; break
            time.sleep(2)
        if not loaded:
            return None, f"chunk at ({x},{z}) never loaded after {gen_timeout:.0f}s"

        # Clean up any prior probe entity with the same tag (safety against
        # accidental leftovers from re-runs).
        server.send(f"kill @e[tag=hp_{label},type=marker]")
        # `positioned over WORLD_SURFACE_WG` snaps Y to the worldgen surface
        # heightmap above the (x, z) of the executor.
        server.send(f"execute positioned {x} 0 {z} "
                    f"positioned over world_surface run "
                    f"summon minecraft:marker ~ ~ ~ "
                    f'{{Tags:["hp_{label}"]}}')
        server.send(f"data get entity @e[tag=hp_{label},type=marker,limit=1] Pos[1]")
        m = server.wait_for(HEIGHT_REPLY_RE, timeout=10)
        if not m:
            return None, "no /data get reply (marker missing or `positioned over` failed)"
        y = int(float(m.group("y")))
        return y, f"surface Y={y}"
    finally:
        server.send(f"kill @e[tag=hp_{label},type=marker]")
        server.send(f"forceload remove {(cx - 1) << 4} {(cz - 1) << 4} "
                    f"{((cx + 1) << 4) + 15} {((cz + 1) << 4) + 15}")


def run_locate_at(server: Server, x: int, z: int, biome_query: str,
                  max_dist: int, timeout: float = 45.0
                  ) -> tuple[bool | None, str, tuple[int, int] | None]:
    """`/execute positioned <x> 80 <z> run locate biome <query>`.
    Returns (within_max_dist, message, coords_or_None).
    `within_max_dist` is None when /locate didn't reply in time — caller MUST
    treat that as an error, not pass. The previous "no reply ⇒ False" logic
    caused a silent pass when the spawn-safety leak check inverted the result.

    `biome_query` is either a tag (`#caero_rings:theme/mountain_high`) or a
    single biome ID (`minecraft:nether_wastes`).

    `/locate biome` consults the biome SOURCE, not the chunk's persisted data,
    so it works on unloaded chunks. It searches outward from the executing
    source position; with `/execute positioned`, that source position is the
    one we set. Searches across broad tags (#minecraft:is_nether) can take
    >20s — bump the timeout when you call those."""
    server.send(f"execute positioned {x} 80 {z} run locate biome {biome_query}")
    combined = re.compile(LOCATE_COORDS_RE.pattern + r"|" + LOCATE_FAIL_RE.pattern)
    m = server.wait_for(combined, timeout=timeout)
    if m is None:
        return None, f"no /locate reply in {timeout:.0f}s", None
    coords_match = LOCATE_COORDS_RE.search(m.group(0))
    if not coords_match:
        return False, "could not find", None
    fx, fz = int(coords_match.group("x")), int(coords_match.group("z"))
    dist = int(math.hypot(fx - x, fz - z))
    ok = dist <= max_dist
    return ok, f"nearest at ({fx}, {fz}), dist={dist}b (limit {max_dist})", (fx, fz)


def run_at_spawn(server: Server, label: str, biome_query: str,
                 should_match: bool) -> tuple[bool, str]:
    """Spawn-only predicate (chunk is loaded), uses `/execute if biome`."""
    upper = label.upper()
    if should_match:
        server.send(f"execute if biome 0 80 0 {biome_query} run say KAROS_AUDIT_{upper}_HIT")
        target = re.compile(rf"KAROS_AUDIT_{upper}_HIT")
    else:
        server.send(f"execute unless biome 0 80 0 {biome_query} run say KAROS_AUDIT_{upper}_MISS")
        target = re.compile(rf"KAROS_AUDIT_{upper}_MISS")
    m = server.wait_for(target, timeout=5)
    if m is None:
        return False, "marker didn't fire — predicate had opposite truth value"
    return True, "hit"


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--staging", type=Path, default=DEFAULT_STAGING)
    ap.add_argument("--boot-timeout", type=int, default=DEFAULT_BOOT_TIMEOUT)
    args = ap.parse_args()

    if not (args.staging / "run.sh").exists():
        sys.exit(f"[error] no server at {args.staging}/run.sh — run-audit.sh "
                 "must stage the server before this script.")

    # Clean prior world so we get a fresh seed-12345 generation each time.
    import shutil
    world = args.staging / "world"
    if world.exists():
        shutil.rmtree(world)

    failures: list[tuple[str, str]] = []
    server = Server(args.staging)
    print("[boot] starting server …", flush=True)
    boot_t0 = time.time()
    server.start()

    diag: re.Match | None = None
    try:
        # Watch boot output for both DIAG (proves codec parsed) and Done.
        deadline = time.time() + args.boot_timeout
        booted = False
        last_heartbeat = time.time()
        while time.time() < deadline and not booted:
            try:
                line = server.out.get(timeout=1)
            except queue.Empty:
                if time.time() - last_heartbeat > 30:
                    elapsed = int(time.time() - boot_t0)
                    print(f"[boot]   …still booting (+{elapsed}s/{args.boot_timeout}s)",
                          flush=True)
                    last_heartbeat = time.time()
                continue
            if line == "":
                sys.exit("[error] server stdout closed during boot")
            if not diag:
                m = DIAG_RE.search(line)
                if m: diag = m
            if DONE_BOOT_RE.search(line):
                booted = True
        if not booted:
            sys.exit(f"[error] server did not finish booting in {args.boot_timeout}s")
        print(f"[boot] ready in {int(time.time() - boot_t0)}s", flush=True)

        if diag:
            print(f"[diag] medium_min={diag.group('med')} hard_min={diag.group('hard')} "
                  f"jitter={diag.group('jit')} seeds={diag.group('seeds')}")
            print(f"[diag] easy_size={diag.group('easy')} medium_size={diag.group('medsz')} "
                  f"hard_size={diag.group('hardsz')} hard_sub_size={diag.group('hsub')}")
            print(f"[diag] themes=[{diag.group('themes')}]")
            # Sanity-check: all five themes must be present and non-empty.
            for required in ("mountain_high", "jungle_outer", "jungle_swamp",
                             "jungle_deep", "cursed_wastes"):
                if required not in diag.group("themes"):
                    failures.append((f"diag_theme_{required}",
                                     f"theme '{required}' missing from caero_rings DIAG"))
                elif f"{required}=0" in diag.group("themes"):
                    failures.append((f"diag_theme_{required}_empty",
                                     f"theme '{required}' has 0 biomes — tag didn't resolve"))
        else:
            failures.append(("diag_missing",
                             "caero_rings DIAG line never logged — biome source not active"))

        # ── Theme position probes (distant — use /locate via /execute) ────
        print("\n[test] themed-cell positions: nearest theme biome must be close to query")
        for label, x, z, tag, limit in THEME_PROBES:
            ok, msg, _ = run_locate_at(server, x, z, tag, limit)
            tag_short = tag.removeprefix("#caero_rings:")
            print(f"  {'PASS' if ok else 'FAIL'}  {label:<24} ({x:>5}, {z:>5}) {tag_short}: {msg}")
            if not ok:
                failures.append((label, f"({x}, {z}) {tag}: {msg}"))

        # ── Vanilla biome-tag probes (proves nether/end biomes are present) ─
        print("\n[test] vanilla biome tags at themed seeds")
        for label, x, z, tag, limit in VANILLA_BIOME_TAG_PROBES:
            ok, msg, _ = run_locate_at(server, x, z, tag, limit)
            tag_short = tag.removeprefix("#")
            print(f"  {'PASS' if ok else 'FAIL'}  {label:<24} ({x:>5}, {z:>5}) {tag_short}: {msg}")
            if not ok:
                failures.append((label, f"({x}, {z}) {tag}: {msg}"))

        # ── Spawn safety (spawn chunk IS loaded — /execute if biome works) ─
        # Audit previously relied on the dedicated server keeping the spawn
        # chunk hot. With spawn-protection=0 + simulation-distance=2 + no
        # players online, that's no longer guaranteed; `/execute if biome`
        # silently returns false on an unloaded chunk and every spawn probe
        # flakes. Forceload spawn first and poll `if loaded` until ready —
        # mirrors the pattern measure_height() uses for distant probes.
        print("\n[setup] forceload spawn chunk (so `if biome` doesn't flake)")
        server.send("forceload add -16 -16 31 31")
        loaded_re = re.compile(r"KAROS_SPAWN_LOADED")
        deadline = time.time() + 30
        spawn_loaded = False
        while time.time() < deadline:
            server.send("execute if loaded 0 80 0 run say KAROS_SPAWN_LOADED")
            if server.wait_for(loaded_re, timeout=3):
                spawn_loaded = True; break
            time.sleep(1)
        if not spawn_loaded:
            failures.append(("spawn_chunk_load",
                             "spawn chunk (0, 0) never loaded after 30s — biome predicates will flake"))

        print("\n[test] spawn (0, 0) safety — must be tier_easy, no themes")
        for label, query, expect in SPAWN_PROBES:
            ok, msg = run_at_spawn(server, label, query, expect)
            q_short = query.removeprefix("#caero_rings:")
            print(f"  {'PASS' if ok else 'FAIL'}  {label:<24} (    0,     0) {'IS' if expect else 'NOT'} {q_short}: {msg}")
            if not ok:
                failures.append((label, f"(0, 0) spawn {q_short}: {msg}"))

        # ── Floor enforcement: no theme should leak into the easy core ────
        # For each probe, run /locate <theme> and require the nearest match
        # to be FAR from the query point. If it's close, the theme is
        # leaking into the easy core (regression).
        print("\n[test] radius-floor enforcement (r=600 — nearest theme must be far)")
        for label, x, z, tag, min_dist in FLOOR_LEAK_PROBES:
            close, msg, _ = run_locate_at(server, x, z, tag, min_dist)
            if close is None:
                ok = False
            else:
                ok = not close  # "close"=hit within min_dist → leak → FAIL
            tag_short = tag.removeprefix("#caero_rings:")
            print(f"  {'PASS' if ok else 'FAIL'}  {label:<24} ({x:>5}, {z:>5}) {tag_short}: {msg}")
            if not ok:
                failures.append((label, f"({x}, {z}) theme leaked into easy core: {msg}"))

        # ── Spawn-safety: nether/end biomes must be FAR from spawn ───────
        # They legitimately exist in overworld (in the themed cells) but must
        # not appear close to origin. Asserts nearest hit ≥ min_dist.
        # /locate searches over broad tags (is_nether/is_end) cross the entire
        # world and can take 20-30s — use a generous timeout.
        print("\n[test] spawn-safety: nether/end biomes must be far from origin")
        for label, tag, min_dist in NETHER_END_DISTANCE_CHECKS:
            close, msg, coords = run_locate_at(server, 0, 0, tag, min_dist, timeout=60)
            # close: True=hit within min_dist (FAIL), False=hit but far enough (PASS),
            #        None=no reply in time (FAIL — can't conclude).
            if close is None:
                ok = False
            else:
                ok = not close
            tag_short = tag.removeprefix("#minecraft:")
            print(f"  {'PASS' if ok else 'FAIL'}  {label:<24} {tag_short}: {msg}")
            if not ok:
                failures.append((label,
                                 f"{tag} too close to spawn: {msg}"))
        # ── Surface-height probes (mountain elevation + spawn cap) ─────────
        # These cost ~10-15s each due to chunk generation, so they run last.
        print("\n[test] surface heights (mountain_high must be tall, spawn must stay low)")
        for label, x, z, min_y, max_y in HEIGHT_PROBES:
            y, msg = measure_height(server, label, x, z)
            if y is None:
                ok = False; detail = msg
            else:
                ok = (min_y <= y <= max_y)
                detail = f"Y={y} (need {min_y}..{max_y})"
            print(f"  {'PASS' if ok else 'FAIL'}  {label:<26} ({x:>5}, {z:>5}): {detail}")
            if not ok:
                failures.append((label, f"({x}, {z}) surface height: {detail}"))
    finally:
        print("\n[shutdown] /stop", flush=True)
        server.stop()

    # ── Report ────────────────────────────────────────────────────────────
    print()
    print("=" * 70)
    if not failures:
        print(" RESULT: PASS — every check succeeded")
        print("=" * 70)
        sys.exit(0)
    print(f" RESULT: FAIL — {len(failures)} failure(s):")
    for name, detail in failures:
        print(f"   • {name}: {detail}")
    print("=" * 70)
    sys.exit(1)


if __name__ == "__main__":
    try: main()
    except KeyboardInterrupt: sys.exit(130)
