package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.ecommerce.CategoryRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.CategoryResponse;

/**
 * Category use cases: paginated read, detail, create, update and delete (refused while products remain).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
public interface CategoryService {

    /** Categories page (0 based) ordered by id. */
    PageResponse<CategoryResponse> list(int page, int size);

    /** @throws com.edgareldy.micronauttutorial.exception.ResourceNotFoundException if the category does not exist */
    CategoryResponse findById(Long id);

    /** Creates a category; the name is trimmed. */
    CategoryResponse create(CategoryRequest request);

    /** @throws com.edgareldy.micronauttutorial.exception.ResourceNotFoundException if the category does not exist */
    CategoryResponse update(Long id, CategoryRequest request);

    /**
     * @throws com.edgareldy.micronauttutorial.exception.ResourceNotFoundException if the category does not exist
     * @throws com.edgareldy.micronauttutorial.exception.BusinessRuleException     if products still reference it
     */
    void delete(Long id);
}
