#!/usr/bin/env python3
"""
Drive a NeoForge 1.21.1 dedicated server through a deterministic pregen run
that exercises every biome tier, then exits cleanly.

What it does:
  1. Stage <staging>/mods   <- symlinks to mods from the Prism instance
                                (with client-only mods filtered out)
  2. Stage <staging>/config <- copy of the Prism instance's config dir
  3. Enable Chunky if it's been .disabled in the Prism instance
  4. Write a deterministic server.properties (fixed seed, peaceful, no whitelist)
  5. Spawn the NeoForge server, wait for "Done" log line
  6. For each tier-anchor biome:
       /locate biome <id>      → parse "X Y Z (n blocks away)" from log
       chunky center X Z; chunky radius R; chunky shape circle; chunky start
       wait for "Task finished" or 100% complete log
  7. /stop, wait for clean shutdown
  8. The test world is now in <staging>/audit-world/

Run analyze.py against <staging>/audit-world afterwards.

Requires: a NeoForge dedicated server already installed at <staging>
          (run scripts/bootstrap-server.sh first).
"""
from __future__ import annotations
import argparse, json, os, re, shutil, signal, subprocess, sys, time
from pathlib import Path

# ---- Defaults ---------------------------------------------------------------

DEFAULT_STAGING = Path("/tmp/aero-audit-server")
DEFAULT_INSTANCE = Path.home() / ".local/share/PrismLauncher/instances/1.21.1/minecraft"
DEFAULT_SEED = "3735928559"   # 0xDEADBEEF; deterministic
DEFAULT_RADIUS = 256          # blocks; ~85 chunks per ring
DEFAULT_BIOMES = {
    "easy":   "minecraft:plains",
    "medium": "minecraft:taiga",
    "hard":   "minecraft:badlands",
}
DEFAULT_BOOT_TIMEOUT = 240    # seconds for cold boot with full mod stack
DEFAULT_PREGEN_TIMEOUT = 360  # per-tier pregen timeout (5-min budget shared across 3 tiers)

# Mods the dedicated server cannot load or doesn't need for ore worldgen.
# Conservative — only known-bad. Add to this list when a server-side crash
# pinpoints the offender.
CLIENT_ONLY_PREFIXES = (
    "iris-", "sodium-", "xaerominimap-", "xaeroworldmap-",
    "ImmediatelyFast-", "FreshAnimations",
    # DistantHorizons does have a server side but it's heavy and irrelevant for
    # ore-density tests. Skip to keep the boot fast.
    "DistantHorizons-",
)

SERVER_PROPS_TEMPLATE = """\
level-seed={seed}
level-name=audit-world
gamemode=spectator
difficulty=peaceful
spawn-protection=0
max-players=1
online-mode=false
view-distance=10
simulation-distance=4
allow-flight=true
white-list=false
broadcast-console-to-ops=false
broadcast-rcon-to-ops=false
spawn-monsters=false
spawn-animals=false
generate-structures=true
"""

# ---- Staging ----------------------------------------------------------------

def stage_mods(staging: Path, instance: Path):
    src = instance / "mods"
    dst = staging / "mods"
    dst.mkdir(exist_ok=True)
    skipped, kept = [], []
    for jar in sorted(src.glob("*.jar")):
        if any(jar.name.startswith(p) for p in CLIENT_ONLY_PREFIXES):
            skipped.append(jar.name); continue
        link = dst / jar.name
        if link.is_symlink() or link.exists(): link.unlink()
        link.symlink_to(jar.resolve())
        kept.append(jar.name)
    # Re-enable Chunky if Prism has it as .disabled.
    for d in src.glob("Chunky-*.jar.disabled"):
        link = dst / d.name.removesuffix(".disabled")
        if link.is_symlink() or link.exists(): link.unlink()
        link.symlink_to(d.resolve())
        kept.append(link.name)
    print(f"[stage] kept {len(kept)} mods, skipped {len(skipped)} client-only")
    if skipped:
        print(f"[stage] skipped: {', '.join(skipped)}")

