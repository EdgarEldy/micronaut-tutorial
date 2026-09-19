package com.edgareldy.micronauttutorial;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;

/**
 * Entry point of the micronaut-tutorial application.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// @OpenAPIDefinition feeds micronaut-openapi, an annotation processor: at compile time it reads the
// controllers and this metadata and writes the OpenAPI YAML, with no runtime reflection.
@OpenAPIDefinition(info = @Info(title = "micronaut-tutorial", version = "0.1",
        description = "Identity/RBAC and e-commerce REST API"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
