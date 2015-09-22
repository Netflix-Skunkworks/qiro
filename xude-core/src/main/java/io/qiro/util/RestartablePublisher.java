package io.qiro.util;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;

public class RestartablePublisher<T> implements Publisher<T> {
    private final List<T> cache;
    private final Publisher<T> inputs;
    private boolean isCompleted;
    private final List<Subscriber<? super T>> subscribeds;

    public RestartablePublisher(Publisher<T> inputs) {
        this.cache = new ArrayList<>();
        this.inputs = inputs;
        this.isCompleted = false;
        this.subscribeds = new ArrayList<>();
    }

    public Publisher<T> restart() {
        return new Publisher<T>() {
            @Override
            public void subscribe(Subscriber<? super T> s) {
                synchronized (RestartablePublisher.this) {
                    cache.forEach(s::onNext);
                    if (isCompleted) {
                        s.onComplete();
                    } else {
                        subscribeds.add(s);
                    }
                }
            }
        };
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        inputs.subscribe(new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription s) {
                subscriber.onSubscribe(s);
            }

            @Override
            public void onNext(T t) {
                synchronized (RestartablePublisher.this) {
                    cache.add(t);
                }
                synchronized (RestartablePublisher.this) {
                    subscribeds.forEach(s -> s.onNext(t));
                }
                subscriber.onNext(t);
            }

            @Override
            public void onError(Throwable t) {
                subscriber.onError(t);
                synchronized (RestartablePublisher.this) {
                    subscribeds.forEach(s -> s.onError(t));
                }
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
                synchronized (RestartablePublisher.this) {
                    isCompleted = true;
                    subscribeds.forEach(s -> s.onComplete());
                }
            }
        });
    }
}
