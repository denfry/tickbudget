# TickBudget

<p align="center">
  <img src="https://raw.githubusercontent.com/denfry/tickbudget/main/.github/assets/logo.png" alt="TickBudget Logo" width="128" onerror="this.style.display='none'"/>
</p>

<p align="center">
  <strong>Tick budget runner, fair-share time pool, and thread diagnostics for Paper and Folia.</strong>
</p>

<p align="center">
  <a href="https://github.com/denfry/tickbudget/actions/workflows/ci.yml"><img src="https://github.com/denfry/tickbudget/actions/workflows/ci.yml/badge.svg" alt="CI Status" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License" /></a>
  <a href="https://openjdk.org/projects/jdk/21/"><img src="https://img.shields.io/badge/java-21%2B-orange.svg" alt="Java 21+" /></a>
  <a href="https://papermc.io"><img src="https://img.shields.io/badge/platform-Paper%20%7C%20Folia-brightgreen.svg" alt="Platforms" /></a>
  <a href="https://bstats.org/plugin/bukkit/TickBudget/34533"><img src="https://img.shields.io/badge/bStats-34533-informational.svg" alt="bStats Metrics" /></a>
</p>

---

## 🌟 Why TickBudget?

In Minecraft server development, heavy operations (block iteration, entity scans, chunk inspections, inventory processing) often suffer from two major problems:

1. **Tick Spikes & Freezes:** Plugins attempt large tasks in single ticks, spiking MSPT past 50ms and dropping server TPS.
2. **The "Tragedy of the Commons":** Multiple plugins each taking "just 5ms" together exhaust the tick window, starving other plugins and the game engine.
3. **Folia Concurrency Pitfalls:** Accessing world state, entities, or blocks from the wrong thread or scheduler leads to crashes or data corruption.

**TickBudget** solves this by providing:
* ⏱️ **Predictable Tick Budgeting:** Slice long-running workloads across consecutive ticks with strict millisecond budgets.
* ⚖️ **Adaptive Fair-Share Pool:** Dynamic pooling based on the server's own work in the previous tick (`ServerTickEndEvent`), ensuring guaranteed execution time (`floor`), weighted max-min sharing between plugins (`LOW`, `NORMAL`, `HIGH`, `CRITICAL`), and round-robin between a plugin's own tasks.
* 🌐 **Zero-Headache Multi-Platform Support:** Single `Target` abstraction (`global()`, `region(loc)`, `entity(entity)`, `async()`) that routes tasks to the correct scheduler on Paper and Folia automatically.
* 🔍 **Thread Ownership Verification (`assertOn`):** Detects illegal cross-region or off-thread access before it corrupts world state.
* 🚀 **Safe Async Helpers:** Unified, reliable asynchronous chunk loading and teleportation across platforms.
* 📊 **Admin Telemetry & bStats:** Live monitoring via `/tickbudget status` and `/tickbudget top` showing exact CPU millisecond usage per plugin over the last minute.

---

## 🏛️ Architecture

```
tickbudget/
├── tickbudget-api        # Thin public API (compileOnly for consumer plugins)
├── tickbudget-core       # Budget runner, fair-share dispatcher, metrics (independent of server APIs)
├── tickbudget-paper      # Paper bridge: single-thread dispatch, ServerTickEndEvent tick timing
├── tickbudget-folia      # Folia bridge: regionized threading & scheduler bridges
├── tickbudget-plugin     # Server plugin: Bukkit service, admin commands, bStats, config.yml
└── tickbudget-testplugin # Live integration test suite for automated CI & server tests
```

---

## 🖥️ For Server Owners

TickBudget is a library plugin: on its own it changes nothing, it only paces plugins that use its API.

