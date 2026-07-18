package com.autoapi.config;

import java.util.Map;

public record RuntimeSnapshotMetadata(
    long snapshotVersion,
    String compiledAt,
    long configurationVersion,
    int routeCount,
    int targetCount,
    Map<String, Integer> policyCounts) {}
