package dev.denfry.tickbudget.api;

public enum TaskState {
    QUEUED,
    DONE,
    CANCELLED,
    FAILED;

    public boolean isTerminal() {
        return this != QUEUED;
    }
}
