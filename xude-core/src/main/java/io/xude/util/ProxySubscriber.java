package io.xude.util;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class ProxySubscriber<T> implements Subscriber<T> {
    private Subscriber<T> underlying;

    public ProxySubscriber(Subscriber<T> underlying) {
        this.underlying = underlying;
    }

    @Override
    public void onSubscribe(Subscription s) {
        underlying.onSubscribe(s);
    }

    @Override
    public void onNext(T t) {
        underlying.onNext(t);
    }

    @Override
    public void onError(Throwable t) {
        underlying.onError(t);
    }

    @Override
    public void onComplete() {
        underlying.onComplete();
    }
}
