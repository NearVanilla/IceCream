package com.nearvanilla.iceCream.testutil;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Collects what a logger records while a test runs, so the test can assert that a warning or error
 * was reported. The logger stops passing records to its parent handlers meanwhile, which keeps the
 * expected warnings out of the test output. Closing it puts the logger back as it was.
 *
 * <pre>{@code
 * try (LogCapture log = LogCapture.of(IceCream.logger)) {
 *   module.register();
 *   assertEquals(List.of("Something went wrong."), log.messages(Level.SEVERE));
 * }
 * }</pre>
 */
public final class LogCapture implements AutoCloseable {
  private final Logger logger;
  private final boolean usedParentHandlers;
  private final Handler handler;
  private final List<LogRecord> records = new ArrayList<>();

  private LogCapture(Logger logger) {
    this.logger = logger;
    this.usedParentHandlers = logger.getUseParentHandlers();
    this.handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            records.add(record);
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);
  }

  /**
   * Starts collecting the records of a logger.
   *
   * @param logger the logger to listen to
   * @return the capture, to close when the test is done with it
   */
  public static LogCapture of(Logger logger) {
    return new LogCapture(logger);
  }

  /**
   * Returns every record collected so far.
   *
   * @return the records, in the order they were logged
   */
  public List<LogRecord> records() {
    return List.copyOf(records);
  }

  /**
   * Returns the messages collected so far at the given level.
   *
   * @param level the level to filter by
   * @return the messages, in the order they were logged
   */
  public List<String> messages(Level level) {
    return records.stream()
        .filter(record -> record.getLevel().equals(level))
        .map(LogRecord::getMessage)
        .toList();
  }

  /** Stops collecting and puts the logger back as it was. */
  @Override
  public void close() {
    logger.removeHandler(handler);
    logger.setUseParentHandlers(usedParentHandlers);
  }
}
