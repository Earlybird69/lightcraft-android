# LightCraft for Android: the Android build only

This repository holds only what the Android phone app of
[LightCraft](https://github.com/storytold/lightcraft) needs to build:

- `apps/lightcraft-mobile`: the app (Kotlin and Jetpack Compose), with a small Rust crate that
  exposes LightCraft's engine to it through UniFFI;
- `crates/`: the 21 engine crates the app compiles;
- `assets/`: the few files they use (three camera profiles, the Inter font, the app icon).

It is a trimmed copy of the full LightCraft workspace, for comparison. The desktop app, the CLI, the
browser build, the MCP server, the desktop UI and the project's tooling (`xtask`) are left out. The
full workspace with the same Android app is
[lightcraft-android-test](https://github.com/bearinmindcat/lightcraft-android-test).

## Build

See [apps/lightcraft-mobile/README.md](apps/lightcraft-mobile/README.md): the Android SDK and NDK,
`cargo install cargo-ndk`, then `./gradlew installDebug` in `apps/lightcraft-mobile/android`.

## Licence

LightCraft is Copyright (c) 2026 ArtCraft Team and the LightCraft contributors, licensed under the
MIT License or the Apache License 2.0, at your option (`LICENSE-MIT`, `LICENSE-APACHE`, `NOTICE`).
Third-party material and its licences are listed in `assets/ATTRIBUTION.md` (Inter: SIL Open Font
License 1.1, `assets/fonts/OFL-Inter.txt`).
