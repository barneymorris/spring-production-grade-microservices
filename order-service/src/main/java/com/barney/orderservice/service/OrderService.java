package com.barney.orderservice.service;

import com.barney.orderservice.dto.InventoryResponse;
import com.barney.orderservice.dto.OrderLineItemsDto;
import com.barney.orderservice.dto.OrderRequest;
import com.barney.orderservice.model.Order;
import com.barney.orderservice.model.OrderLineItems;
import com.barney.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    public OrderService(OrderRepository orderRepository, WebClient.Builder webClientBuilder) {
        this.orderRepository = orderRepository;
        this.webClientBuilder = webClientBuilder;
    }

    public void placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItemsList = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();

        order.setOrderLineItemsList(orderLineItemsList);

        List<String> skuCodes = order.getOrderLineItemsList()
                .stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                        .uri("http://inventory-service/api/inventory",
                                uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                        .retrieve()
                        .bodyToMono(InventoryResponse[].class)
                        .block();

        assert inventoryResponseArray != null;
        boolean allProductsInStock = Arrays.stream(inventoryResponseArray)
                .allMatch(InventoryResponse::isInStock);

        if (Boolean.TRUE.equals(allProductsInStock)) {
            orderRepository.save(order);
        } else {
            throw new IllegalArgumentException("Product is not in stock, please try again later");
        }

    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
