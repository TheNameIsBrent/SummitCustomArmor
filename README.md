# SummitCustomArmor

A Paper/Spigot plugin for custom armor items.

## Setup

Before pushing to GitHub, you need to generate the Gradle wrapper jar **once** on your machine:

```bash
gradle wrapper --gradle-version 8.8
```

This creates `gradle/wrapper/gradle-wrapper.jar` which must be committed alongside the other files.
After that, GitHub Actions will handle all future builds automatically.

## Commands

| Command | Description |
|---|---|
| `/ca give <piece> [player]` | Give a custom armor piece |
| `/ca reload` | Reload config.yml |

Aliases: `/customarmor`

## Permissions

| Permission | Default |
|---|---|
| `customarmor.give` | op |
| `customarmor.reload` | op |
