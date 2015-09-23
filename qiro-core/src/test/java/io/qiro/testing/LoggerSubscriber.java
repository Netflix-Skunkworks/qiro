package io.qiro.testing;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class LoggerSubscriber<T> implements Subscriber<T> {
    private String name;
    private boolean completed;
    private boolean error;

    public LoggerSubscriber(String name) {
        this.name = name;
        this.completed = false;
        this.error = false;
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
        error = true;
    }

    @Override
    public void onComplete() {
        System.out.println(name + " is complete!");
        completed = true;
    }

    public boolean isComplete() {
        return completed;
    }

    public boolean isError() {
        return error;
    }
}
