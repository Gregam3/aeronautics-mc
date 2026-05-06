#!/usr/bin/env python3
"""
Sample biome frequency near world spawn.

Boots the staged NeoForge dedicated server, pregens a disc around (0, 0)
with Chunky, then walks the resulting region files and counts the dominant
biome per chunk (using the same NBT path as the ore-density analyzer).

Reports a histogram so we can see whether some biomes near spawn are
over- or under-represented vs others. Each chunk contributes one vote;
within the configured pregen radius, the easy tier should dominate.

Why pregen instead of /execute if biome at a grid:
  /execute if biome <pos> <biome> needs the chunk at <pos> to be loaded.
  Only the auto-loaded spawn chunk passes — every other point silently
  short-circuits to 'no match'. Chunky pregens the whole disc, so we
  observe what *actually* generated, not what the biome source claims
  during a spot probe.
"""
from __future__ import annotations
import argparse, glob, io, json, os, queue, re, struct, subprocess, sys, threading, time, zlib
from collections import Counter
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
ORE_DENSITY = REPO_ROOT / "test/ore-density/scripts"
import importlib.util  # noqa: E402

def _load(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod); return mod

_pregen = _load("pregen_driver", ORE_DENSITY / "pregen-driver.py")
_analyze = _load("analyze", ORE_DENSITY / "analyze.py")
stage_mods, stage_configs = _pregen.stage_mods, _pregen.stage_configs
write_server_props, reset_world = _pregen.write_server_props, _pregen.reset_world
read_chunk, chunk_dominant_biome = _analyze.read_chunk, _analyze.chunk_dominant_biome

DEFAULT_STAGING = Path("/tmp/aero-audit-server")
DEFAULT_INSTANCE = Path.home() / ".local/share/PrismLauncher/instances/1.21.1/minecraft"
DEFAULT_SEED = "3735928559"
DEFAULT_BOOT_TIMEOUT = 240
DEFAULT_PREGEN_RADIUS = 600           # blocks; ~4400 chunks → ~60-120s pregen
DEFAULT_PREGEN_TIMEOUT = 600

TAG_DIR = REPO_ROOT / "src/main/resources/data/caero_rings/tags/worldgen/biome"

DONE_BOOT_RE = re.compile(r'Done \([0-9.]+s\)! For help, type "help"')
CHUNKY_DONE_RE = re.compile(r"Task (?:finished|complete|completed) for|100(?:\.0+)?%.*/.*chunks", re.I)
CHUNKY_PROGRESS_RE = re.compile(r"\[Chunky\].*?(\d+\.?\d*)%")


class Server:
    """Subprocess wrapper with a stdout-draining thread so we can poll lines
    without blocking forever when the server goes silent."""

    def __init__(self, staging: Path):
        self.staging = staging
        self.proc: subprocess.Popen | None = None
        self.out: queue.Queue[str] = queue.Queue()
        self.log: list[str] = []
        self._reader: threading.Thread | None = None
        self.quiet = False

    def start(self):
        run_sh = self.staging / "run.sh"
        if not run_sh.exists(): sys.exit(f"[error] no run.sh at {run_sh}")
        env = os.environ.copy(); env.setdefault("JAVA_TOOL_OPTIONS", "")
        self.proc = subprocess.Popen(
            ["bash", str(run_sh), "nogui"], cwd=str(self.staging),
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            bufsize=1, text=True, env=env,
        )
        self._reader = threading.Thread(target=self._drain, daemon=True)
        self._reader.start()

    def _drain(self):
        assert self.proc and self.proc.stdout
        for line in self.proc.stdout:
            self.log.append(line)
            if not self.quiet:
                sys.stdout.write(f"  | {line}"); sys.stdout.flush()
            self.out.put(line)
        self.out.put("")

    def send(self, cmd: str):
        if not self.proc or self.proc.stdin is None: return
        self.proc.stdin.write(cmd + "\n"); self.proc.stdin.flush()

    def wait_for(self, pattern: re.Pattern, timeout: float) -> re.Match | None:
        deadline = time.time() + timeout
        while time.time() < deadline:
            try: line = self.out.get(timeout=max(0.1, deadline - time.time()))
            except queue.Empty: return None
            if line == "": return None
            m = pattern.search(line)
            if m: return m
        return None

    def stop(self, timeout: float = 120):
        try: self.send("stop")
        except Exception: pass
        if not self.proc: return
        try: self.proc.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            self.proc.terminate()
            try: self.proc.wait(timeout=20)
            except subprocess.TimeoutExpired: self.proc.kill()


