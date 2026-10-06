# TickBudget

A shared tick-time budget for Paper and Folia plugins. Plugins run long jobs in small slices, and TickBudget divides the available milliseconds between them.

TickBudget is a library plugin. It does nothing on its own until another plugin uses it, but it gives admins a command that shows which plugin spends how much tick time.

### For server owners

If a plugin you run lists TickBudget as a dependency, drop the jar into `plugins/` and restart. There is nothing to set up. The defaults work for most servers.

* **Per-plugin tick usage** - `/tickbudget status` shows CPU time, completed steps, deferred ticks and thread violations for each plugin over the last 60 seconds.
* **Top tasks** - `/tickbudget top` lists running tasks by total CPU time.
* **Fair sharing** - each plugin gets a guaranteed minimum per tick. The rest of the free time is split by task priority, so one plugin cannot use up the whole tick.
* **Paper and Folia** - the plugin detects the platform at startup and uses the matching scheduler.

Requires Java 21. Tested on Paper 1.21 to 26.3 and on every Folia build from 1.21.4 to 26.2.

### Commands

Everything uses the permission `tickbudget.admin`, which operators have by default.

**/tickbudget status** *Platform, active task count and per-plugin usage for the last 60 seconds*
</br>
**/tickbudget top** *Running tasks sorted by CPU time*
</br>
**/tickbudget debug <on|off>** *Log details when a task touches the wrong thread*
</br>
**/tickbudget reload** *Reload config.yml*

### Configuration

```yaml
# Time left unused in each tick for the server itself (ms)
safety-margin-ms: 5.0

# Minimum time each active plugin gets per tick (ms)
floor-budget-ms: 0.5

# Budget for tasks that do not set their own (ms)
default-task-budget-ms: 1.0

# Share of the free time by task priority
priority-weights:
  low: 1
  normal: 2
  high: 4
  critical: 8

debug: false

# Anonymous usage statistics through bStats
metrics: true
```

### For plugin developers

A task is split into steps. TickBudget runs as many steps as fit into the budget each tick and continues on the next tick.

```java
TickBudget tb = TickBudget.of(plugin);

tb.run(Target.region(location), BudgetedTask.iterate(blocks, block -> block.setType(Material.AIR)))
        .name("clear-ruins")
        .budget(Budget.millisPerTick(1.5))
        .priority(Priority.NORMAL)
        .onComplete(() -> plugin.getLogger().info("Done"))
        .onError(error -> plugin.getLogger().severe(error.getMessage()))
        .start();
```

* **Targets** - `Target.global()`, `Target.region(location)`, `Target.entity(entity)` and `Target.async()`. On Folia each one maps to the matching scheduler. On Paper, global, region and entity run on the main thread and async runs off it.
* **Thread checks** - `tb.assertOn(target)` throws if the current thread does not own the target.
* **Async chunks** - `tb.chunks().loadAsync(world, x, z)` loads a chunk without blocking.

Add `depend: [TickBudget]` to your `plugin.yml`. The API jar is attached to every [GitHub release](https://github.com/denfry/tickbudget/releases). To build it yourself, run `./gradlew :tickbudget-api:publishToMavenLocal`.

### Metrics

TickBudget reports anonymous statistics (server platform, number of plugins using it, number of active tasks) to [bStats](https://bstats.org/plugin/bukkit/TickBudget/34533). Set `metrics: false` in `config.yml` to turn it off.

### Links

* [Source code](https://github.com/denfry/tickbudget)
* [Report a bug](https://github.com/denfry/tickbudget/issues)
* [bStats](https://bstats.org/plugin/bukkit/TickBudget/34533)

Released under the MIT License.
