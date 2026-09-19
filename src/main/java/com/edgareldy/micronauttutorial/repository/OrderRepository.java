package com.edgareldy.micronauttutorial.repository;

import com.edgareldy.micronauttutorial.entity.Order;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

/**
 * Data access for orders. Filters and counts are derived from the method names.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Orders of one customer, paginated. */
    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    /** Orders of one product, paginated. */
    Page<Order> findByProductId(Long productId, Pageable pageable);

    /** Orders of one customer for one product, paginated. */
    Page<Order> findByCustomerIdAndProductId(Long customerId, Long productId, Pageable pageable);

    /** How many orders reference the product. */
    long countByProductId(Long productId);

    /** How many orders reference the customer. */
    long countByCustomerId(Long customerId);
}
