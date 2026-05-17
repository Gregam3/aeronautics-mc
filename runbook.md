# Runbook — Aeronautics MC Server

**Last updated:** 2026-04-26

Operational reference for the live server. Design rationale lives in `infra.md`.

---

## 1. Quick reference

| | |
|---|---|
| **Public IP** | `46.225.17.145` |
| **Minecraft port** | `25565/tcp` |
| **Voice chat port** | `24454/udp` |
| **Region** | Hetzner NBG1 (Nuremberg) |
| **Spec** | `cpx42` — 8c AMD shared / 16 GB / 320 GB / Debian 12 |
| **JDK** | Temurin 21.0.11 (Java 21 LTS) |
| **NeoForge / MC** | 21.1.227 / 1.21.1 |
| **Heap** | 12 GB (Aikar's G1 flags) |
| **World** | `server-world` (Beth/Corey's canonical build) |
| **World spawn** | `-136, 73, -162` |
| **Whitelist** | Off (in-game whitelist disabled 2026-04-26) |
| **Hetzner Console** | https://console.hetzner.cloud |

---

## 2. SSH access

```sh
ssh greg@46.225.17.145
```

Key-only auth (`~/.ssh/id_ed25519`). No password auth, no root login.

`greg` has nopasswd sudo and is a member of the `minecraft` group, so reads of `/srv/minecraft/**` are direct; writes outside `staging/` need sudo. To act as the service user:

```sh
sudo -u minecraft <command>
```

---

## 3. Server console (tmux)

The MC server runs in a detached tmux session called `mc` under the `minecraft` user. Attach to read live console output and type commands:

```sh
sudo -u minecraft tmux attach -t mc
```

Detach with `Ctrl-B` then `D`. **Don't `Ctrl-C`** — that would SIGINT the JVM.

Send a one-off command without attaching:

```sh
sudo -u minecraft tmux send-keys -t mc 'op SomePlayer' Enter
```

---

## 4. Service management

The server runs under systemd as `minecraft.service` (unit at `/etc/systemd/system/minecraft.service` — `Type=forking`, `KillMode=none` so a SIGTERM doesn't kill the JVM mid-save).

```sh
sudo systemctl status minecraft       # is it running?
sudo systemctl start minecraft
sudo systemctl stop minecraft         # graceful: sends 'stop' via tmux, waits up to 180s
sudo systemctl restart minecraft
journalctl -u minecraft -f            # live systemd-side logs
```

Auto-restart on failure: yes (`Restart=on-failure`, 30s backoff). Auto-start on boot: yes.

---

## 5. File layout

```
/srv/minecraft/
├── server/                              # NeoForge install + run.sh + libraries
│   ├── mods/                            # 39 mod jars
│   ├── config/                          # mod configs (xaero/, voicechat/, etc.)
│   ├── server-world/                    # the live world (level-name in server.properties)
│   │   ├── data/
│   │   │   └── DistantHorizons.sqlite   # 2.8 GB LOD data
│   │   └── datapacks/
│   │       └── spawn_safe/              # kills hostiles within 100b of -136 73 -162
│   ├── eula.txt
│   ├── server.properties
│   ├── user_jvm_args.txt                # 12G heap + Aikar's G1 flags
│   ├── run.sh                           # NeoForge launch script
│   └── logs/latest.log                  # live MC log
├── staging/                             # rsync drop zone (greg-owned)
└── backups/                             # placeholder; backup automation TBD
```

---

## 6. Common ops

### Op a player
```sh
sudo -u minecraft tmux send-keys -t mc 'op PlayerName' Enter
```

### Toggle whitelist on
Edit `/srv/minecraft/server/server.properties` (`white-list=true`, `enforce-whitelist=true`) → `sudo systemctl restart minecraft`. Or run `whitelist on` via tmux to enable live, then `whitelist add <name>` per player.

### Set world spawn
```sh
sudo -u minecraft tmux send-keys -t mc 'setworldspawn -136 73 -162' Enter
```

### Tail server log
```sh
sudo tail -f /srv/minecraft/server/logs/latest.log
```

### Check TPS / lag
```sh
sudo -u minecraft tmux send-keys -t mc 'neoforge tps' Enter
sleep 1 && sudo tail -10 /srv/minecraft/server/logs/latest.log
```
Healthy: 20.0 TPS, ms/tick under 50. See §13 for tuned values + diagnostic procedure if it ever drops.

### Profile a slow tick (Spark)
```sh
sudo -u minecraft tmux send-keys -t mc 'spark profiler --thread "Server thread" --timeout 30' Enter
# 30 sec later, log gets a https://spark.lucko.me/<id> URL — open in a browser for the breakdown
sudo grep 'spark.lucko.me/' /srv/minecraft/server/logs/latest.log | tail -1
```

### Kill all entities of a type
```sh
sudo -u minecraft tmux send-keys -t mc 'kill @e[type=zombified_piglin]' Enter
```

### Search for real errors
```sh
sudo grep -E 'ERROR|FATAL|Exception' /srv/minecraft/server/logs/latest.log | grep -v RuntimeDistCleaner
```
The `RuntimeDistCleaner: Attempted to load class .../client/...` ERRORs are NeoForge correctly blocking client-only classes (Sodium, Iris, Xaero's) on the dedicated server — benign, despite the severity tag.

---

## 7. Updating mods

From your local PrismLauncher instance:

```sh
INST=~/.local/share/PrismLauncher/instances/1.21.1/minecraft
rsync -av --exclude '*.disabled' -e ssh "$INST/mods/" greg@46.225.17.145:/srv/minecraft/staging/mods/
ssh greg@46.225.17.145 '
  sudo systemctl stop minecraft &&
  sudo rsync -a --delete /srv/minecraft/staging/mods/ /srv/minecraft/server/mods/ &&
  sudo chown -R minecraft:minecraft /srv/minecraft/server/mods &&
  sudo systemctl start minecraft
'
```

Single-jar add: drop into `staging/`, then `sudo mv` it into `/srv/minecraft/server/mods/`, `chown` to `minecraft:minecraft`, restart.

Beth and Corey's clients also need the matching jar — see §10.

---

## 8. Updating configs / datapacks

### Mod config (requires restart, *except* where noted below)
Edit on server: `sudo -u minecraft nano /srv/minecraft/server/config/<mod>/...` → `sudo systemctl restart minecraft`.

### `caero_specialization` — XP tuning hot-reloads (no restart)

The XP-related keys in `serverconfig/caero_specialization-common.toml` are
hot-reloaded by NeoForge's `ModConfigEvent.Reloading` listener — edit the
file and the next refine picks up the new values. No server restart, no
`/reload`, no kick.

```toml
[skill]
  xpPerRefine = 50              # global default, used unless a per-skill override is set
  xpCurveCoefficient = 100      # xpForLevel(L) = COEFFICIENT × (L-1)²; lower = faster levelling
  maxLevel = 100                # cap on reported skill level
  [skill.xpPerRefine]
  forestry  = -1                # -1 = inherit the global default above
  mining    = -1
  armourer  = -1
  husbandry = -1
  alchemist = -1
  jewelery  = -1
```

Caveats:
- Stored XP is **preserved** across `xpCurveCoefficient` changes — only
  the level computed from it shifts. Halving the coefficient mid-game
  bumps everyone's reported level upward without granting them any XP.
- Per-refine XP changes are forward-only — past refines aren't retro'd.
- `[skill]` section also still has `mediumLevelGate` / `highLevelGate`
  (legacy v1.0 keys, no longer read — safe to ignore).
- Other keys in this same file (burn-time ladder, refiner fees, ash rates)
  also hot-reload; they're read via `IntValue.get()` at the call site
  every time. The XP listener path exists only for `SkillMath`'s curve
  params, which are read from places outside the refine hot path.

Server-side config file lives at:
`/srv/minecraft/server/server-world/serverconfig/caero_specialization-common.toml`
(per-world; `defaultconfigs/` ships fresh-world defaults).

### Server.properties (requires restart)
Edit, then restart.

### Datapacks (no restart needed)
Edit/add files under `/srv/minecraft/server/server-world/datapacks/<pack>/`, then:
```sh
sudo -u minecraft tmux send-keys -t mc 'reload' Enter
```

**Live datapacks:**
- **`encumbered_weights`** (enabled) — overrides Encumbered's bundled `item_weights.json` (everything-at-1.0 stub) with calibrated per-item weights from `glue/ring-biomes/.../item_weights.json`. Stone-tier ~1.0, iron-tier ~1.5, storage blocks ~3.0, anvil ~5.0, light items 0.01–0.05. Datapack overlay wins because it loads after mod data.
- **`spawn_safe`** (currently disabled) — kills nearby vanilla hostiles each tick within 100 blocks of `-136, 73, -162`. Re-enable with `/datapack enable "file/spawn_safe"`.
- **`no_overworld_piglins`** (currently disabled) — kills any piglin/piglin_brute/zombified_piglin in the overworld each tick (so portal-crossings die before zombifying). Re-enable with `/datapack enable "file/no_overworld_piglins"`.

---

## 9. Hetzner Cloud control

CLI installed at `~/.local/bin/hcloud` (1.63+). Token in `~/.config/hcloud/cli.toml` (mode 0600, **never committed to git**).

```sh
hcloud server list                              # status overview
hcloud server describe aeronautics-mc           # specs, IPs, image
hcloud server reboot aeronautics-mc             # graceful reboot via ACPI
hcloud server poweroff aeronautics-mc           # immediate power off
hcloud server poweron aeronautics-mc
hcloud server change-type aeronautics-mc ccx23  # vertical scale; ~30s downtime
hcloud server delete aeronautics-mc             # ⚠️ destroys VM + disk
```

Web console for billing, snapshots, console attach: https://console.hetzner.cloud

---

## 10. Client-side mod sync

Every player needs the same `mods/` jars on their PrismLauncher 1.21.1 instance, plus client-side configs (Xaero's "don't show mobs", voicechat client prefs, etc.). Server-side configs only affect the server.

**Quickest path right now:** zip your local `~/.local/share/PrismLauncher/instances/1.21.1/minecraft/{mods,config}` and share. A clean modpack zip is on the TODO list.

---

## 11. Recreate from scratch

The box is reproducible from this repo if it's ever lost:

```sh
cd ~/projects/aeronautics-mc
# 1. SSH key (one-time, only if the project is empty)
hcloud ssh-key create --name greg-omen --public-key-from-file ~/.ssh/id_ed25519.pub
# 2. Server with cloud-init bootstrap
hcloud server create \
  --name aeronautics-mc \
  --type cpx42 \
  --image debian-12 \
  --location nbg1 \
  --ssh-key greg-omen \
  --user-data-from-file infra/cloud-init.yaml
```

`infra/cloud-init.yaml` lays down: `greg` user (sudo, key auth), `minecraft` system user, Temurin JDK 21 (via Adoptium repo), `tmux`, `ufw` (22/25565/24454), `fail2ban` (systemd backend), `/srv/minecraft/{server,world,backups}` layout, SSH hardening (no root, no password).

Post-provision (manual once per recreation):
1. SSH in, install NeoForge: `cd /srv/minecraft/server && curl -fsSL -o n.jar https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.227/neoforge-21.1.227-installer.jar && java -jar n.jar --installServer .`
2. rsync `mods/`, `config/`, `server-world/`, `DistantHorizons.sqlite` from your local instance.
3. Drop in `eula.txt`, `user_jvm_args.txt`, `server.properties` (regenerable; see git history).
4. Install systemd unit (also in git history of this repo).
5. `sudo systemctl enable --now minecraft`.

---

## 12. Public wiki (Caddy)

A static MkDocs Material site is served on `http://46.225.17.145/` (HTTP only, no
domain). Source markdown in `wiki/docs/`, build output in `wiki/site/`.

| | |
|---|---|
| **Webserver** | Caddy 2.11 from the official Cloudsmith apt repo |
| **Config** | `/etc/caddy/Caddyfile` (single `:80` block, gzip+zstd, no access log) |
| **Webroot** | `/var/www/aero-wiki` (owned by `caddy:caddy`) |
| **Service** | `sudo systemctl {status,restart} caddy` — logs via `journalctl -u caddy` |
| **Firewall** | `ufw` rule `80/tcp ALLOW IN` (added 2026-05-04) |

### Update the wiki

From the repo root:

```sh
./wiki/deploy.sh
```

That builds with the project's venv (`wiki/.venv`), `rsync`s `site/` to
`/tmp/aero-wiki-staging` on the server, sudo-syncs into `/var/www/aero-wiki`,
chowns to `caddy`, and curls the homepage to confirm 200. No Caddy restart
needed — Caddy serves static files, picks up new ones on next request.

### First-time setup (already done — kept here for recreate-from-scratch)

```sh
# Caddy install
curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/gpg.key \
  | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt \
  | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt-get update && sudo apt-get install -y caddy

# Webroot + firewall
sudo mkdir -p /var/www/aero-wiki && sudo chown caddy:caddy /var/www/aero-wiki
sudo ufw allow 80/tcp comment "HTTP wiki"

# Caddyfile (HTTP-only, no TLS since no domain)
sudo tee /etc/caddy/Caddyfile <<'EOF'
:80 {
	root * /var/www/aero-wiki
	file_server
	encode gzip zstd
}
EOF
sudo systemctl restart caddy
```

Local venv to rebuild without touching the server:

```sh
cd wiki && python3 -m venv .venv && .venv/bin/pip install mkdocs-material
.venv/bin/mkdocs serve   # http://127.0.0.1:8000 for preview
```

---

## 13. Open ops items

- **Backups** — not configured. Plan: weekly tar of `server-world/` to a Hetzner Storage Box, 14-day retention.
- **DNS** — server reachable by IP only. Optional: A record at a registrar.
- **Monitoring** — no metrics yet.
- **Client-side modpack zip** — Beth/Corey still need to mirror mods+config manually.
- **Modded hostiles in `spawn_safe`** — only vanilla enumerated. Add Born in Chaos / Hybrid Aquatic hostile entity IDs as they appear at spawn.
- **Token rotation** — the Hetzner API token used to provision was pasted in chat history; rotate via console when convenient.

---

## 14. Performance tuning (state as of 2026-04-26)

The server is running with the following knobs deliberately tuned away from defaults to fit a 6–10 player heavy-modded NeoForge server on a single shared-CPU thread (cpx42). Each value here is a pillar of current TPS — revert at your own risk.

### Tunable knobs (current values)

| Knob | File | Default | Tuned to | Why |
|---|---|---|---|---|
| `simulation-distance` | `server.properties` | 10 | **4** | Cuts active-chunk count ~5×. Mobs/redstone tick within 64 blocks of each player instead of 160. Players don't notice (you can't see mob AI 100 blocks away anyway). |
| `view-distance` | `server.properties` | 10 | **8** | Visual range. DH fills in beyond. |
| `randomTickSpeed` | gamerule (live) | 3 | **2** | Slower crop growth, fire spread, leaf decay. Saves ~15–20% on random-tick load. Mostly invisible at our pace. |
| `playersSleepingPercentage` | gamerule (live) | 100 | **45** | <50% of online players need to be in beds to skip night, instead of all. Quality-of-life — at 6–10 players, getting everyone to sleep simultaneously is impractical. Persists in `level.dat`; set with `/gamerule playersSleepingPercentage 45`. |
| TAN `near_heat_cool_proximity` | `config/toughasnails/temperature.toml` | 8 | **4** | TAN flood-fills outward from each player to compute temperature. Volume scales with r³ — cutting from 8→4 is ~8× less work per player. |
| `nutrient_death_loss` | `config/nutritionalbalance-server.toml` | 10.0 | **200.0** | On death, every nutrient is docked by this much, floored at `nutrient_initial` (50). Bumping to 200 guarantees nutrients always bottom at 50 (= "low" zone, below the 100 target band) regardless of pre-death value. Pairs with `caero_rings.DeathPreserve` (hunger + thirst capped at 3 on respawn) so death uniformly leaves players weakened across all survival stats. |

### Mods removed for perf reasons

| Mod | When | Why |
|---|---|---|
| **Radioactive** | 2026-04-26 | Mcreator-generated `BlockRadiationProcedure.onEntityTick` ran a block-state lookup for every entity in the world every tick. Recovered ~50% of tick budget. Jar at `mods/radioactive-3.8.0-...jar.disabled`. |
| **Alex's Mobs** | 2026-04-26 | Showed up as a 16× outlier in spark profile vs all other mods combined. Heavy AI/pathfinding for many ambient creatures (cockroaches, mantis, raccoons). Required client + server, so removed from both. Jar at `mods/alexsmobs-1.22.17.jar.disabled`. |

### Mods added for perf

| Mod | What |
|---|---|
| **Spark** | In-game profiler. `/spark profiler --thread "Server thread" --timeout 30` → web report URL. Both-side optional, server-only install. |
| **ModernFix** | Memory-leak fixes + lazy-loading + small tick wins. Both-side optional, server-only install. |
| ~~Radium~~ | **Tried, blocked by Create's mods.toml hard incompatibility declaration.** Jar at `mods/radium-mc1.21.1-...jar.disabled`. Don't re-enable without disabling Create. |

### Diagnostic procedure (when lag returns)

If TPS drops below 18 sustained:

1. **Quick TPS sample** — `neoforge tps` via tmux. Confirms whether load is actually high or just transient.
2. **`/spark profiler --thread "Server thread" --timeout 30`** in chat — produces a `spark.lucko.me/<id>` URL with a flame graph + percentage breakdown of where Server thread time went. The first place to look — almost always reveals the offending mod or vanilla path.
3. **If spark blames a single mod** (>20% of time on one mod's classes): tune the mod's config or disable it. (Pattern from Radioactive + alexsmobs.)
4. **If spark shows distributed load across vanilla paths** (`PalettedContainer.get`, `LevelChunk.tickChunk`, `FluidState.isRandomlyTicking`): bottleneck is chunk-tick volume — reduce `simulation-distance` further, or `randomTickSpeed`, or look for entity buildup.
5. **Server thread stack samples** (fallback if Spark misbehaves):
   ```
   PID=$(pgrep -u minecraft -f 'java.*neoforge' | head -1)
   for i in $(seq 1 10); do sudo -u minecraft jstack $PID > /tmp/jst-$i.txt; sleep 0.4; done
   for f in /tmp/jst-*.txt; do awk -v RS='\n\n' '/"Server thread" /' "$f" | sed -n '3,6p'; done | sed 's/(.*//' | sort | uniq -c | sort -rn | head -15
   ```
   JDK was installed (`apt install temurin-21-jdk`) precisely to enable jstack — JRE alone doesn't ship it.

### Reference: before/after numbers (2026-04-26 perf-tuning session)

| Metric | Pre-tuning | Post-tuning |
|---|---|---|
| Overworld TPS (8 players) | ~13 | **20.0** |
| Overworld ms/tick | 90–120 | **32** |
| "Can't keep up" warnings / 5 min | 75+ | **0–1** transient |
