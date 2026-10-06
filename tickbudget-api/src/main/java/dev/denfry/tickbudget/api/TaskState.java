package dev.denfry.tickbudget.api;

/**
 * Represents the lifecycle state of a budgeted task.
 */
public enum TaskState {
    /** Queued for execution and progressing across ticks. */
    QUEUED,
    /** Successfully ran to completion. */
    DONE,
    /** Cancelled by the user or owner plugin. */
    CANCELLED,
    /** Terminated due to an uncaught exception. */
    FAILED;

    /**
     * Checks if this state represents task termination.
     *
     * @return true if the task is no longer running or queued
     */
    public boolean isTerminal() {
        return this != QUEUED;
    }
}