def load_biomes() -> tuple[list[str], dict[str, str]]:
    """Return (ordered_biomes, biome -> tier_label)."""
    files = [
        ("easy",   TAG_DIR / "tier_easy.json"),
        ("medium", TAG_DIR / "tier_medium.json"),
        ("hard",   TAG_DIR / "tier_hard.json"),
        ("hard*",  TAG_DIR / "tier_hard_common.json"),
    ]
    seen: dict[str, str] = {}
    order: list[str] = []
    for tier, p in files:
        if not p.exists(): continue
        for b in json.loads(p.read_text()).get("values", []):
            if b in seen: continue
            seen[b] = tier; order.append(b)
    return order, seen


def pregen_disc(server: Server, radius: int, timeout: float):
    server.send("chunky world minecraft:overworld")
    server.send("chunky shape circle")
    server.send("chunky center 0 0")
    server.send(f"chunky radius {radius}")
    server.send("chunky start")
    deadline = time.time() + timeout
    last_pct = 0.0
    last_progress_log = time.time()
    while time.time() < deadline:
        try: line = server.out.get(timeout=5.0)
        except queue.Empty:
            if time.time() - last_progress_log > 30:
                print(f"[pregen] (no chunky output for 30s; last seen {last_pct:.1f}%)")
                last_progress_log = time.time()
            continue
        if line == "":
            raise RuntimeError("server stdout closed during pregen")
        if CHUNKY_DONE_RE.search(line):
            return
        m = CHUNKY_PROGRESS_RE.search(line)
        if m:
            last_pct = float(m.group(1))
            last_progress_log = time.time()
    raise TimeoutError(f"pregen timed out at {last_pct:.1f}%")


