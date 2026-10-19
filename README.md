# TianmingTrial

[简体中文](README.zh-CN.md)

Creator: Eric_SXR

A twelve-real-day Tianming event with inheritable progress, abilities, HUD, and disguises.

## Features

- Run the event and manage its state with `/tianming`.
- Configure abilities and activity rules in YAML.
- Inspired by that popular short drama, and the result feels delightfully juicy.

## Compatibility

- Plugin version: `1.0.0` (from `plugin.yml`).
- Target: Paper/Purpur 1.20.1, API `1.20`.
- Build JDK: 17.
- Required plugins: None declared.
- Optional plugins: ProtocolLib.

## Build and install

1. Run `mvn package` from this repository using JDK 17.
2. Copy the built JAR from `target/` to your server's `plugins/` directory.
3. Restart the server and edit the generated plugin configuration as needed.

## Commands and configuration

- Registered commands: `/tianming`. See `src/main/resources/plugin.yml` for permissions and usage.
- Main configuration sections: `activity`, `disguise`, `hud`, `test`. See `src/main/resources/config.yml`.
