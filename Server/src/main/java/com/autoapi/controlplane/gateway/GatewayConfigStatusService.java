package com.autoapi.controlplane.gateway;

import com.autoapi.controlplane.api.ControlPlaneException;
import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.gateway.GatewayConfigStatusService.ConfigStatusReport;
import com.autoapi.controlplane.persistence.ConfigActivationEventEntity;
import com.autoapi.controlplane.persistence.ConfigActivationEventRepository;
import com.autoapi.controlplane.persistence.ConfigVersionRepository;
import com.autoapi.controlplane.persistence.GatewayApiStatusEntity;
import com.autoapi.controlplane.persistence.GatewayApiStatusRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class GatewayConfigStatusService {

  public static final int MAX_DIAGNOSTIC_LENGTH = 1024;

  private final GatewayRegistrationService registrationService;
  private final ApiDefinitionService apiDefinitionService;
  private final ConfigVersionRepository configVersionRepository;
  private final ConfigActivationEventRepository eventRepository;
  private final GatewayApiStatusRepository gatewayApiStatusRepository;
  private final DatabaseClient databaseClient;

  public GatewayConfigStatusService(
      GatewayRegistrationService registrationService,
      ApiDefinitionService apiDefinitionService,
      ConfigVersionRepository configVersionRepository,
      ConfigActivationEventRepository eventRepository,
      GatewayApiStatusRepository gatewayApiStatusRepository,
      DatabaseClient databaseClient) {
    this.registrationService = registrationService;
    this.apiDefinitionService = apiDefinitionService;
    this.configVersionRepository = configVersionRepository;
    this.eventRepository = eventRepository;
    this.gatewayApiStatusRepository = gatewayApiStatusRepository;
    this.databaseClient = databaseClient;
  }

  @Transactional(transactionManager = "connectionFactoryTransactionManager")
  public Mono<ConfigStatusReport> report(String gatewayId, ConfigStatusRequest request) {
    validateRequest(request);
    return registrationService
        .requireRegistered(gatewayId)
        .then(apiDefinitionService.get(request.apiId()))
        .then(
            configVersionRepository
                .findByApiIdAndVersion(request.apiId(), request.version())
                .switchIfEmpty(
                    Mono.error(
                        ControlPlaneException.configVersionNotFound(
                            "Config version was not found")))
                .flatMap(
                    versionEntity -> {
                      if (!versionEntity.contentHash().equals(request.contentHash())) {
                        return Mono.error(
                            ControlPlaneException.contentHashMismatch(
                                "Reported content hash does not match stored version"));
                      }
                      return eventRepository
                          .findByGatewayIdAndReportId(gatewayId, request.reportId())
                          .flatMap(
                              existing ->
                                  matchesExisting(existing, request)
                                      ? Mono.just(new ConfigStatusReport(true, true))
                                      : Mono.error(
                                          ControlPlaneException.reportIdConflict(
                                              "Report ID was already used with different content")))
                          .switchIfEmpty(persistNewReport(gatewayId, request));
                    }));
  }

  private Mono<ConfigStatusReport> persistNewReport(String gatewayId, ConfigStatusRequest request) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID eventId = UUID.randomUUID();
    var insertSpec =
        bindNullableLong(
                databaseClient
                    .sql(
                        """
                        INSERT INTO config_activation_events (
                            id, gateway_id, api_id, version, content_hash, report_id, status,
                            error_code, diagnostic, apply_duration_ms, created_at
                        ) VALUES (
                            :id, :gatewayId, :apiId, :version, :contentHash, :reportId, :status,
                            :errorCode, :diagnostic, :applyDurationMs, :createdAt
                        )
                        """)
                    .bind("id", eventId)
                    .bind("gatewayId", gatewayId)
                    .bind("apiId", request.apiId())
                    .bind("version", request.version())
                    .bind("contentHash", request.contentHash())
                    .bind("reportId", request.reportId())
                    .bind("status", request.status()),
                "applyDurationMs",
                request.applyDurationMs())
            .bind("createdAt", now);
    if ("ACK".equals(request.status())) {
      insertSpec =
          insertSpec.bindNull("errorCode", String.class).bindNull("diagnostic", String.class);
    } else {
      insertSpec =
          insertSpec
              .bind("errorCode", request.errorCode())
              .bind("diagnostic", truncateDiagnostic(request.diagnostic()));
    }
    return insertSpec
        .fetch()
        .rowsUpdated()
        .flatMap(
            rows ->
                rows != null && rows == 1L
                    ? upsertGatewayApiStatus(gatewayId, request, now)
                        .thenReturn(new ConfigStatusReport(false, true))
                    : Mono.error(
                        ControlPlaneException.internal("Failed to persist status report")));
  }

  private Mono<Void> upsertGatewayApiStatus(
      String gatewayId, ConfigStatusRequest request, OffsetDateTime now) {
    return gatewayApiStatusRepository
        .findByGatewayIdAndApiId(gatewayId, request.apiId())
        .flatMap(existing -> updateGatewayApiStatus(gatewayId, request, now, existing))
        .switchIfEmpty(insertGatewayApiStatus(gatewayId, request, now));
  }

  private Mono<Void> insertGatewayApiStatus(
      String gatewayId, ConfigStatusRequest request, OffsetDateTime now) {
    if ("ACK".equals(request.status())) {
      return bindNullableLong(
              databaseClient
                  .sql(
                      """
                      INSERT INTO gateway_api_status (
                          gateway_id, api_id, active_version, active_content_hash,
                          last_attempted_version, last_attempted_content_hash,
                          last_status, last_apply_duration_ms,
                          last_reported_at, created_at, updated_at
                      ) VALUES (
                          :gatewayId, :apiId, :version, :contentHash,
                          :version, :contentHash,
                          'ACK', :applyDurationMs,
                          :now, :now, :now
                      )
                      """)
                  .bind("gatewayId", gatewayId)
                  .bind("apiId", request.apiId())
                  .bind("version", request.version())
                  .bind("contentHash", request.contentHash()),
              "applyDurationMs",
              request.applyDurationMs())
          .bind("now", now)
          .fetch()
          .rowsUpdated()
          .then();
    }
    return bindNullableLong(
            databaseClient
                .sql(
                    """
                    INSERT INTO gateway_api_status (
                        gateway_id, api_id, active_version, active_content_hash,
                        last_attempted_version, last_attempted_content_hash,
                        last_status, last_error_code, last_diagnostic, last_apply_duration_ms,
                        last_reported_at, created_at, updated_at
                    ) VALUES (
                        :gatewayId, :apiId, NULL, NULL,
                        :version, :contentHash,
                        'NACK', :errorCode, :diagnostic, :applyDurationMs,
                        :now, :now, :now
                    )
                    """)
                .bind("gatewayId", gatewayId)
                .bind("apiId", request.apiId())
                .bind("version", request.version())
                .bind("contentHash", request.contentHash())
                .bind("errorCode", request.errorCode())
                .bind("diagnostic", truncateDiagnostic(request.diagnostic())),
            "applyDurationMs",
            request.applyDurationMs())
        .bind("now", now)
        .fetch()
        .rowsUpdated()
        .then();
  }

  private Mono<Void> updateGatewayApiStatus(
      String gatewayId,
      ConfigStatusRequest request,
      OffsetDateTime now,
      GatewayApiStatusEntity existing) {
    if ("ACK".equals(request.status())) {
      return bindNullableLong(
              databaseClient
                  .sql(
                      """
                      UPDATE gateway_api_status
                      SET active_version = :version,
                          active_content_hash = :contentHash,
                          last_attempted_version = :version,
                          last_attempted_content_hash = :contentHash,
                          last_status = 'ACK',
                          last_error_code = NULL,
                          last_diagnostic = NULL,
                          last_apply_duration_ms = :applyDurationMs,
                          last_reported_at = :now,
                          updated_at = :now
                      WHERE gateway_id = :gatewayId AND api_id = :apiId
                      """)
                  .bind("version", request.version())
                  .bind("contentHash", request.contentHash())
                  .bind("now", now)
                  .bind("gatewayId", gatewayId)
                  .bind("apiId", request.apiId()),
              "applyDurationMs",
              request.applyDurationMs())
          .fetch()
          .rowsUpdated()
          .then();
    }
    return bindNullableLong(
            bindNullableString(
                bindNullableLong(
                    databaseClient
                        .sql(
                            """
                            UPDATE gateway_api_status
                            SET active_version = :activeVersion,
                                active_content_hash = :activeContentHash,
                                last_attempted_version = :version,
                                last_attempted_content_hash = :contentHash,
                                last_status = 'NACK',
                                last_error_code = :errorCode,
                                last_diagnostic = :diagnostic,
                                last_apply_duration_ms = :applyDurationMs,
                                last_reported_at = :now,
                                updated_at = :now
                            WHERE gateway_id = :gatewayId AND api_id = :apiId
                            """)
                        .bind("version", request.version())
                        .bind("contentHash", request.contentHash())
                        .bind("errorCode", request.errorCode())
                        .bind("diagnostic", truncateDiagnostic(request.diagnostic()))
                        .bind("now", now)
                        .bind("gatewayId", gatewayId)
                        .bind("apiId", request.apiId()),
                    "activeVersion",
                    existing.activeVersion()),
                "activeContentHash",
                existing.activeContentHash()),
            "applyDurationMs",
            request.applyDurationMs())
        .fetch()
        .rowsUpdated()
        .then();
  }

  private static DatabaseClient.GenericExecuteSpec bindNullableLong(
      DatabaseClient.GenericExecuteSpec spec, String name, Long value) {
    return value == null ? spec.bindNull(name, Long.class) : spec.bind(name, value);
  }

  private static DatabaseClient.GenericExecuteSpec bindNullableString(
      DatabaseClient.GenericExecuteSpec spec, String name, String value) {
    return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
  }

  private static void validateRequest(ConfigStatusRequest request) {
    if (request.reportId() == null) {
      throw ControlPlaneException.invalidConfigStatus("reportId is required");
    }
    if (request.apiId() == null) {
      throw ControlPlaneException.invalidConfigStatus("apiId is required");
    }
    if (request.version() <= 0) {
      throw ControlPlaneException.invalidConfigStatus("version must be positive");
    }
    if (request.contentHash() == null || request.contentHash().isBlank()) {
      throw ControlPlaneException.invalidConfigStatus("contentHash is required");
    }
    if (!"ACK".equals(request.status()) && !"NACK".equals(request.status())) {
      throw ControlPlaneException.invalidConfigStatus("status must be ACK or NACK");
    }
    if ("ACK".equals(request.status()) && request.errorCode() != null) {
      throw ControlPlaneException.invalidConfigStatus("ACK must not include errorCode");
    }
    if ("NACK".equals(request.status())
        && (request.errorCode() == null || request.errorCode().isBlank())) {
      throw ControlPlaneException.invalidConfigStatus("NACK requires errorCode");
    }
    if (request.applyDurationMs() != null && request.applyDurationMs() < 0) {
      throw ControlPlaneException.invalidConfigStatus("applyDurationMs must be nonnegative");
    }
    if (request.diagnostic() != null && request.diagnostic().length() > MAX_DIAGNOSTIC_LENGTH) {
      throw ControlPlaneException.diagnosticTooLong(
          "diagnostic exceeds maximum length of " + MAX_DIAGNOSTIC_LENGTH);
    }
  }

  private static boolean matchesExisting(
      ConfigActivationEventEntity existing, ConfigStatusRequest request) {
    return existing.apiId().equals(request.apiId())
        && existing.version() == request.version()
        && existing.contentHash().equals(request.contentHash())
        && existing.status().equals(request.status())
        && Objects.equals(existing.errorCode(), request.errorCode())
        && Objects.equals(
            truncateDiagnostic(existing.diagnostic()), truncateDiagnostic(request.diagnostic()))
        && Objects.equals(existing.applyDurationMs(), request.applyDurationMs());
  }

  private static String truncateDiagnostic(String diagnostic) {
    if (diagnostic == null) {
      return null;
    }
    if (diagnostic.length() <= MAX_DIAGNOSTIC_LENGTH) {
      return diagnostic;
    }
    return diagnostic.substring(0, MAX_DIAGNOSTIC_LENGTH);
  }

  public record ConfigStatusRequest(
      UUID reportId,
      UUID apiId,
      long version,
      String contentHash,
      String status,
      String errorCode,
      String diagnostic,
      Long applyDurationMs) {}

  public record ConfigStatusReport(boolean idempotent, boolean accepted) {}
}
