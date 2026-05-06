# Joining the server

## What you need

- **Minecraft Java 1.21.1**
- **NeoForge 21.1.227** (loader)
- The same **mod folder** as the server (≈45 jars)
- The same **client configs** for a few mods (Xaero, voicechat) — without these,
  some features won't behave correctly even though the world will load

## Easiest path

Get the modpack zip from Greg. Drop the `mods/` and `config/` folders into
your launcher's instance directory.

PrismLauncher:
```
~/.local/share/PrismLauncher/instances/<your-instance>/minecraft/
```

## Then connect

Add the server in the multiplayer screen:

- **Address:** `46.225.17.145`
- Port is the default `25565`, no need to specify

## Voice chat

[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) is
proximity-based — players near you sound near. It connects automatically when
you join. UDP port `24454` needs to be open on your end (most home networks
are fine).

## Common first-join issues

| Symptom | Cause |
|---|---|
| Server refuses connection | Missing required mod on your client |
| "Ghost holes" in builds at hub | Client missing a mod that owns those blocks |
| Crash on world load | Mod version mismatch — make sure your jars match the modpack exactly |
| Voice chat says "disconnected" | Outbound UDP/24454 blocked by your network |
| You appear at `-136, 73, -162` | That's spawn — within 100 blocks, hostiles are auto-killed |

## What's at spawn

- A **safe zone**: hostile mobs are silently killed within ~100 blocks of
  spawn coords (`-136, 73, -162`)
- The **trading hub** is here — set up a shop, look at others'
- Currency machines (printer, depositor) live near the hub. See
  [Specialization & quality](specialization.md) for how money is earned.
