# Project operating rules — Create Aeronautics Server

## NO SILENT DEBUGGING — RULE NEGATIVE-ONE

**All debugging reasoning must be in text output to the conversation, not
in internal thinking. Greg can only see what I type. If I'm reasoning
about "why is X failing", that reasoning happens in console-visible text,
sentence by sentence, while I'm doing it.** Not after. Not summarized.
Live, in band, where Greg can see it.

This means: when I read a log line and form a hypothesis ("the say
command isn't logging — must be a modded server issue"), I TYPE that
hypothesis to Greg before I act on it. If I want to try a fix, I say
what the fix is and why before I write code. If I revise the hypothesis
mid-debug, I say that revision.

**Not allowed**: thinking silently while a tool runs, debugging in my
head between tool calls, deciding a course of action without saying it.

Greg's words verbatim (2026-05-16): "debugging silently this is not
allowed. all debugging must be output in this console, do you understand?
There can be no internal thoughts."

## NARRATE EVERYTHING — RULE ZERO

**Greg cannot see tool calls. He sees only my text output. If I make a tool
call without writing text first, he sees "Reading..." or nothing and assumes
I am hung. This has cost hours. THIS IS THE #1 RULE OF THIS PROJECT.**

**Going silent is NEVER safer than too-loud.** If a previous monitor flooded
Greg's input, the fix is a 30s-cadence summary monitor, NOT no monitor.
"I'll check when it completes" is wrong — Greg has lost two hours coming back
to a hung process. Whenever I background a task, I MUST arm a heartbeat
script that emits one summary line every 30 seconds, no faster. The summary
must be informative ("phase1 acks=2/4, server pid=X alive, last log line:
<line>") not just "still running". Four lines for a two-minute task is
right; forty lines is flood; zero is failure.

Concrete, non-negotiable protocol:

1. **Before EVERY single tool call, write a one-line narration of what I'm
   about to do and why.** Not "let me check" — concretely: "reading
   world_audit.py to find the sleep loop" or "grepping for `time.sleep` in
   the audit scripts". Even a Read of one file gets a one-liner.
2. **Before a batch of parallel tool calls, write one sentence covering the
   batch.** "Reading run-audit.sh and world_audit.py in parallel to map the
   current pipeline."
3. **If I am about to do >5 seconds of any silent work (multiple sequential
   reads, large file reads, agent dispatch), tell Greg first.** Estimate the
   duration. "About to read three ~800-line files — ~15s of silence
   incoming, then I'll report back."
4. **While waiting on a background task / audit / build / server boot /
   gen-wait, every ≤30 seconds output a heartbeat** with a concrete
   observable: PID alive? last log line timestamp? region file size? If
   the observable hasn't changed for >2 minutes when it should be changing,
   **assume hung — kill it and diagnose**. Don't wait the full timeout.
5. **NEVER block on `time.sleep`, `proc.wait`, or any opaque sleep without
   a heartbeat.** If the harness can't give me an event, I'm the one
   polling and reporting.
6. **If Greg asks "ready?" or "status?", I must already have a concrete
   answer.** "Still running, will report when notified" is the failure
   mode itself.

The failure pattern I keep falling into: Greg sends a message → I read three
files in sequence with no narration → 60s of "Reading..." on his screen → he
correctly concludes I'm broken. **Talk first, tool-call second. Always.**

## Status updates — never go silent while waiting

**Every 30 seconds of waiting on anything (background task, audit, build,
server boot, gen-wait), I MUST output a status line. If a task is hung,
silence looks identical to "working" — and Greg has lost hours that way.**

Concrete protocol when waiting on a background task:
1. After kicking it off, give a tight ETA up front ("~3 min, will report at
   each minute"). Don't say "waiting" and disappear.
2. While waiting, every ~30s output a line: "still working — PID still
   alive, last log line at HH:MM:SS, server log size N bytes" or whatever
   is observable. Use the Monitor tool with a tight grep filter so
   progress events stream automatically instead of relying on me to poll.
3. If the same observable hasn't changed for >2 minutes when it should be
   changing (log not advancing, region files not growing during gen,
   python process CPU near zero when it should be busy), **assume hung —
   kill it and diagnose**. Don't wait the full timeout.
4. NEVER block waiting on a `time.sleep` or `proc.wait` without a heartbeat.
   If a background task notification hasn't fired and the harness can't
   give me an event, I should be the one polling progress and reporting.
5. If I do nothing else right, do this: when Greg asks "ready?" or "what's
   the status?", I must already have evidence to answer concretely. Not
   "still running, will report when notified" — that's the failure mode.

This isn't optional. The repeat failure mode: I launch a 3-minute audit,
something hangs silently, I sit on the notification, 30+ minutes pass,
Greg comes back to a wedged process. Cost: another lost hour.

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

### Worldgen changes — the #1 rule

**Never declare a worldgen change "done" without running the full
`season3/test/karos-mapgen/run-audit.sh` (or `--replay`) and seeing every
biome-category alignment check PASS.** Worldgen has many interacting layers
(NovoAtlas, Lithostitched, vanilla noise, biome surface rules, mod features)
and a fix for one biome can silently break another. Greg's bottleneck is the
~3 minutes of generating a fresh world per test — every false "it's fixed"
costs him a world, and he has burned dozens already.

Concrete protocol for any biome/terrain/density change:

1. Before making the change: skim the audit's category checks
   (`ocean_align_*`, `mountain_align_*`, `nether_align_*`, `end_align_*`,
   `land_biome_*` — extend if your change touches a category not yet
   covered).
2. Make the change.
3. Run `bash season3/test/karos-mapgen/run-audit.sh` (full boot, ~3 min)
   OR `python3 .../world_audit.py --staging /tmp/aero-s3-karos-test --replay`
   if the previous boot is still on disk and you only changed the audit
   logic / non-worldgen code.
4. Gate on **0 failures across every category**, not just the one you were
   targeting. A single failure in any category = the change isn't shipped.
5. If a category isn't audited yet, **add the check first**, run it, then
   make the change.
6. Only after step 4 passes, sync the datapack with
   `./season3/deploy-datapacks.sh` and tell Greg.

This isn't optional. "Looks plausible" + "ship it for Greg to test" has cost
the project two-digit hours of Greg's time. The audit is fast; an in-game
fresh-world test is not.

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
