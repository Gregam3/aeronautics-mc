#!/usr/bin/env python3
"""
Boot a NeoForge 1.21.1 dedicated server with our mod stack and verify, against
the running biome source, the contracts that matter:

  1. The codec parsed our dimension JSON — we see the `caero_rings DIAG` line.
  2. Spawn (0, 64, 0) resolves to easy — and not to medium or hard.
  3. The nearest tier_easy biome is well inside the easy core.
  4. The nearest tier_hard biome is outside the medium_min_radius floor.

Uses /execute if biome at spawn (cheap: spawn chunk auto-loads at boot) and
/locate biome to find the tier-tagged biomes themselves. /locate doesn't need
chunks generated — it queries the biome source directly, so the test runs in
a few seconds beyond the server boot.

A background thread drains the server's stdout into a queue so the main thread
can poll with timeouts instead of blocking forever on readline().
"""
from __future__ import annotations
import argparse, math, os, queue, re, signal, subprocess, sys, threading, time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
PREGEN_DRIVER = REPO_ROOT / "test/ore-density/scripts/pregen-driver.py"
import importlib.util  # noqa: E402
_spec = importlib.util.spec_from_file_location("pregen_driver", PREGEN_DRIVER)
_pregen = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_pregen)
stage_mods = _pregen.stage_mods
stage_configs = _pregen.stage_configs
write_server_props = _pregen.write_server_props
reset_world = _pregen.reset_world

DEFAULT_STAGING = Path("/tmp/aero-audit-server")
DEFAULT_INSTANCE = Path.home() / ".local/share/PrismLauncher/instances/1.21.1/minecraft"
DEFAULT_SEED = "3735928559"
DEFAULT_BOOT_TIMEOUT = 240

DONE_BOOT_RE = re.compile(r'Done \([0-9.]+s\)! For help, type "help"')
DIAG_RE = re.compile(r"caero_rings DIAG: medium_min=(\d+) hard_min=(\d+) floor_jitter=(\d+) seeds=(\d+) easy_size=(\d+) medium_size=(\d+) hard_size=(\d+)")
MARKER_RE = re.compile(r"\[Server\] (BIOMETIER_[A-Z]+(?:_NOT)?_(?:easy|medium|hard))\b", re.IGNORECASE)
# /locate biome reply format. Coordinates may be `~` if Y is unspecified.
LOCATE_RE = re.compile(r"The nearest .*?caero_rings:tier_(easy|medium|hard).*? is at \[(-?\d+), (?:~|-?\d+), (-?\d+)\]")
LOCATE_FAIL_RE = re.compile(r"Could not find .*?caero_rings:tier_(easy|medium|hard)")


