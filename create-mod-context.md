# Complete Beginner’s Guide to the Create Mod

## Overview

This guide is for Minecraft players who already understand the basics of vanilla Minecraft, but are new to the Create mod or have only used it lightly.

It covers:

- Early-game resource priorities
- First power generation
- Basic Create components
- Starter machines
- Filters
- Farms and contraptions
- Nether progression
- Brass age
- Crushing wheels
- Deployers, mechanical arms, and redstone tools
- Storage and trains
- Copper/fluid systems
- Steam engines
- Workshops and schematics
- Late-game automation goals

---

## 1. Starting a New World

At the start, Create does not change much. Begin like normal Minecraft:

1. Punch a tree.
2. Get food.
3. Make basic tools.
4. Go mining.

You can build a house first if you want, but the Create mod really begins once you start mining.

---

## 2. Important Early Resources

When mining, focus on these materials:

### Most Important

- **Iron**
- **Copper**
- **Zinc**
- **Andesite**

### Also Useful

- Gold
- Coal
- Redstone
- Quartz later in the Nether
- Diamonds for tools and armour

### Zinc

Create adds one major new ore: **zinc ore**.

Zinc is used in important Create crafting recipes, especially later when making brass.

### Andesite

Andesite is extremely important. You use it constantly for Create components, especially **andesite alloy**.

Collect as much as you can.

---

## 3. First Power Source: Water Wheel

Create has four main power generation methods:

1. Water wheels
2. Large water wheels
3. Windmills
4. Steam engines

For the early game, start with a **water wheel**.

### Crafting Andesite Alloy

The first Create component you need is **andesite alloy**.

It is made from:

- Andesite
- Iron nuggets

Andesite alloy is then used to make **shafts**.

### Crafting a Water Wheel

A water wheel requires:

- Planks
- A shaft

You also need a bucket of water.

### Powering the Water Wheel

Do not simply place the water wheel in still water. The water must flow over it.

Place water above or beside it so the water flows across the wheel and causes it to spin.

Once spinning, the shaft inside the wheel rotates. You can connect more shafts to transmit this rotation.

---

## 4. First Useful Tools

Before building many contraptions, make these two tools:

- **Engineer’s Goggles**
- **Wrench**

Both require **golden sheets**.

---

## 5. Making Golden Sheets

Golden sheets are made by pressing gold ingots with a **mechanical press**.

### Mechanical Press Requirements

To craft a mechanical press, you need:

- Shafts
- Block of iron
- Andesite casing

### Andesite Casing

To make andesite casing:

1. Place a stripped log.
2. Right-click it with andesite alloy.

That turns the stripped log into an andesite casing.

### Using the Mechanical Press

Connect the mechanical press to your water wheel using shafts, gearboxes, belts, or other rotational components.

Place a block or depot under the press, then drop gold ingots beneath it.

The press will turn them into golden sheets.

---

## 6. Gearboxes and Rotation

Gearboxes are used to redirect and invert rotational power.

A normal gearbox can transfer rotation between different sides.

A vertical gearbox changes the direction vertically.

Gearboxes also invert rotation depending on the side, so test the direction if something does not work.

---

## 7. Engineer’s Goggles

Engineer’s goggles show useful information when worn, including:

- Kinetic stress capacity
- Kinetic stress impact
- Rotation direction
- Machine status

Create uses **Stress Units**, or **SU**, instead of watts.

Example:

- A water wheel may provide **256 SU**.
- A mechanical press may use **64 SU**.

So the press uses 64 out of the 256 available stress units.

---

## 8. Wrench

The wrench is one of the most useful Create tools.

It can:

- Rotate machines
- Change gearbox faces
- Reconfigure many Create blocks
- Pick up blocks directly into your inventory with shift-right-click

Try using the wrench on different Create blocks. Many of them have special interactions.

---

# Basic Create Components

## 9. Shafts

Shafts transmit rotational power.

