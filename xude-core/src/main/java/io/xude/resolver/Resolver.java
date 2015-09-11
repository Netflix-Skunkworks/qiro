package io.xude.resolver;

import org.reactivestreams.Publisher;

import java.net.SocketAddress;
import java.net.URL;

/**
 * A Resolver is responsible for converting an abstract name into a more meaningful
 * list of socket addresses.
 *
 * This process is asynchronous and the resolution may change over time.
 */
public interface Resolver {
    public interface Event {
        public SocketAddress getAddress();
    }

    public class Addition implements Event {
        private SocketAddress address;
        public Addition(SocketAddress addr) {
            this.address = addr;
        }
        public SocketAddress getAddress() {
            return address;
        }
        public String toString() {
            return "Addition(" + address + ")";
        }
    }

    public class Removal implements Event {
        private SocketAddress address;
        public Removal(SocketAddress addr) {
            this.address = addr;
        }
        public SocketAddress getAddress() {
            return address;
        }
        public String toString() {
            return "Removal(" + address + ")";
        }

    }

    public Publisher<Event> resolve(URL url);
}
