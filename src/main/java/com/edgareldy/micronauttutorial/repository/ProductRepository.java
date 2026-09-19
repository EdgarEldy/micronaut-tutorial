package com.edgareldy.micronauttutorial.repository;

import com.edgareldy.micronauttutorial.entity.Product;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

/**
 * Data access for products. Micronaut Data derives findByCategoryId and countByCategoryId from their names.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Products of one category, paginated. */
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    /** How many products reference the category. */
    long countByCategoryId(Long categoryId);
}
