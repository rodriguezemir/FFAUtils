# FFAUtils — Paper 1.18.2 plugin

## Build & test

```sh
./gradlew build           # compiles + runs tests + shadowJar
./gradlew test             # runs all tests
./gradlew shadowJar        # fat JAR to build/libs/
```

The build targets `io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT` and compiles with a JDK 21 toolchain plus `options.release = 17`, so the plugin is Java 17 bytecode and also loads on a Java 17 server JVM.

Gradle configuration cache is on. If you change `build.gradle.kts`, run with `--no-configuration-cache` or clear `.gradle/configuration-cache/`.

## Run a local server

```sh
./gradlew runServer        # Paper 1.18.2, 2GB RAM, auto-agrees to EULA
```

Server runs in `build/runServer/` on a Java 21 launcher. Plugin JAR is deployed automatically.

## Project layout

```
src/
  main/
    java/site/zvolcan/fFAUtils/
      FFAUtils.java              # main class (static singleton via @Getter)
      FFAPlaceholders.java       # PlaceholderAPI expansion
      managers/                  # business logic — stateful singletons
      commands/                  # Bukkit command implementations
      commands/abs/              # FFACommand interface (CommandExecutor + TabCompleter)
      listeners/                 # Bukkit event handlers
      objects/                   # value objects (DeathEvent, Kit, FFAPlayer, etc.)
      providers/                 # external tier APIs behind the abstract TierProvider
      util/                      # PluginUtils, ItemBuilder, NonBlockingHttpClient
    resources/
      plugin.yml                 # plugin descriptor (commands, permissions, api-version 1.18)
      config.yml                 # main config
      messages.yml               # message strings
      death-messages.yml         # death event message pool
      spawn-lobby-items.yml      # lobby hotbar items
  test/
    java/site/zvolcan/fFAUtils/            # test classes, mirroring the main packages
```

## Architecture

- **Plugin descriptor**: `plugin.yml` with `api-version: "1.18"`. Version expanded from Gradle via `processResources`. Paper 1.18.2 has no `paper-plugin.yml` and no `LifecycleEvents` or Brigadier command API, so neither exists here any more.
- **Commands**: classic Bukkit commands. Every `commands/*Command` implements `commands/abs/FFACommand` (Bukkit `CommandExecutor` + `TabCompleter`) and must be declared under `commands:` in `plugin.yml`; `managers/CommandManager` binds each one with `Plugin#getCommand(name).setExecutor(...)` / `setTabCompleter(...)` and throws if a command is not declared. Tab completion is therefore manual — `FFACommand#matching` reproduces the prefix matching the Brigadier tree used to do for free.
- **Managers**: instantiated in `onEnable()`, stored as `@Getter` fields on the main class. Accessed via `FFAUtils.getInstance().getXxxManager()`.
- **Persistence**:
  - SpawnManager, KitManager: **per-file JSON** via Gson (`spawns/<name>.json`, `kits/<name>.json`)
  - DeathEventManager: **YAML list** via `YamlConfiguration` (`death-messages.yml`)
  - StatsManager: **HikariCP** connection pool to SQL database
- **Utilities**: `site.zvolcan.fFAUtils.util.PluginUtils`, reached via `FFAUtils#getUtils()`, replaces the removed `mcdev-utils` library — same `message`, `broadcast`, `chat` and `ib` helpers. `util/ItemBuilder` and `util/NonBlockingHttpClient` live beside it.
- **PlaceholderAPI**: optional dependency (`required: false`), expansion registered in `onEnable()`.
- **Tier providers**: `TierProvider` is an abstract class holding everything common to an external tier API — the async `java.net.http.HttpClient` call, Gson parsing into `TierProfile`, and a per-UUID cache with TTL and in-flight de-duplication. Subclasses only declare what differs. Every one of these differences breaks lookups **silently** if got wrong, so verify against the live API when touching them:

