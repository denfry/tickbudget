package dev.denfry.tickbudget.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Collects records written to the TickBudget logger while an action runs. */
final class LogCapture {

    private LogCapture() {
    }

    static List<LogRecord> warnings(Runnable action) {
        return records(Level.WARNING, action);
    }

    static List<LogRecord> records(Level level, Runnable action) {
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == level) {
                    records.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger logger = Logger.getLogger("TickBudget");
        logger.addHandler(handler);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
        }
        return records;
    }
}