They connect to:

- Gearboxes
- Cogwheels
- Belts
- Machines

They spin in the direction of the power source.

---

## 10. Belts

Belts move items and transmit rotation between shafts.

To make a belt:

1. Place two shafts apart from each other.
2. Right-click with a belt.

You can extend or shorten belts with:

- Right-click with belt: increase length
- Right-click with wrench: decrease length

Items placed on belts move along them.

Belts are used heavily for automation and assembly lines.

---

## 11. Depots

A depot holds one stack of items in place.

You can place items onto a depot by right-clicking it or by dropping items onto it from a belt.

Depots are often used under:

- Mechanical presses
- Encased fans
- Deployers
- Mixers/basins

---

## 12. Funnels

Funnels move items in or out of inventories.

They can attach to:

- Barrels
- Chests
- Belts
- Depots
- Other inventories

### Andesite Funnel

- Moves one item at a time
- No filter slot

### Brass Funnel

- Can move full stacks
- Has a filter slot
- Can control item quantity

Use the wrench to flip the funnel direction.

---

## 13. Chutes and Smart Chutes

Chutes move items vertically.

### Normal Chute

- Drops up to 16 items at a time

### Smart Chute

- Drops up to 64 items at a time
- Has a filter slot

Chutes are useful when items need to sit somewhere until processed, such as washing or smoking.

---

## 14. Tunnels

Tunnels are used for splitting or routing belt items.

### Andesite Tunnels

These are not especially useful and can often be avoided.

### Brass Tunnels

Brass tunnels are more useful. They can split items across multiple belts using modes such as:

- Split
- Forced split
- Round robin
- Prefer nearest
- Randomize
- Synchronize inputs

Even so, many designs can avoid tunnels entirely.

---

## 15. Basins

Basins hold items and fluids for processing.

They are used for:

- Mixing
- Compacting
- Brewing
- Shapeless crafting
- Packaging

Basins can automatically output completed items onto a depot or belt next to them.

They are usually paired with:

- Mechanical mixer
- Mechanical press

---

## 16. Mechanical Mixer

The mechanical mixer processes items in a basin.

It is used for recipes like:

- Wheat flour + water → dough
- Copper + zinc → brass

Some recipes require a **blaze burner** underneath the basin.

---

## 17. Mechanical Press

The mechanical press is used for:

- Pressing ingots into sheets
- Compacting items in basins
- Assembly lines

On a belt, the belt pauses while the press works, then continues once processing is finished.

This makes belts better than depots for automated assembly lines.

---

## 18. Mechanical Saw

Saws cut items and blocks.

They can:

- Cut logs
- Process wood
- Cut trees in contraptions
- Improve shaft production from andesite alloy

The item movement direction may feel counterintuitive, so test it before building.

---

## 19. Encased Fan

Encased fans push air.

They can be used for:

- Washing
- Smoking
- Blasting
- Haunting
- Moving entities
- Moving items upward through chutes

Fan processing depends on what is placed in front of the airflow:

- Water: washing
- Lava/fire: blasting or heating
- Campfire: smoking
- Soul campfire/soul fire: haunting

---

# Starter Base Setup

## 20. Recommended Basic Base Blocks

Include normal Minecraft utility blocks:

- Crafting table
- Smithing table
- Grindstone
- Anvil
- Enchanting table
- Stonecutter

Then add Create systems:

- Power generation
- Pressing setup
- Compacting setup
- Mixing setup
- Washing/smoking/blasting/haunting setup
- Sawing setup
- Later: crushing wheels
- Later: mechanical crafters

---

## 21. Starter Power

Two large water wheels can power many early and mid-game machines.

Some machines, especially mixers, require a minimum speed. For example, some mixing recipes need at least **32 RPM**.

Use gear ratios or a rotation speed controller later to adjust speed.

---

## 22. Pressing Setup

A simple pressing setup should:

