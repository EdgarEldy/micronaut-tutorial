# micronaut-tutorial

A complete tutorial for building a REST API with **Micronaut Framework**, covering a full identity/RBAC domain (users, roles, permissions, tokens) and an e-commerce domain (categories, products, customers, orders) as two independent data models behind one API.

This document is the **complete specification** of the project: it is meant to be followed step by step to implement each branch.

## Table of contents

- [What is Micronaut, and how does it compare to Spring Boot and Quarkus](#what-is-micronaut-and-how-does-it-compare-to-spring-boot-and-quarkus)
- [Why Micronaut Framework 4.x, not 5.x](#why-micronaut-framework-4x-not-5x)
- [Compile-time AOP: implementing permission checks without a runtime proxy](#compile-time-aop-implementing-permission-checks-without-a-runtime-proxy)
- [Micronaut Data: repositories with no hand-written implementation](#micronaut-data-repositories-with-no-hand-written-implementation)
- [Test Resources: why there's no manual Testcontainers setup](#test-resources-why-theres-no-manual-testcontainers-setup)
- [Tech stack](#tech-stack)
- [Data model](#data-model)
- [Branching strategy](#branching-strategy)
- [Project structure](#project-structure)
- [Standard response format](#standard-response-format)
- [feature/core-architecture](#featurecore-architecture)
- [feature/auth](#featureauth)
- [feature/rbac](#featurerbac)
- [feature/categories](#featurecategories)
- [feature/products](#featureproducts)
- [feature/customers](#featurecustomers)
- [feature/orders](#featureorders)
- [Order of work](#order-of-work)
- [Code conventions](#code-conventions)
- [Concepts covered](#concepts-covered)
- [How to follow this tutorial](#how-to-follow-this-tutorial)

## What is Micronaut, and how does it compare to Spring Boot and Quarkus

**Micronaut** is a JVM framework created in 2018 by the team behind Grails, built around a single core idea: dependency injection and AOP resolved entirely by the Java compiler's annotation processor, at compile time, with zero reflection and zero runtime classpath scanning. Micronaut predates Quarkus (2019) and Spring Native by a year or more on this specific idea - it was the first mainstream JVM framework to build its whole architecture around avoiding reflection rather than adding a native-compilation mode on top of a reflection-based framework afterward.

The practical consequence is the same family of benefits Quarkus offers (fast startup, low memory, straightforward GraalVM native compilation), reached by a different mechanism:

| | Spring Boot 4 | Quarkus | Micronaut |
|---|---|---|---|
| Dependency injection | Spring's own container, resolved mostly at runtime via reflection | CDI (ArC), resolved at build time via a dedicated build-time "augmentation" step | Standard Java annotation processing, resolved as part of the **normal `javac` compile** - no separate build step |
| AOP mechanism | Runtime proxies (JDK dynamic proxies or CGLIB) | Build-time bytecode generation, tied to the same augmentation step as DI | Compile-time generated interceptor classes, produced by the same annotation processor as DI |
| How a dependency bundle works | A *starter* pulls in libraries; auto-configuration activates at runtime | An *extension* pulls in libraries; wiring is precomputed at build time | A dependency plus its own annotation processor; wiring is precomputed at compile time |
| Native compilation (GraalVM) | Possible, added on top of a reflection-based core | First-class, enabled by the build-time model | First-class, enabled by the compile-time model, and the oldest of the three to support it |
| Automatic local dev database | Manual | Dev Services (automatic Testcontainers) | Test Resources (automatic Testcontainers) - the same idea, different name |
| HTTP server | Servlet container (Tomcat by default) or Netty (WebFlux) | Vert.x/Netty | Netty, always |
| Ecosystem size, maturity | Largest of the three | Strong in the Kubernetes/cloud-native niche, backed by Red Hat | Smaller than both, strong in serverless (AWS Lambda cold-start is one of its original design targets) |

None of this makes Micronaut strictly better or worse than Quarkus - they solve the startup/memory problem with genuinely different implementation strategies (a dedicated build-time augmentation phase versus ordinary compile-time annotation processing), and either is a legitimate choice for the same class of workload. This tutorial exists to teach Micronaut specifically, on its own terms - the data model, the layering, and several conventions below (contract/implementation services, a generic `ApiResponse<T>`) are deliberately the same ones used across comparable tutorials for other frameworks, so what's left to explain is what's actually Micronaut-specific.

## Why Micronaut Framework 4.x, not 5.x

Micronaut Framework 5.0 (May 2026) raised the framework's Java baseline to **Java 25**. This tutorial targets **Java 21**, so it stays on the **Micronaut Framework 4.x** line, which continues to support Java 17 and Java 21. This is a deliberate, stated choice, not an oversight - if a Java 25 baseline is acceptable for a given deployment target, Micronaut 5.x would be the version to reach for instead, but that's a different starting point than this tutorial's.

## Compile-time AOP: implementing permission checks without a runtime proxy

Micronaut has no framework-provided fine-grained permission annotation (its own `@Secured` only covers roles), so this tutorial builds one the same way Micronaut itself is built: as compile-time AOP, not a runtime interceptor chain.

- `@RequiresPermission(resource = "CATEGORY", action = "WRITE")`: a custom annotation, meta-annotated `@Around` (Micronaut's AOP advice type) and `@Type(PermissionInterceptor.class)`
- `PermissionInterceptor implements MethodInterceptor<Object, Object>`: reads the permissions embedded in the current request's validated JWT (via Micronaut Security's `SecurityService`), compares them against the annotation's `resource`/`action`, and either proceeds (`context.proceed()`) or returns a 403
- Because `@Around` advice in Micronaut is woven in **at compile time** - the annotation processor generates a real subclass with the interceptor call inlined, not a JDK/CGLIB proxy created at startup - annotating a method with `@RequiresPermission` has no runtime reflection cost at all, consistent with how every other cross-cutting concern in this framework works

## Micronaut Data: repositories with no hand-written implementation

`@Repository` on an interface is enough - Micronaut Data's own annotation processor reads the method signatures (`findByEmail`, `findByCategoryId`, ...) at compile time and generates the SQL and the implementing class itself, the same way Spring Data does, except the implementation is real generated Java source inspectable in `target/generated-sources/annotations`, not a runtime dynamic proxy. This tutorial never hand-writes a `*RepositoryImpl` class the way a framework without this capability would require - only the `service/impl/` layer (genuine business logic) follows the contract/implementation split described in [Code conventions](#code-conventions).

## Test Resources: why there's no manual Testcontainers setup

With `micronaut-test-resources-jdbc-postgresql` on the classpath and no datasource URL configured, Micronaut's Test Resources service resolves `jdbc.url`, `jdbc.username`, and `jdbc.password` to an ephemeral, Testcontainers-backed PostgreSQL instance automatically - for `mn:run` in local development and for `@MicronautTest` alike. This is the direct Micronaut equivalent of the zero-configuration database provisioning other build-time JVM frameworks offer under a different name; this tutorial's test suite has no `@Testcontainers`/`@Container` boilerplate anywhere because of it.

## Tech stack

| Component | Choice |
|---|---|
| Framework | Micronaut Framework 4.x (Java 17/21 baseline) |
| Language | Java 21 (LTS) |
| Build | Maven |
| HTTP | Micronaut HTTP Server (Netty), `@Controller`/`@Get`/`@Post`/... |
| Database | PostgreSQL 16 (Test Resources in dev/test, Docker Compose for the packaged app) |
| Data access | Micronaut Data JPA (`micronaut-data-hibernate-jpa`, `@Repository`, compile-time generated implementations) |
| Migrations | Micronaut Flyway integration |
| Validation | Micronaut Validation (Jakarta Bean Validation, compile-time processed) |
| Security | `micronaut-security-jwt` (token issuance/validation), custom compile-time AOP for fine-grained permissions |
| API documentation | `micronaut-openapi` (Swagger UI) |
| Monitoring | Micronaut Management (health endpoint) |
| Caching | `micronaut-cache-caffeine` |
| Scheduling | Micronaut's built-in `@Scheduled` |
| Tests | `micronaut-test-junit5`, REST Assured, Test Resources (no manual Testcontainers setup) |
| CI/CD | GitHub Actions |
| Containerization | Docker, docker-compose; GraalVM native image (bonus) |

## Data model

Two independent domains, one shared database, no cross-domain foreign key.

```
users (id, first_name, last_name, email, password, enabled, account_locked)
    │ N──N (via role_user)
roles (id, role_name)
    │ N──N (via role_permission)
permissions (id, resource, action)

activation_tokens (id, user_id, token, created_at, expires_at, validated_at)
blacklisted_tokens (id, user_id, token, jti, blacklisted_at, created_at, expires_at, validated_at)
password_reset_tokens (id, user_id, token, type, expiry_date)
audit_logs (id, actor_user_id, action, entity_type, entity_id, details, created_at)

categories (id, category_name)
    │ 1
    │
    │ N
products (id, category_id, product_name, unit_price)
    │ 1
    │
    │ N
orders (id, customer_id, product_id, quantity, total)
    │ N
    │
    │ 1
customers (id, first_name, last_name, telephone, email, address)
```

## Branching strategy

| Branch | Role |
|---|---|
| `master` | Stable, production-ready code. No direct commits, only merges from `develop`. |
| `develop` | Integration branch. |
| `feature/core-architecture` | Project structure, Flyway/PostgreSQL configuration, exception handling, Docker, CI. |
| `feature/auth` | User entity, registration, activation, login, JWT issuance, logout (blacklist), password reset. |
| `feature/rbac` | Roles/permissions, `PermissionInterceptor`, full CRUD, audit logging. |
| `feature/categories` | Category CRUD. |
| `feature/products` | Product CRUD, depends on `categories`. |
| `feature/customers` | Customer CRUD. |
| `feature/orders` | Order create/read (orders are immutable), depends on `products`/`customers`. |

## Project structure

```
micronaut-tutorial/
├── src/
│   ├── main/
│   │   ├── java/com/edgareldy/micronauttutorial/
│   │   │   ├── entity/
│   │   │   │   ├── User.java, Role.java, Permission.java
│   │   │   │   ├── ActivationToken.java, BlacklistedToken.java, PasswordResetToken.java, AuditLog.java
│   │   │   │   ├── Category.java, Product.java, Customer.java, Order.java
│   │   │   ├── repository/
│   │   │   │   ├── UserRepository.java, RoleRepository.java, PermissionRepository.java
│   │   │   │   │   (each: a plain interface annotated @Repository - no hand-written impl, see
│   │   │   │   │    "Micronaut Data: repositories with no hand-written implementation")
│   │   │   │   └── CategoryRepository.java, ProductRepository.java,
│   │   │   │       CustomerRepository.java, OrderRepository.java, AuditLogRepository.java
│   │   │   ├── dto/
│   │   │   │   ├── common/ (ApiResponse.java, PageResponse.java)
│   │   │   │   ├── auth/, rbac/, ecommerce/
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java, RbacService.java, CategoryService.java, ProductService.java,
│   │   │   │   │   CustomerService.java, OrderService.java, AuditLogger.java   (contracts)
│   │   │   │   └── impl/ (one *ServiceImpl per interface, @Singleton)
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── UserController.java, RoleController.java, PermissionController.java
│   │   │   │   ├── CategoryController.java, ProductController.java,
│   │   │   │   │   CustomerController.java, OrderController.java
│   │   │   ├── security/
│   │   │   │   ├── JwtIssuer.java
│   │   │   │   ├── RequiresPermission.java        (custom annotation, @Around)
│   │   │   │   └── PermissionInterceptor.java      (MethodInterceptor<Object, Object>)
│   │   │   └── exception/
│   │   │       ├── ResourceNotFoundException.java, BusinessRuleException.java
│   │   │       ├── GlobalExceptionHandler.java     (ExceptionHandler<Exception, HttpResponse<?>>)
│   │   │       └── *ApiHandler.java                (@Replaces of the framework's more specific handlers, all delegating to GlobalExceptionHandler)
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-test.yml
│   │       ├── application-prod.yml            (packaged app: datasource and JWT key paths from the environment)
│   │       └── db/migration/
│   │           └── V1__init_schema.sql
│   └── test/
│       └── java/com/edgareldy/micronauttutorial/
│           ├── controller/  (@MicronautTest + REST Assured)
│           ├── service/     (Mockito)
│           └── repository/  (@MicronautTest, Test Resources-backed PostgreSQL, no manual container setup)
├── dev-keys/                             (development-only RSA key pair, deliberately outside src/main/resources)
├── docker-compose.yml
├── Dockerfile
├── .github/workflows/ci.yml
├── pom.xml
└── README.md
```

## Standard response format

Every response is wrapped in a generic `ApiResponse<T>`.

```java
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }
}
```

`GlobalExceptionHandler` (`ExceptionHandler<Exception, HttpResponse<?>>`, annotated `@Produces` + `@Singleton` + `@Requires(classes = Exception.class)`) maps `ResourceNotFoundException` to 404, Bean Validation failures to 400 (with per-field messages), `BusinessRuleException` to 422, and anything unmapped to 500 - always returning an `ApiResponse<Void>` with `success = false`.

Micronaut picks the exception handler whose type is closest to the thrown exception, so the framework's own more specific handlers (`ConstraintExceptionHandler`, `HttpStatusHandler`, `NotAllowedExceptionHandler`, `ConversionErrorHandler`, `UnsatisfiedRouteHandler`, `UnsatisfiedArgumentHandler`, `JsonExceptionHandler`) would win over a handler for `Exception` and answer in the framework's own format. Each one is replaced (`@Replaces`) by a small handler delegating to the same mapping method of `GlobalExceptionHandler`, so validation errors, unknown routes (404), wrong methods (405), unsupported content types (415) and malformed JSON (400) all come back as an `ApiResponse` too. `micronaut.security.reject-not-found` is set to `false` so anonymous callers get those statuses instead of a 401. The 401/403 produced by Micronaut Security itself are handled in `feature/auth` and `feature/rbac`.

## feature/core-architecture

### Tasks

- [x] Generate the project via Micronaut Launch (`https://launch.micronaut.io`) or the `mn` CLI, Java 21, Maven, Micronaut Framework 4.x
- [x] Dependencies: `micronaut-http-server-netty`, `micronaut-data-hibernate-jpa`, `micronaut-jdbc-hikari`, `postgresql` driver, `micronaut-flyway`, `micronaut-validation`, `micronaut-security-jwt`, `micronaut-openapi`, `micronaut-management`, `micronaut-cache-caffeine`
- [x] Test dependencies: `micronaut-test-junit5`, `rest-assured`, `micronaut-test-resources-jdbc-postgresql`
- [x] `ApiResponse<T>`, `PageResponse<T>`, `GlobalExceptionHandler`
- [x] Flyway script `V1__init_schema.sql` (all tables from both domains)
- [x] `application.yml`: JWT signing key location (RS256 key files under `dev-keys/` in dev/test, environment variables with no default in prod), Flyway enabled; `application-dev.yml`/`application-test.yml` left without a configured datasource (Test Resources provisions PostgreSQL automatically); a real connection string only under the packaged/production configuration
- [x] `docker-compose.yml` (app + PostgreSQL, for the packaged application only - not used in dev/test), `Dockerfile`
- [x] `.github/workflows/ci.yml`: `mvn verify` (Test Resources provisions PostgreSQL inside the CI runner automatically, same as locally)

## feature/auth

### Endpoints

| Method | URL | Description |
|---|---|---|
| POST | `/api/v1/auth/register` | Register (creates a disabled user + activation token) |
| GET | `/api/v1/auth/activate-account` | Activates a user account |
| POST | `/api/v1/auth/login` | Returns a JWT |
| POST | `/api/v1/auth/logout` | Blacklists the current JWT |
| GET | `/api/v1/auth/me` | Current user profile |
| POST | `/api/v1/auth/forgot-password` | Generates a password-reset token |
| POST | `/api/v1/auth/reset-password` | Consumes the token, updates the password |

### Tasks

- [x] `User` entity, `ActivationToken`, `BlacklistedToken`, `PasswordResetToken`
- [x] `UserRepository` (`@Repository` interface, Micronaut Data-generated)
- [x] `AuthService` (interface) + implementation: registration, activation, login (password hashing/verification via `BCryptPasswordEncoder` from `spring-security-crypto`, exposed as a bean: Micronaut Security 4.x ships no bcrypt encoder of its own), logout, forgot/reset password
- [x] `forgotPassword` returns the exact same response - same status code, same body, roughly the same timing - whether or not the submitted email matches an existing account, so the endpoint can't be used to enumerate registered emails
- [x] `JwtIssuer`: builds a signed JWT via Micronaut Security's `JwtTokenGenerator`, with a unique `jti` claim and the user's resolved permissions embedded as a custom claim
- [x] A custom token check on the incoming JWT's `jti` against `BlacklistedToken`, rejecting the token if found. A plain `TokenValidator` bean cannot do this: the token validators are consulted independently and `JwtTokenValidator` would still authenticate a correctly signed token, so the check is a `GenericJwtClaimsValidator` (`BlacklistedTokenClaimsValidator`), which `JwtTokenValidator` calls after the signature check and off the event loop
- [x] `AuthController` (public routes `@Secured(IS_ANONYMOUS)`, `logout` and `me` `@Secured(IS_AUTHENTICATED)`); Micronaut Security's own 401/403 rejections are turned into an `ApiResponse` by `AuthorizationApiHandler`
- [x] Tests (`@MicronautTest` + REST Assured): register → activate → login → access `/me`, logout followed by a rejected request with the same token, forgot/reset password flow

## feature/rbac

Full CRUD for users, roles, and permissions. Assignments always flow in one direction, never the other: **permissions are assigned onto a role**, never the reverse; **roles are assigned onto a user**, never the reverse.

### Endpoints

| Method | URL | Description | Access |
|---|---|---|---|
| GET | `/api/v1/users` | Paginated list | `@RequiresPermission(USER, READ)` |
| GET | `/api/v1/users/{id}` | Detail, including assigned roles | `@RequiresPermission(USER, READ)` |
| PATCH | `/api/v1/users/{id}/roles/{roleId}` | Assign a role to a user | `@RequiresPermission(USER, WRITE)` |
| DELETE | `/api/v1/users/{id}/roles/{roleId}` | Remove a role from a user | `@RequiresPermission(USER, WRITE)` |
| GET | `/api/v1/roles` | List, including permissions | `@RequiresPermission(ROLE, READ)` |
| POST | `/api/v1/roles` | Create | `@RequiresPermission(ROLE, WRITE)` |
| PUT | `/api/v1/roles/{id}` | Update (name only) | `@RequiresPermission(ROLE, WRITE)` |
| DELETE | `/api/v1/roles/{id}` | Delete | `@RequiresPermission(ROLE, WRITE)` |
| POST | `/api/v1/roles/{id}/permissions/{permissionId}` | Assign a permission to a role | `@RequiresPermission(ROLE, WRITE)` |
| DELETE | `/api/v1/roles/{id}/permissions/{permissionId}` | Remove a permission from a role | `@RequiresPermission(ROLE, WRITE)` |
| GET | `/api/v1/permissions` | List | `@RequiresPermission(PERMISSION, READ)` |
| POST | `/api/v1/permissions` | Create | `@RequiresPermission(PERMISSION, WRITE)` |
| PUT | `/api/v1/permissions/{id}` | Update | `@RequiresPermission(PERMISSION, WRITE)` |
| DELETE | `/api/v1/permissions/{id}` | Delete | `@RequiresPermission(PERMISSION, WRITE)` |

### Tasks

- [x] `Role`, `Permission`, `AuditLog` entities, `RoleRepository`, `PermissionRepository`, `AuditLogRepository`
- [x] `RbacService` (interface) + implementation:
  - `createRole`/`updateRole`/`deleteRole` - `deleteRole` rejects if any user is still assigned this role
  - `createPermission`/`updatePermission`/`deletePermission` - `deletePermission` rejects if any role still has this permission assigned
  - `assignPermissionToRole`/`removePermissionFromRole` - rejects removing `ROLE:WRITE` from a role if it would leave **zero** users anywhere holding a role that grants `ROLE:WRITE`. Only enabled, unlocked accounts count as holders, and a PostgreSQL transaction advisory lock (`pg_advisory_xact_lock`) serialises every operation that can remove one (also `removeRoleFromUser` and an `updatePermission` that alters `ROLE:WRITE`). Because permissions travel inside the JWT, a user stripped of a role keeps that access in an already issued token until it expires: the rule is about database state, not live tokens
  - `assignRoleToUser`/`removeRoleFromUser` - the same last-admin check applied at the point of removal from a specific user
- [x] `AuditLogger`: `log(String action, String entityType, Long entityId, String details)`, called from every method above and joining the business transaction, plus `logRejected(...)` for refusals, written in an independent transaction (`REQUIRES_NEW`) so the refusal row survives the rollback of the rejected change
- [x] `RequiresPermission` annotation (`resource`, `action` attributes, meta-annotated `@Around`)
- [x] `PermissionInterceptor` (`MethodInterceptor<Object, Object>`): reads the `permissions` claim from the `Authentication` that `SecurityService` exposes, compares it against the intercepted method's `@RequiresPermission`, and either proceeds or throws `ForbiddenException` (rendered as a 403 `ApiResponse` by `GlobalExceptionHandler`)
- [x] `UserController`, `RoleController`, `PermissionController`, each protected method annotated `@RequiresPermission` as listed above - `UserController` only manages role assignment on existing users, never user creation directly (registration stays exclusively `feature/auth`'s job)
- [x] A seeding step: Flyway migration `V2__seed_baseline_rbac.sql` (14 baseline permissions covering every resource/action this project defines, assigned to a seeded `ADMIN` role - without this, nobody could ever be granted `ROLE:WRITE`/`PERMISSION:WRITE` to create the first assignment), plus an optional `AdminBootstrap` (`ServerStartupEvent` listener) that creates a first enabled administrator when `app.bootstrap-admin.email`/`password` are configured (dev profile only, with fake values)
- [x] `ExpiredTokenCleanupJob` (`@Scheduled(cron = "0 0 3 * * *")`): daily job deleting `BlacklistedToken`/`ActivationToken`/`PasswordResetToken` rows past their expiry
- [x] Tests: full CRUD on roles and permissions, the "still referenced" rejection on both `deleteRole` and `deletePermission`, `PermissionInterceptor` allowing/denying correctly, and specifically the last-admin rejection triggered both ways, plus an assertion that every mutation above produces a matching `AuditLog` row

## feature/categories

### Endpoints

| Method | URL | Description | Access |
|---|---|---|---|
| GET | `/api/v1/categories` | Paginated list | `@RequiresPermission(CATEGORY, READ)` |
| GET | `/api/v1/categories/{id}` | Detail | `@RequiresPermission(CATEGORY, READ)` |
| POST | `/api/v1/categories` | Create | `@RequiresPermission(CATEGORY, WRITE)` |
| PUT | `/api/v1/categories/{id}` | Update | `@RequiresPermission(CATEGORY, WRITE)` |
| DELETE | `/api/v1/categories/{id}` | Delete | `@RequiresPermission(CATEGORY, WRITE)` |

### Tasks

- [x] `Category` entity, repository, contract/implementation service
- [x] Business rule: deleting a category that still has products is rejected (`BusinessRuleException` → 422)
- [x] `CategoryController`
- [x] Tests for every endpoint, including the rejection case and a permission-denied case

## feature/products

Depends on `feature/categories` existing, since every product references one.

### Endpoints

| Method | URL | Description | Access |
|---|---|---|---|
| GET | `/api/v1/products` | Paginated list, filterable by `categoryId` | `@RequiresPermission(PRODUCT, READ)` |
| GET | `/api/v1/products/{id}` | Detail | `@RequiresPermission(PRODUCT, READ)` |
| POST | `/api/v1/products` | Create | `@RequiresPermission(PRODUCT, WRITE)` |
| PUT | `/api/v1/products/{id}` | Update | `@RequiresPermission(PRODUCT, WRITE)` |
| DELETE | `/api/v1/products/{id}` | Delete | `@RequiresPermission(PRODUCT, WRITE)` |

### Tasks

- [x] `Product` entity, repository, contract/implementation service
- [x] `ProductService.findById` annotated `@Cacheable(value = "product-cache", parameters = "id")`; `update`/`delete` annotated `@CacheInvalidate(value = "product-cache", parameters = "id")` on the same key. The annotations work on the interface methods (verified). The default cache key is built from ALL parameters, so `parameters = "id"` is required: without it `update(id, request)` would never evict the entry cached by `findById(id)`. Only the `ProductResponse` DTO is cached (never an entity, and no category name, which a category rename would leave stale); the cache is bounded (500 entries, 10 minutes)
- [x] `ProductController`
- [x] Tests, including the category filter, a permission-denied case, and a cache invalidation test

## feature/customers

### Endpoints

| Method | URL | Description | Access |
|---|---|---|---|
| GET | `/api/v1/customers` | Paginated list | `@RequiresPermission(CUSTOMER, READ)` |
| GET | `/api/v1/customers/{id}` | Detail | `@RequiresPermission(CUSTOMER, READ)` |
| POST | `/api/v1/customers` | Create | `@RequiresPermission(CUSTOMER, WRITE)` |
| PUT | `/api/v1/customers/{id}` | Update | `@RequiresPermission(CUSTOMER, WRITE)` |
| DELETE | `/api/v1/customers/{id}` | Delete | `@RequiresPermission(CUSTOMER, WRITE)` |

### Tasks

- [x] `Customer` entity, repository, contract/implementation service
- [x] `CustomerController`
- [x] Tests

## feature/orders

### Endpoints

| Method | URL | Description | Access |
|---|---|---|---|
| GET | `/api/v1/orders` | Paginated list, filterable by `customerId`/`productId` | `@RequiresPermission(ORDER, READ)` |
| GET | `/api/v1/orders/{id}` | Detail | `@RequiresPermission(ORDER, READ)` |
| POST | `/api/v1/orders` | Create (computes `total`) | `@RequiresPermission(ORDER, WRITE)` |

### Tasks

- [x] `Order` entity, repository, contract/implementation service: computes `total = quantity * product.unitPrice` (scale 2, `HALF_UP`, frozen in the order row: a later price change does not alter existing orders; a total that does not fit `NUMERIC(14,2)` is a 422). `Order` is a JPQL keyword, so the entity is declared `@Entity(name = "CustomerOrder")` on the `orders` table. Deleting a product or a customer that still has orders is refused with a 422
- [x] `OrderController`
- [x] Tests, including the total computation

## Order of work

1. `feature/core-architecture` → Pull Request to `develop`
2. `feature/auth` (depends on `core-architecture`) → Pull Request to `develop`
3. `feature/rbac` (depends on `auth`) → Pull Request to `develop`
4. `feature/categories` (depends on `rbac`) → Pull Request to `develop`
5. `feature/products` (depends on `categories`) → Pull Request to `develop`
6. `feature/customers` (depends on `rbac`) → Pull Request to `develop`
7. `feature/orders` (depends on `products`, `customers`) → Pull Request to `develop`
8. `develop` → `master`

## Code conventions

- Root package: `com.edgareldy.micronauttutorial`
- **Contract/implementation services**: interface at the root of `service/`, implementation in `service/impl/`, annotated `@Singleton` - repositories are the one exception, since Micronaut Data generates their implementation, there is no `repository/impl/`
- Every endpoint returns an `ApiResponse<T>` (or `ApiResponse<PageResponse<T>>` for lists)
- Permission checks are always declarative (`@RequiresPermission(resource, action)` on the controller method), never a manual `if` inside a method body
- No controller method contains business logic - it validates via Bean Validation annotations on the request DTO, delegates to a service, and maps the result to an `ApiResponse<T>`
- Assignments always flow in one direction, never the other - see [feature/rbac](#featurerbac)
- Every RBAC mutation calls `AuditLogger`

## Concepts covered

- Compile-time dependency injection and AOP, and how that differs mechanically from both runtime-proxy AOP and build-time-augmented AOP
- Test Resources: automatic, zero-configuration database provisioning for development and tests
- Micronaut HTTP controllers, request/response mapping, Bean Validation
- Micronaut Data JPA: compile-time generated repository implementations
- JWT issuance and validation with Micronaut Security
- Token revocation via a blacklist checked through a custom JWT claims validator
- Custom compile-time AOP for fine-grained, declarative permission checks
- Centralized exception handling with `ExceptionHandler<T, R>`
- Generic `ApiResponse<T>` DTO, contract/implementation pattern
- Method-level caching (`@Cacheable`/`@CacheInvalidate`)
- Scheduled background jobs (`@Scheduled`) for housekeeping tasks
- Directional relationship management (permissions onto roles, roles onto users) as a deliberate convention
- Audit logging as a first-class concern for every sensitive RBAC mutation
- Testing with `@MicronautTest` and REST Assured, without manual Testcontainers setup
- Containerization (Docker, docker-compose)
- Continuous integration (GitHub Actions)

## How to follow this tutorial

1. Clone the repository : https://github.com/EdgarEldy/micronaut-tutorial.git and check out `develop`
2. Follow the branches in order: `feature/core-architecture` → `feature/auth` → `feature/rbac` → `feature/categories` → `feature/products` → `feature/customers` → `feature/orders`
3. Run in dev mode: `mvn mn:run` (Test Resources starts PostgreSQL automatically, no `docker-compose up` needed for this)
4. Browse Swagger UI at `http://localhost:8085/swagger-ui` in dev mode (the OpenAPI YAML is at `/swagger/micronaut-tutorial-0.1.yml`)
5. To run the packaged application instead: `docker-compose up`
