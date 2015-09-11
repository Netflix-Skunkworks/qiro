package io.xude.loadbalancing;

import io.xude.ServiceFactory;

public interface Loadbalancer<Req, Resp> extends ServiceFactory<Req, Resp> {

}
