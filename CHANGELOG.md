# Changelog

All notable changes to Discord Presence are documented here.

## 1.0.6

### Added

- **Show session timer** setting. Turning it off removes the elapsed-time
  counter from the Discord presence; turning it back on restores it.
- Open-source project structure with a reproducible Gradle build and release
  workflow.

### Changed

- The Discord application ID is injected at build time and is not exposed as a
  player-facing setting.

## 1.0.5

### Added

- NeoForge configuration screen available from **Mods → Discord Presence →
  Config**.
- **Enable Discord presence** toggle, which disconnects or reconnects the
  presence without restarting the game.

### Changed

- Configuration translations are provided explicitly for the NeoForge screen.

## 1.0.4

### Changed

- New 258×258 Material-style icon, embedded as the mod logo.

## 1.0.3

### Changed

- The presence state now shows only the server/world name and the online player
  count.
- Server addresses and raw dimension IDs such as `minecraft:cb` are never
  displayed.
- Custom dimensions are omitted instead of exposing their resource IDs.

## 1.0.0

### Added

- Initial Discord Rich Presence integration for Minecraft 26.1.x on NeoForge.
