# Dev Environment Setup — Hardcore Achievements Plus

Fabric mod for **Minecraft 26.2** · Java 25 · Gradle (wrapper included) · VSCode

## 1. Install JDK 25 (one-time, macOS)

```sh
brew install --cask temurin@25
```

Verify:

```sh
java -version   # should report version 25
```

If `java` isn't found or shows an older version, add to `~/.zshrc`:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
```

## 2. Install VSCode extensions

Open this folder in VSCode — it will prompt to install the recommended extensions
(**Extension Pack for Java**, **Gradle for Java**). Accept, or install manually from
the Extensions panel.

## 3. First build

VSCode's Java extension imports the Gradle project automatically on open
(first import downloads Minecraft + dependencies; takes a few minutes).

Or from the terminal:

```sh
./gradlew build
```

Generate VSCode launch configs (adds Run/Debug entries for the Minecraft client and server):

```sh
./gradlew vscode
```

## 4. Run the game

```sh
./gradlew runClient   # launches Minecraft with your mod
./gradlew runServer   # dedicated server
```

Or use the launch configs from step 3 via VSCode's Run and Debug panel (F5) — this
gives you breakpoints and hot code reload.

## Project layout

- `src/main/java` — common (server + client) code. Entrypoint: `HardcoreAchievementsPlus`
- `src/client/java` — client-only code. Entrypoint: `HardcoreAchievementsPlusClient`
- `src/main/resources/fabric.mod.json` — mod metadata
- `*.mixins.json` — mixin configs (bytecode injection into vanilla classes)
- `gradle.properties` — Minecraft/loader/Fabric API versions (check https://fabricmc.net/develop for updates)

Mod ID: `hardcore_achievements_plus` · Package: `com.xcusestudios.hardcoreachievementsplus`

Note: since Minecraft 26.1, Mojang ships unobfuscated jars — no more yarn mappings;
you code directly against official class names (e.g. `net.minecraft.client.Minecraft`).

Built jars land in `build/libs/`.
