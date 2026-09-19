package com.edgareldy.micronauttutorial.service.impl;

import com.edgareldy.micronauttutorial.dto.common.PageResponse;
import com.edgareldy.micronauttutorial.dto.ecommerce.CustomerRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.CustomerResponse;
import com.edgareldy.micronauttutorial.entity.Customer;
import com.edgareldy.micronauttutorial.exception.ResourceNotFoundException;
import com.edgareldy.micronauttutorial.repository.CustomerRepository;
import com.edgareldy.micronauttutorial.service.CustomerService;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

/**
 * Default CustomerService. Delete is a plain delete until orders exist (feature/orders adds the refusal).
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
@Singleton
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customers;

    public CustomerServiceImpl(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(int page, int size) {
        Pageable pageable = Pageable.from(page, size, Sort.of(Sort.Order.asc("id")));
        return PageResponse.from(customers.findAll(pageable), CustomerServiceImpl::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        return toResponse(load(id));
    }

    @Override
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer();
        apply(customer, request);
        return toResponse(customers.save(customer));
    }

    @Override
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = load(id);
        apply(customer, request);
        return toResponse(customers.update(customer));
    }

    @Override
    public void delete(Long id) {
        customers.delete(load(id));
    }

    private Customer load(Long id) {
        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }

    private static void apply(Customer customer, CustomerRequest request) {
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setTelephone(blankToNull(request.telephone()));
        customer.setEmail(blankToNull(request.email()));
        customer.setAddress(blankToNull(request.address()));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getFirstName(), c.getLastName(), c.getTelephone(), c.getEmail(), c.getAddress());
    }
}
