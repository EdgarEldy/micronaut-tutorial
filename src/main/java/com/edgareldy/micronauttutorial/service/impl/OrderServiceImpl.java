package com.edgareldy.micronauttutorial.service.impl;

import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.ecommerce.OrderRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.OrderResponse;
import com.edgareldy.micronauttutorial.entity.Order;
import com.edgareldy.micronauttutorial.entity.Product;
import com.edgareldy.micronauttutorial.exception.BusinessRuleException;
import com.edgareldy.micronauttutorial.exception.ResourceNotFoundException;
import com.edgareldy.micronauttutorial.repository.CustomerRepository;
import com.edgareldy.micronauttutorial.repository.OrderRepository;
import com.edgareldy.micronauttutorial.repository.ProductRepository;
import com.edgareldy.micronauttutorial.service.OrderService;
import com.edgareldy.micronauttutorial.event.OrderCreatedEvent;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Default OrderService. Computes the total from the current product price, validating customer and product existence.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Singleton
@Transactional
public class OrderServiceImpl implements OrderService {

    /** Smallest value that no longer fits NUMERIC(14,2): 10^12. */
    private static final BigDecimal TOTAL_LIMIT = BigDecimal.TEN.pow(12);

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final ApplicationEventPublisher<OrderCreatedEvent> events;

    public OrderServiceImpl(OrderRepository orders, CustomerRepository customers, ProductRepository products,
                            ApplicationEventPublisher<OrderCreatedEvent> events) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(Long customerId, Long productId, int page, int size) {
        Pageable pageable = Pageable.from(page, size, Sort.of(Sort.Order.asc("id")));
        Page<Order> result;
        if (customerId != null && productId != null) {
            result = orders.findByCustomerIdAndProductId(customerId, productId, pageable);
        } else if (customerId != null) {
            result = orders.findByCustomerId(customerId, pageable);
        } else if (productId != null) {
            result = orders.findByProductId(productId, pageable);
        } else {
            result = orders.findAll(pageable);
        }
        return PageResponse.from(result, OrderServiceImpl::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return toResponse(orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id)));
    }

    @Override
    public OrderResponse create(OrderRequest request) {
        if (!customers.existsById(request.customerId())) {
            throw new BusinessRuleException("Customer " + request.customerId() + " does not exist");
        }
        Product product = products.findById(request.productId())
                .orElseThrow(() -> new BusinessRuleException("Product " + request.productId() + " does not exist"));
        BigDecimal total = product.getUnitPrice()
                .multiply(BigDecimal.valueOf(request.quantity()))
                .setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(TOTAL_LIMIT) >= 0) {
            throw new BusinessRuleException("Order total " + total.toPlainString() + " is too large");
        }
        OrderResponse created = toResponse(orders.save(new Order(request.customerId(), request.productId(), request.quantity(), total)));
        // Published inside the transaction: the transactional listener only sees it after a successful commit.
        events.publishEvent(new OrderCreatedEvent(created));
        return created;
    }

    private static OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getProductId(), order.getQuantity(), order.getTotal());
    }
}
