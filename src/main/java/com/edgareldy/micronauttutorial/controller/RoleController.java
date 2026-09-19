package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.dto.common.ApiResponse;
import com.edgareldy.micronauttutorial.dto.rbac.RoleRequest;
import com.edgareldy.micronauttutorial.dto.rbac.RoleResponse;
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
 * Roles under /api/v1/roles: CRUD plus assignment of permissions onto a role.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Controller("/api/v1/roles")
@Validated
@Secured(SecurityRule.IS_AUTHENTICATED)
public class RoleController {

    private final RbacService rbacService;

    public RoleController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Get
    @RequiresPermission(resource = "ROLE", action = "READ")
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.success(rbacService.listRoles(), "Roles");
    }

    @Post
    @RequiresPermission(resource = "ROLE", action = "WRITE")
    public HttpResponse<ApiResponse<RoleResponse>> create(@Body @Valid RoleRequest request) {
        return HttpResponse.status(HttpStatus.CREATED).body(ApiResponse.success(rbacService.createRole(request), "Role created"));
    }

    @Put("/{id}")
    @RequiresPermission(resource = "ROLE", action = "WRITE")
    public ApiResponse<RoleResponse> update(Long id, @Body @Valid RoleRequest request) {
        return ApiResponse.success(rbacService.updateRole(id, request), "Role updated");
    }

    @Delete("/{id}")
    @Consumes(MediaType.ALL)
    @RequiresPermission(resource = "ROLE", action = "WRITE")
    public ApiResponse<Void> delete(Long id) {
        rbacService.deleteRole(id);
        return ApiResponse.success(null, "Role deleted");
    }

    @Post("/{id}/permissions/{permissionId}")
    @Consumes(MediaType.ALL)
    @RequiresPermission(resource = "ROLE", action = "WRITE")
    public ApiResponse<RoleResponse> assignPermission(Long id, Long permissionId) {
        return ApiResponse.success(rbacService.assignPermissionToRole(id, permissionId), "Permission assigned");
    }

    @Delete("/{id}/permissions/{permissionId}")
    @Consumes(MediaType.ALL)
    @RequiresPermission(resource = "ROLE", action = "WRITE")
    public ApiResponse<RoleResponse> removePermission(Long id, Long permissionId) {
        return ApiResponse.success(rbacService.removePermissionFromRole(id, permissionId), "Permission removed");
    }
}
