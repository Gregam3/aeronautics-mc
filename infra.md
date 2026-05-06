# Infrastructure — Create Aeronautics Server

**Last updated:** 2026-04-26
**Status:** **Provisioned 2026-04-26.** Hetzner Cloud **cpx42** (x86 AMD shared,
16 GB / 8 vCPU / 320 GB NVMe, ~€25.49/mo net) in **NBG1** (Nuremberg). Public IP
**46.225.17.145**. Server is bootstrapped (Debian 12 + Temurin JDK 21 + ufw +
fail2ban) but **idle** — no Minecraft jar deployed yet, awaiting glue-mod work
per Greg's sequencing.

> **Lineup change vs. earlier plan:** The original target was cx42 (~€12.49/mo).
> Hetzner retired that line; closest like-for-like today is `cx43` (Intel shared,
> same price). We picked `cpx42` (AMD shared, newer gen) instead — better
> single-thread under Create Aeronautics physics load, plus 2× the disk (320 GB
> vs 160 GB) for ~€13/mo more. FSN1 was at capacity at provision time, so we
> took NBG1 (same EU-central network zone, equivalent latency).

> This doc is for hosting + ops. Mod choices live in `mods.md`; gameplay design in
> `PLAN.md`.

---

## 1. Target profile

- **Loader:** NeoForge 1.21.1.
- **Players:** up to 10 concurrent.
- **Server type:** dedicated modded Java server (NeoForge only, not Paper/Spigot).
- **Ops style:** Greg wants shell/SSH access; comfortable running a VPS.

---

## 2. Workload characteristics

| Load source | Impact | Notes |
|---|---|---|
| Create Aeronautics | High main-thread CPU | Physics sim of moving contraptions. Scales with number + size of active aircraft. |
| Distant Horizons (server LODs) | High RAM + disk | DH stores LOD data server-side; file size grows with explored world. |
| Terralith | Moderate chunk-gen CPU + disk | Amortized per chunk. |
| Cataclysm + Mowzie + BiC | Moderate tick CPU | More entities with complex AI. |
| Simple Voice Chat | Low-moderate network | ~20–40 KB/s per speaker, Opus-encoded. |
| 10 players | Moderate | Player ticking, view-distance chunk loading. |

Dominant resource: **RAM** (JVM heap + DH + loaded chunks), then **single-thread CPU**
(Minecraft ticks are largely single-threaded).

---

## 3. Resource targets

- **JVM heap:** 10–12 GB.
- **OS + overhead:** 2–4 GB.
- **Total RAM:** **12–16 GB** (16 GB preferred).
- **CPU:** 4 vCPU minimum, 8 vCPU comfortable. High single-thread clock matters more
  than core count.
- **Disk:** 100 GB+ NVMe/SSD. DH LODs can be several GB; allow room for world +
  backups + mod updates.
- **Network:** trivial for 10 players — any reasonable VPS handles it.

---

## 4. Hosting provider comparison

All prices approximate, **verified 2026-04-26 against provider public pricing pages**.
The spec target below is **16 GB RAM / 8 vCPU / ≥160 GB SSD**. Hetzner reshuffled
its lineup since 2026-04: the old `cx42` is retired; current EU-central options
at this tier are below.

| Provider / plan | Monthly (net) | CPU | Disk | Shell access | Notes |
|---|---|---|---|---|---|
| **Hetzner cx43** (Intel shared) | ~€11.99 | 8 c | 160 GB | Full root | Direct successor to old cx42. Cheapest option. |
| **Hetzner cpx42** (AMD shared, Gen 2) — **PICKED** | ~€25.49 | 8 c | **320 GB** | Full root | Newer-gen AMD, better single-thread for Create Aeronautics physics. Doubled disk for DH LODs. |
| **Hetzner ccx23** (AMD dedicated) | ~€31.49 | 4 c (ded.) | 160 GB | Full root | Upgrade path if cpx42 single-thread isn't enough. Fewer cores but no noisy neighbours. |
| **Hetzner cax31** (ARM Ampere) | ~€16 (est.) | 8 c | 160 GB | Full root | ARM — confirm all mods load under aarch64 JVM. |
| **Dedicated MC hosts** (BisectHosting, Shockbyte, Apex) | ~$30–50 | varies | varies | Limited (panel + FTP) | Zero ops. Less flexibility. |
| **DigitalOcean / Vultr / Linode** (16 GB droplet) | ~$80–96 | 8 c | varies | Full root | ~3× Hetzner. |
| **AWS EC2 m7i.xlarge** | ~$145 on-demand | 4 c | EBS | Full root | Overpriced for a single MC box. |
| **Self-host on a spare PC** | $0 hardware | varies | varies | Full | Port-forward + DDNS. Electricity cost depends on rig. |

### Recommendation

**Picked: Hetzner cpx42, NBG1** (locked + provisioned 2026-04-26). The
single-thread headroom over `cx43` (Intel shared) is the relevant lever for
Create Aeronautics physics; `ccx23` (dedicated) is one `hcloud server change-type`
away if cpx42 isn't enough.

