package com.edgareldy.micronauttutorial.repository;

import com.edgareldy.micronauttutorial.entity.Category;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

/**
 * Data access for categories. Micronaut Data generates the implementation at compile time.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
}
