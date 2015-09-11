package io.xude.loadbalancing;

import io.xude.Service;
import io.xude.ServiceFactory;
import io.xude.ServiceProxy;
import io.xude.util.EmptySubscriber;
import io.xude.util.Publishers;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.*;

public class RoundRobinBalancer<Req, Resp> implements Loadbalancer<Req, Resp> {
    private final List<Service<Req, Resp>> services;
    private int i;

    public RoundRobinBalancer(Publisher<ServiceFactory<Req, Resp>> factories) {
        services = new ArrayList<>();
        i = 0;
        factories.subscribe(new Subscriber<ServiceFactory<Req, Resp>>() {
            int j = 0;

            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ServiceFactory<Req, Resp> factory) {
                factory.apply().subscribe(new EmptySubscriber<Service<Req, Resp>>() {
                    @Override
                    public void onNext(Service<Req, Resp> service) {
                        System.out.println("RoundRobinBalancer: Eager creation of service" + j);
                        final ServiceProxy<Req, Resp> proxy = new ServiceProxy<Req, Resp>(service) {
                            private int jj = j++;

                            @Override
                            public Publisher<Void> close() {
                                return s -> {
                                    System.out.println("Service" + jj + " load DOWN");
                                    s.onComplete();
                                };
                            }
                        };
                        synchronized (RoundRobinBalancer.this) {
                            services.add(proxy);
                        }
                    }
                });
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onComplete() {}
        });
    }

    @Override
    public Publisher<Service<Req, Resp>> apply() {
        return new Publisher<Service<Req, Resp>>() {
            @Override
            public void subscribe(Subscriber<? super Service<Req, Resp>> s) {
                synchronized (RoundRobinBalancer.this) {
                    if (services.isEmpty()) {
                        s.onError(new Exception("No Server available in the Loadbalancer!"));
                    } else {
                        i = (i + 1) % services.size();
                        Service<Req, Resp> service = services.get(i);
                        System.out.println("Service" + i + " load UP");;
                        s.onNext(service);
                        s.onComplete();
                    }
                }
            }
        };
    }

    @Override
    public Publisher<Void> close() {
        return s -> {
            synchronized (RoundRobinBalancer.this) {
                 services.forEach(Service::close);
            }
        };
    }
}
