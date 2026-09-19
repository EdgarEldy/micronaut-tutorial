package com.edgareldy.micronauttutorial.event;

import com.edgareldy.micronauttutorial.dto.ecommerce.OrderResponse;

/**
 * Immutable application event announcing that an order was saved. It carries the public view of the order (a DTO,
 * never the entity) and is published by the order service inside the business transaction.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
public record OrderCreatedEvent(OrderResponse order) {
}
