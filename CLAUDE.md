# Project operating rules — Create Aeronautics Server

## Autonomy

**Default to maximum autonomy.** Greg is the chokepoint on testing, so minimize
round-trips:

- Research, verify, cross-check, and self-test without asking for confirmation.
- When a decision is reversible, make it and log it. When it's irreversible or
  user-visible (cost, data loss, public actions), check first.
- Install developer dependencies (Java 21, NeoForge MDK, etc.) without prompting.
- Clone upstream mod repos into `.research/repos/` to grep APIs directly rather
  than relying on web-fetched docs.

## Verify the target mod is actually installed — FIRST, ALWAYS

**Before writing a single line of config, JSON, or Kotlin that references another
mod's entity/block/item IDs, verify that mod is in the running PrismLauncher
instance's `mods/` folder.** Not the plan, not `mods.md`, not what was discussed
last session — the *actual* `ls` of:

```
~/.local/share/PrismLauncher/instances/1.21.1/minecraft/mods/
```

`mods.md` documents intent; the instance folder documents reality. They drift.
Working against the plan instead of reality wastes entire sessions — biome
modifiers referencing entity IDs that don't exist silently do nothing, and
`BuiltInRegistries.ENTITY_TYPE.containsKey` guards swallow the failure invisibly.
There is no compile-time error, no runtime error, just spawns that never happen.

If the mod isn't installed:
1. Stop. Do not build config for it.
2. Tell Greg what's missing and ask: install it, or pivot to something that is
   installed?
3. Only after confirmation: install the jar (it's probably in `.research/` or
   can be fetched from Modrinth) and proceed.

This rule supersedes "default to maximum autonomy" — installing a new mod into
the instance is a state change Greg should sign off on.

## Deploy after every change — no batching

**After any change Greg might want to test in-game, run
`./glue/<mod>/deploy.sh` immediately and confirm in the message.** This includes
config tweaks (`config.json`), Kotlin/Java edits, mixin changes, and
resource-file changes — anything that affects runtime behaviour.

Don't batch multiple changes into one deploy "to save time," and don't hand off
with `./gradlew build` only. The deploy step copies the new jar into the
running PrismLauncher instance, but Minecraft only loads jars at process start
— so Greg restarting Minecraft *before* the deploy lands wastes the test.
Always deploy first, then tell Greg the build is ready to test.

If a change isn't safe to deploy yet (e.g., known crash, mid-refactor), say so
explicitly and wait. Never silently leave the deployed jar stale relative to
what the conversation says is current.

## Log every runtime-affecting change to `things-to-test.md`

After every change Greg might want to test in-game (anything that lands a new
jar via `deploy.sh`, datapack edit, config tweak, etc.), **append** an entry
to `things-to-test.md` at the repo root. Greg deletes entries manually after
testing — Claude only ever appends, never edits or removes existing lines.

Format: `- <YYYY-MM-DD> <mod-name> — <what changed> → <what to verify>`.
Keep each entry to one or two lines. The "what to verify" half should be
concrete enough that Greg can act on it without re-reading the diff.

This is not optional — if a change is worth deploying, it's worth logging.

## Self-testing (before declaring a task done)

The project is a modded Minecraft server. Verify as much as possible without
requiring Greg to launch a client:

| Layer | How I verify |
|---|---|
| Code compiles | `./gradlew build` passes. |
| API surface is real | Grep the cloned upstream source in `.research/repos/`; don't hallucinate method signatures. |
| Mod loads | `./gradlew runGameTestServer` boots headlessly with the mod registered. |
| Logic behaves | **NeoForge GameTest** (`@GameTest`) — headless in-world scenarios that place blocks, insert items, assert state changes. This is the primary integration-test tool. |
| Pure logic | Plain JUnit for anything that doesn't touch Minecraft state. |
| Whole mod stack boots + worldgen behaves | **Headless dedicated-server harness** — boot a NeoForge dedicated server with the mods + configs symlinked from the Prism instance, drive it via stdin, scrape stdout for assertions. Templates: `glue/ring-biomes/test/ore-density/` (per-tier ore ratios, parses region NBT) and `glue/ring-biomes/test/biome-tiers/` (codec / tag binding / `/locate biome` per tier). Pattern is reusable for any "did the worldgen JSON take effect" question. |
| Mod compatibility with the real stack (visual quality, perf under load) | Only Greg can test this on the provisioned server. Flag it explicitly when that's the gate. |

If something can't be self-verified, say so plainly in the status report instead of
claiming success.

### Dedicated-server harness pattern

When a change touches dimension/biome/feature JSON and JUnit can't reach the
runtime — i.e. the failure mode is "the codec parsed, but did the right
data flow into the world?" — write a harness like the two above:

1. Reuse `glue/ring-biomes/test/ore-density/scripts/bootstrap-server.sh` to
   install a NeoForge dedicated server into `/tmp/aero-audit-server`. Idempotent.
