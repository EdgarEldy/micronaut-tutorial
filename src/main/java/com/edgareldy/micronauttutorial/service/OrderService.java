package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.ecommerce.OrderRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.OrderResponse;

/**
 * Order use cases: paginated read (optional customer and product filters), detail and create. Orders are immutable.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
public interface OrderService {

    /** Orders page (0 based) ordered by id, restricted by customerId and/or productId when not null (both may be combined). */
    PageResponse<OrderResponse> list(Long customerId, Long productId, int page, int size);

    /** @throws com.edgareldy.micronauttutorial.exception.ResourceNotFoundException if the order does not exist */
    OrderResponse findById(Long id);

    /**
     * Creates an order with total = quantity * unit price of the product (scale 2, HALF_UP).
     *
     * @throws com.edgareldy.micronauttutorial.exception.BusinessRuleException if the customer or product does not exist,
     *                                                                         or the total exceeds NUMERIC(14,2)
     */
    OrderResponse create(OrderRequest request);
}
