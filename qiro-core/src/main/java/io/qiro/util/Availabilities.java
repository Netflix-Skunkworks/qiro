package io.qiro.util;

import io.qiro.Service;
import io.qiro.ServiceFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class Availabilities {

    public static <Req, Resp>
    double avgOfServices(Collection<? extends Service<Req, Resp>> services) {
        if (services.isEmpty()) {
            return 0.0;
        } else {
            double sum = 0.0;
            int count = 0;
            for (Service<Req, Resp> svc : services) {
                sum += svc.availability();
                count += 1;
            }
            if (count != 0) {
                return sum / count;
            } else {
                return 0.0;
            }
        }
    }

    public static <Req, Resp>
    double avgOfServiceFactories(
        Collection<? extends ServiceFactory<Req, Resp>> factories
    ) {
        if (factories.isEmpty()) {
            return 0.0;
        } else {
            double sum = 0.0;
            int count = 0;
            for (ServiceFactory<Req, Resp> svc : factories) {
                sum += svc.availability();
                count += 1;
            }
            if (count != 0) {
                return sum / count;
            } else {
                return 0.0;
            }
        }
    }
}