def stage_configs(staging: Path, instance: Path):
    src = instance / "config"
    dst = staging / "config"
    if dst.exists(): shutil.rmtree(dst)
    if src.exists():
        shutil.copytree(src, dst, symlinks=False)
        print(f"[stage] copied config dir ({sum(1 for _ in dst.rglob('*'))} entries)")

def write_server_props(staging: Path, seed: str):
    (staging / "server.properties").write_text(SERVER_PROPS_TEMPLATE.format(seed=seed))
    (staging / "eula.txt").write_text("eula=true\n")

def reset_world(staging: Path, world_name: str = "audit-world"):
    """Delete any prior test world so each run is deterministic."""
    world = staging / world_name
    if world.exists():
        shutil.rmtree(world)
        print(f"[stage] removed prior {world}")

# ---- Server I/O -------------------------------------------------------------

class ServerProcess:
    def __init__(self, staging: Path):
        self.staging = staging
        self.proc: subprocess.Popen | None = None
        self.log: list[str] = []

    def start(self):
        run_sh = self.staging / "run.sh"
        if not run_sh.exists():
            sys.exit(f"[error] no run.sh at {run_sh} — run scripts/bootstrap-server.sh first")
        # NeoForge's installer-generated run.sh sources user_jvm_args + unix_args.
        env = os.environ.copy()
        env.setdefault("JAVA_TOOL_OPTIONS", "")
        self.proc = subprocess.Popen(
            ["bash", str(run_sh), "nogui"],
            cwd=str(self.staging),
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            bufsize=1, text=True, env=env,
        )

    def send(self, cmd: str):
        if not self.proc or self.proc.stdin is None: return
        self.proc.stdin.write(cmd + "\n"); self.proc.stdin.flush()
        print(f"[>] {cmd}")

    def readline(self) -> str | None:
        if not self.proc or self.proc.stdout is None: return None
        line = self.proc.stdout.readline()
        if not line: return None
        self.log.append(line)
        sys.stdout.write(f"  | {line}"); sys.stdout.flush()
        return line

    def wait_for(self, pattern: re.Pattern, timeout: float) -> re.Match:
        t0 = time.time()
        while time.time() - t0 < timeout:
            line = self.readline()
            if line is None:
                raise RuntimeError("server stdout closed before match")
            m = pattern.search(line)
            if m: return m
        raise TimeoutError(f"timed out after {timeout}s waiting for {pattern.pattern!r}")

    def stop_and_wait(self, timeout: float = 120):
        try:
            self.send("stop")
        except Exception: pass
        if self.proc is None: return
        t0 = time.time()
        while time.time() - t0 < timeout:
            line = self.readline()
            if line is None: break
            if "ThreadedAnvilChunkStorage" in line and "Saved" in line: pass
            if "Stopping server" in line and (time.time() - t0) > 5:
                # let the JVM finish flushing world data
                continue
            if self.proc.poll() is not None: return
        # last resort
        try: self.proc.terminate()
        except Exception: pass
        self.proc.wait(timeout=15)

# ---- Locate + pregen flow ---------------------------------------------------

DONE_BOOT_RE = re.compile(r'Done \([0-9.]+s\)! For help, type "help"')
LOCATE_RE = re.compile(r"The nearest .* is at \[(-?\d+), (?:~|-?\d+), (-?\d+)\]")
CHUNKY_DONE_RE = re.compile(r"Task (?:finished|complete|completed) for|100(?:\.0+)?%.*/.*chunks", re.I)
CHUNKY_PROGRESS_RE = re.compile(r"\[Chunky\].*?(\d+\.?\d*)%")

def locate_biome(server: ServerProcess, biome_id: str, timeout: float = 60) -> tuple[int, int]:
    server.send(f"locate biome {biome_id}")
    m = server.wait_for(LOCATE_RE, timeout=timeout)
    return int(m.group(1)), int(m.group(2))

