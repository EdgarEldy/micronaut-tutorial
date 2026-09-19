package com.edgareldy.micronauttutorial.controller;

import com.edgareldy.micronauttutorial.dto.common.ApiResponse;
import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.rbac.UserDetailResponse;
import com.edgareldy.micronauttutorial.dto.rbac.UserSummaryResponse;
import com.edgareldy.micronauttutorial.security.RequiresPermission;
import com.edgareldy.micronauttutorial.service.RbacService;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Users under /api/v1/users: listing, detail and role assignment. Never creates users (registration belongs to /auth).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// @Secured(IS_AUTHENTICATED) rejects a missing or invalid token (401) before the method runs; the
// @RequiresPermission on each method then adds the fine grained check through PermissionInterceptor.
// Bodiless PATCH/DELETE accept any Content-Type (@Consumes ALL) because clients often add a default one.
@Controller("/api/v1/users")
@Validated
@Secured(SecurityRule.IS_AUTHENTICATED)
public class UserController {

    private final RbacService rbacService;

    public UserController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Get
    @RequiresPermission(resource = "USER", action = "READ")
    public ApiResponse<PageResponse<UserSummaryResponse>> list(
            @QueryValue(defaultValue = "0") @PositiveOrZero int page,
            @QueryValue(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(rbacService.listUsers(page, size), "Users");
    }

    @Get("/{id}")
    @RequiresPermission(resource = "USER", action = "READ")
    public ApiResponse<UserDetailResponse> get(Long id) {
        return ApiResponse.success(rbacService.getUser(id), "User");
    }

    @Patch("/{id}/roles/{roleId}")
    @Consumes(MediaType.ALL)
    @RequiresPermission(resource = "USER", action = "WRITE")
    public ApiResponse<UserDetailResponse> assignRole(Long id, Long roleId) {
        return ApiResponse.success(rbacService.assignRoleToUser(id, roleId), "Role assigned");
    }

    @Delete("/{id}/roles/{roleId}")
    @Consumes(MediaType.ALL)
    @RequiresPermission(resource = "USER", action = "WRITE")
    public ApiResponse<UserDetailResponse> removeRole(Long id, Long roleId) {
        return ApiResponse.success(rbacService.removeRoleFromUser(id, roleId), "Role removed");
    }
}