class Server:
    """Wraps the dedicated-server process with a stdout-draining thread so we
    can ask 'has line matching X arrived in the last N seconds?' without
    blocking forever when the server goes silent."""

    def __init__(self, staging: Path):
        self.staging = staging
        self.proc: subprocess.Popen | None = None
        self.out: queue.Queue[str] = queue.Queue()
        self._reader: threading.Thread | None = None
        self.log: list[str] = []  # captured everything, for post-hoc dumps

    def start(self):
        run_sh = self.staging / "run.sh"
        if not run_sh.exists():
            sys.exit(f"[error] no run.sh at {run_sh}")
        env = os.environ.copy()
        env.setdefault("JAVA_TOOL_OPTIONS", "")
        self.proc = subprocess.Popen(
            ["bash", str(run_sh), "nogui"],
            cwd=str(self.staging),
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            bufsize=1, text=True, env=env,
        )
        self._reader = threading.Thread(target=self._drain, daemon=True)
        self._reader.start()

    def _drain(self):
        assert self.proc and self.proc.stdout
        for line in self.proc.stdout:
            self.log.append(line)
            sys.stdout.write(f"  | {line}"); sys.stdout.flush()
            self.out.put(line)
        self.out.put("")  # EOF sentinel

    def send(self, cmd: str):
        if not self.proc or self.proc.stdin is None: return
        self.proc.stdin.write(cmd + "\n"); self.proc.stdin.flush()
        print(f"[>] {cmd}")

    def wait_for(self, pattern: re.Pattern, timeout: float) -> re.Match | None:
        """Pull lines off the queue until one matches or `timeout` elapses."""
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
        """Drain the queue for `seconds` and return every match against any of
        the given patterns. Lines that don't match are discarded (they're still
        in self.log for forensics)."""
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

    def stop(self, timeout: float = 60):
        try: self.send("stop")
        except Exception: pass
        if not self.proc: return
        try: self.proc.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            print(f"[!] /stop didn't shut down in {timeout}s, sending SIGTERM")
            self.proc.terminate()
            try: self.proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                self.proc.kill()


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--staging", type=Path, default=DEFAULT_STAGING)
    ap.add_argument("--instance", type=Path, default=DEFAULT_INSTANCE)
    ap.add_argument("--seed", default=DEFAULT_SEED)
    ap.add_argument("--boot-timeout", type=int, default=DEFAULT_BOOT_TIMEOUT)
    ap.add_argument("--keep-world", action="store_true")
    args = ap.parse_args()

    if not args.staging.exists() or not (args.staging / "run.sh").exists():
        sys.exit(f"[error] no NeoForge server at {args.staging}. "
                 "Run ../ore-density/scripts/bootstrap-server.sh first.")
    if not (args.instance / "mods").exists():
        sys.exit(f"[error] no mods/ at {args.instance}.")

    print(f"[stage] staging={args.staging}")
    if not args.keep_world: reset_world(args.staging)
    stage_mods(args.staging, args.instance)
    stage_configs(args.staging, args.instance)
    write_server_props(args.staging, args.seed)

    failures: list[str] = []
    server = Server(args.staging)
    server.start()
    diag: re.Match | None = None
    try:
        print(f"[boot] waiting up to {args.boot_timeout}s for server…")
        # Watch for the DIAG line during the same wait — it usually fires
        # mid-boot when the biome source is first instantiated.
        deadline = time.time() + args.boot_timeout
        booted = False
        while time.time() < deadline and not booted:
            try:
                line = server.out.get(timeout=max(0.1, deadline - time.time()))
            except queue.Empty:
                continue
            if line == "":
                sys.exit("[error] server stdout closed during boot")
            if not diag:
                m = DIAG_RE.search(line)
                if m: diag = m
            if DONE_BOOT_RE.search(line):
                booted = True
        if not booted:
            sys.exit(f"[error] boot did not complete in {args.boot_timeout}s")
        print("[boot] server ready")

        if diag:
            print(f"[diag] medium_min={diag.group(1)} hard_min={diag.group(2)} "
                  f"floor_jitter={diag.group(3)} seeds={diag.group(4)} "
                  f"easy_size={diag.group(5)} medium_size={diag.group(6)} "
                  f"hard_size={diag.group(7)}")
        else:
            failures.append("DIAG line never logged — biome source may not be active")

        # ── spawn must resolve to easy ───────────────────────────────────────
        # Spawn chunk auto-loads at boot, so /execute if biome works without
        # any forceload.
        print("\n[test] /execute if biome at spawn")
        for tier in ("easy", "medium", "hard"):
            tag = "BIOMETIER_SPAWN_" + tier.upper()
            server.send(f"execute if biome 0 64 0 #caero_rings:tier_{tier} run say {tag}")
        # Drain for a few seconds to collect markers — only the matching tier's
        # marker actually fires.
        matches = server.collect_for(8, MARKER_RE)
        spawn_tiers = {m.group(1).split("_")[-1].lower() for m in matches if "_SPAWN_" in m.group(1)}
        print(f"[test] spawn matched tiers: {sorted(spawn_tiers) or '∅'}")
        if "easy" not in spawn_tiers:
            failures.append("spawn (0, 64, 0) did not match #caero_rings:tier_easy")
        if "medium" in spawn_tiers:
            failures.append("spawn (0, 64, 0) unexpectedly matched #caero_rings:tier_medium")
        if "hard" in spawn_tiers:
            failures.append("spawn (0, 64, 0) unexpectedly matched #caero_rings:tier_hard")

        # ── nearest tier biomes via /locate biome ────────────────────────────
        # /locate doesn't need chunks generated. The default search radius is
        # 6400 blocks, which fully covers our 5000-radius world.
        print("\n[test] /locate biome for each tier tag")
        # Configured floors — must match the dimension preset. A locate
        # result for tier_easy past medium_min_radius would mean the easy
        # core failed to seed. A tier_hard result inside (medium_min_radius
        # − floor_jitter) would mean the hard floor isn't taking effect.
        medium_min = int(diag.group(1)) if diag else 1500
        floor_jitter = int(diag.group(3)) if diag else 600
        hard_floor_min = max(0, medium_min - floor_jitter)  # tightest the floor can scallop

        results: dict[str, tuple[int, int]] = {}
        for tier in ("easy", "medium", "hard"):
            server.send(f"locate biome #caero_rings:tier_{tier}")
            m = server.wait_for(re.compile(LOCATE_RE.pattern + r"|" + LOCATE_FAIL_RE.pattern), timeout=30)
            if m is None:
                failures.append(f"/locate biome #caero_rings:tier_{tier} produced no reply in 30s")
                continue
            line = m.group(0)
            ok = LOCATE_RE.search(line)
            if not ok:
                fail = LOCATE_FAIL_RE.search(line)
                tier_name = fail.group(1) if fail else tier
                failures.append(f"/locate biome #caero_rings:tier_{tier_name} returned 'could not find'")
                continue
            t, x, z = ok.group(1), int(ok.group(2)), int(ok.group(3))
            r = int(math.hypot(x, z))
            results[t] = (x, z, r)
            print(f"[test] nearest tier_{t}: ({x:>5}, {z:>5})  r={r}")

        if "easy" in results:
            _, _, r = results["easy"]
            if r > medium_min:
                failures.append(f"nearest easy biome at r={r} is past medium_min_radius={medium_min} — easy core failed")
        if "hard" in results:
            _, _, r = results["hard"]
            if r < hard_floor_min:
                failures.append(f"nearest hard biome at r={r} is inside the hardest possible floor "
                                f"({hard_floor_min}) — hard tier leaked into easy core")
    finally:
        print("\n[shutdown] /stop")
        server.stop()

    print()
    print("─" * 70)
    if not failures:
        print(" RESULT: PASS — all in-game checks succeeded")
        print("─" * 70)
        sys.exit(0)
    print(f" RESULT: FAIL — {len(failures)} check(s) failed:")
    for f in failures:
        print(f"   • {f}")
    print("─" * 70)
    sys.exit(1)


if __name__ == "__main__":
    try: main()
    except KeyboardInterrupt: sys.exit(130)
