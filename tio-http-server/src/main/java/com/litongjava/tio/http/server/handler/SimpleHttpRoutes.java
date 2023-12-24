package com.litongjava.tio.http.server.handler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleHttpRoutes implements HttpRoutes {
  Map<String, HttpRequestRouteHandler> requestMapping = new ConcurrentHashMap<>();

  public void add(String path, HttpRequestRouteHandler handler) {
    requestMapping.put(path, handler);
  }

  public HttpRequestRouteHandler find(String path) {
    return requestMapping.get(path);
  }

}
