package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.dto.common.ApiResponse;
import com.edgareldy.micronauttutorial.dto.rbac.PermissionRequest;
import com.edgareldy.micronauttutorial.dto.rbac.PermissionResponse;
import com.edgareldy.micronauttutorial.security.RequiresPermission;
import com.edgareldy.micronauttutorial.service.RbacService;
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
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;

import java.util.List;

/**
 * Permissions under /api/v1/permissions: CRUD on the RESOURCE and ACTION catalogue.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Controller("/api/v1/permissions")
@Validated
@Secured(SecurityRule.IS_AUTHENTICATED)
public class PermissionController {

    private final RbacService rbacService;

    public PermissionController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Get
    @RequiresPermission(resource = "PERMISSION", action = "READ")
    public ApiResponse<List<PermissionResponse>> list() {
        return ApiResponse.success(rbacService.listPermissions(), "Permissions");
    }

    @Post
    @RequiresPermission(resource = "PERMISSION", action = "WRITE")
    public HttpResponse<ApiResponse<PermissionResponse>> create(@Body @Valid PermissionRequest request) {
        return HttpResponse.status(HttpStatus.CREATED)
                .body(ApiResponse.success(rbacService.createPermission(request), "Permission created"));
    }

    @Put("/{id}")
    @RequiresPermission(resource = "PERMISSION", action = "WRITE")
    public ApiResponse<PermissionResponse> update(Long id, @Body @Valid PermissionRequest request) {
        return ApiResponse.success(rbacService.updatePermission(id, request), "Permission updated");
    }

    @Delete("/{id}")
    @Consumes(MediaType.ALL)
    @RequiresPermission(resource = "PERMISSION", action = "WRITE")
    public ApiResponse<Void> delete(Long id) {
        rbacService.deletePermission(id);
        return ApiResponse.success(null, "Permission deleted");
    }
}
