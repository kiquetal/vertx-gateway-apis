package com.cresterida.gateway.util;

import org.slf4j.Logger;

import java.util.Locale;

/**
 * Logging utility that respects a configurable LOG_LEVEL from environment or system properties.
 */
public final class LoggingUtil {

    private LoggingUtil() {}

    /**
     * Logs the provided message using the level specified by LOG_LEVEL.
     * Priority of configuration: environment variable LOG_LEVEL, then system property LOG_LEVEL, default INFO.
     * Supported levels: TRACE, DEBUG, INFO, WARN, ERROR.
     */
    public static void logWithConfiguredLevel(Logger logger, String message) {
        if (logger == null) return;
        String level = System.getenv("LOG_LEVEL");
        if (level == null || level.isBlank()) {
            level = System.getProperty("LOG_LEVEL", "INFO");
        }
        switch (level.toUpperCase(Locale.ROOT)) {
            case "TRACE":
                if (logger.isTraceEnabled()) logger.trace(message);
                break;
            case "DEBUG":
                if (logger.isDebugEnabled()) logger.debug(message);
                break;
            case "WARN":
                logger.warn(message);
                break;
            case "ERROR":
                logger.error(message);
                break;
            case "INFO":
            default:
                logger.info(message);
                break;
        }
    }
}
