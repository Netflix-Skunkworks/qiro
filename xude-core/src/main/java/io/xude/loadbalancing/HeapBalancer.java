package io.xude.loadbalancing;

import io.xude.Service;
import io.xude.ServiceFactory;
import io.xude.ServiceFactoryProxy;
import io.xude.util.EmptySubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.PriorityQueue;

public class HeapBalancer<Req, Resp> implements Loadbalancer<Req, Resp> {
    final private PriorityQueue<WeightedServiceFactory<Req, Resp>> queue;

    private class WeightedServiceFactory<Req, Resp>
        extends ServiceFactoryProxy<Req, Resp>
        implements Comparable<WeightedServiceFactory<Req, Resp>> {

        private double load = 0.0;
        private double availabilityValue = 0.0;

        WeightedServiceFactory(ServiceFactory<Req, Resp> underlying) {
            super(underlying);
        }

        @Override
        public synchronized Publisher<Void> close() {
            load -= 1;
            return super.close();
        }

        synchronized void increment() { load += 1; }

        synchronized double getLoad() {
            availability().subscribe(new EmptySubscriber<Double>() {
                @Override
                public void onNext(Double x) {
                    availabilityValue = x;
                }
            });

            // in case all availabilities are zeros, it nicely degrades to a normal
            // least loaded loadbalancer.
            double penaltyFactor = (double) Integer.MAX_VALUE;
            if (availabilityValue != 0.0) {
                penaltyFactor = (1.0 / availabilityValue) * load;
            }

            System.out.println(this.hashCode() + " Service load =" + penaltyFactor);
            return penaltyFactor * load;
        }

        @Override
        public int compareTo(WeightedServiceFactory<Req, Resp> other) {
            return Double.compare(this.getLoad(), other.getLoad());
        }
    }

    public HeapBalancer(Publisher<ServiceFactory<Req, Resp>> factories) {
        this.queue = new PriorityQueue<>();
        factories.subscribe(new EmptySubscriber<ServiceFactory<Req, Resp>>() {
            @Override
            public void onNext(ServiceFactory<Req, Resp> factory) {
                factory.apply().subscribe(new EmptySubscriber<Service<Req, Resp>>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(1L);
                    }

                    @Override
                    public void onNext(Service<Req, Resp> service) {
                        System.out.println("HeapBalancer: Storing ServiceFactory");
                        synchronized (HeapBalancer.this) {
                            queue.add(new WeightedServiceFactory<>(factory));
                        }
                    }
                });
            }
        });
    }

    @Override
    public Publisher<Service<Req, Resp>> apply() {
        return subscriber -> {
            System.out.println("HeapBalancer.apply");
            synchronized (HeapBalancer.this) {
                if (queue.isEmpty()) {
                    subscriber.onError(new Exception("No Server available in the Loadbalancer!"));
                } else {
                    WeightedServiceFactory<Req, Resp> factory = queue.poll();
                    factory.increment();
                    queue.offer(factory);
                    factory.apply().subscribe(subscriber);
                }
            }
        };
    }

    @Override
    public Publisher<Double> availability() {
        return new Publisher<Double>() {
            private double sum = 0.0;
            private int count = 0;

            @Override
            public void subscribe(Subscriber<? super Double> subscriber) {
                for (ServiceFactory<Req, Resp> factory: queue) {
                    factory.availability().subscribe(new EmptySubscriber<Double>() {
                        @Override
                        public void onSubscribe(Subscription s) {
                            s.request(1L);
                        }

                        @Override
                        public void onNext(Double aDouble) {
                            sum += aDouble;
                            count += 1;
                        }
                    });
                }
                if (count != 0) {
                    subscriber.onNext(sum / count);
                } else {
                    subscriber.onNext(0.0);
                }
                subscriber.onComplete();
            }
        };
    }

    @Override
    public Publisher<Void> close() {
        return s -> {
            synchronized (HeapBalancer.this) {
                // TODO: improve
                queue.forEach(svc ->
                        svc.close().subscribe(new EmptySubscriber<>())
                );
            }
        };
    }
}
