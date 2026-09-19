package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.ecommerce.CustomerRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.CustomerResponse;

/**
 * Customer use cases: paginated read, detail, create, update and delete.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
public interface CustomerService {

    /** Customers page (0 based) ordered by id. */
    PageResponse<CustomerResponse> list(int page, int size);

    /** @throws com.edgareldy.micronauttutorial.exception.ResourceNotFoundException if the customer does not exist */
    CustomerResponse findById(Long id);

    /** Creates a customer; strings are trimmed and blank optional values stored as null. */
    CustomerResponse create(CustomerRequest request);

    /** @throws com.edgareldy.micronauttutorial.exception.ResourceNotFoundException if the customer does not exist */
    CustomerResponse update(Long id, CustomerRequest request);

    /** @throws com.edgareldy.micronauttutorial.exception.ResourceNotFoundException if the customer does not exist */
    void delete(Long id);
}
