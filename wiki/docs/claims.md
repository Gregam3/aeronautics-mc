# Land claims

Land protection on this server is custom — `caero_claims`. You spend
**Numismatics spurs** to claim **arbitrary 3D block volumes** (not chunks).
Claims are **permanent**, **non-refundable**, and protect against
breaking, placing, and most automated interaction by non-owners.

## The Claim Wand

Get one with `/caero-claim wand`. While you hold the wand, all confirmed
claims in your current dimension render as outlines (through walls, so you
can see fully-buried claims). Color tells you ownership at a glance:

| Color | Meaning |
|---|---|
| **Yellow** | Live, unconfirmed selection — first corner placed, second corner follows your cursor |
| **Orange** | Armed — both corners locked, awaiting `/caero-claim yes` |
| **Green** | Confirmed claim, owned by you |
| **Red** | Confirmed claim, owned by another player |
| **Purple** | Admin-owned (public protected zone — e.g., the spawn hub) |

### How to claim

1. **Hold the Claim Wand.**
2. **Right-click block A** — first corner. Outline turns yellow and
   follows your cursor.
3. **Right-click block B** — second corner. Outline turns orange. Action
   bar shows volume in blocks, cost in spurs, and your account balance.
4. **Type `/caero-claim yes`** to confirm. Spurs are deducted; the volume
   becomes a green outline.
5. To abandon a selection without spending: `/caero-claim cancel`, **or**
   right-click again while the selection is armed.

### Why three steps

The 3-step gesture exists so you don't accidentally burn 5,000 spurs by
mistiming a click. Yellow = looking, orange = "are you sure", green =
done. Right-click while orange acts as a hard "no", regardless of whether
you typed the chat command.

## What's protected

Inside a claim, non-owners cannot:

- Break, place, or trample blocks
- Right-click most blocks (chests, furnaces, levers, doors, etc.)
- Left-click blocks (vanilla mining input)
- Interact with entities (ride, lead, shear, etc.)
- Pick up items lying on the ground
- Damage entities
- Be hit by Create contraption block-breakers (drills, saws) trying to
  carve into the volume

Inside a claim, **fire does not spread**. Existing fires age out and die
without lighting neighbours or consuming flammable blocks. Flint & steel
still works to start a single fire (lights and stoves still function),
the spread step is what's gated.

Players inside a claim **can** still take fall damage, fight hostile mobs
that already aggro'd them, eat, fly, etc. — the claim guards block + entity
state, not gameplay.

A small set of blocks tagged `caero_claims:public_interactable` (and similar)
remain right-clickable by anyone — used for things like community shop
fronts inside an admin-owned zone.

## Cost & permanence

- **1 spur per block** of volume. A 16×16×16 cube costs 4,096 spurs.
- **No unclaim. No refund.** Spurs are burned on confirmation.
- Cost is configurable server-side (`SPURS_PER_BLOCK`, default 1) but
  changing it does not refund existing claims.

Plan your shape before you confirm — paying for vertical empty space is
intentional, since a small footprint with high reach is a lot of
protection.

## Player commands

| Command | Effect |
|---|---|
| `/caero-claim wand` | Grants you a Claim Wand |
| `/caero-claim yes` | Confirm the currently armed selection (deducts spurs) |
| `/caero-claim cancel` | Discard the armed selection (free) |

## Admin commands

| Command | Effect |
|---|---|
| `/caero-claim admin-wand` | Grants an admin wand — claims via this wand are FREE and owned by `Admin` (purple outline, public-protected) |
| `/caeroclaims list` | List claims in current dimension |
| `/caeroclaims info <pos>` | Diagnostic for a specific block position — which claim contains it |
| `/caeroclaims delete <claim-id> [volume-index]` | Force-delete a claim or one of its volumes |
| `/caeroclaims transfer <claim-id> <new-owner>` | Reassign ownership |
| `/caeroclaims tp <claim-id>` | Teleport to a claim |
| `/caeroclaims test foreigner <claim-id>` | Diagnostic — simulate a non-owner action |

## Earning spurs

Spurs come from:

- **Refiner fees** — owning a Refiner block and setting a per-refine fee
  (see [Specialization & quality](specialization.md))
- **Selling to other players** via Numismatics shop blocks
- **The Numismatics coin printer** — but the underlying resources are
  themselves bottlenecked by [biome tiers](biome-tiers.md)
