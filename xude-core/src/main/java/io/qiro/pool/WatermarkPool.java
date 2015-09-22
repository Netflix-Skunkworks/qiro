package io.qiro.pool;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import io.qiro.ServiceProxy;
import io.qiro.util.Availabilities;
import io.qiro.util.EmptySubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class WatermarkPool<Req, Resp> implements ServiceFactory<Req,Resp> {
    private final int low;
    private final int high;
    private final ServiceFactory<Req, Resp> underlying;
    private final Deque<Service<Req, Resp>> queue;
    private AtomicInteger createdServices;

    public WatermarkPool(int low, int high, ServiceFactory<Req, Resp> underlying) {
        this.low = low;
        this.high = high;
        this.underlying = underlying;
        queue = new ConcurrentLinkedDeque<>();
        createdServices = new AtomicInteger(0);
    }

    @Override
    public Publisher<Service<Req, Resp>> apply() {
        return new Publisher<Service<Req, Resp>>() {
            @Override
            public void subscribe(Subscriber<? super Service<Req, Resp>> subscriber) {
                // try to grab the `right` to create a Service
                System.out.println("WatermarkPool: subscribing createdServices:" +
                    createdServices.get() + ", queue:" + queue);
                final boolean creationGranted = createdServices.incrementAndGet() <= high;
                Service<Req, Resp> service = queue.pollFirst();
                if (service != null) {
                    subscriber.onNext(service);
                    // if a service was available, just return the `right`
                    createdServices.decrementAndGet();
                } else if (creationGranted) {
                    createAndPublishService(subscriber);
                } else {
                    subscriber.onError(new java.lang.Exception(
                        "WatermarkPool: Max Capacity (" + high + ")"));
                    createdServices.decrementAndGet();
                }
            }
        };
    }

    private void createAndPublishService(final Subscriber<? super Service<Req, Resp>> subscriber) {
        underlying.apply().subscribe(new Subscriber<Service<Req, Resp>>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(1L);
            }

            @Override
            public void onNext(Service<Req, Resp> service) {
                Service<Req, Resp> proxy = new ServiceProxy<Req, Resp>(service) {
                    @Override
                    public Publisher<Void> close() {
                        return subscriber -> {
                            int count = createdServices.decrementAndGet();
                            if (count > low) {
                                underlying.close().subscribe(subscriber);
                            } else {
                                System.out.println("WatermarkPool: moving svc " +
                                    this + " to the queue");
                                queue.addLast(this);
                            }
                        };
                    }
                };
                System.out.println("WatermarkPool: Creating ServiceProxy " + proxy);
                subscriber.onNext(proxy);
            }

            @Override
            public void onError(Throwable t) {
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
    }

    @Override
    public Publisher<Double> availability() {
        return Availabilities.avgOfServices(queue);
    }

    @Override
    public Publisher<Void> close() {
        return subscriber -> {
            createdServices.addAndGet(high);
            queue.forEach(svc -> svc.close().subscribe(new EmptySubscriber<>()));
            subscriber.onNext(null);
            subscriber.onComplete();
        };
    }
}
