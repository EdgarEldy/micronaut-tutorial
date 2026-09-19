package com.edgareldy.micronauttutorial.event;

import com.edgareldy.micronauttutorial.dto.ecommerce.OrderResponse;
import io.micronaut.transaction.annotation.TransactionalEventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Bridge between the transactional world and the reactive one: receives {@link OrderCreatedEvent} once the order
 * transaction has committed and fans it out to every connected Server-Sent Events subscriber.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : micronaut-tutorial
 */
// @TransactionalEventListener (micronaut-data-tx) is an event listener whose invocation is deferred to a phase of
// the transaction that was active when the event was published. Its default phase is AFTER_COMMIT: if the
// transaction rolls back the listener is never called, so a rolled-back order can never be broadcast. Outside any
// transaction it is not called at all.
//
// A Reactor Sinks.Many is a hot, programmatic source: the writer pushes with tryEmitNext, subscribers consume the
// Flux. multicast().directBestEffort() delivers each element to every subscriber that is currently ready and simply
// drops it for a subscriber that is too slow (no buffer at all), so a slow SSE client can neither block nor grow
// the memory of the thread that committed the order.
@Singleton
public class OrderEventBroadcaster {

    private static final Logger LOG = LoggerFactory.getLogger(OrderEventBroadcaster.class);

    private final Sinks.Many<OrderResponse> sink = Sinks.many().multicast().directBestEffort();

    /**
     * Called after the order transaction commits.
     *
     * @param event the created order event
     */
    @TransactionalEventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        // A sink accepts one emitting thread at a time: commits can complete on several threads, so serialize.
        Sinks.EmitResult result;
        synchronized (sink) {
            result = sink.tryEmitNext(event.order());
        }
        // FAIL_ZERO_SUBSCRIBER just means nobody is connected. Anything else means a live subscriber may have
        // missed the event, which must not go unnoticed.
        if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            LOG.warn("Order event {} was not delivered to every subscriber: {}", event.order().id(), result);
        }
    }

    /**
     * Hot stream of the orders committed after the subscription; each subscriber has its own view of it.
     *
     * @return the flux of created orders
     */
    public Flux<OrderResponse> stream() {
        return sink.asFlux();
    }
}
