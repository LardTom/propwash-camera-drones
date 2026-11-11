package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LogProbe {
    private static final List<String> LINES = new CopyOnWriteArrayList<>();
    private static boolean installed;
    private static String problem = "";

    private LogProbe() {
    }

    public static synchronized boolean install() {
        if (installed) {
            return problem.isEmpty();
        }
        installed = true;
        if (!(LogManager.getContext(CamDrones.class.getClassLoader(), false) instanceof LoggerContext context)) {
            problem = "Logging ohne Log4j-Kern";
            return false;
        }
        AbstractAppender appender = new AbstractAppender("camdrones-selftest-probe", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (CamDrones.MOD_NAME.equals(event.getLoggerName())) {
                    LINES.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        context.getConfiguration().getRootLogger().addAppender(appender, null, null);
        return true;
    }

    public static String problem() {
        return problem;
    }

    public static int mark() {
        return LINES.size();
    }

    public static List<String> since(int mark) {
        List<String> all = new ArrayList<>(LINES);
        return mark >= all.size() ? List.of() : List.copyOf(all.subList(Math.max(0, mark), all.size()));
    }
}
