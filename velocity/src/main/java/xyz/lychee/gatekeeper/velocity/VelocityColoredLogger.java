package xyz.lychee.gatekeeper.velocity;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.lychee.gatekeeper.shared.objects.ColoredLogger;

import java.util.logging.Level;
import java.util.logging.LogRecord;

public class VelocityColoredLogger extends ColoredLogger {
    private final Logger slf4jLogger;

    public VelocityColoredLogger() {
        super(null);
        this.slf4jLogger = LoggerFactory.getLogger(this.getName());
    }

    @Override
    public void log(@NotNull LogRecord record) {
        String colored = ColoredLogger.convertStringMessage(record.getMessage());

        Level level = record.getLevel();
        if (level == Level.SEVERE) {
            this.slf4jLogger.error(colored, record.getThrown());
        } else if (level == Level.WARNING) {
            this.slf4jLogger.warn(colored, record.getThrown());
        } else if (level == Level.INFO) {
            this.slf4jLogger.info(colored);
        } else if (level == Level.CONFIG || level == Level.FINE) {
            this.slf4jLogger.debug(colored);
        } else {
            this.slf4jLogger.trace(colored);
        }
    }
}
