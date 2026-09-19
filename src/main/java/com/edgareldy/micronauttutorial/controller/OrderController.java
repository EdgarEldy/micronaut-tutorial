package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.dto.common.ApiResponse;
import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.ecommerce.OrderRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.OrderResponse;
import com.edgareldy.micronauttutorial.security.RequiresPermission;
import com.edgareldy.micronauttutorial.service.OrderService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

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

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
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
