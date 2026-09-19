package com.edgareldy.micronauttutorial.repository;

import com.edgareldy.micronauttutorial.entity.Category;
import com.edgareldy.micronauttutorial.entity.Product;
import com.edgareldy.micronauttutorial.support.TestDatabase;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository tests of ProductRepository against the Test Resources PostgreSQL: save and find, the paginated
 * findByCategoryId, countByCategoryId and the two decimal unit price round trip.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// @MicronautTest boots the application context with the Test Resources PostgreSQL (no manual container);
// transactional = false so each repository call commits. Rows carry the QA_PRD_ prefix and are swept after each
// test (products before categories, because of the foreign key).
@MicronautTest(transactional = false)
class ProductRepositoryTest {

    @Inject
    ProductRepository products;

    @Inject
    CategoryRepository categories;

    @Inject
    ConnectionOperations<Connection> connections;

    @AfterEach
    void cleanUp() {
        TestDatabase db = new TestDatabase(connections);
        db.execute("DELETE FROM products WHERE category_id IN "
                + "(SELECT id FROM categories WHERE category_name LIKE 'QA\\_PRD\\_%')");
        db.execute("DELETE FROM categories WHERE category_name LIKE 'QA\\_PRD\\_%'");
    }

    private static String uniqueName() {
        return "QA_PRD_" + UUID.randomUUID().toString().replace("-", "");
    }

    private Category category() {
        return categories.save(new Category(uniqueName()));
    }

    @Test
    void saveAssignsAnIdAndFindByIdReadsItBack() {
        Category category = category();
        String name = uniqueName();
        Product saved = products.save(new Product(category.getId(), name, new BigDecimal("19.99")));

        assertTrue(saved.getId() != null && saved.getId() > 0);
        Product found = products.findById(saved.getId()).orElseThrow();
        assertEquals(name, found.getProductName());
        assertEquals(category.getId(), found.getCategoryId());
        assertTrue(products.findById(999_999_999L).isEmpty());
    }

    @Test
    void findByCategoryIdOnlyReturnsThatCategoryPaginatedAndCounted() {
        Category a = category();
        Category b = category();
        for (int i = 0; i < 3; i++) {
            products.save(new Product(a.getId(), uniqueName(), BigDecimal.TEN));
        }
        products.save(new Product(b.getId(), uniqueName(), BigDecimal.ONE));

        Sort byId = Sort.of(Sort.Order.asc("id"));
        Page<Product> first = products.findByCategoryId(a.getId(), Pageable.from(0, 2, byId));
        Page<Product> second = products.findByCategoryId(a.getId(), Pageable.from(1, 2, byId));

        assertEquals(2, first.getContent().size());
        assertEquals(1, second.getContent().size());
        assertEquals(3, first.getTotalSize());
        assertEquals(2, first.getTotalPages());
        assertTrue(first.getContent().get(0).getId() < first.getContent().get(1).getId());
        assertTrue(first.getContent().stream().allMatch(p -> p.getCategoryId().equals(a.getId())));
        assertEquals(0, products.findByCategoryId(999_999_999L, Pageable.from(0, 5)).getTotalSize());
    }

    @Test
    void countByCategoryIdFollowsTheProductsOfEachCategory() {
        Category a = category();
        Category b = category();
        assertEquals(0, products.countByCategoryId(a.getId()));

        products.save(new Product(a.getId(), uniqueName(), BigDecimal.TEN));
        products.save(new Product(a.getId(), uniqueName(), BigDecimal.TEN));

        assertEquals(2, products.countByCategoryId(a.getId()));
        assertEquals(0, products.countByCategoryId(b.getId()));
    }

    @Test
    void unitPriceRoundTripsWithTwoDecimals() {
        Category category = category();
        Product saved = products.save(new Product(category.getId(), uniqueName(), new BigDecimal("12.5")));

        BigDecimal read = products.findById(saved.getId()).orElseThrow().getUnitPrice();

        assertEquals(new BigDecimal("12.50"), read);
        assertEquals(2, read.scale());
    }
}
