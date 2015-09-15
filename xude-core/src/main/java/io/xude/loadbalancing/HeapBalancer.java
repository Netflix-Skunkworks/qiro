package io.xude.loadbalancing;

import io.xude.Service;
import io.xude.ServiceFactory;
import io.xude.ServiceProxy;
import io.xude.util.EmptySubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import java.util.PriorityQueue;

public class HeapBalancer<Req, Resp> implements Loadbalancer<Req, Resp> {
    final private PriorityQueue<WeightedService<Req, Resp>> queue;

    private class WeightedService<Req, Resp>
        extends ServiceProxy<Req, Resp>
        implements Comparable<WeightedService<Req, Resp>> {

        private double load = 0.0;
        private double availabilityValue = 0.0;

        WeightedService(Service<Req, Resp> underlying) {
            super(underlying);
        }

        @Override
        public synchronized Publisher<Void> close() {
            load -= 1;
            return super.close();
        }

        synchronized void increment() { load += 1; }

        synchronized double getLoad() {
            // TODO: This must be synchronous, verify the hypothesis
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
        public int compareTo(WeightedService<Req, Resp> other) {
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
                        System.out.println("HeapBalancer: Creating service");
                        WeightedService<Req, Resp> weightedService = new WeightedService<>(service);
                        synchronized (HeapBalancer.this) {
                            queue.add(weightedService);
                        }
                    }
                });
            }
        });
    }

    @Override
    public Publisher<Service<Req, Resp>> apply() {
        return s -> {
            System.out.println("HeapBalancer.apply");
            synchronized (HeapBalancer.this) {
                if (queue.isEmpty()) {
                    s.onError(new Exception("No Server available in the Loadbalancer!"));
                } else {
                    WeightedService<Req, Resp> service = queue.poll();
                    service.increment();
                    queue.offer(service);
                    s.onNext(service);
                    s.onComplete();
                }
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
