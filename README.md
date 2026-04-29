# SummitCustomArmor

A Paper/Spigot plugin providing custom armor items with a proc system for Summit servers.

## Setup

This project uses Gradle. GitHub Actions will build it automatically on every push via `.github/workflows/gradle.yml`.

To build locally (requires JDK 21):
```bash
gradle build
```

The compiled jar will be in `build/libs/`.

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/ca give <piece> [player]` | Give a custom armor piece | `customarmor.give` (op) |
| `/ca reload` | Reload config.yml | `customarmor.reload` (op) |
| `/ca check` | Show how many custom pieces you're wearing | `customarmor.check` (all) |

**Alias:** `/customarmor`

**Pieces:** `chestplate`, `leggings`, `boots`

---

## Proc System

When a player wearing custom armor mines a block, harvests a crop, or catches a fish, there is a chance a reward command fires.

### Formula

```
finalChance = min(maxChance, (pieces * baseChance) * setBonus)
```

### Default Values (config.yml)

| Setting | Value |
|---|---|
| `proc.base-chance` | `0.02` per piece |
| `proc.max-chance` | `0.15` (15% hard cap) |
| Set bonus — 1 piece | `1.0×` |
| Set bonus — 2 pieces | `1.1×` |
| Set bonus — 3 pieces | `1.25×` |

### Chance Table

| Pieces | Calculation | Final Chance |
|---|---|---|
| 1 | (1 × 0.02) × 1.0 | 2.0% |
| 2 | (2 × 0.02) × 1.1 | 4.4% |
| 3 | (3 × 0.02) × 1.25 | 7.5% |

### Trigger Events

- **Mining** — any block breakable by pickaxe, axe, or shovel
- **Fishing** — catching a fish (`CAUGHT_FISH` state)
- **Crop harvesting** — breaking wheat, carrots, potatoes, beetroots, nether wart, cocoa, or sweet berry bushes

### Reward Command

Configurable in `config.yml` under `proc.command`. Use `%player%` as a placeholder:
```yaml
proc:
  command: "crate give %player% crate_lost physical 1"
```

---

## Configuration

```yaml
proc:
  base-chance: 0.02
  max-chance: 0.15
  set-bonus:
    1: 1.0
    2: 1.1
    3: 1.25
  command: "crate give %player% crate_lost physical 1"

armor:
  chestplate:
    name: "&bKey Finder Chestplate"
    material: DIAMOND_CHESTPLATE
  leggings:
    name: "&bKey Finder Leggings"
    material: DIAMOND_LEGGINGS
  boots:
    name: "&bKey Finder Boots"
    material: DIAMOND_BOOTS
```
