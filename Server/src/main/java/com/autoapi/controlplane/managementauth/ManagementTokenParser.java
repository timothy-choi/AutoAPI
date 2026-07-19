package com.autoapi.controlplane.managementauth;

import com.autoapi.controlplane.api.ControlPlaneException;
import java.util.regex.Pattern;

public final class ManagementTokenParser {

  public static final int MAX_TOKEN_LENGTH = 512;
  private static final Pattern PUBLIC_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

  private ManagementTokenParser() {}

  public record ParsedToken(String publicTokenId, String secret) {}

  public static ParsedToken parse(String plaintextToken, String expectedPrefix) {
    if (plaintextToken == null || plaintextToken.isBlank()) {
      throw ControlPlaneException.invalidCredential("Invalid management token");
    }
    if (plaintextToken.length() > MAX_TOKEN_LENGTH) {
      throw ControlPlaneException.invalidCredential("Invalid management token");
    }
    String prefix = expectedPrefix == null || expectedPrefix.isBlank() ? "aat" : expectedPrefix;
    String marker = prefix + "_";
    if (!plaintextToken.startsWith(marker)) {
      throw ControlPlaneException.invalidCredential("Invalid management token");
    }
    String remainder = plaintextToken.substring(marker.length());
    int separator = remainder.indexOf('_');
    if (separator <= 0 || separator >= remainder.length() - 1) {
      throw ControlPlaneException.invalidCredential("Invalid management token");
    }
    String publicTokenId = remainder.substring(0, separator);
    String secret = remainder.substring(separator + 1);
    if (!PUBLIC_ID_PATTERN.matcher(publicTokenId).matches() || secret.isBlank()) {
      throw ControlPlaneException.invalidCredential("Invalid management token");
    }
    return new ParsedToken(publicTokenId, secret);
  }
}
