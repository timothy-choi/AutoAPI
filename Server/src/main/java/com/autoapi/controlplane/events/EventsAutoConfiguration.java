package com.autoapi.controlplane.events;

import com.autoapi.controlplane.events.webhooks.EventOutboxDispatcher;
import com.autoapi.controlplane.events.webhooks.WebhookDeliveryWorker;
import com.autoapi.controlplane.events.webhooks.WebhookHttpClient;
import com.autoapi.controlplane.events.webhooks.WebhookSecretCrypto;
import com.autoapi.controlplane.events.webhooks.WebhooksMetrics;
import com.autoapi.controlplane.events.webhooks.WebhooksProperties;
import com.autoapi.controlplane.persistence.PlatformEventRepositoryCustom;
import com.autoapi.controlplane.persistence.WebhookDeliveryRepositoryCustom;
import com.autoapi.controlplane.persistence.WebhookSubscriptionRepositoryCustom;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({EventsProperties.class, WebhooksProperties.class})
@ConditionalOnProperty(
    name = "autoapi.controlplane.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EventsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  Clock eventsClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnProperty(
      name = {"autoapi.controlplane.enabled", "autoapi.webhooks.enabled"},
      havingValue = "true",
      matchIfMissing = true)
  WebhookSecretCrypto webhookSecretCrypto(WebhooksProperties properties) {
    return new WebhookSecretCrypto(properties.security().masterKey());
  }

  @Bean
  @ConditionalOnProperty(
      name = {"autoapi.controlplane.enabled", "autoapi.webhooks.enabled"},
      havingValue = "true",
      matchIfMissing = true)
  WebhookHttpClient webhookHttpClient(WebhooksProperties properties) {
    return new WebhookHttpClient(properties);
  }

  @Bean
  @ConditionalOnProperty(
      name = {"autoapi.controlplane.enabled", "autoapi.events.enabled"},
      havingValue = "true",
      matchIfMissing = true)
  EventOutboxDispatcher eventOutboxDispatcher(
      EventsProperties eventsProperties,
      WebhooksProperties webhooksProperties,
      PlatformEventRepositoryCustom eventRepository,
      WebhookSubscriptionRepositoryCustom subscriptionRepository,
      WebhookDeliveryRepositoryCustom deliveryRepository,
      ObjectMapper objectMapper,
      Clock eventsClock) {
    return new EventOutboxDispatcher(
        eventsProperties,
        webhooksProperties,
        eventRepository,
        subscriptionRepository,
        deliveryRepository,
        objectMapper,
        eventsClock);
  }

  @Bean
  @ConditionalOnProperty(
      name = {"autoapi.controlplane.enabled", "autoapi.webhooks.enabled"},
      havingValue = "true",
      matchIfMissing = true)
  WebhookDeliveryWorker webhookDeliveryWorker(
      WebhooksProperties properties,
      WebhookDeliveryRepositoryCustom deliveryRepository,
      WebhookSubscriptionRepositoryCustom subscriptionRepository,
      PlatformEventRepositoryCustom eventRepository,
      WebhookSecretCrypto secretCrypto,
      WebhookHttpClient httpClient,
      PlatformEventRecorder eventRecorder,
      WebhooksMetrics metrics,
      ObjectMapper objectMapper,
      Clock eventsClock) {
    return new WebhookDeliveryWorker(
        properties,
        deliveryRepository,
        subscriptionRepository,
        eventRepository,
        secretCrypto,
        httpClient,
        eventRecorder,
        metrics,
        objectMapper,
        eventsClock);
  }
}
