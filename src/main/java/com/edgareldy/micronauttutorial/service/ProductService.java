package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.ecommerce.ProductRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.ProductResponse;
import io.micronaut.cache.annotation.CacheInvalidate;
import io.micronaut.cache.annotation.Cacheable;

/**
 * Product use cases: paginated read (optional category filter), detail, create, update and delete.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
public interface ProductService {

    /** Products page (0 based) ordered by id, restricted to a category when categoryId is not null. */
    PageResponse<ProductResponse> list(Long categoryId, int page, int size);

    // @Cacheable / @CacheInvalidate (micronaut-cache): like @RequiresPermission, they are compile-time AOP,
    // the annotation processor generates the interceptor call, no runtime proxy or reflection. The result of
    // findById is kept in the Caffeine cache "product-cache" (see application.yml) so a repeated read skips the
    // database. By default the key is built from ALL parameters; update(id, request) has a second parameter, so
    // its default key would never match findById(id). parameters = "id" restricts the key to the id on all three
    // methods, hence the same entry is read, then evicted by update and delete. Only the ProductResponse DTO is
    // cached, never an entity.
    /** @throws com.edgareldy.micronauttutorial.exception.ResourceNotFoundException if the product does not exist */
    @Cacheable(value = "product-cache", parameters = "id")
    ProductResponse findById(Long id);

    /**
     * Creates a product; the name is trimmed.
     *
     * @throws com.edgareldy.micronauttutorial.exception.BusinessRuleException if the category does not exist
     */
    ProductResponse create(ProductRequest request);

    /**
     * @throws com.edgareldy.micronauttutorial.exception.ResourceNotFoundException if the product does not exist
     * @throws com.edgareldy.micronauttutorial.exception.BusinessRuleException     if the category does not exist
     */
    @CacheInvalidate(value = "product-cache", parameters = "id")
    ProductResponse update(Long id, ProductRequest request);

    /** @throws com.edgareldy.micronauttutorial.exception.ResourceNotFoundException if the product does not exist */
    @CacheInvalidate(value = "product-cache", parameters = "id")
    void delete(Long id);
}
