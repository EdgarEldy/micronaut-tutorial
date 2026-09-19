package com.edgareldy.micronauttutorial.service.impl;

import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.ecommerce.ProductRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.ProductResponse;
import com.edgareldy.micronauttutorial.entity.Product;
import com.edgareldy.micronauttutorial.exception.BusinessRuleException;
import com.edgareldy.micronauttutorial.exception.ResourceNotFoundException;
import com.edgareldy.micronauttutorial.repository.CategoryRepository;
import com.edgareldy.micronauttutorial.repository.ProductRepository;
import com.edgareldy.micronauttutorial.service.ProductService;
import io.micronaut.cache.annotation.CacheInvalidate;
import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

/**
 * Default ProductService.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Singleton
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;

    public ProductServiceImpl(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(Long categoryId, int page, int size) {
        Pageable pageable = Pageable.from(page, size, Sort.of(Sort.Order.asc("id")));
        return PageResponse.from(
                categoryId == null ? products.findAll(pageable) : products.findByCategoryId(categoryId, pageable),
                ProductServiceImpl::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return toResponse(load(id));
    }

    @Override
    public ProductResponse create(ProductRequest request) {
        requireCategory(request.categoryId());
        return toResponse(products.save(new Product(request.categoryId(), request.productName().trim(), request.unitPrice())));
    }

    @Override
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = load(id);
        requireCategory(request.categoryId());
        product.setCategoryId(request.categoryId());
        product.setProductName(request.productName().trim());
        product.setUnitPrice(request.unitPrice());
        return toResponse(products.update(product));
    }

    @Override
    public void delete(Long id) {
        products.delete(load(id));
    }

    private void requireCategory(Long categoryId) {
        if (!categories.existsById(categoryId)) {
            throw new BusinessRuleException("Category " + categoryId + " does not exist");
        }
    }

    private Product load(Long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    private static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getCategoryId(), product.getProductName(), product.getUnitPrice());
    }
}
