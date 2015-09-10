package io.xude.loadbalancing;

import io.xude.Service;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class P2CEwma<T> implements Loadbalancer<T> {

    private Function<T, Double> loadEstimator;
    private List<T> entities;

    public P2CEwma(Function<T, Double> loadEstimator) {
        this.loadEstimator = loadEstimator;
        entities = new ArrayList<T>();
    }

    @Override
    public Publisher<Service> apply() {
        return null;
    }

    @Override
    public Publisher<Void> close() {
        return null;
    }

    @Override
    public T select() {
        return null;
    }

    @Override
    public void add(T t) {

    }

    @Override
    public void remove(T t) {

    }
}
