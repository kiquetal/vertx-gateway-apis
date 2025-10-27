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

        String logLevel = System.getenv("LOG_LEVEL");
        if (logLevel == null || logLevel.trim().isEmpty()) {
            logLevel = System.getProperty("LOG_LEVEL");
        }
        if (logLevel == null || logLevel.trim().isEmpty()) {
            logLevel = "INFO";
        }

        // Simply use the appropriate log level through SLF4J
        switch (logLevel.toUpperCase(Locale.ROOT)) {
            case "TRACE":
                logger.trace(message);
                break;
            case "DEBUG":
                logger.debug(message);
                break;
            case "INFO":
                logger.info(message);
                break;
            case "WARN":
                logger.warn(message);
                break;
            case "ERROR":
                logger.error(message);
                break;
            default:
                // Default to INFO if level is not recognized
                logger.info(message);
                break;
        }
    }
}
