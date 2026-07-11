package com.autoapi.config;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;

class ConfigLoaderTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void loadsValidConfig(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("runtime.json");
    Files.writeString(
        file,
        """
        {
          "gateway": { "listenAddress": "0.0.0.0", "port": 8080 },
          "routes": [{
            "id": "r1",
            "host": "api.example.com",
            "pathPrefix": "/v1",
            "methods": ["GET"],
            "upstream": { "url": "http://upstream:8080" }
          }]
        }
        """);
    RuntimeConfig config = ConfigLoader.load(file.toString(), mapper);
    assertEquals(8080, config.gateway().port());
    assertEquals(1, config.routes().size());
    assertEquals(Set.of(HttpMethod.GET), config.routes().getFirst().methods());
  }

  @Test
  void missingFileFails() {
    assertThrows(ConfigLoadException.class, () -> ConfigLoader.load("/no/such/file.json", mapper));
  }

  @Test
  void malformedJsonFails(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("bad.json");
    Files.writeString(file, "{");
    assertThrows(ConfigLoadException.class, () -> ConfigLoader.load(file.toString(), mapper));
  }
}
