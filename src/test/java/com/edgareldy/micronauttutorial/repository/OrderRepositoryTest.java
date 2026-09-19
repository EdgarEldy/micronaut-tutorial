package com.edgareldy.micronauttutorial.repository;

import com.edgareldy.micronauttutorial.entity.Category;
import com.edgareldy.micronauttutorial.entity.Customer;
import com.edgareldy.micronauttutorial.entity.Order;
import com.edgareldy.micronauttutorial.entity.Product;
import com.edgareldy.micronauttutorial.support.TestDatabase;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository tests of OrderRepository against the Test Resources PostgreSQL: save and find, the three derived
 * paginated filters, the two counts and the two decimal total round trip.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Same mechanism as ProductRepositoryTest. Rows carry the QA_ORD_ prefix and are swept after each test, orders
// first (foreign keys to products and customers), then products, categories and customers.
@MicronautTest(transactional = false)
class OrderRepositoryTest {

    private static final Pageable FIRST = Pageable.from(0, 50, Sort.of(Sort.Order.asc("id")));

    @Inject
    OrderRepository orders;

    @Inject
    ProductRepository products;

    @Inject
    CategoryRepository categories;

    @Inject
    CustomerRepository customers;

    @Inject
    ConnectionOperations<Connection> connections;

    Product p1;
    Product p2;
    Customer c1;
    Customer c2;

    @BeforeEach
    void setUp() {
        Category category = categories.save(new Category(uniqueName()));
        p1 = products.save(new Product(category.getId(), uniqueName(), new BigDecimal("2.50")));
        p2 = products.save(new Product(category.getId(), uniqueName(), new BigDecimal("3.00")));
        c1 = customers.save(customer());
        c2 = customers.save(customer());
    }

    @AfterEach
    void cleanUp() {
        TestDatabase db = new TestDatabase(connections);
        db.execute("DELETE FROM orders WHERE customer_id IN (SELECT id FROM customers WHERE last_name LIKE 'QA\\_ORD\\_%')");
        db.execute("DELETE FROM products WHERE product_name LIKE 'QA\\_ORD\\_%'");
        db.execute("DELETE FROM categories WHERE category_name LIKE 'QA\\_ORD\\_%'");
        db.execute("DELETE FROM customers WHERE last_name LIKE 'QA\\_ORD\\_%'");
    }

    private static String uniqueName() {
        return "QA_ORD_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static Customer customer() {
        Customer customer = new Customer();
        customer.setFirstName("Ada");
        customer.setLastName(uniqueName());
        return customer;
    }

    private Order order(Customer customer, Product product, int quantity) {
        return orders.save(new Order(customer.getId(), product.getId(), quantity, new BigDecimal("1.00")));
    }

    /** c1/p1 twice, c1/p2 once, c2/p1 once. */
    private void fourOrders() {
        order(c1, p1, 1);
        order(c1, p1, 2);
        order(c1, p2, 3);
        order(c2, p1, 4);
    }

    @Test
    void saveAssignsAnIdAndFindByIdReadsItBack() {
        Order saved = orders.save(new Order(c1.getId(), p1.getId(), 3, new BigDecimal("7.50")));

        assertTrue(saved.getId() != null && saved.getId() > 0);
        Order found = orders.findById(saved.getId()).orElseThrow();
        assertEquals(c1.getId(), found.getCustomerId());
        assertEquals(p1.getId(), found.getProductId());
        assertEquals(3, found.getQuantity());
        assertEquals(new BigDecimal("7.50"), found.getTotal());
        assertTrue(orders.findById(999_999_999L).isEmpty());
    }

    @Test
    void findByCustomerIdOnlyReturnsThatCustomerPaginated() {
        fourOrders();

        Page<Order> all = orders.findByCustomerId(c1.getId(), FIRST);
        Page<Order> firstPage = orders.findByCustomerId(c1.getId(), Pageable.from(0, 2, Sort.of(Sort.Order.asc("id"))));

        assertEquals(3, all.getTotalSize());
        assertTrue(all.getContent().stream().allMatch(o -> o.getCustomerId().equals(c1.getId())));
        assertEquals(2, firstPage.getContent().size());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(0, orders.findByCustomerId(999_999_999L, FIRST).getTotalSize());
    }

    @Test
    void findByProductIdOnlyReturnsThatProduct() {
        fourOrders();

        Page<Order> page = orders.findByProductId(p1.getId(), FIRST);

        assertEquals(3, page.getTotalSize());
        assertTrue(page.getContent().stream().allMatch(o -> o.getProductId().equals(p1.getId())));
        assertEquals(0, orders.findByProductId(999_999_999L, FIRST).getTotalSize());
    }

    @Test
    void findByCustomerIdAndProductIdCombinesBothFilters() {
        fourOrders();

        Page<Order> page = orders.findByCustomerIdAndProductId(c1.getId(), p1.getId(), FIRST);

        assertEquals(2, page.getTotalSize());
        assertTrue(page.getContent().stream()
                .allMatch(o -> o.getCustomerId().equals(c1.getId()) && o.getProductId().equals(p1.getId())));
        assertEquals(0, orders.findByCustomerIdAndProductId(c2.getId(), p2.getId(), FIRST).getTotalSize());
    }

    @Test
    void countsFollowTheOrdersOfEachProductAndCustomer() {
        assertEquals(0, orders.countByProductId(p1.getId()));
        assertEquals(0, orders.countByCustomerId(c1.getId()));

        fourOrders();

        assertEquals(3, orders.countByProductId(p1.getId()));
        assertEquals(1, orders.countByProductId(p2.getId()));
        assertEquals(3, orders.countByCustomerId(c1.getId()));
        assertEquals(1, orders.countByCustomerId(c2.getId()));
    }

    @Test
    void totalRoundTripsWithTwoDecimals() {
        Order saved = orders.save(new Order(c1.getId(), p1.getId(), 1, new BigDecimal("12.5")));

        BigDecimal read = orders.findById(saved.getId()).orElseThrow().getTotal();

        assertEquals(new BigDecimal("12.50"), read);
        assertEquals(2, read.scale());
    }
}
