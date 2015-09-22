package io.qiro.resolver;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.net.*;
import java.util.*;

public class DnsResolver implements Resolver {
    private static String PROTOCOL_NAME = "dns";

    @FunctionalInterface
    public interface LookupFunc {
        InetAddress[] apply(String host) throws UnknownHostException;
    }

    private LookupFunc lookupFunc;
    private long refreshPeriodMs;

    /*VisibleForTesting*/
    public DnsResolver(long refreshPeriodMs, LookupFunc lookupFunc) {
        this.lookupFunc = lookupFunc;
        this.refreshPeriodMs = refreshPeriodMs;
    }

    public DnsResolver() {
        this(-1L, DnsResolver::nativeLookup);
    }

    @Override
    public Publisher<Set<SocketAddress>> resolve(URL url) {
        return new Publisher<Set<SocketAddress>>() {
            @Override
            public void subscribe(Subscriber<? super Set<SocketAddress>> subscriber) {
                String protocol = url.getProtocol();
                if (!protocol.equals(PROTOCOL_NAME)) {
                    String message = "'" + protocol + "' isn't supported by the DnsResolver\n";
                    message += "URL should be in the form 'dns://hostname:port,hostname2:port2'";
                    subscriber.onError(new MalformedURLException(message));
                } else {
                    // TODO: fix that, make it asynchronous and check periodically
                    Set<SocketAddress> addresses = new HashSet<>(resolveDns(url.getHost()));
                    subscriber.onNext(addresses);
                }
            }
        };
    }

    private List<SocketAddress> resolveDns(String hostPort) {
        String[] split = hostPort.split(":");
        String host = split[0];
        int port = Integer.parseInt(split[1]);

        List<SocketAddress> addresses = new ArrayList<>();
        try {
            for (InetAddress addr: lookupFunc.apply(host)) {
                addresses.add(new InetSocketAddress(addr, port));
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return addresses;
    }

    private static InetAddress[] nativeLookup(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}