2. Symlink mods + copy configs from `~/.local/share/PrismLauncher/instances/1.21.1/minecraft`
   so the test runs against the same stack Greg plays. Filter client-only
   mods (iris, sodium, xaero, ...).
3. Drive the server via `subprocess.Popen` with `stdin=PIPE`. **Drain stdout
   on a daemon thread into a `queue.Queue`** rather than blocking on
   `readline()` — a heavily-modded server falls behind under load, goes
   silent for tens of seconds, and naive `readline()` loops will hang
   forever. The threaded-queue pattern is in `biome-tiers/scripts/verify.py`.
4. Prefer probes that don't require chunk pre-generation (`/locate biome`
   queries the biome source directly across the world; `/execute if biome`
   at spawn works without `/forceload` since spawn chunks auto-load). Pregen
   only what you actually need to NBT-parse afterwards (ore-density model).
5. Tag every command's expected output with a unique marker (`run say
   BIOMETIER_<id>_<tier>` etc.) so the parser can grep matches out of
   the noise without false positives from mod log lines.

## Documentation discipline

The repo splits docs into **internal** (design / ops, never user-facing) and
**player-facing** (the wiki, the single source of truth for players). Keep
both consistent when decisions change.

### Internal docs

- `PLAN.md` — design source of truth. Versioned; bump on every material decision.
- `heuristics.md` — the 4 design pillars. **Check every proposal against it.**
- `mods.md` — formal mod list with Modrinth links + verified 1.21.1 NeoForge versions.
- `infra.md` — hosting, sizing, provisioning (design rationale + how the box was built).
- `runbook.md` — live-server ops reference: SSH, tmux, systemd, common commands, troubleshooting, recreate-from-scratch. Read this first when operating the running server.
- `CLAUDE.md` (this file) — operating rules that survive across sessions.

Per-component plans live alongside their code (e.g., `glue/<mod>/PLAN.md`).

### The wiki — single canonical source for player-facing docs

`wiki/` builds an MkDocs Material site served at `http://46.225.17.145/` via
Caddy (setup: `runbook.md §12`). **Anything a player might see, do, ask, or
wonder about — items, blocks, commands, mechanics, what's been removed, why
something behaves a certain way — belongs in `wiki/docs/`. One canonical
place.** Don't duplicate player-facing info into `welcome.md`, `mods.md`,
top-level READMEs, or scattered `PLAN.md` sections.

**At the end of every flow** that ships a player-visible change (new item,
command, mechanic, balance tweak, removed feature, renamed industry, etc.):

1. Update the relevant `wiki/docs/*.md` page so it reflects current code.
2. Append an entry to `wiki/docs/changelog.md` (the patch-notes page) — under
   today's `## YYYY-MM-DD` heading, grouped by area (*Specialization*,
   *Claims*, *World*, etc.). Players read this to learn what's new without
   re-reading every page.
3. Run `./wiki/deploy.sh` so the live site matches the repo. Caddy serves
   static files; no restart needed.

**Verify against code, not against earlier docs.** When updating the wiki,
read the actual glue-mod source files (`glue/<mod>/src/...`) — past wiki
prose, prior chat summaries, and even older `PLAN.md` sections **will be
stale**. Treat the source as ground truth. `SkillKind.kt`, `Quality.kt`,
`SkillMath.kt`, `VitalityMath.kt`, `RestorationFood.kt`, `ClaimRenderer.kt`,
the `data/.../tags/item/*.json` files and the like are the real spec — the
PLAN.md was a design doc and *will* drift.

**Scope:** player-facing only. Design rationale, why-we-picked-X, the four
pillars — those stay in `PLAN.md` / `heuristics.md` and never get copied
into the wiki.

## Design decisions must cite a pillar

Before proposing or accepting any new mod, rule, or config tweak, check it against
`heuristics.md`. A proposal should support at least one pillar and undermine none.
When landing a decision in `PLAN.md` §3, call out which pillar(s) it serves if
non-obvious — future sessions read that trace to sanity-check later additions.

## Evidence

Every factual claim about a mod (slug, loader, version, release date) must be
backed by a file in `.research/`. That directory holds raw Modrinth API responses
and cloned upstream source so future sessions can audit past conclusions without
re-fetching.

## Scope guardrails

- Preference order for implementation: **mod config → datapack → small glue mod →
  full custom mod.** Pick the leftmost option that solves the problem.
- Glue mods stay single-purpose: roughly "1 item/block + 1 event handler + 1 API
  call". If a glue mod grows past that, split it instead of bloating it.
- No forks. No world-gen rewrites. No custom economy mod.

## Sequencing (from Greg)

Glue mod #1 (Numismatics → OPAC claim-quota bridge) ships **before** the server is
provisioned. Server launch is gated on the glue mods being buildable and passing
their gametests.
