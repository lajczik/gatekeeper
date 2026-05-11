package xyz.lychee.gatekeeper.sponge;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.lychee.gatekeeper.shared.objects.ColoredLogger;

import java.util.logging.Level;
import java.util.logging.LogRecord;

public class SpongeColoredLogger extends ColoredLogger {
    private final Logger log4jLogger;

    public SpongeColoredLogger(Logger logger) {
        super(null);
        this.log4jLogger = logger;
    }

    @Override
    public void log(@NotNull LogRecord record) {
        String colored = ColoredLogger.convertStringMessage(record.getMessage());

        Level level = record.getLevel();
        if (level == Level.SEVERE) {
            this.log4jLogger.error(colored, record.getThrown());
        } else if (level == Level.WARNING) {
            this.log4jLogger.warn(colored, record.getThrown());
        } else if (level == Level.INFO) {
            this.log4jLogger.info(colored);
        } else if (level == Level.CONFIG || level == Level.FINE) {
            this.log4jLogger.debug(colored);
        } else {
            this.log4jLogger.trace(colored);
        }
    }
}
