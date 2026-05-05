package xyz.lychee.gatekeeper.shared.objects;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Log4jFilter extends AbstractFilter {
    public Log4jFilter() {
        super(Filter.Result.DENY, Filter.Result.NEUTRAL);
    }

    @NotNull
    private Result doFilter(@Nullable String message) {
        if (message != null && (message.contains("lost connection:") || message.startsWith("Disconnecting "))) {
            return onMatch;
        }
        return onMismatch;
    }

    @Override
    public Result filter(LogEvent event) {
        return doFilter(event == null ? null : event.getMessage().getFormattedMessage());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return doFilter(msg == null ? null : msg.toString());
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        return doFilter(msg);
    }

    @Override
    public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        return doFilter(msg == null ? null : msg.getFormattedMessage());
    }
}