**"Zero ops" alternative:** BisectHosting Premium 16 GB, ~$40/mo. Worth the 3×
premium if Greg doesn't want to do OS-level admin. Supports custom NeoForge jars.

**AWS EC2 is no longer the default.** Keeping it in the table for completeness
only.

---

## 5. Storage layout (cpx42, 320 GB NVMe)

Single root partition, no separate volume. Directory tree under `/srv/minecraft`,
all owned by the `minecraft` system user with mode 0750:

- `/` (root + OS): ~3 GB used after bootstrap
- `/srv/minecraft/server` — server jar, mods, config
- `/srv/minecraft/world` — world data + DH LODs (expect 10–30 GB actual use)
- `/srv/minecraft/backups` — local hot backups before push to offsite

The doubled disk (vs the old 160 GB target) means DH LODs + world + 14 days of
local backups all comfortably fit on root; no need for a separate Hetzner Volume
unless we later want detachable persistence.

- **Backups:** weekly tarball pushed to Hetzner Storage Box (cheap, same network)
  or S3 Glacier IR. Retention ~14 days. **Not yet configured.**

---

## 6. Networking / access

- **Minecraft:** 25565 TCP, open to the internet (or whitelisted CIDRs if we want it
  invite-only; the in-game whitelist is easier).
- **Simple Voice Chat:** 24454 UDP, open to internet.
- **SSH:** 22 TCP, restricted to Greg's IP or a small allowlist. No root login;
  key-based auth only.
- **HTTP wiki:** 80 TCP, open to internet. Caddy serves a static MkDocs site
  (`wiki/`) at `http://46.225.17.145/`. HTTP-only — no domain, no TLS. See
  `runbook.md §12`.

---

## 7. World limits + pre-generation

- **World border:** enforced via vanilla `/worldborder center 0 0` +
  `/worldborder set 5000` (5000×5000 square centered on origin). Damage outside,
  players physically can't cross.
- **Chunk pre-gen:** no mature Modrinth pre-gen mod exists for NeoForge 1.21.1
  (Chunky is Bukkit-only). Three options:
  1. Run a CurseForge pre-gen mod if one exists (needs verification).
  2. Script a pre-gen pass using `/forceload add` across a grid (slow, manual).
  3. Skip pre-gen and let chunks generate on-the-fly during play. For a
     5000×5000 world this is annoying but not fatal.
- See PLAN.md §5 for the map-border decision log.

---

## 8. OS + runtime

- **OS:** Debian 12 or Ubuntu 24.04 (both free, well-supported on Hetzner).
- **JVM:** Eclipse Temurin JDK 21 (NeoForge 1.21.1 requires Java 21).
- **Service manager:** systemd unit under a dedicated `minecraft` user. Auto-restart
  on crash, auto-start on boot.
- **Console attach:** run the server inside `tmux` so ops can attach via SSH.
- **Firewall:** `ufw` + SSH key-only.

---

## 9. Open questions for Greg

1. ~~**Provider pick**~~ — **Locked 2026-04-20, provisioned 2026-04-26: Hetzner cpx42, NBG1.**
2. ~~**Region**~~ — **NBG1 (Nuremberg).** FSN1 was at capacity at provision time;
   NBG1 is the same EU-central network zone. Revisit if Beth/Corey play from US.
3. ~~**Access model**~~ — **Decided 2026-04-26: SSH key-only auth, port 22 open
   to internet; root login disabled, password auth disabled.** MC port (25565)
   open to internet; relying on the in-game whitelist for access control.
4. **Backup tooling** — Hetzner Storage Box (cheap, same-network), or S3 Glacier?
   Not yet configured.
5. **Domain name** — any preferred hostname for the server's DNS A record?
   Currently reachable only via IP `46.225.17.145`.

---

## 10. Provisioning

- **Tool:** `hcloud` CLI (1.63+), installed at `~/.local/bin/hcloud`. Token in
  `~/.config/hcloud/cli.toml` (mode 0600, **never committed**).
- **Cloud-init:** `infra/cloud-init.yaml` — first-boot bootstrap (users, JDK 21,
  ufw, fail2ban, /srv/minecraft layout, SSH hardening). Tracked in repo.
- **Recreate command:**
  ```
  hcloud server create --name aeronautics-mc --type cpx42 --image debian-12 \
    --location nbg1 --ssh-key greg-omen \
    --user-data-from-file infra/cloud-init.yaml
  ```
- **SSH:** `ssh greg@46.225.17.145`.

## 11. What this doc doesn't cover yet

- Terraform conversion (cloud-init covers the box; Terraform only useful if we
  manage multiple resources).
- Mod-update workflow (`rsync` + restart script).
- Monitoring (`node_exporter` + a small Grafana somewhere).
- DNS (A record at whatever registrar → server IP).
- Backup automation (decision pending — Storage Box vs S3 Glacier).
