---
source: Ultimate Guide to Create Aeronautics (1.0 launch video transcript)
purpose: Reference for what blocks/items the Create Aeronautics + Simulated + Off-road bundle adds, and how each one works mechanically. Use this when designing glue mods, datapacks, or server rules that interact with this mod stack.
---

# Create Aeronautics — Mod Reference

The 1.0 release ships as a bundle:

- **Create Simulated** — core physics blocks; turns regular blocks into physics-driven contraptions.
- **Create Aeronautics** — propellers, balloons, steam vents — anything that flies.
- **Create Off-road** — wheels and vehicle parts for cars/trucks/tanks.
- **Sable** — companion physics engine that runs all the simulation math.

The creative tab is split into **Simulated**, **Aeronautics**, and **Off-road** subtabs.

---

## 1. Core Physics Assembly

### Physics Assembler
The entry point. Right-click onto any face of a block, then hold-right-click and drag the lever upward to assemble that block (and anything glued to it) into a physics contraption.

- Same block (or a different assembler) used to **disassemble**: hold right-click and pull the lever down.
- Once assembled, you can build directly off the floating contraption — new blocks become part of it.

### Connecting blocks before assembly
Anything sticky binds blocks into one contraption when assembled:

- **Slime blocks** — sticky, work as glue.
- **Create chassis** — bind regions together as usual.
- **Create superglue** — works for assembly. Also keeps working as superglue under regular Create contraptions (pistons, etc.).
- **Honey glue** (new, Simulated) — only works for assembly/disassembly. Does NOT carry blocks under piston motion. Two placement modes:
  - Right-click two corners; scroll+ctrl on faces to extend/retract the volume.
  - Hold **alt** to place the second corner in midair.
  - Diagonal-connected blocks are included; disconnected blocks are excluded.

**Honey/superglue interaction:** A honey-glued region overlapping a superglued region pulls the superglued blocks in. The reverse doesn't — superglued sections don't pull honey-glued sections. Rule of thumb: honey glue = main hull; superglue = moving add-ons (wheels, wings).

### Slime ball (re-gluing)
Right-click a face on an assembled contraption with a slime ball, then click another face → they merge into one physics object. Only works contraption-to-contraption, never contraption-to-world.

---

## 2. Player & Creative Tools

| Item | Function |
|---|---|
| **Aviator's goggles** | Like engineer's goggles — show block info — plus armor. Cheap to craft. |
| **Music disc** | Mod soundtrack. |
| **Creative physics staff** | Creative-only. Right-click an assembly to grab; scroll = closer/further; hold tab + mouse = rotate; left-click any contraption to lock it in place mid-air; left-click again to unlock. |
| **Plunger launcher** | Has durability, can be powered with backtank air. Right-click two contraptions to pull them together. Shift+right-click clears placed plungers. |
| **Engineer's goggles** | (Vanilla Create.) Required to read block weight class, special effects (slippery, bouncy, sticky, fragile, floating), force vectors. |

---

## 3. Block Properties (visible with engineer's goggles)

### Weight classes
| Class | Weight | Examples |
|---|---|---|
| Weightless | 0 | Redstone components, small plants |
| Super light | 0.25 | Fences, slabs, small blocks |
| Light | 0.5 | Most woods, oak logs |
| Normal | 1 | Concrete, terracotta, shulker boxes, polished stones |
| Heavy | 2 | Raw stones — cobblestone, mossy cobble, granite, andesite |
| Super heavy | (high) | Iron blocks, netherite blocks |

### Special modifiers
- **Bouncy** — slime.
- **Sticky** — honey blocks (slide stops on contact).
- **Slippery** — ice (objects slide off).
- **Fragile** — regular ice; heavy blocks dropped on it shatter the ice. Packed ice is not fragile.
- **Floating** — endstone (and similar). Provides upward levitation force; see §10 levitite for stronger version.

### Fluids on contraptions
Fluids flow on a contraption's surface but **do not flow off the edges**, so contraptions can't flood the world. Bottom-layer fluids don't flow at all.

### Cross-contraption interactions
Many Create operations work across separate contraptions — e.g. a mechanical press on contraption A pressing items on a depot on contraption B. Worth testing case-by-case.

---

## 4. Mechanical Control

### Handle
Two modes:

- **Right-click**: grab onto handle; player moves toward it. Scroll down = closer, up = farther. Useful for boarding airships.
- **Shift + right-click**: grab the contraption itself; drag it around physically. Same scroll behavior.

