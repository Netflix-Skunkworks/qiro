package io.qiro.loadbalancing.selector;

import io.qiro.ServiceFactory;
import io.qiro.loadbalancing.WeightedServiceFactory;

import java.util.List;
import java.util.function.Function;

public interface Selector<Req, Resp> extends
    Function<List<WeightedServiceFactory<Req, Resp>>, ServiceFactory<Req, Resp>> {
}
