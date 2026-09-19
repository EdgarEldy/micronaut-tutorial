package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.dto.common.ApiResponse;
import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.ecommerce.CategoryRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.CategoryResponse;
import com.edgareldy.micronauttutorial.security.RequiresPermission;
import com.edgareldy.micronauttutorial.service.CategoryService;
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
 * Categories under /api/v1/categories: paginated list, detail and CRUD.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Controller("/api/v1/categories")
@Validated
@Secured(SecurityRule.IS_AUTHENTICATED)
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Get
    @RequiresPermission(resource = "CATEGORY", action = "READ")
    public ApiResponse<PageResponse<CategoryResponse>> list(
            @QueryValue(defaultValue = "0") @PositiveOrZero int page,
            @QueryValue(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(categoryService.list(page, size), "Categories");
    }

    @Get("/{id}")
    @RequiresPermission(resource = "CATEGORY", action = "READ")
    public ApiResponse<CategoryResponse> get(Long id) {
        return ApiResponse.success(categoryService.findById(id), "Category");
    }

    @Post
    @RequiresPermission(resource = "CATEGORY", action = "WRITE")
    public HttpResponse<ApiResponse<CategoryResponse>> create(@Body @Valid CategoryRequest request) {
        return HttpResponse.status(HttpStatus.CREATED).body(ApiResponse.success(categoryService.create(request), "Category created"));
    }

    @Put("/{id}")
    @RequiresPermission(resource = "CATEGORY", action = "WRITE")
    public ApiResponse<CategoryResponse> update(Long id, @Body @Valid CategoryRequest request) {
        return ApiResponse.success(categoryService.update(id, request), "Category updated");
    }

    @Delete("/{id}")
    @Consumes(MediaType.ALL)
    @RequiresPermission(resource = "CATEGORY", action = "WRITE")
    public ApiResponse<Void> delete(Long id) {
        categoryService.delete(id);
        return ApiResponse.success(null, "Category deleted");
    }
}