Crafted as iron handle (andesite alloy + iron nugget). Copper variant exists. All 16 dye colors.

### Bearings
- **Regular Create mechanical bearing** — still works for assemblies on simulated contraptions; the spinning structure interacts physically with the world (spinning wheels actually push a vehicle).
- **Swivel bearing** (Simulated) — powered via cog on the side instead of shaft. Critically: the assembly it produces is a **real simulated object** — you can build off it, redstone works on it, items can travel through belts on it. Has a **pass-through shaft**: power in the back exits the shaft on the other side.

  Four lock modes:
  1. Unpowered locked (default) — locked to the cog rotation; redstone power releases it to swivel freely.
  2. Always locked.
  3. Powered locked — inverse: unpowered = swivels.
  4. Always unlocked.

### Redstone Magnet
Particle field shows polarity. Red attracts blue, like polls repel. Redstone strength controls magnetic strength (gradual attraction/repulsion). Distance also affects pull strength.

### Ropes
Three blocks:
- **Rope connector** — placed on contraption.
- **Rope coupling** — click connector A, then connector B → places a rope between them. One rope per coupling. Remove with shears.
- **Rope winch** — connects to a connector; redstone-powered. Right-click extends slack; shift-right-click retracts.

Ropes hold weight (e.g. a suspended walkway is a separate contraption held up by ropes). Right-click a rope with a wrench to **zipline** down it.

### Spring
Right-click two spots to place a spring between them.

- Right-click with wrench → cycles weight: heavy / normal / skinny (skinny = bouncier, can barely hold itself).
- Right-click with another spring → +0.25m length per click. Shift right-click → -0.25m.

### Torsion Spring
Sits between rotational input and output. Two effects:

- **Limits max angle** (default 90°, configurable).
- **Returns to zero when unpowered** (via clutch).

Reverse rotational direction reverses the angle direction. Use cases: airplane control surfaces (flaps), car steering with auto-centering.

### Docking Connector
Powered → extends. If a mating connector is in range, they pull together and lock. Works contraption-to-world and contraption-to-contraption. Strongest available locking mechanism.

- **Transports items and fluids** between docked contraptions (funnel in one side, funnel out the other).
- Outputs a **redstone signal proportional to docking distance**: ~3 (far), ~7 (mid), 15 (fully docked).

---

## 5. Power & Drivetrain

### Portable Engine
Standalone SU source. Feed it fuel:

- **Coal** — 2048 SU; one stack ≈ 1.5 hours burn time.
- **Blaze cake** — supercharged: 4096 SU at 2× speed; one stack ≈ 6 hours.

Rotation direction switchable on the block (no gear shift needed). Crafted with **engine assembly** = iron sheet pressed 8 times. 16 dye colors. Auto-feedable via belt+funnel or mechanical arm.

### Directional Gear Shift
Outputs spin direction depends on which side power is input. If both sides powered or neither, it stops. More compact than a redstone-controlled gear shift setup.

### Analog Transmission
Bidirectional gearbox. Two operating modes depending on which side you feed:

- Power in via shaft → cog output. Redstone analog input gradually slows/stops the output (32 RPM → 24 → 18 → 0 at full power).
- Power in via cog → shaft output. Redstone analog input gradually **increases** speed (32 → 64 → 128 → 256, maxes at 256).

At max power in either direction it **decouples** (turns off). Once decoupled, an alternate input can drive the system.

### Steering Wheel
Hold and drag mouse left/right to control rotational angle of the connected output. Default max rotation: 180°, adjustable down (e.g. 45° for sail trim).

- Right-click with wood plank → recolors wheel to that wood.
- Hold shift while turning → snaps in 45° increments (handy for returning to zero).

---

## 6. Special / Diagnostic Items

### Contraption Diagram
Craft: paper + physics assembler. Place on a contraption → right-click for a 3D schematic UI.

Shows:
- **Total mass**
- **Center of mass** (toggleable pip — critical for plane/airship balance)
- **Force arrows** — separate or merged. Toggleable: levitation, balloon lift, propulsion, lift (default on); magnetic force, drag, gravity (off by default).
- Click-and-drag to box-zoom into details.
- Players standing on the contraption appear in the schematic.
- Diagram diagrams can be placed in 1×1, 2×2, or 3×3 sizes based on available wall space.
- Forces are displayed in the contraption's local frame, but force vectors (gravity etc.) are rotated to reflect actual orientation.

### Linked Typewriter
Two modes:

