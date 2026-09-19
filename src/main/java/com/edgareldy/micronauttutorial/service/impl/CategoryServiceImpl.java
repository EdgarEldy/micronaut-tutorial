package com.edgareldy.micronauttutorial.service.impl;

import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.ecommerce.CategoryRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.CategoryResponse;
import com.edgareldy.micronauttutorial.entity.Category;
import com.edgareldy.micronauttutorial.exception.BusinessRuleException;
import com.edgareldy.micronauttutorial.exception.ResourceNotFoundException;
import com.edgareldy.micronauttutorial.repository.CategoryRepository;
import com.edgareldy.micronauttutorial.service.CategoryService;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

/**
 * Default CategoryService. The delete rule is check-then-act: a product inserted concurrently between the count and the delete hits fk_products_category and surfaces as a 500 (no locking, by design).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Singleton
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categories;

    public CategoryServiceImpl(CategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> list(int page, int size) {
        Pageable pageable = Pageable.from(page, size, Sort.of(Sort.Order.asc("id")));
        return PageResponse.from(categories.findAll(pageable), CategoryServiceImpl::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return toResponse(load(id));
    }

    @Override
    public CategoryResponse create(CategoryRequest request) {
        return toResponse(categories.save(new Category(request.categoryName().trim())));
    }

    @Override
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = load(id);
        category.setCategoryName(request.categoryName().trim());
        return toResponse(categories.update(category));
    }

    @Override
    public void delete(Long id) {
        Category category = load(id);
        long products = categories.countProductsByCategoryId(id);
        if (products > 0) {
            throw new BusinessRuleException("Category " + id + " still has " + products + " product(s) and cannot be deleted");
        }
        categories.delete(category);
    }

    private Category load(Long id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getCategoryName());
    }
}