1. Download `TickBudget-<version>.jar` from [Modrinth](https://modrinth.com/plugin/tickbudget) or [GitHub Releases](https://github.com/denfry/tickbudget/releases).
2. Put it into `plugins/` and restart. No setup is needed, the defaults suit most servers.
3. Run `/tickbudget status` to see how much tick time each plugin that uses TickBudget spends.

Install it when a plugin you run lists TickBudget as a dependency or recommends it.

---

## 📦 For Developers: Getting Started

### 1. `plugin.yml`
Use `depend` if your plugin cannot work without TickBudget:
```yaml
depend: [TickBudget]
```
or `softdepend` together with a fallback (see [Optional dependency](#5-optional-dependency-softdepend-with-a-fallback)) if it should also run on servers without it:
```yaml
softdepend: [TickBudget]
```

### 2. Gradle (Kotlin DSL)
The API is published through [JitPack](https://jitpack.io/#denfry/tickbudget) (only the API module is published there, so the artifact is simply `tickbudget`); it is not on Maven Central.
```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.denfry:tickbudget:v0.3.0")
}
```

### 3. Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.denfry</groupId>
    <artifactId>tickbudget</artifactId>
    <version>v0.3.0</version>
    <scope>provided</scope>
</dependency>
```

Without a repository: every [GitHub release](https://github.com/denfry/tickbudget/releases) has `tickbudget-api-<version>.jar` attached, use it as `compileOnly(files("libs/tickbudget-api-0.3.0.jar"))`.

Do not shade the API into your plugin: the classes come from the TickBudget plugin at runtime.

---

## 💡 Code Examples

### 1. Chunked Collection Iteration
Process a large collection of items or blocks without ever dropping TPS:

```java
import dev.denfry.tickbudget.api.*;

TickBudget tb = TickBudget.of(plugin);

List<Block> blocksToProcess = ...;

TaskHandle handle = tb.run(Target.region(location), BudgetedTask.iterate(blocksToProcess, block -> {
    // Verified to run on the region's owning thread
    block.setType(Material.AIR);
}))
.name("clear-ruins-blocks")
.budget(Budget.millisPerTick(1.5)) // Max 1.5ms per tick
.priority(Priority.NORMAL)
.onComplete(() -> getLogger().info("Cleared all blocks safely!"))
.onError(error -> getLogger().severe("Task encountered an error: " + error.getMessage()))
.start();
```

### 2. Custom Stepped Workflow
For tasks whose progress isn't a simple collection:

```java
tb.run(Target.entity(player), () -> {
    // Perform a small slice of work
    boolean hasMore = processBatch();
    return hasMore ? StepResult.MORE : StepResult.DONE;
})
.budgetMillis(2.0)
.priority(Priority.HIGH)
.start();
```

### 3. Step Results
A step returns one of:

| Result | Meaning |
|---|---|
| `MORE` | More work remains; another step may run in this tick if the budget allows. |
| `YIELD` | Nothing more to do in this tick, but the task is not finished; it continues on a later tick. *(since 0.3.0)* |
| `DONE` | The task is finished; `onComplete` runs. |

### 4. Long-Lived Queue Workers (`YIELD`)
A worker that drains a queue which is sometimes empty should yield instead of returning `MORE`, otherwise an idle
worker spins through its whole budget every tick:

```java
Queue<Chunk> pending = new ArrayDeque<>();

tb.run(Target.global(), () -> {
    Chunk chunk = pending.poll();
    if (chunk == null) {
        return StepResult.YIELD; // idle: one cheap step per tick
    }
    fixChunk(chunk);
    return StepResult.MORE;
})
.name("chunk-fixer")
.budgetMillis(3.0)
.priority(Priority.LOW)
.start();
```

### 5. Optional Dependency (`softdepend`) with a Fallback
Keep every TickBudget call in a nested class that is loaded only when the plugin is present, and fall back to a
plain timer with a fixed per-tick budget otherwise:

```java
public final class Pacer {

    /** Runs {@code step} until it returns false. */
    public static void run(Plugin plugin, String name, double millisPerTick, BooleanSupplier step) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("TickBudget")) {
            WithTickBudget.run(plugin, name, millisPerTick, step);
            return;
        }
        long budgetNanos = (long) (millisPerTick * 1_000_000);
        new BukkitRunnable() {
            @Override
            public void run() {
                long deadline = System.nanoTime() + budgetNanos;
                do {
                    if (!step.getAsBoolean()) {
                        cancel();
                        return;
                    }
                } while (System.nanoTime() < deadline);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // Not loaded unless TickBudget is enabled, so its classes are never resolved without it
    private static final class WithTickBudget {
        static void run(Plugin plugin, String name, double millisPerTick, BooleanSupplier step) {
            TickBudget.of(plugin)
                    .run(Target.global(), () -> step.getAsBoolean() ? StepResult.MORE : StepResult.DONE)
                    .name(name)
                    .budgetMillis(millisPerTick)
                    .start();
        }
    }
}
```

Keep steps short (well under the budget, ideally fractions of a millisecond): the budget is checked between steps,
so a long step overshoots and is reported as a violation.

### 6. Thread Safety Diagnostics (`assertOn`)
Protect against illegal threading before it reaches production:

```java
// Throws IllegalStateException with helpful context if not in the target's owning thread
tb.assertOn(Target.entity(targetPlayer));
```

### 7. Async Chunk Loading & Teleportation
Uniform, crash-safe async utilities for both Paper and Folia:

```java
tb.chunks().loadAsync(world, chunkX, chunkZ).thenAccept(chunk -> {
    getLogger().info("Chunk loaded asynchronously: " + chunk);
});

tb.teleport(player, targetLocation).thenAccept(success -> {
    if (success) {
        getLogger().info("Player teleported successfully!");
    }
});
```

---

## ⚙️ Configuration (`config.yml`)

```yaml
# Unused tick buffer reserved for vanilla ticks (milliseconds)
safety-margin-ms: 5.0

# Guaranteed minimum execution time per active plugin per tick (milliseconds)
floor-budget-ms: 0.5

# Default budget per task when not explicitly specified (milliseconds)
default-task-budget-ms: 1.0

# Hard cap on any single task's budget per tick, CRITICAL included (milliseconds)
max-task-budget-ms: 20.0

# Fair-share distribution weights by task priority
priority-weights:
  low: 1
  normal: 2
  high: 4
  critical: 8

# Debug mode: logs stack traces and scheduler hints when thread assertions fail
debug: false

# Anonymous metrics reporting via bStats (https://bstats.org)
metrics: true
bstats-id: 34533
```

---

## 🛠️ Admin Commands

Permission required: `tickbudget.admin` (granted to OP by default).

| Command | Description |
|---|---|
| `/tickbudget status` | Displays platform bridge, active task count and per-plugin stats: CPU ms over the last 60 s, plus completed steps, deferred ticks and violations since startup. |
| `/tickbudget top` | Lists top active tasks sorted by cumulative CPU time consumed. |
| `/tickbudget debug <on\|off>` | Toggles detailed thread ownership violation diagnostics. |
| `/tickbudget reload` | Reloads `config.yml` settings without restarting the server. |

### Sample Output (`/tickbudget status`)
```
---------------- [ TickBudget Status ] ----------------
Platform: Paper | Active Tasks: 1
Plugin Metrics (CPU: last 60s, steps/deferred/violations: since startup):
 • MyMiningPlugin: 14.82 ms | Tasks: 1 | Steps: 420 | Deferred: 0 | Violations: 0
 • CustomSpawns: 3.10 ms | Tasks: 0 | Steps: 15 | Deferred: 0 | Violations: 0
-------------------------------------------------------
```

---

## 📈 bStats Metrics

TickBudget includes anonymous server metrics using [bStats](https://bstats.org/plugin/bukkit/TickBudget/34533).
Metrics include:
* Server platform distribution (Paper vs. Folia)
* Number of active client plugins utilizing TickBudget
* Total active budgeted tasks
* Debug mode toggle state

You can disable metrics at any time by setting `metrics: false` in `plugins/TickBudget/config.yml`.

---

## 🔨 Building from Source

Requirements:
* **Java 21 JDK** or later
* **Git**

```bash
git clone https://github.com/denfry/tickbudget.git
cd tickbudget
./gradlew build
```

Compiled plugin JAR is generated at:
`tickbudget-plugin/build/libs/TickBudget-<version>.jar`

Publish API to local Maven repository:
```bash
./gradlew :tickbudget-api:publishToMavenLocal
```

---

## 📄 License

TickBudget is released under the **[MIT License](LICENSE)**.
Copyright (c) 2026 denfry.
