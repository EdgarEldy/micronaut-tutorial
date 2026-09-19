package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.dto.ecommerce.OrderRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.OrderResponse;
import com.edgareldy.micronauttutorial.entity.Order;
import com.edgareldy.micronauttutorial.entity.Product;
import com.edgareldy.micronauttutorial.exception.BusinessRuleException;
import com.edgareldy.micronauttutorial.exception.ResourceNotFoundException;
import com.edgareldy.micronauttutorial.repository.CustomerRepository;
import com.edgareldy.micronauttutorial.repository.OrderRepository;
import com.edgareldy.micronauttutorial.repository.ProductRepository;
import com.edgareldy.micronauttutorial.service.impl.OrderServiceImpl;
import com.edgareldy.micronauttutorial.event.OrderCreatedEvent;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests of OrderServiceImpl with its three repositories mocked: the total computation (scale and HALF_UP),
 * the NUMERIC(14,2) overflow refusal, the missing customer and product refusals and the repository method picked by
 * each list filter combination.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Same style as ProductServiceImplTest: the service is built by hand around Mockito mocks, no Micronaut context.
class OrderServiceImplTest {

    private OrderRepository orders;
    private CustomerRepository customers;
    private ProductRepository products;
    private ApplicationEventPublisher<OrderCreatedEvent> events;
    private OrderServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        customers = mock(CustomerRepository.class);
        products = mock(ProductRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new OrderServiceImpl(orders, customers, products, events);
        when(customers.existsById(1L)).thenReturn(true);
        when(orders.save(any(Order.class))).thenAnswer(returnsFirstArg());
    }

    private void productPrice(String price) {
        when(products.findById(2L)).thenReturn(Optional.of(new Product(5L, "Pen", new BigDecimal(price))));
    }

    private OrderResponse create(int quantity) {
        return service.create(new OrderRequest(1L, 2L, quantity));
    }

    @Test
    void totalIsQuantityTimesUnitPriceAtScaleTwo() {
        productPrice("10.50");

        OrderResponse response = create(3);

        assertEquals(new BigDecimal("31.50"), response.total());
        assertEquals(3, response.quantity());
        assertEquals(1L, response.customerId());
        assertEquals(2L, response.productId());
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(saved.capture());
        assertEquals(new BigDecimal("31.50"), saved.getValue().getTotal());
    }

    @Test
    void aWholePriceStillGivesScaleTwo() {
        productPrice("4");

        assertEquals(new BigDecimal("12.00"), create(3).total());
    }

    @Test
    void totalIsRoundedHalfUpWhenThePriceHasMoreDecimals() {
        productPrice("0.125");
        assertEquals(new BigDecimal("0.13"), create(1).total());
        assertEquals(new BigDecimal("0.38"), create(3).total());

        productPrice("0.124");
        assertEquals(new BigDecimal("0.12"), create(1).total());
    }

    @Test
    void theLargestFittingTotalIsAcceptedAndTheFirstOverflowRefusedWithoutSaving() {
        productPrice("9999999.99");
        assertEquals(new BigDecimal("999999999000.00"), create(100000).total());

        productPrice("10000000.00");
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> create(100000));
        assertEquals("Order total 1000000000000.00 is too large", e.getMessage());

        productPrice("9999999999.99");
        assertThrows(BusinessRuleException.class, () -> create(100000));

        // Only the first, fitting order was ever saved.
        verify(orders).save(any(Order.class));
    }

    @Test
    void aMissingCustomerIsRefusedWithoutSaving() {
        when(customers.existsById(99L)).thenReturn(false);

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> service.create(new OrderRequest(99L, 2L, 1)));

        assertEquals("Customer 99 does not exist", e.getMessage());
        verify(orders, never()).save(any());
    }

    @Test
    void aMissingProductIsRefusedWithoutSaving() {
        when(products.findById(88L)).thenReturn(Optional.empty());

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> service.create(new OrderRequest(1L, 88L, 1)));

        assertEquals("Product 88 does not exist", e.getMessage());
        verify(orders, never()).save(any());
    }

    // The event carries the created OrderResponse and is published exactly once per successful create.
    @Test
    void aSuccessfulCreatePublishesExactlyOneEventWithTheCreatedOrder() {
        productPrice("10.50");

        OrderResponse response = create(3);

        ArgumentCaptor<OrderCreatedEvent> published = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(events, times(1)).publishEvent(published.capture());
        assertEquals(response, published.getValue().order());
        assertEquals(new BigDecimal("31.50"), published.getValue().order().total());
    }

    @Test
    void aRefusedCreatePublishesNothing() {
        when(customers.existsById(99L)).thenReturn(false);
        assertThrows(BusinessRuleException.class, () -> service.create(new OrderRequest(99L, 2L, 1)));

        when(products.findById(88L)).thenReturn(Optional.empty());
        assertThrows(BusinessRuleException.class, () -> service.create(new OrderRequest(1L, 88L, 1)));

        productPrice("10000000.00");
        assertThrows(BusinessRuleException.class, () -> create(100000));

        verifyNoInteractions(events);
    }

    @Test
    void findByIdMapsTheOrderOrRaisesResourceNotFound() {
        Order order = new Order(1L, 2L, 4, new BigDecimal("8.00"));
        order.setId(7L);
        when(orders.findById(7L)).thenReturn(Optional.of(order));
        when(orders.findById(8L)).thenReturn(Optional.empty());

        assertEquals(new OrderResponse(7L, 1L, 2L, 4, new BigDecimal("8.00")), service.findById(7L));
        assertThrows(ResourceNotFoundException.class, () -> service.findById(8L));
    }

    @Test
    void listPicksTheRepositoryMethodMatchingTheFilters() {
        Page<Order> page = Page.of(List.of(new Order(1L, 2L, 1, BigDecimal.ONE)), Pageable.from(0, 10), 1L);
        when(orders.findAll(any(Pageable.class))).thenReturn(page);
        when(orders.findByCustomerId(eq(1L), any(Pageable.class))).thenReturn(page);
        when(orders.findByProductId(eq(2L), any(Pageable.class))).thenReturn(page);
        when(orders.findByCustomerIdAndProductId(eq(1L), eq(2L), any(Pageable.class))).thenReturn(page);

        service.list(null, null, 0, 10);
        verify(orders).findAll(any(Pageable.class));
        verify(orders, never()).findByCustomerId(any(), any());
        verify(orders, never()).findByProductId(any(), any());
        verify(orders, never()).findByCustomerIdAndProductId(any(), any(), any());

        service.list(1L, null, 0, 10);
        verify(orders).findByCustomerId(eq(1L), any(Pageable.class));
        verify(orders, never()).findByProductId(any(), any());

        service.list(null, 2L, 0, 10);
        verify(orders).findByProductId(eq(2L), any(Pageable.class));

        assertEquals(1, service.list(1L, 2L, 0, 10).totalElements());
        verify(orders).findByCustomerIdAndProductId(eq(1L), eq(2L), any(Pageable.class));
        // Each of the four calls used exactly one method: findAll, byCustomer, byProduct and the combined one, once.
        verify(orders).findAll(any(Pageable.class));
        verify(orders).findByCustomerId(any(), any());
        verify(orders).findByProductId(any(), any());
    }
}
