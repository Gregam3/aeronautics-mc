# Season 3 ("Karos") — Heuristics (working draft)

Status: working set, dictated by Greg 2026-05-09. Will supersede `heuristics.md` when S3 launches. Until then this is the doc that S3 design proposals must check against.

Every S3 design choice — mod, glue mod, recipe, config — must support at least one heuristic and undermine none.

## H1. There must be a reason to trade at every stage of the game.

Trade can't be a mid-game-only mechanic. Early game, mid game, and late game must each have a forcing function for player-to-player exchange. A design that leaves any phase tradeless violates this.

## H2. There must be a reason to build vehicles.

Building a vehicle is structurally required, not optional convenience. If a player can succeed without ever building one, Create Aeronautics is cosmetic.

## H3. There must be a reason for vehicles to scale.

Bigger vehicles must unlock something a smaller vehicle cannot do — content, range, cargo, survival. If the first viable vehicle is also the last one anyone needs, the escalation arc collapses.

## H4. Players should be able to deploy unique strategies to succeed.

No single optimal path to the win. Different players should be able to reach the win condition via genuinely different playstyles. The "everyone follows the same loop" failure mode (sameness) violates this directly.

## H5. There must be a clear progression from early to mid to end game.

Players should be able to identify which phase they're in and what they're working toward. Phases should be named and recognizable — not just "more of the same with bigger numbers."

## H6. The economy must be adaptive; strategies must be adaptive and advanced.

A simplistic, static strategy ("find the cheapest loop and grind it forever") cannot succeed. The economy responds to what players do — overproducing tanks prices, demand shifts, contracts change. A player who doesn't adapt loses to one who does.

## H7. There must be a way to print money.

The economy needs a controlled mechanism for new currency to enter circulation, or it never takes off — a pure zero-sum starting allocation leaves early-game players with nothing to spend, no incentive to seek out trade, and prices that never form because nobody can afford to bid. A mint is required.

The mint must be **rate-limited and structurally non-grindable**: not an infinite NPC buyer accepting any volume of any good (that was the S2 raw-log failure), but capped — daily quotas, rotating bounties, first-come contracts, or similar. The mint provides liquidity; player-to-player trade should still dominate volume.
