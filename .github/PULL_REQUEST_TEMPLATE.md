## Branch

<!-- e.g. feature/core-architecture -->

## Task checklist

<!-- Copy the relevant section's checklist from README.md and check off each item. -->

- [ ]

## Commit summary

<!-- One line per group of commits, summarizing what changed and why. -->

## Test plan

- [ ] Unit tests pass
- [ ] Integration tests pass (where the branch's checklist calls for them)
- [ ] `mvn clean verify` is green on the whole project

## Code review checklist

- [ ] No business logic in controllers
- [ ] Every response, success or error, is an `ApiResponse<T>` (errors through `GlobalExceptionHandler`)
- [ ] Permission checks are declarative (`@RequiresPermission`), no manual permission `if`
- [ ] Every `@Cacheable` has its matching `@CacheInvalidate` on the write operations
- [ ] Every RBAC mutation calls `AuditLogger`
- [ ] The database schema only changes through a Flyway migration
- [ ] No manual Testcontainers setup (Test Resources only)
- [ ] Commits are atomic, follow Conventional Commits with a scope, no em dash, no tool attribution
