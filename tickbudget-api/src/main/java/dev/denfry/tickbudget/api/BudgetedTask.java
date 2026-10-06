package dev.denfry.tickbudget.api;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Functional interface representing work executed in small steps spread across ticks.
 */
@FunctionalInterface
public interface BudgetedTask {

    /**
     * Executes one small unit of work. Must complete well within the per-tick budget.
     *
     * @return {@link StepResult#MORE} if more work remains, or {@link StepResult#DONE} if finished
     * @throws Exception if an error occurs during execution
     */
    StepResult step() throws Exception;

    /**
     * Convenience factory for processing an iterable collection one item per step.
     *
     * @param <T> element type
     * @param items collection to iterate over
     * @param action operation to perform on each item
     * @return a budgeted task iterating over the elements
     */
    static <T> BudgetedTask iterate(Iterable<T> items, Consumer<? super T> action) {
        Iterator<T> it = items.iterator();
        return () -> {
            if (!it.hasNext()) {
                return StepResult.DONE;
            }
            action.accept(it.next());
            return it.hasNext() ? StepResult.MORE : StepResult.DONE;
        };
    }
}
