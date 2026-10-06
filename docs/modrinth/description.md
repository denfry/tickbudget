# ⏱️ TickBudget

**Tick budget runner, adaptive fair-share time pool, and thread diagnostics for Paper and Folia servers (Minecraft 1.21+, Java 21).**

---

## 🌟 Why TickBudget?

In Minecraft server development, heavy operations (block iteration, entity scans, chunk inspections, inventory processing) often suffer from two major problems:

1. **Tick Spikes & Freezes:** Tasks attempted in single ticks spike MSPT past 50ms and drop server TPS.
2. **The "Tragedy of the Commons":** Multiple plugins each consuming "just 5ms" together exhaust the tick window, starving other plugins and vanilla game mechanics.
3. **Folia Concurrency Pitfalls:** Accessing world state, entities, or blocks from the wrong thread or scheduler leads to crashes or silent data corruption.

**TickBudget** solves this by providing:
* ⏱️ **Predictable Tick Budgeting:** Slice long-running workloads across consecutive ticks with strict millisecond budgets.
* ⚖️ **Adaptive Fair-Share Pool:** Dynamic pooling based on real-time server tick consumption (`ServerTickStartEvent`), guaranteeing minimum execution time (`floor`) and weighted priority distribution (`LOW`, `NORMAL`, `HIGH`, `CRITICAL`).
* 🌐 **Zero-Headache Multi-Platform Support:** Single `Target` abstraction (`global()`, `region(loc)`, `entity(entity)`, `async()`) that routes tasks to the correct scheduler on Paper and Folia automatically.
* 🔍 **Thread Ownership Verification (`assertOn`):** Detects illegal cross-region or off-thread access before it causes server crashes.
* 🚀 **Safe Async Helpers:** Unified, crash-safe asynchronous chunk loading and teleportation.
* 📊 **Admin Telemetry & bStats:** Live monitoring via `/tickbudget status` and `/tickbudget top` showing exact CPU millisecond usage per plugin over the last minute.

---

## 🛠️ For Server Administrators

### Commands & Permissions

Permission required: `tickbudget.admin` (granted to OP by default).

| Command | Description |
|---|---|
| `/tickbudget status` | Displays platform bridge, active task count, and last 60s per-plugin stats (CPU ms, completed steps, deferred ticks, violations). |
| `/tickbudget top` | Lists top active tasks sorted by cumulative CPU time consumed. |
| `/tickbudget debug <on\|off>` | Toggles detailed thread ownership violation diagnostics. |
| `/tickbudget reload` | Reloads `config.yml` settings without restarting the server. |

### Live Status Example
```
---------------- [ TickBudget Status ] ----------------
Platform: Paper | Active Tasks: 1
Plugin Metrics (last 60s):
 • MyMiningPlugin: 14.82 ms | Tasks: 1 | Steps: 420 | Deferred: 0 | Violations: 0
 • CustomSpawns: 3.10 ms | Tasks: 0 | Steps: 15 | Deferred: 0 | Violations: 0
-------------------------------------------------------
```

### Configuration (`config.yml`)
```yaml
# Unused tick buffer reserved for vanilla ticks (milliseconds)
safety-margin-ms: 5.0

# Guaranteed minimum execution time per active plugin per tick (milliseconds)
floor-budget-ms: 0.5

# Default budget per task when not explicitly specified (milliseconds)
default-task-budget-ms: 1.0

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

## 📦 For Developers: API Quickstart

### 1. Depend on TickBudget in `plugin.yml`
```yaml
name: YourPlugin
version: 1.0.0
main: com.example.YourPlugin
depend: [TickBudget]
```

### 2. Gradle (Kotlin DSL)
```kotlin
repositories {
    mavenCentral()
}

dependencies {
    compileOnly("dev.denfry.tickbudget:tickbudget-api:0.1.0")
}
```

### 3. Stepped Task Iteration Example
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

---

## 🔗 Links & Resources

* 💻 **GitHub Repository:** [denfry/tickbudget](https://github.com/denfry/tickbudget)
* 🐞 **Issue Tracker:** [GitHub Issues](https://github.com/denfry/tickbudget/issues)
* 📈 **bStats Metrics:** [bStats (34533)](https://bstats.org/plugin/bukkit/TickBudget/34533)
* 📄 **License:** MIT License
