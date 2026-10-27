package dev.denfry.tickbudget.testplugin;

import dev.denfry.tickbudget.api.Budget;
import dev.denfry.tickbudget.api.BudgetedTask;
import dev.denfry.tickbudget.api.Priority;
import dev.denfry.tickbudget.api.StepResult;
import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.api.TaskHandle;
import dev.denfry.tickbudget.api.TaskState;
import dev.denfry.tickbudget.api.TickBudget;
import dev.denfry.tickbudget.api.TickBudgetService;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class TickBudgetTestPlugin extends JavaPlugin {

    private final AtomicBoolean globalTaskDone = new AtomicBoolean(false);
    private final AtomicBoolean asyncTaskDone = new AtomicBoolean(false);
    private final AtomicBoolean chunkLoadDone = new AtomicBoolean(false);
    private final AtomicBoolean assertionDone = new AtomicBoolean(false);
    private final AtomicBoolean entityTaskDone = new AtomicBoolean(false);
    private final AtomicBoolean cancelTaskDone = new AtomicBoolean(false);
    private final AtomicBoolean resultReported = new AtomicBoolean(false);
    private final AtomicInteger failures = new AtomicInteger();

    // Watches the TickBudget core logger: budget clamp warnings are expected, SEVERE records are failures
    private final AtomicInteger clampWarnings = new AtomicInteger();
    private final AtomicInteger severeRecords = new AtomicInteger();
    private final Handler tickBudgetLogWatcher = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel() == Level.SEVERE) {
                severeRecords.incrementAndGet();
            } else if (record.getLevel() == Level.WARNING && record.getMessage().contains("clamped")) {
                clampWarnings.incrementAndGet();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @Override
    public void onEnable() {
        getLogger().info("==================================================");
        getLogger().info("[TEST SUITE] Starting TickBudget Integration Tests");
        getLogger().info("==================================================");

        Logger.getLogger("TickBudget").addHandler(tickBudgetLogWatcher);

        TickBudget tb = TickBudget.of(this);
        getLogger().info("[TEST 1] TickBudget.of(this) acquired: " + tb);

        // Test 2: Global thread assertion. Runs through the global target because on Folia
        // onEnable is not executed on the global region thread.
        tb.schedule(Target.global(), () -> {
            try {
                tb.assertOn(Target.global());
                getLogger().info("[TEST 2] assertOn(Target.global()) PASSED on global thread");
            } catch (Throwable t) {
                fail("[TEST 2] assertOn(Target.global()) FAILED: " + t.getMessage());
            }
            assertionDone.set(true);
            checkAllDone();
        });

        // Test 3: BudgetedTask on Target.global()
        List<String> items = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            items.add("Item-" + i);
        }

        AtomicInteger processedItems = new AtomicInteger();
        TaskHandle globalHandle = tb.run(Target.global(), BudgetedTask.iterate(items, item -> {
            tb.assertOn(Target.global()); // verifies we are on global tick thread
            processedItems.incrementAndGet();
            long start = System.nanoTime();
            while (System.nanoTime() - start < 200_000L) {
                // busy work
            }
        }))
        .name("test-global-iteration")
        .budget(Budget.millisPerTick(1.0))
        .priority(Priority.HIGH)
        .onComplete(() -> {
            getLogger().info("[TEST 3] Global task completed! Processed " + processedItems.get() + " items.");
            globalTaskDone.set(true);
            checkAllDone();
        })
        .onError(err -> {
            fail("[TEST 3] Global task failed: " + err.getMessage());
            globalTaskDone.set(true);
            checkAllDone();
        })
        .start();

        getLogger().info("[TEST 3] Queued global task: " + globalHandle.name());

        // Test 4: BudgetedTask on Target.async()
        AtomicInteger asyncSteps = new AtomicInteger();
        TaskHandle asyncHandle = tb.run(Target.async(), () -> {
            int step = asyncSteps.incrementAndGet();
            tb.assertOn(Target.async());
            return step >= 10 ? StepResult.DONE : StepResult.MORE;
        })
        .name("test-async-task")
        .budget(Budget.millisPerTick(1.0))
        .priority(Priority.NORMAL)
        .onComplete(() -> {
            getLogger().info("[TEST 4] Async task completed! Steps: " + asyncSteps.get());
            asyncTaskDone.set(true);
            checkAllDone();
        })
        .onError(err -> {
            fail("[TEST 4] Async task failed: " + err.getMessage());
            asyncTaskDone.set(true);
            checkAllDone();
        })
        .start();

        getLogger().info("[TEST 4] Queued async task: " + asyncHandle.name());

        // Test 5: AsyncChunks loading
        World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (defaultWorld != null) {
            tb.chunks().loadAsync(defaultWorld, 0, 0).thenAccept(chunk -> {
                getLogger().info("[TEST 5] AsyncChunks loaded chunk (" + chunk.getX() + ", " + chunk.getZ() + ") in " + chunk.getWorld().getName());
                chunkLoadDone.set(true);
                checkAllDone();
            }).exceptionally(err -> {
                fail("[TEST 5] AsyncChunks failed: " + err.getMessage());
                chunkLoadDone.set(true);
                checkAllDone();
                return null;
            });
        } else {
            // World not ready yet, delay check
            tb.schedule(Target.global(), () -> {
                World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (w != null) {
                    tb.chunks().loadAsync(w, 0, 0).thenAccept(chunk -> {
                        getLogger().info("[TEST 5] AsyncChunks loaded chunk (" + chunk.getX() + ", " + chunk.getZ() + ") in " + chunk.getWorld().getName());
                        chunkLoadDone.set(true);
                        checkAllDone();
                    });
                } else {
                    chunkLoadDone.set(true);
                    checkAllDone();
                }
            });
        }

        runEntityRemovalTest(tb);
        runImpostorTest();
        runCancelTest(tb);
        runBudgetClampTest(tb);
    }

    @Override
    public void onDisable() {
        Logger.getLogger("TickBudget").removeHandler(tickBudgetLogWatcher);
    }

    // Test 6: an entity task whose entity is removed. Folia retires the entity scheduler, so the task must be
    // cancelled; on Paper the scheduler keeps running and the task observes the dead entity and finishes.
    private void runEntityRemovalTest(TickBudget tb) {
        World world = Bukkit.getWorlds().getFirst();
        boolean folia = isFolia();
        tb.chunks().loadAsync(world, 0, 0).thenAccept(chunk -> tb.schedule(Target.region(world, 0, 0), () -> {
            Location location = new Location(world, 8.5, world.getHighestBlockYAt(8, 8) + 1, 8.5);
            ArmorStand stand = world.spawn(location, ArmorStand.class);
            AtomicInteger steps = new AtomicInteger();
            TaskHandle handle = tb.run(Target.entity(stand), () -> {
                if (!stand.isValid()) {
                    return StepResult.DONE;
                }
                if (steps.incrementAndGet() == 3) {
                    // Removed from a separate task so the removal happens between slices
                    tb.schedule(Target.entity(stand), stand::remove);
                }
                return StepResult.MORE;
            })
            .name("test-entity-removal")
            .budget(Budget.millisPerTick(1.0))
            .start();

            handle.future().whenComplete((ignored, error) -> {
                TaskState expected = folia ? TaskState.CANCELLED : TaskState.DONE;
                if (handle.state() == expected) {
                    getLogger().info("[TEST 6] Entity task ended as " + expected + " after its entity was removed");
                } else {
                    fail("[TEST 6] Entity task ended as " + handle.state() + ", expected " + expected);
                }
                entityTaskDone.set(true);
                checkAllDone();
            });
        })).exceptionally(err -> {
            fail("[TEST 6] Entity test setup failed: " + err);
            entityTaskDone.set(true);
            checkAllDone();
            return null;
        });
    }

    // Test 7: a Plugin object the server does not know must not get a TickBudget instance
    private void runImpostorTest() {
        Plugin impostor = (Plugin) Proxy.newProxyInstance(getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName", "toString" -> "Impostor";
                    case "isEnabled" -> true;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        TickBudgetService service = Bukkit.getServicesManager().load(TickBudgetService.class);
        try {
            service.forPlugin(impostor);
            fail("[TEST 7] forPlugin accepted an unregistered plugin");
        } catch (IllegalArgumentException expected) {
            getLogger().info("[TEST 7] forPlugin rejected an unregistered plugin: " + expected.getMessage());
        }
    }

    // Test 8: cancelling an endless async task from another thread completes its future as cancelled
    private void runCancelTest(TickBudget tb) {
        AtomicReference<TaskHandle> self = new AtomicReference<>();
        AtomicInteger steps = new AtomicInteger();
        TaskHandle handle = tb.run(Target.async(), () -> {
            if (steps.incrementAndGet() == 5) {
                tb.schedule(Target.global(), () -> self.get().cancel());
            }
            return StepResult.MORE;
        })
        .name("test-cancel")
        .budget(Budget.millisPerTick(0.5))
        .start();
        self.set(handle);

        handle.future().whenComplete((ignored, error) -> {
            if (handle.future().isCancelled() && handle.state() == TaskState.CANCELLED) {
                getLogger().info("[TEST 8] Endless async task cancelled after " + steps.get() + " steps");
            } else {
                fail("[TEST 8] Expected a cancelled task, got " + handle.state());
            }
            cancelTaskDone.set(true);
            checkAllDone();
        });
    }

    // Test 9: an oversized budget is clamped and reported once
    private void runBudgetClampTest(TickBudget tb) {
        int before = clampWarnings.get();
        tb.run(Target.global(), () -> StepResult.DONE).name("test-clamp").budgetMillis(10_000.0).start();
        tb.run(Target.global(), () -> StepResult.DONE).name("test-clamp-2").budgetMillis(10_000.0).start();
        int logged = clampWarnings.get() - before;
        if (logged == 1) {
            getLogger().info("[TEST 9] Oversized budget clamped with a single warning");
        } else {
            fail("[TEST 9] Expected 1 clamp warning, got " + logged);
        }
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void fail(String message) {
        failures.incrementAndGet();
        getLogger().severe(message);
    }

    private void checkAllDone() {
        if (assertionDone.get() && globalTaskDone.get() && asyncTaskDone.get() && chunkLoadDone.get()
                && entityTaskDone.get() && cancelTaskDone.get()
                // Tests finish on different threads; only the first caller to see them all done reports
                && resultReported.compareAndSet(false, true)) {
            if (severeRecords.get() > 0) {
                fail("[TEST SUITE] TickBudget logged " + severeRecords.get() + " SEVERE record(s)");
            }
            getLogger().info("==================================================");
            if (failures.get() == 0) {
                getLogger().info("[TEST SUITE RESULT] ALL TICKBUDGET TESTS PASSED!");
            } else {
                getLogger().severe("[TEST SUITE RESULT] TICKBUDGET TESTS FAILED: " + failures.get() + " failure(s)");
            }
            getLogger().info("==================================================");

            TickBudget tb = TickBudget.of(this);
            // Delay 2 seconds using tb on async target, then dispatch commands on global
            tb.schedule(Target.async(), () -> {
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException ignored) {}
                tb.schedule(Target.global(), () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tickbudget status");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tickbudget top");
                    getLogger().info("[TEST SUITE] Shutting down test server cleanly...");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop");
                });
            });
        }
    }
}
