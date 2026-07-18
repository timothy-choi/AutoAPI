package com.autoapi.controlplane.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WebhookSubscriptionRepositoryCustom {

  private final DatabaseClient databaseClient;
  private final ObjectMapper objectMapper;

  public WebhookSubscriptionRepositoryCustom(
      DatabaseClient databaseClient, ObjectMapper objectMapper) {
    this.databaseClient = databaseClient;
    this.objectMapper = objectMapper;
  }

  public Mono<WebhookSubscriptionEntity> insert(
      UUID projectId,
      String name,
      String description,
      String url,
      List<String> eventFilters,
      List<String> resourceFilters,
      byte[] encryptedSecret,
      int maxAttempts,
      int initialBackoffSeconds,
      int maxBackoffSeconds,
      int timeoutMs,
      OffsetDateTime now) {
    UUID id = UUID.randomUUID();
    return databaseClient
        .sql(
            """
            INSERT INTO webhook_subscriptions (
              id, project_id, name, description, url, enabled, event_filters, resource_filters,
              encrypted_secret, secret_version, max_attempts, initial_backoff_seconds,
              max_backoff_seconds, timeout_ms, created_at, updated_at
            ) VALUES (
              :id, :projectId, :name, :description, :url, true, :eventFilters::jsonb,
              :resourceFilters::jsonb, :encryptedSecret, 1, :maxAttempts, :initialBackoffSeconds,
              :maxBackoffSeconds, :timeoutMs, :createdAt, :updatedAt
            )
            RETURNING id, project_id, name, description, url, enabled, event_filters::text,
                      resource_filters::text, encrypted_secret, secret_version, max_attempts,
                      initial_backoff_seconds, max_backoff_seconds, timeout_ms, created_at,
                      updated_at, disabled_at
            """)
        .bind("id", id)
        .bind("projectId", projectId)
        .bind("name", name)
        .bind("description", description)
        .bind("url", url)
        .bind("eventFilters", writeJson(eventFilters))
        .bind("resourceFilters", writeJson(resourceFilters))
        .bind("encryptedSecret", encryptedSecret)
        .bind("maxAttempts", maxAttempts)
        .bind("initialBackoffSeconds", initialBackoffSeconds)
        .bind("maxBackoffSeconds", maxBackoffSeconds)
        .bind("timeoutMs", timeoutMs)
        .bind("createdAt", now)
        .bind("updatedAt", now)
        .map(this::mapSubscription)
        .one();
  }

  public Mono<WebhookSubscriptionEntity> findBySubscriptionId(UUID subscriptionId) {
    return databaseClient
        .sql(
            """
            SELECT id, project_id, name, description, url, enabled, event_filters::text,
                   resource_filters::text, encrypted_secret, secret_version, max_attempts,
                   initial_backoff_seconds, max_backoff_seconds, timeout_ms, created_at,
                   updated_at, disabled_at
            FROM webhook_subscriptions WHERE id = :id
            """)
        .bind("id", subscriptionId)
        .map(this::mapSubscription)
        .one();
  }

  public Mono<WebhookSubscriptionEntity> findById(UUID projectId, UUID subscriptionId) {
    return databaseClient
        .sql(
            """
            SELECT id, project_id, name, description, url, enabled, event_filters::text,
                   resource_filters::text, encrypted_secret, secret_version, max_attempts,
                   initial_backoff_seconds, max_backoff_seconds, timeout_ms, created_at,
                   updated_at, disabled_at
            FROM webhook_subscriptions
            WHERE id = :id AND project_id = :projectId
            """)
        .bind("id", subscriptionId)
        .bind("projectId", projectId)
        .map(this::mapSubscription)
        .one();
  }

  public Flux<WebhookSubscriptionEntity> listByProject(UUID projectId) {
    return databaseClient
        .sql(
            """
            SELECT id, project_id, name, description, url, enabled, event_filters::text,
                   resource_filters::text, encrypted_secret, secret_version, max_attempts,
                   initial_backoff_seconds, max_backoff_seconds, timeout_ms, created_at,
                   updated_at, disabled_at
            FROM webhook_subscriptions WHERE project_id = :projectId ORDER BY created_at DESC
            """)
        .bind("projectId", projectId)
        .map(this::mapSubscription)
        .all();
  }

  public Mono<Long> countByProject(UUID projectId) {
    return databaseClient
        .sql("SELECT COUNT(*) AS cnt FROM webhook_subscriptions WHERE project_id = :projectId")
        .bind("projectId", projectId)
        .map((row, meta) -> row.get("cnt", Long.class))
        .one();
  }

  public Flux<WebhookSubscriptionEntity> listEnabledForProject(UUID projectId) {
    return databaseClient
        .sql(
            """
            SELECT id, project_id, name, description, url, enabled, event_filters::text,
                   resource_filters::text, encrypted_secret, secret_version, max_attempts,
                   initial_backoff_seconds, max_backoff_seconds, timeout_ms, created_at,
                   updated_at, disabled_at
            FROM webhook_subscriptions
            WHERE project_id = :projectId AND enabled = true
            """)
        .bind("projectId", projectId)
        .map(this::mapSubscription)
        .all();
  }

  public Mono<WebhookSubscriptionEntity> update(
      UUID projectId,
      UUID subscriptionId,
      String name,
      String description,
      String url,
      Boolean enabled,
      List<String> eventFilters,
      List<String> resourceFilters,
      Integer maxAttempts,
      Integer initialBackoffSeconds,
      Integer maxBackoffSeconds,
      Integer timeoutMs,
      OffsetDateTime now) {
    return findById(projectId, subscriptionId)
        .flatMap(
            existing -> {
              String nextName = name != null ? name : existing.name();
              String nextDescription = description != null ? description : existing.description();
              String nextUrl = url != null ? url : existing.url();
              boolean nextEnabled = enabled != null ? enabled : existing.enabled();
              List<String> nextEventFilters =
                  eventFilters != null ? eventFilters : readList(existing.eventFilters());
              List<String> nextResourceFilters =
                  resourceFilters != null ? resourceFilters : readList(existing.resourceFilters());
              int nextMaxAttempts = maxAttempts != null ? maxAttempts : existing.maxAttempts();
              int nextInitialBackoff =
                  initialBackoffSeconds != null
                      ? initialBackoffSeconds
                      : existing.initialBackoffSeconds();
              int nextMaxBackoff =
                  maxBackoffSeconds != null ? maxBackoffSeconds : existing.maxBackoffSeconds();
              int nextTimeout = timeoutMs != null ? timeoutMs : existing.timeoutMs();
              OffsetDateTime disabledAt =
                  nextEnabled
                      ? null
                      : (existing.disabledAt() != null ? existing.disabledAt() : now);
              return databaseClient
                  .sql(
                      """
                      UPDATE webhook_subscriptions SET
                        name = :name, description = :description, url = :url, enabled = :enabled,
                        event_filters = :eventFilters::jsonb, resource_filters = :resourceFilters::jsonb,
                        max_attempts = :maxAttempts, initial_backoff_seconds = :initialBackoffSeconds,
                        max_backoff_seconds = :maxBackoffSeconds, timeout_ms = :timeoutMs,
                        updated_at = :updatedAt, disabled_at = :disabledAt
                      WHERE id = :id AND project_id = :projectId
                      RETURNING id, project_id, name, description, url, enabled, event_filters::text,
                                resource_filters::text, encrypted_secret, secret_version, max_attempts,
                                initial_backoff_seconds, max_backoff_seconds, timeout_ms, created_at,
                                updated_at, disabled_at
                      """)
                  .bind("id", subscriptionId)
                  .bind("projectId", projectId)
                  .bind("name", nextName)
                  .bind("description", nextDescription)
                  .bind("url", nextUrl)
                  .bind("enabled", nextEnabled)
                  .bind("eventFilters", writeJson(nextEventFilters))
                  .bind("resourceFilters", writeJson(nextResourceFilters))
                  .bind("maxAttempts", nextMaxAttempts)
                  .bind("initialBackoffSeconds", nextInitialBackoff)
                  .bind("maxBackoffSeconds", nextMaxBackoff)
                  .bind("timeoutMs", nextTimeout)
                  .bind("updatedAt", now)
                  .bind("disabledAt", disabledAt)
                  .map(this::mapSubscription)
                  .one();
            });
  }

  public Mono<WebhookSubscriptionEntity> rotateSecret(
      UUID projectId, UUID subscriptionId, byte[] encryptedSecret, OffsetDateTime now) {
    return databaseClient
        .sql(
            """
            UPDATE webhook_subscriptions
            SET encrypted_secret = :encryptedSecret, secret_version = secret_version + 1,
                updated_at = :updatedAt
            WHERE id = :id AND project_id = :projectId
            RETURNING id, project_id, name, description, url, enabled, event_filters::text,
                      resource_filters::text, encrypted_secret, secret_version, max_attempts,
                      initial_backoff_seconds, max_backoff_seconds, timeout_ms, created_at,
                      updated_at, disabled_at
            """)
        .bind("encryptedSecret", encryptedSecret)
        .bind("updatedAt", now)
        .bind("id", subscriptionId)
        .bind("projectId", projectId)
        .map(this::mapSubscription)
        .one();
  }

  public Mono<Void> delete(UUID projectId, UUID subscriptionId) {
    return databaseClient
        .sql("DELETE FROM webhook_subscriptions WHERE id = :id AND project_id = :projectId")
        .bind("id", subscriptionId)
        .bind("projectId", projectId)
        .fetch()
        .rowsUpdated()
        .then();
  }

  private List<String> readList(String json) {
    try {
      return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    } catch (Exception ex) {
      return List.of();
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalArgumentException("Failed to serialize JSON", ex);
    }
  }

  private WebhookSubscriptionEntity mapSubscription(
      io.r2dbc.spi.Readable row, io.r2dbc.spi.RowMetadata metadata) {
    return new WebhookSubscriptionEntity(
        row.get("id", UUID.class),
        row.get("project_id", UUID.class),
        row.get("name", String.class),
        row.get("description", String.class),
        row.get("url", String.class),
        row.get("enabled", Boolean.class),
        row.get("event_filters", String.class),
        row.get("resource_filters", String.class),
        row.get("encrypted_secret", byte[].class),
        row.get("secret_version", Integer.class),
        row.get("max_attempts", Integer.class),
        row.get("initial_backoff_seconds", Integer.class),
        row.get("max_backoff_seconds", Integer.class),
        row.get("timeout_ms", Integer.class),
        row.get("created_at", OffsetDateTime.class),
        row.get("updated_at", OffsetDateTime.class),
        row.get("disabled_at", OffsetDateTime.class));
  }
}
