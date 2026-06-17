# Dams's Soul Covenant

**Dams's Soul Covenant (DSC)** is a Paper plugin for Minecraft that adds a soul-based progression system, karmic item states, altar interactions, pledge logic, and world-level consequences for the Ataraxia server.

The plugin is built around seven souls, each tied to a different style of play. The exact unlock conditions are intentionally not documented here so the progression stays discoverable in-game.

## Features

- Seven unique souls with their own passive and active behavior
- Event-driven soul progression with chat feedback like `(1/30)`
- Soul item states including healthy, corrupted, and shattered
- Karma-based item progression and visual feedback
- Soul altar interaction and resonance handling
- Pledge system with integrity tracking
- Patience chest support with configurable coordinates
- Persistent metadata stored on soul items through Paper's item data APIs

## Soul System

Each soul is tracked independently and reacts to different gameplay events.

- `Kindness` rewards supportive play and healing-focused actions
- `Bravery` responds to combat and survival pressure
- `Determination` watches long-term server-wide persistence
- `Justice` follows kill-streak style behavior
- `Patience` is tied to long-form endurance and a configurable hidden chest location
- `Integrity` is driven by pledge fulfillment
- `Perseverance` tracks repeated endurance and follow-through

The progress logic is surfaced in chat when the relevant event happens, but the underlying conditions are kept inside the tracker classes.

## Commands

### `/soul`

Admin command for granting, inspecting, dropping, and listing souls.

Permission:

- `dsc.admin.soul`

### `/pledge`

Used for creating and managing soul pledges between players.

## Requirements

- Minecraft Paper `1.21.11`
- Java `21`
- Gradle build system

## Installation

1. Build the plugin:

```bash
./gradlew build
```

2. Copy the generated jar from `app/build/libs/` into your server's `plugins/` folder.
3. Start or restart the server.
4. Edit `config.yml` if you want to change altar or Patience chest coordinates.

## Configuration

The plugin ships with a location-based configuration file for altar anchors and the Patience chest.

### Altar

The altar section defines the main ritual center and the anchor points for each soul.

```yml
altar:
  search-radius: 12
  center:
    enabled: true
    world: world
    x: -165
    y: 131
    z: -127
```

### Patience chest

Patience can be bound to a hidden chest in a cave or any other location you choose.

```yml
patience:
  chest:
    enabled: false
    world: world
    x: 0
    y: 0
    z: 0
```

Set `enabled: true` and change the coordinates to your encrypted location when you want the chest path active.

## Technical Notes

- The plugin uses Paper's event system for most progression checks.
- Soul items store metadata in `PersistentDataContainer` so they remain identifiable across restarts.
- Passive effects are applied through scheduled tasks rather than per-tick world scanning.
- `SoulStateManager` continuously evaluates the global soul state and drives fracture/resonance behavior.
- The Patience chest is resolved from config at runtime, which means the location can be moved without changing code.
- The project targets Java 21 and uses UTF-8 resource processing in Gradle.

## Project Structure

```text
app/src/main/java/me/st4r/DSC/
  altar/           Soul altar logic
  listener/        Gameplay listeners and soul progression hooks
  passive/         Passive effects and effect scheduling
  pledge/          Pledge management and integrity handling
  soul/            Soul item, registry, manager, and commands
  tracker/         Progress trackers for each soul
  world/           Global soul-state evaluation and world effects
```

## Behavior Overview

The plugin is designed to feel reactive:

- when players trigger a soul event, they receive immediate progress feedback
- when a soul is granted, the item is tracked and rendered with its current state
- when a soul becomes corrupted or shattered, the item state changes and the rest of the system reacts
- when the altar and world-state conditions line up, the server enters more dramatic phases

## Notes for Server Owners

- Keep the Patience chest coordinates private if you want the location to remain hidden from players.
- Review soul-related permissions before giving out `/soul`.
- If you use custom worlds, make sure the configured world names match the loaded server worlds.

## Development

To build locally:

```bash
./gradlew build
```

The resulting jar will be written to:

```text
app/build/libs/
```

## License

No license is currently specified in this repository.
