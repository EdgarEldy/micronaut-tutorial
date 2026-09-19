package com.edgareldy.micronauttutorial.repository;

import com.edgareldy.micronauttutorial.entity.Category;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository tests of CategoryRepository against the Test Resources PostgreSQL: save and find, id ordered
 * pagination, and the product count of a category through ProductRepository.countByCategoryId.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// transactional = false so each repository call commits; the test sweeps its QA_CAT_ rows itself.
@MicronautTest(transactional = false)
class CategoryRepositoryTest {

    @Inject
    CategoryRepository categories;

    @Inject
    ProductRepository products;

    @Inject
    ConnectionOperations<Connection> connections;

    @AfterEach
    void cleanUp() {
        TestDatabase db = new TestDatabase(connections);
        db.execute("DELETE FROM products WHERE category_id IN "
                + "(SELECT id FROM categories WHERE category_name LIKE 'QA\\_CAT\\_%')");
        db.execute("DELETE FROM categories WHERE category_name LIKE 'QA\\_CAT\\_%'");
    }

    private static String uniqueName() {
        return "QA_CAT_" + UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    void saveAssignsAnIdAndFindByIdReadsItBack() {
        String name = uniqueName();
        Category saved = categories.save(new Category(name));

        assertTrue(saved.getId() != null && saved.getId() > 0);
        assertEquals(name, categories.findById(saved.getId()).orElseThrow().getCategoryName());
        assertTrue(categories.findById(999_999_999L).isEmpty());
    }

    @Test
    void findAllIsPaginatedAndSortedById() {
        Category a = categories.save(new Category(uniqueName()));
        Category b = categories.save(new Category(uniqueName()));
        Category c = categories.save(new Category(uniqueName()));

        Page<Category> first = categories.findAll(Pageable.from(0, 2, Sort.of(Sort.Order.asc("id"))));
        assertEquals(2, first.getContent().size());
        assertTrue(first.getTotalSize() >= 3);
        assertTrue(first.getContent().get(0).getId() < first.getContent().get(1).getId());

        // The last page in id order ends with the most recently inserted row.
        long total = first.getTotalSize();
        int lastPage = (int) ((total - 1) / 2);
        Page<Category> last = categories.findAll(Pageable.from(lastPage, 2, Sort.of(Sort.Order.asc("id"))));
        assertEquals(c.getId(), last.getContent().get(last.getContent().size() - 1).getId());
        assertTrue(a.getId() < b.getId() && b.getId() < c.getId());
    }

    @Test
    void countProductsByCategoryIdIsZeroThenFollowsInsertedProducts() {
        Category category = categories.save(new Category(uniqueName()));
        Category other = categories.save(new Category(uniqueName()));
        TestDatabase db = new TestDatabase(connections);

        assertEquals(0, products.countByCategoryId(category.getId()));

        for (int i = 0; i < 2; i++) {
            db.execute("INSERT INTO products (category_id, product_name, unit_price) VALUES (?, ?, 5.00)",
                    category.getId(), uniqueName());
        }
        assertEquals(2, products.countByCategoryId(category.getId()));
        assertEquals(0, products.countByCategoryId(other.getId()));
    }
}
