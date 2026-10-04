package co.andesexpress.orders.config;

import co.andesexpress.orders.domain.port.in.ConfirmOrderInputPort;
import co.andesexpress.orders.domain.port.in.CreateOrderInputPort;
import co.andesexpress.orders.domain.port.in.GetOrderStatusInputPort;
import co.andesexpress.orders.domain.port.in.MarkGuideReadyInputPort;
import co.andesexpress.orders.domain.port.out.CoverageClientPort;
import co.andesexpress.orders.domain.port.out.EventPublisherPort;
import co.andesexpress.orders.domain.port.out.OrderRepositoryPort;
import co.andesexpress.orders.usecase.ConfirmOrderUseCase;
import co.andesexpress.orders.usecase.CreateOrderUseCase;
import co.andesexpress.orders.usecase.GetOrderStatusUseCase;
import co.andesexpress.orders.usecase.MarkGuideReadyUseCase;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfiguration {

    @Bean
    public CreateOrderInputPort createOrderInputPort(OrderRepositoryPort orderRepository,
                                                       CoverageClientPort coverageClient) {
        return new CreateOrderUseCase(orderRepository, coverageClient);
    }

    @Bean
    public ConfirmOrderInputPort confirmOrderInputPort(OrderRepositoryPort orderRepository,
                                                         EventPublisherPort eventPublisher) {
        return new ConfirmOrderUseCase(orderRepository, eventPublisher);
    }

    @Bean
    public GetOrderStatusInputPort getOrderStatusInputPort(OrderRepositoryPort orderRepository) {
        return new GetOrderStatusUseCase(orderRepository);
    }

    @Bean
    public MarkGuideReadyInputPort markGuideReadyInputPort(OrderRepositoryPort orderRepository) {
        return new MarkGuideReadyUseCase(orderRepository);
    }
}