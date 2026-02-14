package matlabmaster.multiplayer;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;

import java.util.function.Consumer;

/**
 * Central logging for the multiplayer mod. Use {@link #log()} in all mod code
 * instead of System.out/err. Only these logs are sent to the UI log area;
 * game output (e.g. combat log) is not captured.
 */
public final class MultiplayerLog {
    private static final String LOGGER_NAME = "multiplayer";

    /** Logger for all mod messages. Use this instead of System.out/err. */
    private static final Logger LOG = LogManager.getLogger(LOGGER_NAME);

    private static volatile Consumer<String> uiSink;
    private static volatile UILogAppender uiAppender;

    private MultiplayerLog() {}

    public static Logger log() {
        return LOG;
    }

    /**
     * Registers the UI log area as the sink for mod logs. Call from UI when the log area is ready.
     * Only messages logged via {@link #log()} are sent here; System.out/err are not captured.
     */
    public static void setUILogSink(Consumer<String> sink) {
        uiSink = sink;
        if (sink != null && uiAppender == null) {
            uiAppender = new UILogAppender();
            Logger logger = LogManager.getLogger(LOGGER_NAME);
            logger.addAppender(uiAppender);
            logger.setLevel(Level.ALL);
        }
    }

    /**
     * Log4j appender that forwards log events to the UI log area.
     */
    private static class UILogAppender extends AppenderSkeleton {
        private final Layout layout = new PatternLayout("[%p] %m%n");

        @Override
        protected void append(LoggingEvent event) {
            Consumer<String> sink = uiSink;
            if (sink != null) {
                String line = layout.format(event);
                sink.accept(line);
            }
        }

        @Override
        public void close() {}

        @Override
        public boolean requiresLayout() {
            return false;
        }
    }
}