1. Take ingots from a chest/barrel.
2. Drop them onto a belt.
3. Move them under a mechanical press.
4. Output sheets into storage.

Use this for:

- Iron sheets
- Gold sheets
- Brass sheets
- Other pressed materials

---

## 23. Compacting Setup

A compacting setup uses:

- Basin
- Mechanical press
- Input storage
- Output storage

It can make things like blocks from ingots.

However, without filters, it may output the wrong item or process unexpected recipes.

Filters become important later.

---

## 24. Mixing Setup

A mixing setup uses:

- Basin
- Mechanical mixer
- Optional blaze burner underneath
- Optional water input

Useful early recipe:

- Wheat flour + water → dough

Leave room underneath the basin for a blaze burner, because brass production needs one.

---

## 25. Washing, Smoking, Blasting, and Haunting Setup

Use an encased fan blowing through different blocks to process items.

Examples:

- Wash wheat flour into dough
- Wash crushed ores into ingots
- Smoke food
- Haunt sand into soul sand once you have soul fire or soul campfires

A depot is simple early on, but belts and chutes become better for automation.

---

## 26. Sawing Setup

A saw can improve some crafting efficiency.

For example, cutting andesite alloy with a saw gives more shafts than the normal crafting recipe.

This adds up quickly.

---

## 27. Stressometer

A stressometer shows how close your system is to overstressing.

If your machines stop because the system is overstressed, add more power generation or reduce machine load.

---

# Filters

## 28. Basic Filters

Filters can be placed into blocks with a filter slot, such as:

- Brass funnels
- Smart chutes
- Basins
- Some saws
- Other smart Create blocks

A filter controls which items may pass through.

---

## 29. List Filter

A list filter can hold multiple items.

It has two main modes:

### Allow List

Only items in the list may pass.

### Deny List

Items in the list are blocked. Everything else may pass.

### Respect Data

Matches item data, such as durability or NBT.

Useful for things like removing a nearly broken sword from a deployer.

### Ignore Data

Matches only the item type. This is the default behaviour.

---

## 30. Attribute Filter

Attribute filters use item tags instead of exact items.

For example, you can filter by:

- Logs
- Placeable items
- Furnace fuel
- Tools and utilities
- Mod source
- Item category

Modes include:

- Allow any selected attribute
- Allow all selected attributes
- Deny selected attributes

This is useful when you want broad filtering, such as allowing all logs rather than only oak logs.

---

# Farms and Contraptions

## 31. Minecart Contraptions

Create contraptions are like easier, more flexible flying machines.

A simple minecart contraption needs:

- Powered rail
- Minecart
- Cart assembler
- Blocks attached to the cart
- Super glue if multiple blocks need to move together

Set the cart assembler to **lock rotation** for most farms.

---

## 32. Super Glue

Super glue connects blocks into one contraption.

Use it to define which blocks should move together.

To remove glue, left-click the glue area.

Some Create blocks, such as harvesters, automatically join contraptions without needing glue directly on them.

---

## 33. Crop Farm

A simple crop farm uses:

- Minecart contraption
- Mechanical harvesters
- Barrels for storage
- Rails
- Crops planted beside the rail path

Mechanical harvesters automatically:

- Harvest mature crops
- Replant them
- Store output in the attached inventory

This works for crops like wheat.

For sugar cane, leave it one block higher because harvesters do not replant sugar cane in the same way.

If sugar cane grows too tall, add another harvester higher up.

---

## 34. Tree Farm

A basic tree farm can use saws on a minecart contraption.

Saws will cut trees as the contraption moves.

Early on, you may need to replant saplings manually.

Later, a deployer can automate sapling placement.

---

# Nether Progression

## 35. Why You Need the Nether

To progress into brass, you need a **blaze burner**.

That means going to the Nether and finding blazes.

While there, collect quartz too. It is useful later.

---

## 36. Getting a Blaze Burner

Craft an **empty blaze burner**.

