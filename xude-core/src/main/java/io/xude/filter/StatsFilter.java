package io.xude.filter;

import io.xude.Filter;
import io.xude.Service;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class StatsFilter<Request, Response>
    implements Filter<Request, Request, Response, Response> {
    // TODO: add logger, trace recorder, or equivalent
    public StatsFilter() {

    }

    @Override
    public Publisher<Response> apply(Publisher<Request> inputs, Service<Request, Response> service) {
        return new Publisher<Response>() {
            private Long epoch = -1L;

            @Override
            public void subscribe(Subscriber<? super Response> responseSubscriber) {
                epoch = System.currentTimeMillis();
                service.apply(inputs).subscribe(new Subscriber<Response>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        responseSubscriber.onSubscribe(s);
                    }

                    @Override
                    public void onNext(Response response) {
                        System.out.println("Response: +1");
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