1. **Bound to redstone links** — every keyboard key becomes a separately-bindable redstone-link trigger. Right-click to enter typing mode; pressed keys fire the bound links. Unbound keys still control the player hotbar normally.
2. **Bound to display board** — right-click a board onto the typewriter to bind. Then right-click typewriter to type into the board. Only bound keys produce characters.

Settings UI exposes a key list, an "additional" field for non-standard keys (keypad, mouse buttons), and a delete-all button. Settings persist when picked up.

### Name Plates
Sign-like; only place on side faces. All display-board name plates on a single contraption show the same text — edit one, all update. Name appears in the contraption diagram (bottom-right).

---

## 7. Item Movement

### Augur (two variants)
- **Augur shaft** — placed as a shaft; forms a tube with shaft inputs at both ends. Power either end → items move through. Works at any angle, including vertical and arbitrary contraption-to-contraption angles.
- **Augur cog** — convert via wrench right-click on a shaft, or craft directly. Powered from the side via cog. Items move from unpowered side → powered side. Reports throughput (e.g. "31 stacks/sec") under goggles.

Rules:
- Cannot input items from the cog side or from either end (the shaft blocks it). Must insert from the side, mid-tube.
- When attached to a drill or harvester, automatically collects mined/harvested blocks into the augur. Useful for self-collecting mining contraptions.

---

## 8. Redstone Upgrades

### Redstone Inductor
Delays a signal change. Configurable seconds-per-step. E.g. set 3s → applied power 15 takes (3s × n) to ramp up; removed power decays one step every 3s.

### Redstone Accumulator
Like the inductor but the **decrement is gated by a separate input**, not automatic. Builds up from one side, drains from the other, holds otherwise.

### Throttle Lever
Upgrade to the analog lever. Right-click and drag for fine, fast adjustment of redstone strength 0–15. Designed for live-control inputs like throttle.

### Modulating Link Receiver
Reads **distance** from a paired redstone link within a configurable min/max range. Redstone output is proportional: closer = stronger, beyond max = off.

### Directional Linked Receiver
Reads **direction** to a paired link. Strongest signal directly in front; weaker as the link moves off-axis; zero behind.

### Altitude Sensor
Two visual variants — bar (right-click wrench → swap) and dial. Outputs redstone proportional to current altitude within a configurable range (default world: -64..320). Tighter range = finer resolution. With goggles, the altitude sensor also displays current air pressure.

### Velocity Sensor
Outputs redstone proportional to contraption speed. Default fires on the side opposite the direction of motion (configurable: "away" or "towards"). Configurable max speed up to 50 m/s defines what 15 represents.

### Gimbal Sensor
Outputs redstone on the **down-side** of the contraption's tilt. Works on compound angles — two adjacent sides will both fire if the tilt is diagonal. Use cases: auto-leveling, signaling pitch/roll for flight controls.

### Navigational Table
Outputs redstone toward a navigation target. Pointer rotates with the contraption. Targets:
- **Map with banner** (right-click a banner with a map → bind banner location).
- **Compass** → world spawn.
- **Lodestone compass** → bound lodestone.
- **Recovery compass** → last death point.

Like the gimbal sensor, splits signal across adjacent sides when the target is at a corner.

### Optical Sensor
Senses distance to a block in front; redstone strength inversely proportional. Configurable max range. Has a filter slot — only detects blocks matching the filter (any block type works).

### Laser Pointer / Laser Sensor
Powered laser pointer emits a beam to a sensor → transfers the redstone signal. Solid blocks block the beam; transparent blocks (glass) don't.

- Beam strength matches input strength (visual opacity reflects this).
- Configurable **casting distance** caps beam length regardless of power.
- Right-click pointer with dye → 16 colors. Sensor accepts any color by default; insert a colored filter to require matching color.
- **Black laser pointer is invisible.** Combined with a black filter, gives a hidden invisible signal channel.

---

## 9. Aeronautics — Lift

### Hot Air Burner + Hot Air Envelope
Build an envelope — a sealed empty volume bounded by hot air envelope blocks. Burner sits below; redstone activates the flame.

- Burner produces hot air → fills the envelope → balloon lift force (visible in contraption diagram).
- Lift must exceed gravity for the contraption to rise.
- Air becomes thinner with altitude → balloon lift drops with height → contraption stabilizes at an equilibrium altitude.
- Burner has a configurable max volume output (with goggles → see gas output, fill, total volume). Lower the redstone signal or the configured max → less hot air → balloon descends. The yellow bar in the UI shows target fill.
- **Aeronautics tracks air loss through holes.** Any gap in the envelope = puff animation + altitude loss. Seal the envelope completely.
- Right-click envelope with dye: 1× = single block, 2× = plus pattern, 3× rapid = whole balloon.
- A shaft passing through the envelope can use a hot air envelope as casing → keeps the section airtight (any internal block reduces total volume).
- **Burner max balloon size: 500 m³.**

