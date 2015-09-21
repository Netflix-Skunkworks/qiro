package io.xude.util;

import io.xude.Service;
import io.xude.ServiceFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;

public class Availabilities {
    public static Publisher<Double> UNAVAILABLE = s -> {
        s.onNext(0.0);
        s.onComplete();
    };

    public static <Req, Resp>
    Publisher<Double> avgOf(List<Publisher<Double>> availabilities, Object monitor) {
        return new Publisher<Double>() {
            @Override
            public void subscribe(Subscriber<? super Double> subscriber) {
                synchronized (monitor) {
                    if (availabilities.isEmpty()) {
                        UNAVAILABLE.subscribe(subscriber);
                    } else {
                        Publishers.concat(availabilities).subscribe(
                            new Subscriber<List<Double>>() {
                                private double sum = 0.0;
                                private int count = 0;

                                @Override
                                public void onSubscribe(Subscription s) {
                                    subscriber.onSubscribe(s);
                                }

                                @Override
                                public void onNext(List<Double> doubles) {
                                    for (Double a : doubles) {
                                        sum += a;
                                        count += 1;
                                    }
                                    if (count != 0) {
                                        subscriber.onNext(sum / count);
                                    } else {
                                        subscriber.onNext(0.0);
                                    }
                                }

                                @Override
                                public void onError(Throwable t) {
                                    subscriber.onError(t);
                                }

                                @Override
                                public void onComplete() {
                                    subscriber.onComplete();
                                }
                            }
                        );
                    }
                }
            }
        };
    }

    public static <Req, Resp>
    Publisher<Double> avgOfServices(
        List<? extends Service<Req, Resp>> services,
        Object monitor
    ) {
        return new Publisher<Double>() {
            @Override
            public void subscribe(Subscriber<? super Double> subscriber) {
                synchronized (monitor) {
                    if (services.isEmpty()) {
                        UNAVAILABLE.subscribe(subscriber);
                    } else {
                        List<Publisher<Double>> availabilities = new ArrayList<>();
                        for (Service<Req, Resp> service : services) {
                            availabilities.add(service.availability());
                        }
                        avgOf(availabilities, availabilities).subscribe(subscriber);
                    }
                }
            }
        };
    }

    public static <Req, Resp>
    Publisher<Double> avgOfServiceFactories(
        List<? extends ServiceFactory<Req, Resp>> factories,
        Object monitor
    ) {
        return new Publisher<Double>() {
            @Override
            public void subscribe(Subscriber<? super Double> subscriber) {
                synchronized (monitor) {
                    if (factories.isEmpty()) {
                        UNAVAILABLE.subscribe(subscriber);
                    } else {
                        List<Publisher<Double>> availabilities = new ArrayList<>();
                        for (ServiceFactory<Req, Resp> service : factories) {
                            availabilities.add(service.availability());
                        }
                        avgOf(availabilities, availabilities).subscribe(subscriber);
                    }
                }
            }
        };
    }
}