Then use it on:

- A blaze
- Or a blaze spawner, if your modpack allows interaction that way

This gives you a blaze burner.

Some Create setups let you move blaze spawners with minecart contraptions, which can make blaze farms much safer and easier.

---

# Brass Age

## 37. Making Brass

Brass is made from:

- Copper
- Zinc

You need:

- Basin
- Mechanical mixer
- Heated blaze burner underneath

Feed the blaze burner with fuel, then mix copper and zinc in the basin.

---

## 38. Fueling Blaze Burners

In base Create, blaze burners are fueled manually by right-clicking them with fuel.

Valid fuel includes anything that works in a furnace:

- Coal
- Logs
- Planks
- Sticks
- Lava buckets

Lava buckets last much longer than wood.

Some modpacks allow lava to be piped directly into blaze burners, but this is not part of base Create.

---

## 39. Brass Sheets

Brass ingots can be pressed into brass sheets.

Brass sheets unlock many smart Create components, including:

- Brass funnels
- Brass tunnels
- Mechanical arms
- Deployers
- Smart chutes
- Mechanical crafters
- Rotation speed controllers

---

# Mechanical Crafters and Crushing Wheels

## 40. Mechanical Crafters

Mechanical crafters require brass components and electron tubes.

Electron tubes require a lot of redstone, so redstone becomes important at this stage.

Mechanical crafters connect together and move items according to their arrows.

For large recipes, arrange the crafters in the shape of the recipe and make sure all arrows eventually point to the output.

Some older Create versions require a redstone signal to start crafting. Newer versions may start automatically once the recipe is complete.

---

## 41. Crushing Wheels

Crushing wheels require mechanical crafters.

They are one of the biggest upgrades in Create.

They can:

- Process ores
- Crush cobblestone into gravel
- Crush gravel into sand
- Crush netherrack into cinder flour
- Process many items at once
- Improve automation possibilities

Crushing wheels are much faster and more powerful than millstones.

---

## 42. Ore Processing

Crushing ore can improve output.

Example:

1. Crush ore into crushed ore.
2. Wash crushed ore.
3. Get ingots and possible bonus byproducts.

Silk Touch improves ore processing because you can process raw ore blocks more effectively.

---

## 43. Redstone Automation

Redstone can be made from:

- Potion of strength
- Cinder flour

Cinder flour comes from crushing netherrack.

Netherrack is not renewable in base Create, but it is extremely easy to mine, so this can still be much easier than mining redstone directly.

---

# Deployers, Mechanical Arms, and Smart Blocks

## 44. Deployer

A deployer uses rotational power and simulates player actions.

It can:

- Place blocks
- Use items
- Apply items to blocks
- Attack mobs when set to attack mode
- Use enchanted tools and weapons

Examples:

- Automatically make casings by applying alloy to stripped logs
- Plant saplings
- Kill mobs in XP farms
- Use looting or sweeping edge from a sword

Use a wrench on the hand section to change modes.

---

## 45. Mechanical Arm

A mechanical arm moves items between Create inventories and processing points.

It can take from and deposit into things like:

- Depots
- Belts
- Blaze burners
- Other Create-compatible targets

A common use is automatically feeding fuel into blaze burners.

---

## 46. Smart Observer

A smart observer is not the same as a vanilla observer.

It only emits a signal when the block or item in front matches its filter.

This makes it useful for controlled redstone automation.

---

# Create Redstone Components

## 47. Pulse Timer

Repeatedly emits a pulse based on a configured delay.

Useful for clocks.

---

## 48. Pulse Repeater

Delays a redstone pulse by a configured amount of time.

---

## 49. Pulse Extender

Extends the length of a redstone pulse.

If it receives another pulse before the timer ends, it can remain powered continuously.

---

## 50. Redstone Links

Redstone links are wireless redstone.

They use two filter slots to define a frequency.

One link sends, another receives.

Use a wrench to toggle receive mode.

