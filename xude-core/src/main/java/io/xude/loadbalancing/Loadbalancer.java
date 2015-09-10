package io.xude.loadbalancing;

import io.xude.ServiceFactory;

public interface Loadbalancer<T> extends ServiceFactory {
    public T select();
    public void add(T t);
    public void remove(T t);
}
