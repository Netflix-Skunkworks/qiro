package io.qiro.testing;

import io.qiro.Service;
import io.qiro.util.EmptySubscriber;
import io.qiro.util.Publishers;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Function;

public class TestingService<Req, Resp> implements Service<Req, Resp> {
    private class StreamInfo {
        private final Deque<Req> requests = new LinkedBlockingDeque<>();
        private final Subscriber<? super Resp> subscriber;

        StreamInfo(Subscriber<? super Resp> subscriber) {
            this.subscriber = subscriber;
        }

        synchronized void offer(Req req) {
            requests.offer(req);
        }

        synchronized void respond() {
            if (!requests.isEmpty()) {
                Req request = requests.pollFirst();
                Resp response = serviceFn.apply(request);
                subscriber.onNext(response);
            }
        }

        synchronized void complete() {
            subscriber.onComplete();
        }
    }

    private boolean open = true;
    private double availabilityValue = 1.0;

    private List<StreamInfo> streamInfos;
    private Function<Req, Resp> serviceFn;

    public TestingService(Function<Req, Resp> fn) {
        serviceFn = fn;
        streamInfos = new ArrayList<>();
    }

    @Override
    public Publisher<Resp> requestChannel(Publisher<Req> requests) {
        if (!open) {
            throw new IllegalStateException("applying on a close TestingService");
        }
        return subscriber -> {
            synchronized (TestingService.this) {
                StreamInfo info = new StreamInfo(subscriber);
                requests.subscribe(new EmptySubscriber<Req>() {
                    @Override
                    public void onNext(Req req) {
                        info.offer(req);
                    }
                });
                streamInfos.add(info);
            }
        };
    }

    public synchronized int queueSize() {
        int size = 0;
        for (StreamInfo info: streamInfos) {
            size += info.requests.size();
        }
        return size;
    }

    public synchronized int queueSize(int streamId) {
        return streamInfos.get(streamId).requests.size();
    }

    public synchronized void respond() {
        streamInfos.forEach(StreamInfo::respond);
    }

    public synchronized void respond(int streamId) {
        streamInfos.get(streamId).respond();
    }

    public synchronized void complete() {
        while (!streamInfos.isEmpty()) {
            StreamInfo info = streamInfos.remove(0);
            info.complete();
        }
    }

    public synchronized void complete(int streamId) {
        streamInfos.remove(streamId).complete();
    }

    public synchronized boolean isOpen() {
        return open;
    }

    public synchronized boolean isClosed() {
        return !open;
    }

    @Override
    public double availability() {
        return availabilityValue;
    }

    @Override
    public Publisher<Void> close() {
        return s -> {
            synchronized (TestingService.this) {
                open = false;
            }
            s.onComplete();
        };
    }

    public synchronized void updateAvailability(double newValue) {
        availabilityValue = newValue;
    }
}
