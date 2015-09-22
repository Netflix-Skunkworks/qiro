package io.qiro.util;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class EmptySubscriber<T> implements Subscriber<T> {
    @Override
    public void onSubscribe(Subscription s) {
        s.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(T t) {}

    @Override
    public void onError(Throwable t) {}

    @Override
    public void onComplete() {}
}
