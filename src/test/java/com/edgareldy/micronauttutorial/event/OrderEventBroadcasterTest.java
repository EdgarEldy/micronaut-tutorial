package com.edgareldy.micronauttutorial.event;

import com.edgareldy.micronauttutorial.dto.ecommerce.OrderResponse;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests (no Micronaut context) of OrderEventBroadcaster: fan-out to several subscribers, and a slow
 * subscriber that requests nothing neither blocks nor breaks the others.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// Reactor testing technique: a Flux is tested by subscribing to it. Collecting into a list is enough for a hot
// multicast source; a BaseSubscriber whose hookOnSubscribe does not call request() models a slow consumer, because
// Reactive Streams delivers nothing to a subscriber that has requested nothing (no demand, no signal).
class OrderEventBroadcasterTest {

    private static OrderCreatedEvent event(long id) {
        return new OrderCreatedEvent(new OrderResponse(id, 1L, 2L, 1, new BigDecimal("1.00")));
    }

    private static List<Long> collect(OrderEventBroadcaster broadcaster) {
        List<Long> ids = new CopyOnWriteArrayList<>();
        broadcaster.stream().subscribe(order -> ids.add(order.id()));
        return ids;
    }

    @Test
    void everySubscriberReceivesEveryEventInOrder() {
        OrderEventBroadcaster broadcaster = new OrderEventBroadcaster();
        List<Long> first = collect(broadcaster);
        List<Long> second = collect(broadcaster);

        broadcaster.onOrderCreated(event(1));
        broadcaster.onOrderCreated(event(2));

        assertEquals(List.of(1L, 2L), first);
        assertEquals(List.of(1L, 2L), second);
    }

    @Test
    void anEventWithoutSubscribersIsDroppedAndALateSubscriberOnlySeesNewOnes() {
        OrderEventBroadcaster broadcaster = new OrderEventBroadcaster();

        assertDoesNotThrow(() -> broadcaster.onOrderCreated(event(1)));
        List<Long> late = collect(broadcaster);
        broadcaster.onOrderCreated(event(2));

        assertEquals(List.of(2L), late);
    }

    @Test
    void aSlowSubscriberNeitherBlocksNorBreaksTheOthers() {
        OrderEventBroadcaster broadcaster = new OrderEventBroadcaster();
        List<Long> fast = collect(broadcaster);
        List<Long> slowSeen = new CopyOnWriteArrayList<>();
        boolean[] slowFailed = {false};
        Disposable slow = broadcaster.stream().subscribeWith(new BaseSubscriber<OrderResponse>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                // Deliberately no request(): this consumer never asks for anything.
            }

            @Override
            protected void hookOnNext(OrderResponse value) {
                slowSeen.add(value.id());
            }

            @Override
            protected void hookOnError(Throwable throwable) {
                slowFailed[0] = true;
            }
        });
        List<Long> fastAfter = collect(broadcaster);

        // Returns immediately (no blocking on the slow subscriber) for every emission.
        assertDoesNotThrow(() -> {
            broadcaster.onOrderCreated(event(1));
            broadcaster.onOrderCreated(event(2));
            broadcaster.onOrderCreated(event(3));
        });

        assertEquals(List.of(1L, 2L, 3L), fast);
        assertEquals(List.of(1L, 2L, 3L), fastAfter);
        assertTrue(slowSeen.isEmpty());
        assertTrue(!slowFailed[0] || slow.isDisposed());
    }
}
