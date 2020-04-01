package net.iqaros.wamp;


import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import net.iqaros.util.LogstashJsonLogger;

public class WampUtil {
  private static final LogstashJsonLogger LOGGER = LogstashJsonLogger.of();
  
  protected String decodeURI(String param) {
    try {
      return URLDecoder.decode(param, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException ex) {
      LOGGER.log(Level.WARNING,"Unsupported encoding: " + param + " " + ex.getMessage());
      return param;
    }
  }

  public static String encodeURI(String param) {
    try {
      return URLEncoder.encode(param, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException ex) {
      LOGGER.log(Level.WARNING,"Unsupported encoding: " + param + " " + ex.getMessage());
      return param;
    }
  }

  public boolean isValidMac(String mac) {
    //TODO: use validation with regexp
    return mac != null && !mac.isBlank() && mac.length() > 6 && mac.length() < 15;
  }

  public static String removeSemicolonsFromMacAddress(String longMac) {
    return longMac.toLowerCase().replaceAll(":","");
  }

  public static String addSemicolonsToMacAddress(String shortMac) {
    if (shortMac.indexOf(":") != -1) {
      return shortMac;
    }
    int size = 2;
    String[] tokens = shortMac.toLowerCase().split("(?<=\\G.{" + size + "})");
    return String.join(":",tokens);
  }

}