The sender and receiver must have matching filters.

---

## 51. Analog Lever

An analog lever outputs a selected redstone power level.

For example, power level 5 sends redstone power five blocks.

---

## 52. Powered Latch and Powered Toggle Latch

These are used for more advanced redstone logic.

The powered toggle latch behaves like a toggle: one pulse turns it on, another pulse turns it off.

---

## 53. Redstone Contacts

Redstone contacts emit power when two contacts touch.

They are useful with contraptions, for example detecting when a moving contraption returns home.

---

# Storage and Contraptions

## 54. Item Vaults

Item vaults store many items.

They can connect into larger structures, similar to fluid tanks.

They are useful for bulk storage, especially when paired with other storage mods.

You interact with them using:

- Funnels
- Chutes
- Item hatches in newer versions

---

## 55. Other Contraption Blocks

Create has many contraption systems, including:

- Gantry shafts
- Gantry carriages
- Clockwork bearings
- Rope pulleys
- Elevator pulleys
- Contraption controls

### Rope Pulley

Useful for vertical mining machines and quarry-style builds.

### Elevator Pulley

Useful for making working elevators.

### Contraption Controls

Contraption controls can enable or disable specific tools on a contraption, such as saws or drills.

They are also used with elevators to select floors.

---

## 56. Ponder Menu

Hold **W** over many Create items to open the Ponder menu.

The Ponder menu shows animated explanations of how blocks work.

This is one of the best ways to learn Create.

---

# Trains

## 57. Basic Train Setup

To make a train:

1. Place train tracks.
2. Place a train station on the track.
3. Use the station to create a new train.
4. Place train casings to create bogeys.
5. Build a carriage structure.
6. Add a seat.
7. Add train controls.
8. Glue the train together.
9. Assemble it from the station.

You can then sit in the train and drive it.

---

## 58. Train Storage

You can add storage to trains using:

- Item vaults
- Fluid tanks
- Portable storage interfaces
- Portable fluid interfaces

This lets trains transport items and fluids between distant locations.

Train tracks do not need to remain loaded the entire way. If the destination is loaded, trains can travel long distances and arrive after the expected travel time.

---

# Copper and Fluids

## 59. Copper Components

Copper casings and copper machines are mainly used for fluid systems.

Important fluid components include:

- Fluid tanks
- Fluid pipes
- Mechanical pumps
- Smart fluid pipes
- Hose pulleys

---

## 60. Fluid Tanks

Fluid tanks connect into larger tanks.

You can build them wider and taller to increase capacity.

---

## 61. Fluid Pipes and Mechanical Pumps

Fluid pipes connect fluid sources to tanks or machines.

A mechanical pump is required to actually move the fluid.

Use the wrench or placement direction to ensure the pump arrows face the correct way.

Faster pump rotation moves fluid faster.

---

## 62. Smart Fluid Pipes

Smart fluid pipes filter fluids.

Examples:

- Allow only water
- Allow only lava
- Allow only a specific potion
- Filter XP fluid if your setup uses it

This is very useful for potion automation.

---

## 63. Hose Pulley

The hose pulley can draw from or fill large bodies of fluid.

If a fluid body has more than 10,000 source blocks, Create treats it as bottomless.

This works for:

- Water
- Lava
- Other fluids depending on the modpack

A hose pulley can also fill an area with fluid, up to a limit.

For lava, a hose pulley in a huge lava lake or the Nether can create effectively infinite lava access.

---

# Steam Engines

## 64. Steam Engine Basics

Steam engines produce huge amounts of stress units.

A boiler needs:

- Fluid tanks
- Blaze burners underneath
- Water input
- Steam engine blocks attached

Engineer’s goggles show boiler stats:

- Size
- Heat
- Water
- Boiler level
- Stress capacity

---

## 65. Boiler Size

Increase boiler size by increasing the tank structure.

A larger tank gives more boiler size.

