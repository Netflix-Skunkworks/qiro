package io.qiro.http;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

class RxNettyResponse implements HttpResponse {
    private final HttpClientResponse<ByteBuf> response;

    private RxNettyResponse(HttpClientResponse<ByteBuf> response) {
        this.response = response;
    }

    public static RxNettyResponse wrap(HttpClientResponse<ByteBuf> response) {
        return new RxNettyResponse(response);
    }

    @Override
    public DecoderResult decoderResult() {
        throw new NotImplementedException();
    }

    @Override
    public void setDecoderResult(DecoderResult result) {
        throw new NotImplementedException();
    }

    @Override
    public DecoderResult getDecoderResult() {
        throw new NotImplementedException();
    }

    @Override
    public HttpResponseStatus getStatus() {
        return status();
    }

    @Override
    public HttpResponseStatus status() {
        return response.getStatus();
    }

    @Override
    public HttpResponse setStatus(HttpResponseStatus status) {
        throw new NotImplementedException();
    }

    @Override
    public HttpVersion getProtocolVersion() {
        return protocolVersion();
    }

    @Override
    public HttpVersion protocolVersion() {
        return response.getHttpVersion();
    }

    @Override
    public HttpResponse setProtocolVersion(HttpVersion version) {
        throw new NotImplementedException();
    }

    @Override
    public HttpHeaders headers() {
        throw new NotImplementedException();
    }
}
