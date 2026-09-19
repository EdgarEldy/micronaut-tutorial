package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.dto.common.ApiResponse;
import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.ecommerce.ProductRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.ProductResponse;
import com.edgareldy.micronauttutorial.security.RequiresPermission;
import com.edgareldy.micronauttutorial.service.ProductService;
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
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Products under /api/v1/products: paginated list (optional categoryId filter), detail and CRUD.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Controller("/api/v1/products")
@Validated
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Get
    @RequiresPermission(resource = "PRODUCT", action = "READ")
    public ApiResponse<PageResponse<ProductResponse>> list(
            @QueryValue @Nullable Long categoryId,
            @QueryValue(defaultValue = "0") @PositiveOrZero int page,
            @QueryValue(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(productService.list(categoryId, page, size), "Products");
    }

    @Get("/{id}")
    @RequiresPermission(resource = "PRODUCT", action = "READ")
    public ApiResponse<ProductResponse> get(Long id) {
        return ApiResponse.success(productService.findById(id), "Product");
    }

    @Post
    @RequiresPermission(resource = "PRODUCT", action = "WRITE")
    public HttpResponse<ApiResponse<ProductResponse>> create(@Body @Valid ProductRequest request) {
        return HttpResponse.status(HttpStatus.CREATED).body(ApiResponse.success(productService.create(request), "Product created"));
    }

    @Put("/{id}")
    @RequiresPermission(resource = "PRODUCT", action = "WRITE")
    public ApiResponse<ProductResponse> update(Long id, @Body @Valid ProductRequest request) {
        return ApiResponse.success(productService.update(id, request), "Product updated");
    }

    @Delete("/{id}")
    @Consumes(MediaType.ALL)
    @RequiresPermission(resource = "PRODUCT", action = "WRITE")
    public ApiResponse<Void> delete(Long id) {
        productService.delete(id);
        return ApiResponse.success(null, "Product deleted");
    }
}
