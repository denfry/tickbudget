package dev.denfry.tickbudget.api;

import java.util.Iterator;
import java.util.function.Consumer;

/** Work that is executed in small steps spread over several ticks. */
@FunctionalInterface
public interface BudgetedTask {

    /** Does one small unit of work. Keep it much shorter than the budget. */
    StepResult step() throws Exception;

    /** One element of {@code items} per step. */
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
