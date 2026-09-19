package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.dto.ecommerce.ProductRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.ProductResponse;
import com.edgareldy.micronauttutorial.entity.Product;
import com.edgareldy.micronauttutorial.exception.BusinessRuleException;
import com.edgareldy.micronauttutorial.repository.CategoryRepository;
import com.edgareldy.micronauttutorial.repository.OrderRepository;
import com.edgareldy.micronauttutorial.repository.ProductRepository;
import com.github.benmanes.caffeine.cache.Cache;
import io.micronaut.cache.SyncCache;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the behaviour of the product cache with a Mockito counter on the repository: a repeated findById skips
 * the repository, update and delete evict, ids are cached independently, a failed update does not evict and only
 * ProductResponse DTOs are stored.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Cache advice only exists inside the Micronaut context (compile-time AOP on the ProductService interface), so a
// hand-built service like in ProductServiceImplTest would never cache. @MicronautTest boots the context and
// @MockBean replaces the real repositories with Mockito mocks: the service is the real, cached bean, the
// repository is a counter, so verify(repository, times(n)) shows how often the "database" was really reached.
@MicronautTest(transactional = false)
class ProductServiceCacheTest {

    @Inject
    ProductService service;

    @Inject
    ProductRepository products;

    @Inject
    CategoryRepository categories;

    // The Caffeine cache behind @Cacheable("product-cache"), injected by name to look inside it.
    @Inject
    @Named("product-cache")
    SyncCache<Cache<Object, Object>> cache;

    @MockBean(ProductRepository.class)
    ProductRepository productRepository() {
        return mock(ProductRepository.class);
    }

    @MockBean(OrderRepository.class)
    OrderRepository orderRepository() {
        return mock(OrderRepository.class);
    }

    @MockBean(CategoryRepository.class)
    CategoryRepository categoryRepository() {
        return mock(CategoryRepository.class);
    }

    @BeforeEach
    void setUp() {
        reset(products, categories);
        cache.invalidateAll();
    }

    private Product product(long id, String name, String price) {
        Product product = new Product(1L, name, new BigDecimal(price));
        product.setId(id);
        return product;
    }

    @Test
    void twoReadsOfTheSameIdReachTheRepositoryOnce() {
        when(products.findById(5L)).thenReturn(Optional.of(product(5L, "Pen", "2.50")));

        ProductResponse first = service.findById(5L);
        ProductResponse second = service.findById(5L);

        assertEquals(first, second);
        verify(products, times(1)).findById(5L);
    }

    @Test
    void differentIdsAreCachedIndependently() {
        when(products.findById(5L)).thenReturn(Optional.of(product(5L, "Pen", "2.50")));
        when(products.findById(6L)).thenReturn(Optional.of(product(6L, "Ink", "4.00")));

        assertEquals("Pen", service.findById(5L).productName());
        assertEquals("Ink", service.findById(6L).productName());
        service.findById(5L);
        service.findById(6L);

        verify(products, times(1)).findById(5L);
        verify(products, times(1)).findById(6L);
    }

    @Test
    void updateEvictsTheEntryAndTheNextReadReturnsTheNewValueThenCachesIt() {
        Product stored = product(5L, "Pen", "2.50");
        when(products.findById(5L)).thenReturn(Optional.of(stored));
        when(categories.existsById(1L)).thenReturn(true);
        when(products.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(new BigDecimal("2.50"), service.findById(5L).unitPrice());
        service.findById(5L);
        verify(products, times(1)).findById(5L);

        // update itself loads the product once (a second call), then evicts.
        service.update(5L, new ProductRequest(1L, "Pen", new BigDecimal("9.00")));
        verify(products, times(2)).findById(5L);

        assertEquals(new BigDecimal("9.00"), service.findById(5L).unitPrice());
        verify(products, times(3)).findById(5L);
        service.findById(5L);
        verify(products, times(3)).findById(5L);
    }

    @Test
    void deleteEvictsTheEntry() {
        when(products.findById(5L)).thenReturn(Optional.of(product(5L, "Pen", "2.50")));

        service.findById(5L);
        service.delete(5L);
        verify(products, times(2)).findById(5L);

        service.findById(5L);
        verify(products, times(3)).findById(5L);
    }

    @Test
    void aFailedUpdateDoesNotEvictTheEntry() {
        when(products.findById(5L)).thenReturn(Optional.of(product(5L, "Pen", "2.50")));
        when(categories.existsById(99L)).thenReturn(false);

        service.findById(5L);
        assertThrows(BusinessRuleException.class,
                () -> service.update(5L, new ProductRequest(99L, "Pen", BigDecimal.ONE)));
        verify(products, times(2)).findById(5L);
        verify(products, never()).update(any());

        service.findById(5L);
        verify(products, times(2)).findById(5L);
    }

    @Test
    void theCacheHoldsProductResponseDtosNeverEntities() {
        when(products.findById(5L)).thenReturn(Optional.of(product(5L, "Pen", "2.50")));
        assertTrue(cache.getNativeCache().asMap().isEmpty());

        service.findById(5L);

        assertEquals(1, cache.getNativeCache().asMap().size());
        cache.getNativeCache().asMap().values().forEach(value -> {
            // The Caffeine cache may wrap values (Optional or a placeholder), so unwrap before checking the type.
            Object unwrapped = value instanceof Optional<?> optional ? optional.orElseThrow() : value;
            assertInstanceOf(ProductResponse.class, unwrapped);
            assertFalse(unwrapped instanceof Product);
        });
    }
}
