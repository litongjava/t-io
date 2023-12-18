package com.litongjava.tio.http.server.handler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleHttpRoutes implements HttpRoutes {
  Map<String, RouteHandler> requestMapping = new ConcurrentHashMap<>();

  public void add(String path, RouteHandler handler) {
    requestMapping.put(path, handler);
  }

  public RouteHandler find(String path) {
    return requestMapping.get(path);
  }

}
