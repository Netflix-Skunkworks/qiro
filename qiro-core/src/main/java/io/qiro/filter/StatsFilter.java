package io.qiro.filter;

import io.qiro.Filter;
import io.qiro.Service;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class StatsFilter<Req, Resp> implements Filter<Req, Req, Resp, Resp> {
    // TODO: add logger, trace recorder, or equivalent
    public StatsFilter() {

    }

    @Override
    public Publisher<Resp> requestChannel(Publisher<Req> inputs, Service<Req, Resp> service) {
        return new Publisher<Resp>() {
            private Long epoch = -1L;

            @Override
            public void subscribe(Subscriber<? super Resp> responseSubscriber) {
                epoch = System.currentTimeMillis();
                service.requestChannel(inputs).subscribe(new Subscriber<Resp>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        responseSubscriber.onSubscribe(s);
                    }

                    @Override
                    public void onNext(Resp response) {
                        System.out.println("Resp: +1");
                        responseSubscriber.onNext(response);
                    }

                    @Override
                    public void onError(Throwable t) {
                        System.out.println("Error: +1");
                        responseSubscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        long latency = System.currentTimeMillis() - epoch;
                        System.out.println("Success: +1");
                        System.out.println("Latency: " + latency);
                        responseSubscriber.onComplete();
                    }
                });
            }
        };
    }
}
