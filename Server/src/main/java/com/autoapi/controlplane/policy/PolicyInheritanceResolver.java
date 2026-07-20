package com.autoapi.controlplane.policy;

import com.autoapi.controlplane.apidefinition.ApiDefinitionService;
import com.autoapi.controlplane.persistence.GatewayGroupEntity;
import com.autoapi.controlplane.persistence.GatewayGroupRepositoryCustom;
import com.autoapi.controlplane.persistence.PolicyBundleAssignmentEntity;
import com.autoapi.controlplane.persistence.PolicyBundleAssignmentRepositoryCustom;
import com.autoapi.controlplane.persistence.PolicyBundleRepositoryCustom;
import com.autoapi.controlplane.persistence.PolicyBundleRevisionEntity;
import com.autoapi.controlplane.persistence.PolicyOverrideEntity;
import com.autoapi.controlplane.persistence.PolicyOverrideRepositoryCustom;
import com.autoapi.controlplane.persistence.ProjectEntity;
import com.autoapi.controlplane.persistence.ProjectRepository;
import com.autoapi.controlplane.persistence.RouteEntity;
import com.autoapi.controlplane.persistence.RouteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Walks the policy hierarchy org→project→gateway_group→api→route, loading bundle assignments and
 * overrides from the database.
 */
@Component
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PolicyInheritanceResolver {

  private final ApiDefinitionService apiDefinitionService;
  private final ProjectRepository projectRepository;
  private final RouteRepository routeRepository;
  private final GatewayGroupRepositoryCustom gatewayGroupRepository;
  private final PolicyBundleAssignmentRepositoryCustom assignmentRepository;
  private final PolicyOverrideRepositoryCustom overrideRepository;
  private final PolicyBundleRepositoryCustom bundleRepository;
  private final PolicyTypeRegistry typeRegistry;
  private final ObjectMapper objectMapper;
  private final PolicyEngineMetrics metrics;

  public PolicyInheritanceResolver(
      ApiDefinitionService apiDefinitionService,
      ProjectRepository projectRepository,
      RouteRepository routeRepository,
      GatewayGroupRepositoryCustom gatewayGroupRepository,
      PolicyBundleAssignmentRepositoryCustom assignmentRepository,
      PolicyOverrideRepositoryCustom overrideRepository,
      PolicyBundleRepositoryCustom bundleRepository,
      PolicyTypeRegistry typeRegistry,
      ObjectMapper objectMapper,
      PolicyEngineMetrics metrics) {
    this.apiDefinitionService = apiDefinitionService;
    this.projectRepository = projectRepository;
    this.routeRepository = routeRepository;
    this.gatewayGroupRepository = gatewayGroupRepository;
    this.assignmentRepository = assignmentRepository;
    this.overrideRepository = overrideRepository;
    this.bundleRepository = bundleRepository;
    this.typeRegistry = typeRegistry;
    this.objectMapper = objectMapper;
    this.metrics = metrics;
  }

  public Mono<List<PolicyContribution>> resolve(UUID apiId, UUID routeId) {
    return apiDefinitionService
        .get(apiId)
        .flatMap(
            api ->
                projectRepository
                    .findById(api.projectId())
                    .flatMap(
                        project ->
                            resolveContext(api, project, routeId)
                                .flatMap(this::collectContributions)));
  }

  private Mono<ResolutionContext> resolveContext(
      com.autoapi.controlplane.persistence.ApiEntity api, ProjectEntity project, UUID routeId) {
    Mono<RouteEntity> routeMono =
        routeId == null
            ? Mono.empty()
            : routeRepository.findById(routeId).filter(route -> route.apiId().equals(api.id()));

    Mono<List<GatewayGroupEntity>> groupsMono =
        gatewayGroupRepository
            .listByProject(api.projectId(), 1000, 0)
            .filter(group -> group.apiId().equals(api.id()) && group.enabled())
            .collectList();

    return Mono.zip(routeMono.defaultIfEmpty(nullRoute(api.id())), groupsMono)
        .map(
            tuple ->
                new ResolutionContext(
                    project.organizationId(),
                    project.id(),
                    api.id(),
                    routeId,
                    tuple.getT2(),
                    tuple.getT1()));
  }

  private static RouteEntity nullRoute(UUID apiId) {
    return new RouteEntity(null, apiId, null, null, null, null, null, null, true, null, null);
  }

  private Mono<List<PolicyContribution>> collectContributions(ResolutionContext context) {
    List<Mono<List<PolicyContribution>>> stages = new ArrayList<>();

    stages.add(
        contributionsForLevel(
            PolicyHierarchyLevel.ORGANIZATION,
            context.organizationId(),
            context.organizationId(),
            "organization",
            assignmentRepository.findEnabledByOrganization(context.organizationId()),
            overrideRepository.findByOrganization(context.organizationId())));

    stages.add(
        contributionsForLevel(
            PolicyHierarchyLevel.PROJECT,
            context.projectId(),
            context.projectId(),
            "project",
            assignmentRepository.findEnabledByProject(context.projectId()),
            overrideRepository.findByProject(context.projectId())));

    for (GatewayGroupEntity group : context.gatewayGroups()) {
      stages.add(
          contributionsForLevel(
              PolicyHierarchyLevel.GATEWAY_GROUP,
              group.id(),
              group.id(),
              group.name(),
              assignmentRepository.findEnabledByGatewayGroup(group.id()),
              overrideRepository.findByGatewayGroup(group.id())));
    }

    stages.add(
        contributionsForLevel(
            PolicyHierarchyLevel.API,
            context.apiId(),
            context.apiId(),
            "api",
            assignmentRepository.findEnabledByApi(context.apiId()),
            overrideRepository.findByApi(context.apiId())));

    if (context.routeId() != null) {
      stages.add(
          contributionsForLevel(
              PolicyHierarchyLevel.ROUTE,
              context.routeId(),
              context.routeId(),
              "route",
              assignmentRepository.findEnabledByRoute(context.routeId()),
              overrideRepository.findByRoute(context.routeId())));
    }

    return Flux.concat(stages)
        .collectList()
        .map(
            nested -> {
              List<PolicyContribution> all = new ArrayList<>();
              for (List<PolicyContribution> batch : nested) {
                all.addAll(batch);
              }
              metrics.recordInheritanceDepth(stages.size());
              return all;
            });
  }

  private Mono<List<PolicyContribution>> contributionsForLevel(
      PolicyHierarchyLevel level,
      UUID sourceId,
      UUID scopeResourceId,
      String sourceName,
      Flux<PolicyBundleAssignmentEntity> assignments,
      Flux<PolicyOverrideEntity> overrides) {
    Mono<List<PolicyContribution>> bundleContributions =
        assignments
            .flatMap(assignment -> bundleContributions(level, sourceId, sourceName, assignment))
            .collectList();

    Mono<List<PolicyContribution>> overrideContributions =
        overrides
            .flatMap(override -> overrideContribution(level, sourceId, sourceName, override))
            .collectList();

    return Mono.zip(bundleContributions, overrideContributions)
        .map(
            tuple -> {
              List<PolicyContribution> combined = new ArrayList<>(tuple.getT1());
              combined.addAll(tuple.getT2());
              return combined;
            });
  }

  private Flux<PolicyContribution> bundleContributions(
      PolicyHierarchyLevel level,
      UUID sourceId,
      String sourceName,
      PolicyBundleAssignmentEntity assignment) {
    metrics.recordBundleAssignment();
    return bundleRepository
        .findRevision(assignment.bundleId(), assignment.revisionNumber())
        .flatMapMany(
            revision ->
                extractBundleContributions(level, sourceId, sourceName, assignment, revision))
        .switchIfEmpty(Flux.empty());
  }

  private Flux<PolicyContribution> extractBundleContributions(
      PolicyHierarchyLevel level,
      UUID sourceId,
      String sourceName,
      PolicyBundleAssignmentEntity assignment,
      PolicyBundleRevisionEntity revision) {
    JsonNode root = parseJson(revision.contentJson().asString());
    if (root == null || !root.isObject()) {
      return Flux.empty();
    }
    List<PolicyContribution> contributions = new ArrayList<>();
    Iterator<String> fieldNames = root.fieldNames();
    while (fieldNames.hasNext()) {
      String policyType = fieldNames.next();
      if (!typeRegistry.find(policyType).isPresent()) {
        continue;
      }
      contributions.add(
          new PolicyContribution(
              policyType,
              root.get(policyType),
              level,
              sourceId,
              sourceName,
              assignment.revisionNumber(),
              false));
    }
    return Flux.fromIterable(contributions);
  }

  private Mono<PolicyContribution> overrideContribution(
      PolicyHierarchyLevel level, UUID sourceId, String sourceName, PolicyOverrideEntity override) {
    metrics.recordOverride();
    PolicyOverrideMode mode = PolicyOverrideMode.parse(override.mode());
    if (mode == PolicyOverrideMode.INHERIT) {
      return Mono.empty();
    }
    JsonNode value =
        switch (mode) {
          case DISABLE -> objectMapper.nullNode();
          case OVERRIDE, MERGE, APPEND -> parseJson(override.contentJson());
          default -> null;
        };
    return Mono.just(
        new PolicyContribution(override.policyType(), value, level, sourceId, sourceName, 0, true));
  }

  private JsonNode parseJson(io.r2dbc.postgresql.codec.Json json) {
    if (json == null) {
      return objectMapper.nullNode();
    }
    return parseJson(json.asString());
  }

  private JsonNode parseJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return objectMapper.nullNode();
    }
    try {
      return objectMapper.readTree(raw);
    } catch (Exception ex) {
      return objectMapper.nullNode();
    }
  }

  private record ResolutionContext(
      UUID organizationId,
      UUID projectId,
      UUID apiId,
      UUID routeId,
      List<GatewayGroupEntity> gatewayGroups,
      RouteEntity route) {}
}
