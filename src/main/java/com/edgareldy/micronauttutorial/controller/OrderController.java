package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.dto.common.ApiResponse;
import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.ecommerce.OrderRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.OrderResponse;
import com.edgareldy.micronauttutorial.security.RequiresPermission;
import com.edgareldy.micronauttutorial.service.OrderService;
import com.edgareldy.micronauttutorial.event.OrderEventBroadcaster;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.sse.Event;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * Orders under /api/v1/orders: paginated list (optional customerId and productId filters), detail and creation.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Controller("/api/v1/orders")
@Validated
@Secured(SecurityRule.IS_AUTHENTICATED)
public class OrderController {

    private final OrderService orderService;
    private static final Duration HEARTBEAT_PERIOD = Duration.ofSeconds(15);

    private final OrderEventBroadcaster broadcaster;

    public OrderController(OrderService orderService, OrderEventBroadcaster broadcaster) {
        this.orderService = orderService;
        this.broadcaster = broadcaster;
    }

    @Get
    @RequiresPermission(resource = "ORDER", action = "READ")
    public ApiResponse<PageResponse<OrderResponse>> list(
            @QueryValue @Nullable Long customerId,
            @QueryValue @Nullable Long productId,
            @QueryValue(defaultValue = "0") @PositiveOrZero int page,
            @QueryValue(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(orderService.list(customerId, productId, page, size), "Orders");
    }

    // Server-Sent Events: a long lived HTTP response (text/event-stream) where the server pushes one "data:" frame per
    // element of the returned Flux. Micronaut subscribes to the Flux when the client connects and cancels it when
    // the client disconnects. Wrapping each element in io.micronaut.http.sse.Event makes the framing explicit. The
    // literal "/stream" wins over the "/{id}" template for a client that accepts text/event-stream.
    @Get("/stream")
    @Produces(MediaType.TEXT_EVENT_STREAM)
    @RequiresPermission(resource = "ORDER", action = "READ")
    public Flux<Event<ApiResponse<OrderResponse>>> stream(
            @QueryValue @Nullable Long customerId,
            @QueryValue @Nullable Long productId) {
        Flux<Event<ApiResponse<OrderResponse>>> orders = broadcaster.stream()
                .filter(order -> customerId == null || customerId.equals(order.customerId()))
                .filter(order -> productId == null || productId.equals(order.productId()))
                .map(order -> Event.of(ApiResponse.success(order, "Order created")).name("order"));
        // A first heartbeat is sent at once, so the client gets its response headers even when no order is
        // created, then one every 15 seconds keeps idle connections open through proxies.
        Flux<Event<ApiResponse<OrderResponse>>> heartbeat = Flux.interval(Duration.ZERO, HEARTBEAT_PERIOD)
                .map(tick -> Event.of(ApiResponse.<OrderResponse>success(null, "heartbeat")).name("heartbeat"));
        return Flux.merge(orders, heartbeat);
    }

    @Get("/{id}")
    @RequiresPermission(resource = "ORDER", action = "READ")
    public ApiResponse<OrderResponse> get(Long id) {
        return ApiResponse.success(orderService.findById(id), "Order");
    }

    @Post
    @RequiresPermission(resource = "ORDER", action = "WRITE")
    public HttpResponse<ApiResponse<OrderResponse>> create(@Body @Valid OrderRequest request) {
        return HttpResponse.status(HttpStatus.CREATED).body(ApiResponse.success(orderService.create(request), "Order created"));
    }
}