A common useful setup is a 3x3 tank up to four blocks tall.

---

## 66. Boiler Heat

Heat comes from blaze burners underneath the boiler.

Fuel the blaze burners with:

- Lava
- Coal
- Charcoal
- Other furnace fuels

Lava is excellent because it lasts a long time.

Automate fueling with a mechanical arm.

---

## 67. Boiler Water

Water must be pumped into the boiler.

A simple infinite water source with a mechanical pump works.

Make sure water input is sufficient for your boiler size and heat level.

---

## 68. Steam Engine Output

Each steam engine block can only output part of the boiler’s available stress capacity.

To use the full boiler output, attach enough steam engines.

Once you have a strong steam engine setup, you can power most of your base at useful speeds.

---

# Workshop and Midgame Progression

## 69. Rotation Speed Controller

A rotation speed controller lets you choose target RPM.

It requires a precision mechanism, so it is expensive early on.

Use it to cleanly power machines at the exact speed you want.

---

## 70. Precision Mechanism

Precision mechanisms are made through a deployer assembly process.

They require repeated application of:

- Cogwheel
- Large cogwheel
- Iron nugget

Onto a gold sheet.

The process has a chance to fail and create random scrap.

Because this is tedious, automate it as soon as possible.

---

## 71. Why Build a Workshop

A workshop gives you a central place for general-purpose automation.

It should include machines for:

- Pressing
- Compacting
- Mixing
- Washing
- Smoking
- Blasting
- Haunting
- Sawing
- Crushing
- Mechanical crafting
- Precision mechanisms
- Casing production
- Ore processing
- Storage
- Power generation

A good workshop prevents every small task from needing a dedicated farm.

---

# Schematics

## 72. What Schematics Do

Schematics let you copy builds into another world.

They are useful for importing workshops, farms, or machines.

---

## 73. Using a Schematic

You need:

- Schematic table
- Empty schematic
- `.nbt` schematic file
- Schematic cannon
- Gunpowder
- Required building materials

Basic process:

1. Download an `.nbt` schematic file.
2. Put it in your Minecraft schematics folder.
3. Use the schematic table to load it onto an empty schematic.
4. Place the schematic preview in the world.
5. Put the schematic into a schematic cannon.
6. Add gunpowder.
7. Print a material checklist with a book or clipboard.
8. Supply the required items.
9. Start the cannon.

---

## 74. Schematic Cannon Settings

Useful settings include:

- Do not replace solid blocks
- Replace solid with solid
- Replace solid with any
- Replace solid with empty
- Skip missing blocks
- Protect block entities

Use these carefully depending on the build location.

---

# Late-Game Automation

## 75. Goals After the Workshop

Once you have a workshop and steam power, Create opens up massively.

Possible goals:

- Automatic XP farm
- Automatic enchanting
- Ore processing facility
- Cobblestone generator
- Gravel and sand generator
- Andesite, diorite, and granite farms
- Large train network
- Steam engine silos
- Full factory automation
- Automated production of every Create component

---

## 76. Create 6.0 Late Game

Create 6.0 adds major late-game systems, including:

- Package logistics
- Factory boards
- Frogports
- Chain conveyors
- More advanced item request systems

These systems allow factories to automatically request, craft, stock, and distribute items.

This is mostly late-game content and is more complicated than the beginner and midgame systems.

---

## 77. Final Advice

Create is about automation.

When you need a resource, ask:

> Can I automate this instead of manually gathering it?

Examples:

- Need sand? Make cobblestone, crush it into gravel, then crush gravel into sand.
- Need redstone? Crush netherrack into cinder flour and combine it with strength potions.
- Need wood? Build a tree farm.
- Need brass? Automate copper, zinc, mixing, and blaze burner fueling.

The Create mod rewards experimentation. Use JEI and the Ponder menu constantly.

Start small, automate one thing at a time, and gradually build toward a factory that produces everything you need.