package io.xude.testing;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class LoggerSubscriber<T> implements Subscriber<T> {
    private String name;

    public LoggerSubscriber(String name) {
        this.name = name;
    }

    @Override
    public void onSubscribe(Subscription s) {
        s.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(T s) {
        System.out.println(name + " received " + s);
    }

    @Override
    public void onError(Throwable t) {
        System.out.println(name + " received exception " + t);
//        t.printStackTrace();
    }

    @Override
    public void onComplete() {
        System.out.println(name + " is complete!");
    }
}
