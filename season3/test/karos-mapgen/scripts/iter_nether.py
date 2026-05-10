#!/usr/bin/env python3
"""
Tight iteration loop for Karos Nether tuning.

Boots a fresh dedicated-server world with the current karos-datapack +
fork, force-loads a 5×5-chunk patch over the Nether bbox center, waits for
generation, then runs analyze_nether.py against the saved chunks.

Use: python3 iter_nether.py
Then iterate on generate-karos-datapack.py and re-run.
"""
from __future__ import annotations
import argparse
import os
import queue
import re
import shutil
import subprocess
import sys
import threading
import time
from pathlib import Path

DEFAULT_STAGING = Path("/tmp/aero-s3-karos-test")
DEFAULT_SEED = "12345"
NETHER_CTR_X = 0
NETHER_CTR_Z = 1900
PATCH_RADIUS_CHUNKS = 2  # 5×5 chunk patch (radius 2 → cx-2..cx+2)


class Server:
    def __init__(self, staging):
        self.staging = staging
        self.proc = None
        self.out = queue.Queue()
        self.log = []

    def start(self):
        run_sh = self.staging / "run.sh"
        env = os.environ.copy()
        env.setdefault("JAVA_TOOL_OPTIONS", "")
        self.proc = subprocess.Popen(
            ["bash", str(run_sh), "nogui"],
            cwd=str(self.staging),
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            bufsize=1, text=True, env=env,
        )
        threading.Thread(target=self._drain, daemon=True).start()

    def _drain(self):
        for line in self.proc.stdout:
            self.log.append(line)
            self.out.put(line)
        self.out.put("")

    def send(self, cmd):
        self.proc.stdin.write(cmd + "\n")
        self.proc.stdin.flush()

    def wait_for(self, pattern, timeout):
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                line = self.out.get(timeout=max(0.1, deadline - time.time()))
            except queue.Empty:
                return None
            if line == "":
                return None
            m = pattern.search(line)
            if m:
                return m
        return None

    def stop(self, timeout=60):
        try: self.send("stop")
        except Exception: pass
        try: self.proc.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            self.proc.terminate()
            try: self.proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                self.proc.kill()


def reset_world(staging, datapack_paths):
    world = staging / "world"
    if world.exists():
        shutil.rmtree(world)
    (world / "datapacks").mkdir(parents=True)
    for dp in datapack_paths:
        if dp.exists():
            shutil.copytree(dp, world / "datapacks" / dp.name)


def write_server_props(staging, seed):
    props = staging / "server.properties"
    lines = {
        "online-mode": "false", "level-seed": seed, "level-name": "world",
        "level-type": "minecraft:overworld", "max-players": "1",
        "spawn-protection": "0", "view-distance": "4",
        "simulation-distance": "4", "motd": "karos-iter",
        "enable-command-block": "true", "allow-cheats": "true",
    }
    existing = {}
    if props.exists():
        for line in props.read_text().splitlines():
            if "=" in line and not line.startswith("#"):
                k, v = line.split("=", 1)
                existing[k] = v
    existing.update(lines)
    props.write_text("\n".join(f"{k}={v}" for k, v in existing.items()) + "\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--staging", type=Path, default=DEFAULT_STAGING)
    ap.add_argument("--seed", default=DEFAULT_SEED)
    ap.add_argument("--gen-time", type=float, default=20)
    args = ap.parse_args()

    repo = Path(__file__).resolve().parents[4]
    datapacks = [
        repo / "season3" / "karos-datapack",
        repo / "season3" / "karos-terrain-overrides",
    ]

    print(f"[iter] resetting world at {args.staging}/world")
    reset_world(args.staging, datapacks)
    write_server_props(args.staging, args.seed)

    s = Server(args.staging)
    s.start()
    print("[iter] booting...")
    if not s.wait_for(re.compile(r'Done \([0-9.]+s\)'), 240):
        print("[iter] boot failed", file=sys.stderr)
        s.stop()
        sys.exit(1)
    print("[iter] booted, force-loading nether patch")
    cx0 = (NETHER_CTR_X >> 4) - PATCH_RADIUS_CHUNKS
    cx1 = (NETHER_CTR_X >> 4) + PATCH_RADIUS_CHUNKS
    cz0 = (NETHER_CTR_Z >> 4) - PATCH_RADIUS_CHUNKS
    cz1 = (NETHER_CTR_Z >> 4) + PATCH_RADIUS_CHUNKS
    s.send(f"forceload add {cx0*16} {cz0*16} {cx1*16+15} {cz1*16+15}")
    time.sleep(args.gen_time)
    print(f"[iter] waited {args.gen_time}s, stopping")
    s.stop()
    print("[iter] running analyze_nether.py\n")

    here = Path(__file__).parent
    subprocess.run([sys.executable, str(here / "analyze_nether.py")])


if __name__ == "__main__":
    main()
