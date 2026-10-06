package dev.denfry.tickbudget.testplugin;

import dev.denfry.tickbudget.api.Budget;
import dev.denfry.tickbudget.api.BudgetedTask;
import dev.denfry.tickbudget.api.Priority;
import dev.denfry.tickbudget.api.StepResult;
import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.api.TaskHandle;
import dev.denfry.tickbudget.api.TickBudget;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public final class TickBudgetTestPlugin extends JavaPlugin {

    private final AtomicBoolean globalTaskDone = new AtomicBoolean(false);
    private final AtomicBoolean asyncTaskDone = new AtomicBoolean(false);
    private final AtomicBoolean chunkLoadDone = new AtomicBoolean(false);
    private final AtomicBoolean assertionDone = new AtomicBoolean(false);
    private final AtomicInteger failures = new AtomicInteger();

    @Override
    public void onEnable() {
        getLogger().info("==================================================");
        getLogger().info("[TEST SUITE] Starting TickBudget Integration Tests");
        getLogger().info("==================================================");

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
    }

    private void fail(String message) {
        failures.incrementAndGet();
        getLogger().severe(message);
    }

    private void checkAllDone() {
        if (assertionDone.get() && globalTaskDone.get() && asyncTaskDone.get() && chunkLoadDone.get()) {
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
