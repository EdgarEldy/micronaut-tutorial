package com.edgareldy.micronauttutorial.dto.rbac;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Public view of a permission.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Serdeable
public record PermissionResponse(Long id, String resource, String action) {
}
