package com.edgareldy.micronauttutorial.repository;

import com.edgareldy.micronauttutorial.entity.Customer;
import com.edgareldy.micronauttutorial.support.TestDatabase;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository tests of CustomerRepository against the Test Resources PostgreSQL: save and find, id ordered pagination and the round trip of the nullable columns.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// transactional = false so each repository call commits; the test sweeps its QA_CUS_ rows itself.
@MicronautTest(transactional = false)
class CustomerRepositoryTest {

    @Inject
    CustomerRepository customers;

    @Inject
    ConnectionOperations<Connection> connections;

    @AfterEach
    void cleanUp() {
        new TestDatabase(connections).execute("DELETE FROM customers WHERE last_name LIKE 'QA\\_CUS\\_%'");
    }

    private static String uniqueLastName() {
        return "QA_CUS_" + UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    void saveAssignsAnIdAndFindByIdReadsAllFieldsBack() {
        String last = uniqueLastName();
        Customer saved = customers.save(new Customer("Ada", last, "123", "ada@example.com", "Street 1"));

        assertTrue(saved.getId() != null && saved.getId() > 0);
        Customer found = customers.findById(saved.getId()).orElseThrow();
        assertEquals("Ada", found.getFirstName());
        assertEquals(last, found.getLastName());
        assertEquals("123", found.getTelephone());
        assertEquals("ada@example.com", found.getEmail());
        assertEquals("Street 1", found.getAddress());
        assertTrue(customers.findById(999_999_999L).isEmpty());
    }

    @Test
    void nullableColumnsRoundTripAsNullAndCanBeCleared() {
        Customer saved = customers.save(new Customer("Ada", uniqueLastName(), null, null, null));
        Customer found = customers.findById(saved.getId()).orElseThrow();
        assertNull(found.getTelephone());
        assertNull(found.getEmail());
        assertNull(found.getAddress());

        found.setTelephone("999");
        customers.update(found);
        assertEquals("999", customers.findById(saved.getId()).orElseThrow().getTelephone());

        found.setTelephone(null);
        customers.update(found);
        assertNull(customers.findById(saved.getId()).orElseThrow().getTelephone());
    }

    @Test
    void findAllIsPaginatedAndSortedById() {
        customers.save(new Customer("A", uniqueLastName(), null, null, null));
        customers.save(new Customer("B", uniqueLastName(), null, null, null));
        Customer c = customers.save(new Customer("C", uniqueLastName(), null, null, null));

        Page<Customer> first = customers.findAll(Pageable.from(0, 2, Sort.of(Sort.Order.asc("id"))));
        assertEquals(2, first.getContent().size());
        assertTrue(first.getTotalSize() >= 3);
        assertTrue(first.getContent().get(0).getId() < first.getContent().get(1).getId());

        int lastPage = (int) ((first.getTotalSize() - 1) / 2);
        Page<Customer> last = customers.findAll(Pageable.from(lastPage, 2, Sort.of(Sort.Order.asc("id"))));
        assertEquals(c.getId(), last.getContent().get(last.getContent().size() - 1).getId());
    }
}
