package co.andesexpress.orders.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las tareas programadas (relay del outbox) salvo que {@code orders.outbox.relay.enabled=false}. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "orders.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
