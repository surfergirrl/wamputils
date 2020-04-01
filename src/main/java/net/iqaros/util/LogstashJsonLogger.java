package net.iqaros.util;

//copied from com.comhem.lan.sigmadomain.logging;

import argo.format.CompactJsonFormatter;
import argo.format.JsonFormatter;
import java.io.Writer;
import static argo.jdom.JsonNodeFactories.*;
import argo.jdom.JsonRootNode;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.logging.Level;

public class LogstashJsonLogger {
  private static final JsonFormatter COMPACT_FORMATTER = new CompactJsonFormatter();
  private final Writer writer;

  public LogstashJsonLogger(Writer writer) {
    this.writer = writer;
  }

  public void log(Level level, String message) {
    Instant now = Instant.now();
    JsonRootNode jroot = object(field("@timestamp", string(now.toString())),
        field("level", string(level.getName())), field("message", string(message)));
    write(jroot);
  }

  public void log(Level level, String message, JsonRootNode details) {
    Instant now = Instant.now();
    JsonRootNode jroot = object(field("@timestamp", string(now.toString())),
        field("level", string(level.getName())), field("message", string(message)), field("details", details));
    write(jroot);
  }

  public void log(Level level, String message, Throwable exception) {
    Instant now = Instant.now();
    JsonRootNode jroot = object(field("@timestamp", string(now.toString())),
        field("level", string(level.getName())), field("message", string(message)),
        field("exception", exception(exception)));
    write(jroot);
  }

  public void log(Level level, String message, JsonRootNode details, Throwable exception) {
    Instant now = Instant.now();
    JsonRootNode jroot = object(field("@timestamp", string(now.toString())),
        field("level", string(level.getName())), field("message", string(message)), field("details", details),
        field("exception", exception(exception)));
    write(jroot);
  }

  private static JsonRootNode exception(Throwable exception) {
    return object(field("class", string(exception.getClass().getName())),
        field("message", string(exception.getMessage() == null ? "" : exception.getMessage())),
        field("stack_trace", string(exception.getStackTrace().toString())));
  }

  private synchronized void write(JsonRootNode jroot) {
    try {
      COMPACT_FORMATTER.format(jroot, writer);
      writer.write("\n");
      writer.flush();
    } catch (IOException ex) {
      System.err.println("Failed to write log data! " + ex.getMessage());
    }
  }

  public Consumer<Exception> exceptionConsumer(Level logLevel) {
    return (Exception ex) -> log(logLevel, (ex.getMessage() == null ? "" : ex.getMessage()), ex);
  }


  //Factory
  public static LogstashJsonLogger of() {
    return new LogstashJsonLogger(new PrintWriter(System.out));
  }
}
