package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.ecommerce.ProductRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.ProductResponse;
import com.edgareldy.micronauttutorial.entity.Product;
import com.edgareldy.micronauttutorial.exception.BusinessRuleException;
import com.edgareldy.micronauttutorial.exception.ResourceNotFoundException;
import com.edgareldy.micronauttutorial.repository.CategoryRepository;
import com.edgareldy.micronauttutorial.repository.ProductRepository;
import com.edgareldy.micronauttutorial.service.impl.ProductServiceImpl;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of ProductServiceImpl with both repositories mocked: missing category and product rules, name
 * trimming and the choice of repository method for the list.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// The service is built by hand around Mockito mocks (no Micronaut context, so no cache or transaction advice).
class ProductServiceImplTest {

    private ProductRepository products;
    private CategoryRepository categories;
    private ProductServiceImpl service;
    private Product product;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        categories = mock(CategoryRepository.class);
        service = new ProductServiceImpl(products, categories);
        product = new Product(1L, "Pen", new BigDecimal("2.50"));
        product.setId(7L);
    }

    @Test
    void createWithAMissingCategoryIsRefusedAndNeverSaves() {
        when(categories.existsById(99L)).thenReturn(false);

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> service.create(new ProductRequest(99L, "Pen", BigDecimal.ONE)));

        assertEquals("Category 99 does not exist", e.getMessage());
        verify(products, never()).save(any());
    }

    @Test
    void updateWithAMissingCategoryIsRefusedAndNeverUpdates() {
        when(products.findById(7L)).thenReturn(Optional.of(product));
        when(categories.existsById(99L)).thenReturn(false);

        assertThrows(BusinessRuleException.class, () -> service.update(7L, new ProductRequest(99L, "Pen", BigDecimal.ONE)));

        verify(products, never()).update(any());
        assertEquals(1L, product.getCategoryId());
    }

    @Test
    void missingProductsRaiseResourceNotFound() {
        when(products.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(9L));
        assertThrows(ResourceNotFoundException.class, () -> service.update(9L, new ProductRequest(1L, "x", BigDecimal.ONE)));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L));
        verify(products, never()).delete(any());
        verify(products, never()).update(any());
    }

    @Test
    void namesAreTrimmedOnCreateAndUpdate() {
        when(categories.existsById(1L)).thenReturn(true);
        when(products.save(any(Product.class))).thenAnswer(inv -> {
            Product saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        ProductResponse created = service.create(new ProductRequest(1L, "  Ruler  ", new BigDecimal("3.00")));
        assertEquals("Ruler", created.productName());

        when(products.findById(7L)).thenReturn(Optional.of(product));
        when(products.update(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        ProductResponse updated = service.update(7L, new ProductRequest(1L, "  Marker ", new BigDecimal("4.00")));
        assertEquals("Marker", updated.productName());
        assertEquals(7L, updated.id());
    }

    @Test
    void listWithoutAFilterUsesFindAllAndWithOneUsesFindByCategoryId() {
        when(products.findAll(any(Pageable.class))).thenReturn(Page.of(List.of(product), Pageable.from(0, 10), 1L));
        when(products.findByCategoryId(eq(1L), any(Pageable.class))).thenReturn(Page.of(List.of(product), Pageable.from(0, 10), 1L));

        PageResponse<ProductResponse> all = service.list(null, 0, 10);
        assertEquals(1, all.content().size());
        verify(products).findAll(any(Pageable.class));
        verify(products, never()).findByCategoryId(any(), any());

        PageResponse<ProductResponse> filtered = service.list(1L, 0, 10);
        assertEquals(1, filtered.totalElements());
        verify(products).findByCategoryId(eq(1L), any(Pageable.class));
    }
}
