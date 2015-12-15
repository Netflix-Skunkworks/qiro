package io.qiro.testing;

import io.qiro.Service;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class ThreadedService<Req, Resp> implements Service<Req, Resp> {
    private static ExecutorService EXECUTOR = Executors.newFixedThreadPool(8, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    private Function<Req, Resp> function;

    public ThreadedService(Function<Req, Resp> function) {
        this.function = function;
    }

    @Override
    public Publisher<Resp> requestChannel(Publisher<Req> inputs) {
        return subscriber ->
            EXECUTOR.submit(() -> {
                inputs.subscribe(new Subscriber<Req>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        subscriber.onSubscribe(s);
                    }

                    @Override
                    public void onNext(Req req) {
                        Resp resp = function.apply(req);
                        subscriber.onNext(resp);
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
            });
    }

    @Override
    public double availability() {
        return 1.0;
    }

    @Override
    public Publisher<Void> close() {
        return s -> {
            s.onNext(null);
            s.onComplete();
        };
    }
}
