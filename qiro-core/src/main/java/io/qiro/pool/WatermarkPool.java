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

public class WatermarkPool<Req, Resp> implements ServiceFactory<Req,Resp> {
    private final int low;
    private final int high;
    private final int maxBuffer;
    private final ServiceFactory<Req, Resp> underlying;
    private final Deque<Service<Req, Resp>> queue;
    private final Deque<Subscriber<? super Service<Req, Resp>>> waiters;
    private int createdServices;

    public WatermarkPool(int low, int high, int maxBuffer, ServiceFactory<Req, Resp> underlying) {
        this.low = low;
        this.high = high;
        this.maxBuffer = maxBuffer;
        this.underlying = underlying;
        queue = new ConcurrentLinkedDeque<>();
        waiters = new ConcurrentLinkedDeque<>();
        createdServices = 0;
    }

    @Override
    public Publisher<Service<Req, Resp>> apply() {
        return new Publisher<Service<Req, Resp>>() {
            @Override
            public void subscribe(Subscriber<? super Service<Req, Resp>> subscriber) {
                System.out.println("WatermarkPool: subscribing createdServices:" +
                    createdServices + ", queue:" + queue + ", waiters:" + waiters);
                synchronized (WatermarkPool.this) {
                    Service<Req, Resp> service = queue.pollFirst();
                    if (service != null) {
                        subscriber.onNext(service);
                    } else if (createdServices < high) {
                        createdServices += 1;
                        createAndPublishService(subscriber);
                    } else if (waiters.size() >= maxBuffer) {
                        subscriber.onError(new java.lang.Exception(
                            "WatermarkPool: Max Capacity (" + high + ")"));
                    } else {
                        waiters.add(subscriber);
                    }
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
                            synchronized (WatermarkPool.this) {
                                if (!waiters.isEmpty()) {
                                    Subscriber<? super Service<Req, Resp>> waitingSubscriber =
                                        waiters.pollFirst();
                                    waitingSubscriber.onNext(this);
                                } else if (createdServices > low) {
                                    createdServices -= 1;
                                    underlying.close().subscribe(subscriber);
                                } else {
                                    System.out.println("WatermarkPool: moving svc " +
                                        this + " to the queue");
                                    queue.addLast(this);
                                }
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
    public double availability() {
        return Availabilities.avgOfServices(queue);
    }

    @Override
    public Publisher<Void> close() {
        return subscriber -> {
            createdServices = 2 * high;
            queue.forEach(svc -> svc.close().subscribe(new EmptySubscriber<>()));
            subscriber.onNext(null);
            subscriber.onComplete();
        };
    }
}