| | `McTiersProvider` | `PvpTiersProvider` | `EliteStormProvider` |
|---|---|---|---|
| Endpoint | `mctiers.com/api/v2/profile/{uuid}` | `pvptiers.com/api/profile/{uuid}` | `api.elitestorm.es/v2/users?nickname=` / `?uuid=` |
| Player id | path segment | path segment | **query parameter** |
| UUID form | **with** dashes | **without** (dashed → 400) | **without** (dashed → 422) |
| "No profile" | 404 | **422** | 404 (422 here means *bad query*, not unknown player) |
| Name lookup | no | no | **yes, preferred** |
| Payload | map of slug → `{tier,pos}` | same | **array of `{mode,tier:"LT4",isRetired}`** |
| Gamemode slugs | `nethop`, `vanilla` | `neth_pot`, `crystal` | numeric ids, guild-scoped |

  MCTiers and PvPTiers return the same JSON shape, so `parseProfile` has a working default they both reuse; EliteStorm overrides it. Slugs are translated between sites via `getGamemodeAliases()` — `vanilla` and `crystal` exist on only one site each and are deliberately not aliased, so a player simply has no ranking there.

  **Threading contract — nothing HTTP-related may touch the main thread.** Every lookup is dispatched with `CompletableFuture.supplyAsync(..., executor)` onto `TierManager`'s dedicated `FFAUtils-Tiers` pool, which is also the `HttpClient`'s executor, so URL building, request construction, the send, response handling and Gson parsing all happen off-tick. Results come back via `TierManager.runOnMain`. Two rules when editing this code:
  - Never call `HttpClient.close()` — it **blocks** until in-flight requests finish (measured: 5s with one stalled request, i.e. 100 dropped ticks). Use the non-blocking `shutdown()`. Same for `ExecutorService`.
  - Never call `.join()` / `.get()` on a lookup future from plugin code.

  `TierProviderAsyncTest` enforces both by pointing a provider at a local server that accepts and never answers; anything that blocks blocks for the full timeout and the test fails.

  Two EliteStorm specifics worth knowing:
  - It is looked up **by nickname by default**, which is what makes the gate work on offline-mode servers where Bukkit's UUID is generated locally and never matches the Mojang UUID the other sites are keyed by. Results are still cached under the UUID.
  - Its gamemode ids are **scoped to a Discord guild**, so the id → slug vocabulary is loaded from `/v2/modes?guildId=` by the `warmUp()` lifecycle hook. `EliteStormProvider.DEFAULT_MODE_SLUGS` is only correct for `DEFAULT_GUILD_ID` and is a fallback if that call fails.

## Persistence quirks

- Spawn and kit files live in subfolders of the plugin data folder, one file per entity.
- Gson serialization for ItemStack requires `normalizeTypes()` (Integer vs Double coercion) in KitManager — do not remove.
- `death-messages.yml` is deployed via `saveResource("death-messages.yml", false)` — it will NOT overwrite existing files.

## Dependencies (relocated by Shadow)

| Library | Relocated to |
|---|---|
| `com.google.code.gson:gson:2.11.0` | `site.zvolcan.fFAUtils.libs.gson` |
| `com.zaxxer:HikariCP:5.1.0` | `site.zvolcan.fFAUtils.libs.hikari` |
| `fr.mrmicky:fastinv:3.1.2` | `site.zvolcan.fFAUtils.fastinv` |

`com.github.putindeer:mcdev-utils` was removed: it is compiled against 1.20.5+ API that does not exist on 1.18.2, and the plugin now ships its own `util` package instead. `fr.mrmicky:fastinv:3.1.2` stays because it only uses pre-1.18 Bukkit API. PlaceholderAPI 2.11.6 is `compileOnly`/`testImplementation`, not relocated.

## Testing

- **MockBukkit v1.18** (`com.github.seeseemelk:MockBukkit-v1.18:2.85.2`, package `be.seeseemelk.mockbukkit`) — not plain Mockito — provides a mocked Paper 1.18.2 server environment.
- Tests use `@TempDir` for filesystem isolation.
- No MockBukkit extension is used: server-backed tests call `MockBukkit.mock()` in setup and `MockBukkit.unmock()` in teardown themselves, while the rest test YAML/JSON config logic at the unit level.
- Test files are in `src/test/java/` mirroring the main source tree.

## Config files

- **config.yml**: currently empty, populated at runtime by plugin defaults.
- **messages.yml**: MiniMessage format (`<gradient:...>`, `<red>`, `<green>`). Prefix uses gradient.
- **spawn-lobby-items.yml**: slot index → material, display name, command mapping.

## CI

- `.github/workflows/build.yml`: runs `shadowJar` on every push/PR (Java 21, Temurin, Gradle cache).
- Tests run transitively (shadowJar depends on build → depends on test).

## Lombok

Used throughout. Annotation processor is configured in build.gradle.kts — both `compileOnly` and `annotationProcessor` scopes.
