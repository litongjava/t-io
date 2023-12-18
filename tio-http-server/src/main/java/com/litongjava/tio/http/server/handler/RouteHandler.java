package com.litongjava.tio.http.server.handler;

import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;

@FunctionalInterface
public interface RouteHandler {
  HttpResponse handle(HttpRequest httpRequest) throws Exception;
}
