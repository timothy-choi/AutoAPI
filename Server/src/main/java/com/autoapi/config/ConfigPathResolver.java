package com.autoapi.config;

final class ConfigPathResolver {

  private ConfigPathResolver() {}

  /**
   * Precedence: {@code --autoapi.config.path=...} or {@code --autoapi.config.path ...} command-line
   * argument, then {@code AUTOAPI_CONFIG_PATH} environment variable, then {@code
   * autoapi.config.path} system property (may be set externally).
   */
  static String resolve(String[] args) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.startsWith("--autoapi.config.path=")) {
        String value = arg.substring("--autoapi.config.path=".length()).trim();
        if (!value.isEmpty()) {
          return value;
        }
      }
      if ("--autoapi.config.path".equals(arg) && i + 1 < args.length) {
        String value = args[i + 1].trim();
        if (!value.isEmpty()) {
          return value;
        }
      }
    }
    String env = System.getenv("AUTOAPI_CONFIG_PATH");
    if (env != null && !env.isBlank()) {
      return env.trim();
    }
    String property = System.getProperty("autoapi.config.path");
    if (property != null && !property.isBlank()) {
      return property.trim();
    }
    throw new ConfigLoadException(
        "Runtime configuration path not set. Use --autoapi.config.path or AUTOAPI_CONFIG_PATH.");
  }
}
