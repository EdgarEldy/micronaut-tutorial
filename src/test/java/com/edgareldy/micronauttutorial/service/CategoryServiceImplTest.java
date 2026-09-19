package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.dto.ecommerce.CategoryRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.CategoryResponse;
import com.edgareldy.micronauttutorial.entity.Category;
import com.edgareldy.micronauttutorial.exception.BusinessRuleException;
import com.edgareldy.micronauttutorial.exception.ResourceNotFoundException;
import com.edgareldy.micronauttutorial.repository.CategoryRepository;
import com.edgareldy.micronauttutorial.repository.ProductRepository;
import com.edgareldy.micronauttutorial.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of CategoryServiceImpl with the repository mocked: the delete refusal, missing categories and name
 * trimming.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// The service is built by hand around a Mockito mock (same approach as RbacServiceImplTest), so no database is used.
class CategoryServiceImplTest {

    private CategoryRepository categories;
    private ProductRepository products;
    private CategoryServiceImpl service;
    private Category category;

    @BeforeEach
    void setUp() {
        categories = mock(CategoryRepository.class);
        products = mock(ProductRepository.class);
        service = new CategoryServiceImpl(categories, products);
        category = new Category("Books");
        category.setId(7L);
    }

    @Test
    void deleteIsRefusedAndNeverDeletesWhileProductsRemain() {
        when(categories.findById(7L)).thenReturn(Optional.of(category));
        when(products.countByCategoryId(7L)).thenReturn(3L);

        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> service.delete(7L));

        assertEquals("Category 7 still has 3 product(s) and cannot be deleted", e.getMessage());
        verify(categories, never()).delete(any());
    }

    @Test
    void deleteIsAllowedWhenNoProductRemains() {
        when(categories.findById(7L)).thenReturn(Optional.of(category));
        when(products.countByCategoryId(7L)).thenReturn(0L);

        service.delete(7L);

        verify(categories).delete(category);
    }

    @Test
    void missingCategoriesRaiseResourceNotFound() {
        when(categories.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(9L));
        assertThrows(ResourceNotFoundException.class, () -> service.update(9L, new CategoryRequest("x")));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L));
        verify(categories, never()).delete(any());
    }

    @Test
    void namesAreTrimmedOnCreateAndUpdate() {
        when(categories.save(any(Category.class))).thenAnswer(inv -> {
            Category saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        CategoryResponse created = service.create(new CategoryRequest("  Toys  "));
        assertEquals("Toys", created.categoryName());

        when(categories.findById(7L)).thenReturn(Optional.of(category));
        when(categories.update(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        CategoryResponse updated = service.update(7L, new CategoryRequest("  Games "));
        assertEquals("Games", updated.categoryName());
        assertEquals(7L, updated.id());
    }
}
