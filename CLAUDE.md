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
| Mod compatibility with the real stack | Only Greg can test this on the provisioned server. Flag it explicitly when that's the gate. |

If something can't be self-verified, say so plainly in the status report instead of
claiming success.

## Documentation discipline

The repo has five top-level docs; keep them consistent when decisions change:

- `PLAN.md` — design source of truth. Versioned; bump on every material decision.
- `heuristics.md` — the 4 design pillars. **Check every proposal against it.**
- `mods.md` — formal mod list with Modrinth links + verified 1.21.1 NeoForge versions.
- `infra.md` — hosting, sizing, provisioning.
- `CLAUDE.md` (this file) — operating rules that survive across sessions.

Per-component plans live alongside their code (e.g., `glue/<mod>/PLAN.md`).

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
