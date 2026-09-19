package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.dto.common.ApiResponse;
import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.ecommerce.CustomerRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.CustomerResponse;
import com.edgareldy.micronauttutorial.security.RequiresPermission;
import com.edgareldy.micronauttutorial.service.CustomerService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Customers under /api/v1/customers: paginated list, detail and CRUD.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Controller("/api/v1/customers")
@Validated
@Secured(SecurityRule.IS_AUTHENTICATED)
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Get
    @RequiresPermission(resource = "CUSTOMER", action = "READ")
    public ApiResponse<PageResponse<CustomerResponse>> list(
            @QueryValue(defaultValue = "0") @PositiveOrZero int page,
            @QueryValue(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(customerService.list(page, size), "Customers");
    }

    @Get("/{id}")
    @RequiresPermission(resource = "CUSTOMER", action = "READ")
    public ApiResponse<CustomerResponse> get(Long id) {
        return ApiResponse.success(customerService.findById(id), "Customer");
    }

    @Post
    @RequiresPermission(resource = "CUSTOMER", action = "WRITE")
    public HttpResponse<ApiResponse<CustomerResponse>> create(@Body @Valid CustomerRequest request) {
        return HttpResponse.status(HttpStatus.CREATED).body(ApiResponse.success(customerService.create(request), "Customer created"));
    }

    @Put("/{id}")
    @RequiresPermission(resource = "CUSTOMER", action = "WRITE")
    public ApiResponse<CustomerResponse> update(Long id, @Body @Valid CustomerRequest request) {
        return ApiResponse.success(customerService.update(id, request), "Customer updated");
    }

    @Delete("/{id}")
    @Consumes(MediaType.ALL)
    @RequiresPermission(resource = "CUSTOMER", action = "WRITE")
    public ApiResponse<Void> delete(Long id) {
        customerService.delete(id);
        return ApiResponse.success(null, "Customer deleted");
    }
}