def scan_world(world_dir: Path, max_radius: int) -> tuple[Counter, int]:
    """Walk audit-world's region files, return (biome_counts, total_chunks)
    for chunks whose center lies within `max_radius` blocks of (0, 0)."""
    region_glob = str(world_dir / "region" / "r.*.mca")
    paths = [p for p in sorted(glob.glob(region_glob)) if os.path.getsize(p) > 8192]
    counts: Counter = Counter()
    total = 0
    name_re = re.compile(r"r\.(-?\d+)\.(-?\d+)\.mca")
    r2 = max_radius * max_radius
    for p in paths:
        m = name_re.search(os.path.basename(p))
        if not m: continue
        rx, rz = int(m.group(1)), int(m.group(2))
        for cz in range(32):
            for cx in range(32):
                wx = (rx * 32 + cx) * 16 + 8
                wz = (rz * 32 + cz) * 16 + 8
                if wx * wx + wz * wz > r2: continue
                try: ch = read_chunk(p, cx, cz)
                except Exception: continue
                if ch is None: continue
                status = ch.get("Status")
                if status and status.value != "minecraft:full": continue
                biome = chunk_dominant_biome(ch)
                if not biome: continue
                counts[biome] += 1; total += 1
    return counts, total


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--staging", type=Path, default=DEFAULT_STAGING)
    ap.add_argument("--instance", type=Path, default=DEFAULT_INSTANCE)
    ap.add_argument("--seed", default=DEFAULT_SEED)
    ap.add_argument("--boot-timeout", type=int, default=DEFAULT_BOOT_TIMEOUT)
    ap.add_argument("--pregen-radius", type=int, default=DEFAULT_PREGEN_RADIUS,
                    help="radius (blocks) of the chunky pregen disc")
    ap.add_argument("--pregen-timeout", type=int, default=DEFAULT_PREGEN_TIMEOUT)
    ap.add_argument("--scan-radius", type=int, default=None,
                    help="only count chunks within this radius from spawn "
                         "(default: pregen-radius)")
    ap.add_argument("--keep-world", action="store_true")
    ap.add_argument("--report-json", type=Path, default=None)
    ap.add_argument("--exclude-mod", action="append", default=[],
                    help="filename prefix of a mod jar to drop from staging")
    args = ap.parse_args()

    if not args.staging.exists() or not (args.staging / "run.sh").exists():
        sys.exit(f"[error] no NeoForge server at {args.staging}.")
    if not (args.instance / "mods").exists():
        sys.exit(f"[error] no mods/ at {args.instance}.")

    biomes, biome_tier = load_biomes()
    if not biomes: sys.exit("[error] biome tier tag files empty or missing")
    scan_radius = args.scan_radius or args.pregen_radius

    print(f"[plan] pregen disc r={args.pregen_radius}, scan r={scan_radius}")
    print(f"[stage] staging={args.staging}")
    if not args.keep_world: reset_world(args.staging)
    stage_mods(args.staging, args.instance)
    stage_configs(args.staging, args.instance)
    write_server_props(args.staging, args.seed)

    if args.exclude_mod:
        mods_dir = args.staging / "mods"
        for jar in sorted(mods_dir.glob("*.jar")):
            for prefix in args.exclude_mod:
                if jar.name.startswith(prefix):
                    print(f"[stage] excluding {jar.name}")
                    jar.unlink(); break

    server = Server(args.staging)
    server.start()
    try:
        print(f"[boot] waiting up to {args.boot_timeout}s for server…")
        if server.wait_for(DONE_BOOT_RE, args.boot_timeout) is None:
            sys.exit(f"[error] boot did not complete in {args.boot_timeout}s")
        print("[boot] server ready")
        print(f"[pregen] chunky disc r={args.pregen_radius} (timeout {args.pregen_timeout}s)")
        t0 = time.time()
        try:
            pregen_disc(server, args.pregen_radius, args.pregen_timeout)
            print(f"[pregen] done in {time.time() - t0:.1f}s")
        except TimeoutError as e:
            print(f"[pregen] !! {e} — proceeding with whatever generated so far")
            server.send("chunky cancel")
            time.sleep(3)
    finally:
        print("\n[shutdown] /stop (let world flush)")
        server.stop()

    world = args.staging / "audit-world"
    print(f"[scan] walking {world}/region for chunks within r={scan_radius}")
    counts, total = scan_world(world, scan_radius)

    biome_tier_lookup = lambda b: biome_tier.get(b, "OTHER")
    per_tier: Counter = Counter()
    for b, c in counts.items():
        per_tier[biome_tier_lookup(b)] += c

    print()
    print("─" * 78)
    print(f" Biome frequency near spawn  (seed={args.seed}, "
          f"pregen={args.pregen_radius}, scan={scan_radius})")
    print(f" {total} chunks scanned, {len(counts)} distinct biomes observed")
    print("─" * 78)
    print(" tier   chunks  share   biome")
    for biome, count in counts.most_common():
        tier = biome_tier_lookup(biome)
        share = 100.0 * count / total if total else 0.0
        print(f" {tier:<5}  {count:>5}  {share:5.1f}%  {biome}")
    print("─" * 78)
    print(" Tier rollup (within this radius the easy core SHOULD dominate):")
    for tier in ("easy", "medium", "hard", "hard*", "OTHER"):
        c = per_tier.get(tier, 0)
        share = 100.0 * c / total if total else 0.0
        if c or tier in ("easy", "medium", "hard"):
            print(f"   {tier:<6} {c:>5}  ({share:5.1f}%)")
    if counts:
        # The user's question: are some biomes way more common than others?
        # Compare each biome's share against the uniform expectation
        # (1 / number-of-biomes-that-actually-appeared) and flag anything ≥3×.
        uniform = total / len(counts)
        top = counts.most_common(1)[0]
        print(f" Distribution stats among the {len(counts)} biomes that appeared:")
        print(f"   uniform expectation per biome: {uniform:.1f} chunks")
        print(f"   most common: {top[0]} = {top[1]} chunks ({top[1]/uniform:.1f}× uniform)")
        outliers = [(b, c) for b, c in counts.items() if c >= 3 * uniform]
        outliers.sort(key=lambda kv: -kv[1])
        if outliers:
            print(f"   biomes ≥3× uniform ({3 * uniform:.1f}+ chunks each):")
            for b, c in outliers:
                print(f"     {b}: {c} ({c/uniform:.1f}×)")
        else:
            print("   no biome is ≥3× uniform — distribution looks reasonable")
    print("─" * 78)

    if args.report_json:
        args.report_json.write_text(json.dumps({
            "seed": args.seed,
            "pregen_radius": args.pregen_radius,
            "scan_radius": scan_radius,
            "total_chunks": total,
            "biome_counts": dict(counts),
            "biome_tier": biome_tier,
            "per_tier": dict(per_tier),
        }, indent=2))
        print(f"[report] wrote {args.report_json}")


if __name__ == "__main__":
    try: main()
    except KeyboardInterrupt: sys.exit(130)