def pregen_ring(server: ServerProcess, x: int, z: int, radius: int, timeout: float):
    server.send("chunky world minecraft:overworld")
    server.send("chunky shape circle")
    server.send(f"chunky center {x} {z}")
    server.send(f"chunky radius {radius}")
    server.send("chunky start")
    # Chunky logs progress; wait until 100% or "task finished"
    t0 = time.time()
    last_progress = 0.0
    while time.time() - t0 < timeout:
        line = server.readline()
        if line is None:
            raise RuntimeError("server stdout closed during pregen")
        if CHUNKY_DONE_RE.search(line): return
        m = CHUNKY_PROGRESS_RE.search(line)
        if m: last_progress = float(m.group(1))
    raise TimeoutError(f"pregen timed out at {last_progress:.1f}%")

# ---- Main -------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--staging", type=Path, default=DEFAULT_STAGING,
                    help="Where the NeoForge server is installed (created by bootstrap-server.sh)")
    ap.add_argument("--instance", type=Path, default=DEFAULT_INSTANCE,
                    help="Path to the Prism Minecraft instance (source of mods/ and config/)")
    ap.add_argument("--seed", default=DEFAULT_SEED, help="Fixed level-seed for determinism")
    ap.add_argument("--radius", type=int, default=DEFAULT_RADIUS, help="Pregen radius per tier (blocks)")
    ap.add_argument("--biomes", type=json.loads, default=None,
                    help='Override tier→biome anchors as JSON. Default: ' + json.dumps(DEFAULT_BIOMES))
    ap.add_argument("--boot-timeout", type=int, default=DEFAULT_BOOT_TIMEOUT)
    ap.add_argument("--pregen-timeout", type=int, default=DEFAULT_PREGEN_TIMEOUT)
    ap.add_argument("--keep-world", action="store_true",
                    help="Don't delete the prior audit-world before this run (debug only)")
    args = ap.parse_args()

    biomes = args.biomes or DEFAULT_BIOMES

    if not args.staging.exists() or not (args.staging / "run.sh").exists():
        sys.exit(f"[error] no NeoForge server at {args.staging}. Run scripts/bootstrap-server.sh first.")
    if not (args.instance / "mods").exists():
        sys.exit(f"[error] no mods/ at {args.instance}. Pass --instance pointing to a Prism MC dir.")

    print(f"[stage] staging={args.staging}, instance={args.instance}, seed={args.seed}")
    if not args.keep_world: reset_world(args.staging)
    stage_mods(args.staging, args.instance)
    stage_configs(args.staging, args.instance)
    write_server_props(args.staging, args.seed)

    server = ServerProcess(args.staging)
    server.start()
    try:
        print(f"[boot] waiting up to {args.boot_timeout}s for server to be ready…")
        server.wait_for(DONE_BOOT_RE, timeout=args.boot_timeout)
        print("[boot] server ready")

        for tier, biome_id in biomes.items():
            print(f"\n[{tier}] locating {biome_id}")
            try:
                x, z = locate_biome(server, biome_id)
                print(f"[{tier}] anchor at ({x}, {z})")
            except TimeoutError as e:
                print(f"[{tier}] !! locate failed: {e}; skipping")
                continue
            print(f"[{tier}] pregen radius={args.radius} blocks")
            try:
                pregen_ring(server, x, z, args.radius, args.pregen_timeout)
                print(f"[{tier}] pregen done")
            except TimeoutError as e:
                print(f"[{tier}] !! pregen timed out: {e}")
                server.send("chunky cancel")
    finally:
        print("\n[shutdown] /stop")
        server.stop_and_wait()

    world = args.staging / "audit-world"
    print(f"[done] world ready at {world}")

if __name__ == "__main__":
    try: main()
    except KeyboardInterrupt: sys.exit(130)
