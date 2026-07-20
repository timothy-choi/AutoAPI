package com.autoapi.controlplane.policy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EffectivePolicyDocumentSerdeTest {

  @Test
  void unwrapsPolicyTypesAtTopLevel() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    ObjectNode rateLimit = mapper.createObjectNode().put("limitCount", 500);
    EffectivePolicyDocument document =
        new EffectivePolicyDocument(Map.of("rateLimit", rateLimit), List.of());

    String json = mapper.writeValueAsString(document);

    assertTrue(json.contains("\"rateLimit\""));
    assertTrue(json.contains("\"limitCount\":500"));
    assertTrue(!json.contains("\"policies\""));
  }
}