### Steam Vent
Larger version of the burner. **Max balloon size: 5000 m³.** Stacks: place multiple steam vents on the same envelope to scale further.

Requires a steam supply like a steam engine (water tank + heat below it). Two color variants: gold (default) and dark gray (right-click with iron sheet).

---

## 10. Aeronautics — Propulsion

### Propeller Bearing
A standalone bearing — attach blocks and assemble. Generates thrust if the assembly contains valid propeller blocks: **windmill sails, symmetric sails, wool**.

- Thrust direction shown by air particles.
- Reverse via clutch (rotation reversal) or via the device's own thrust direction setting.
- Air pressure affects propeller bearings: thrust drops with altitude (just like balloons).

### Gyroscopic Propeller Bearing
Variant of the propeller bearing that always orients its thrust **upright** (opposite gravity), regardless of contraption tilt. Use for helicopters or anything that must hover stably.

### Single-block propellers
- **Wooden propeller** (crafted from andesite propeller).
- **Andesite propeller**.
- **Smart propeller** — a single-block gyroscopic equivalent, but only stabilizes one axis. If unbalanced perpendicular to its stabilization axis, it will tilt.

These provide identical thrust to bearing-based propellers; aesthetic and compactness choice.

### Symmetric Sail
Variant of the windmill sail with sails on both sides. Acts like a vertical stabilizer — keeps a plane facing one direction (resists yaw). Regular windmill sails as wings provide lift.

---

## 11. Levitite

A floating block, ~5× stronger than endstone (floating-with-10 vs floating-with-2). Two variants:
- **Levitite**
- **Pearlescent levitite** (pink, identical mechanics)

**Cannot be mined.** Hand-mining destroys it. Move it via assembly + drag.

### Quirks
- Players standing on or in levitite **don't fall through** — float at current Y. Press shift to descend (and drown if submerged).
- Slow-moving objects experience drag from a small barrier above the block; high-speed motion overcomes it.

### Crafting
1. Mix **endstone powder** (crushed endstone) + **zinc** + water → **levitite blend**.
2. Pour blend into a casting basin in the desired shape.
3. Apply heat:
   - **Campfire** → cures into regular levitite.
   - **Soul torch / soul campfire** → cures into pearlescent levitite.
4. Curing propagates: heated block cures adjacent blend. Surrounding blocks (clay, etc.) get popped off as the levitite forms.

### Mounted Potato Cannon
Standalone potato-cannon block. Two-step firing:
1. Charge it from rotational power on the side.
2. Apply redstone → fires.

Items inserted via hopper/chute/funnel; speed via belt. With sustained redstone + auto-feed, becomes a fully automated weapon.

---

## 12. Off-road

### Borehead Bearing + Rock Cutting Wheels
- **Borehead bearing** — bearing powered from the side.
- **Rock cutting wheels** — attach to the bearing; mine in a 3×3 area as the bearing rotates. Drops can be funneled into a chest on the same contraption → self-collecting mining drill.

### Wheel Mount + Wheels
Wheel mount block accepts attached wheels (right-click with wheel item). Four sizes: **small / medium / large / monstrous**.

- **Drive**: power the back of the mount → wheels spin → contraption moves.
- **Steering**: redstone on left/right side turns the wheel that direction. Strength is proportional (analog steering). Both sides can be combined — high one side, light tap on the other for fine angle.
- **Suspension strength**: adjustable in the mount's UI. Default 10 = bouncy. Higher = stiffer.

Cars commonly use a torsion spring for steering feel + auto-centering rather than wiring redstone directly.

---

## 13. Building Notes

- Building directly off an assembled physics contraption is awkward once it tips over. Prefer building on the world grid first, then assembling.
- A disconnected block on an existing contraption splits off into its own physics object — mind your glue topology.
- Honey-glued sections are immovable under Create kinematics; superglued sections are not. Plan accordingly: hull = honey glue, moving parts = superglue.
- Center-of-mass alignment is the single most important diagnostic for any plane/airship — check the contraption diagram before flight.
- For airtight balloon walls, use hot air envelope blocks as casings around shafts.
- For mining contraptions, augur shafts route mined material to onboard storage automatically.
