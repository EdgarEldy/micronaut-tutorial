package com.edgareldy.micronauttutorial.service;

import com.edgareldy.micronauttutorial.dto.ecommerce.CustomerRequest;
import com.edgareldy.micronauttutorial.dto.ecommerce.CustomerResponse;
import com.edgareldy.micronauttutorial.entity.Customer;
import com.edgareldy.micronauttutorial.exception.ResourceNotFoundException;
import com.edgareldy.micronauttutorial.repository.CustomerRepository;
import com.edgareldy.micronauttutorial.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests of CustomerServiceImpl with the repository mocked: trimming, blank optional fields becoming null and missing customers.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
class CustomerServiceImplTest {

    private CustomerRepository customers;
    private CustomerServiceImpl service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customers = mock(CustomerRepository.class);
        service = new CustomerServiceImpl(customers);
        customer = new Customer("Old", "Name", "1", "old@example.com", "Old street");
        customer.setId(7L);
    }

    @Test
    void createTrimsValuesAndTurnsBlankOptionalsIntoNull() {
        when(customers.save(any(Customer.class))).thenAnswer(inv -> {
            Customer saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        CustomerResponse created = service.create(new CustomerRequest("  Ada ", " Lovelace  ", "   ", "", "  Rue 5 "));

        assertEquals("Ada", created.firstName());
        assertEquals("Lovelace", created.lastName());
        assertNull(created.telephone());
        assertNull(created.email());
        assertEquals("Rue 5", created.address());
    }

    @Test
    void createKeepsNullOptionalsAsNull() {
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse created = service.create(new CustomerRequest("Ada", "Lovelace", null, null, null));

        assertNull(created.telephone());
        assertNull(created.email());
        assertNull(created.address());
    }

    @Test
    void updateReplacesEveryFieldAndClearsBlankOptionals() {
        when(customers.findById(7L)).thenReturn(Optional.of(customer));
        when(customers.update(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse updated = service.update(7L, new CustomerRequest(" New ", " Person ", " ", null, " Avenue 9 "));

        assertEquals(7L, updated.id());
        assertEquals("New", updated.firstName());
        assertEquals("Person", updated.lastName());
        assertNull(updated.telephone());
        assertNull(updated.email());
        assertEquals("Avenue 9", updated.address());
    }

    @Test
    void deleteRemovesTheLoadedCustomer() {
        when(customers.findById(7L)).thenReturn(Optional.of(customer));

        service.delete(7L);

        verify(customers).delete(customer);
    }

    @Test
    void missingCustomersRaiseResourceNotFound() {
        when(customers.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(9L));
        assertThrows(ResourceNotFoundException.class,
                () -> service.update(9L, new CustomerRequest("a", "b", null, null, null)));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L));
        verify(customers, never()).delete(any());
        verify(customers, never()).update(any());
    }
}
