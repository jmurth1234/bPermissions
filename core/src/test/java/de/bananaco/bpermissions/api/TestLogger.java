package de.bananaco.bpermissions.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test utilities for logging during test execution.
 */
public class TestLogger {
    private static final Logger logger = LoggerFactory.getLogger("de.bananaco.bpermissions");

    public static void info(String message) {
        logger.info(message);
    }

    public static void debug(String message) {
        logger.debug(message);
    }

    public static void error(String message) {
        logger.error(message);
    }

    public static Logger getLogger() {
        return logger;
    }
}
