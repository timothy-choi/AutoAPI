package com.autoapi.gateway.traffic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StableTrafficHashTest {

  @Test
  void sameMaterialProducesSameBucketAcrossInstances() {
    String material =
        StableTrafficHash.hashMaterial("route-1", "policy-1", "fingerprint-abc", "user-0042");
    long first = StableTrafficHash.nonNegativeBucket(material);
    long second = StableTrafficHash.nonNegativeBucket(material);
    assertEquals(first, second);
    assertTrue(first >= 0);
  }

  @Test
  void bucketModTotalRespectsWeightBoundaries() {
    String material = StableTrafficHash.hashMaterial("route", "policy", "fp", "key-a");
    long bucket = StableTrafficHash.nonNegativeBucket(material);
    int selected = StableTrafficHash.bucketModTotal(bucket, 100);
    assertTrue(selected >= 0 && selected < 100);
  }

  @Test
  void fingerprintChangeMayRemapSelection() {
    int differences = 0;
    for (int i = 1; i <= 100; i++) {
      String key = "user-" + i;
      int bucketA =
          StableTrafficHash.bucketModTotal(
              StableTrafficHash.nonNegativeBucket(
                  StableTrafficHash.hashMaterial("route", "policy", "fp1", key)),
              100);
      int bucketB =
          StableTrafficHash.bucketModTotal(
              StableTrafficHash.nonNegativeBucket(
                  StableTrafficHash.hashMaterial("route", "policy", "fp2", key)),
              100);
      if (bucketA != bucketB) {
        differences++;
      }
    }
    assertTrue(differences > 0);
  }
}
