# Infrastructure — Create Aeronautics Server

**Last updated:** 2026-04-20
**Status:** Provider locked — **Hetzner Cloud CX42** (x86, 16 GB / 8 vCPU / 160 GB NVMe,
~€12.49/mo). Region defaulted to Falkenstein (FSN). Not yet provisioned; glue-mod
work comes first per Greg's sequencing.

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

All prices approximate, **verified 2026-04 against provider public pricing pages**.
The spec target below is **16 GB RAM / 8 vCPU / ~160 GB SSD**.

| Provider / plan | Monthly | Shell access | Notes |
|---|---|---|---|
| **Hetzner Cloud CX42** (x86 AMD) | ~**€12.49 / $14** | Full root | Excellent price/perf. EU (FSN/HEL) + US (ASH/HIL). Hourly billing. |
| **Hetzner Cloud CAX41** (ARM Ampere) | ~€12.49 / $14 | Full root | ARM — confirm all mods load under aarch64 JVM. Same price as x86. |
| **Oracle Cloud Free Tier** (ARM A1) | **$0** (always-free) | Full root | 4 OCPU / 24 GB cap. Capacity often unavailable in the free tier; reliability varies. ARM. |
| **Dedicated MC hosts** (BisectHosting Premium 16GB, Shockbyte, Apex) | ~$30–50 | Limited (admin panel + FTP; sometimes sudo) | Zero ops. One-click mod uploads. Less flexibility. |
| **OVH VPS Elite** or similar | ~$30–50 | Full root | Midrange option; less cheap than Hetzner, more capable than MC hosts. |
| **DigitalOcean / Vultr / Linode** (16 GB droplet) | ~$80–96 | Full root | Same tier as AWS but cheaper. Still 6× Hetzner. |
| **AWS EC2 m7i.xlarge** | ~$145 on-demand | Full root | Ops tooling value doesn't apply to a single MC box. Overpriced. |
| **Self-host on a spare PC** | $0 hardware | Full | Port-forward + DDNS. Electricity cost depends on rig. |

### Recommendation

**Default: Hetzner Cloud CX42 (x86), ~$14/mo.** Full shell, reliable DC, hourly
billing (easy to tear down + re-launch), same resources as the $145 AWS box. If
players are concentrated in the UK/EU, use FSN or HEL; if mostly US, use ASH.

**"Zero ops" alternative:** BisectHosting Premium 16 GB, ~$40/mo. Worth the 3×
premium if Greg doesn't want to do OS-level admin. Supports custom NeoForge jars.

**AWS EC2 is no longer the default.** Keeping it in the table for completeness
only.

---

## 5. Storage layout (Hetzner CX42 assumed)

- **Root volume:** 160 GB NVMe that comes with the instance. Split:
  - `/` (root + OS): ~10 GB
  - `/srv/minecraft` (server + mods + config): ~20 GB
  - `/srv/minecraft/world` (world data + DH LODs): ~100 GB runway, expect 10–30 GB actual use
  - `/srv/minecraft/backups` (hot backups before S3 / offsite): ~20 GB
- **Backups:** weekly tarball pushed to Hetzner Storage Box (cheap) or S3 Glacier IR.
  Retention ~14 days.

---

## 6. Networking / access

- **Minecraft:** 25565 TCP, open to the internet (or whitelisted CIDRs if we want it
  invite-only; the in-game whitelist is easier).
- **Simple Voice Chat:** 24454 UDP, open to internet.
- **SSH:** 22 TCP, restricted to Greg's IP or a small allowlist. No root login;
  key-based auth only.
- **No inbound web traffic** unless we later want a status dashboard.

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

1. ~~**Provider pick**~~ — **Locked 2026-04-20: Hetzner CX42.**
2. **Region** — FSN default. Where are Beth and Corey physically? If mostly US,
   switch to ASH (Ashburn, VA). If mostly UK/EU, FSN or HEL (Helsinki) are fine.
3. **Access model** — open with in-game whitelist (easier), or SSH/MC restricted
   to a CIDR allowlist (more paranoid)?
4. **Backup tooling** — Hetzner Storage Box (cheap, same-network), or S3 Glacier?
5. **Domain name** — any preferred hostname for the server's DNS A record?

---

## 10. What this doc doesn't cover yet

- Terraform / provisioning scripts (will add once provider is locked).
- Mod-update workflow (`rsync` + restart script).
- Monitoring (`node_exporter` + a small Grafana somewhere).
- DNS (A record at whatever registrar → server IP).
