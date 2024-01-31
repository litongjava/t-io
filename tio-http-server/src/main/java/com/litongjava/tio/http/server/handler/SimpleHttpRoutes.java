package com.litongjava.tio.http.server.handler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleHttpRoutes implements HttpRoutes {
  Map<String, HttpRequestRouteHandler> requestMapping = new ConcurrentHashMap<>();

  public void add(String path, HttpRequestRouteHandler handler) {
    requestMapping.put(path, handler);
  }

  /**
   * find route
   * /* 表示匹配任何以特定路径开始的路径，/** 表示匹配该路径及其下的任何子路径
   */
  public HttpRequestRouteHandler find(String path) {
    HttpRequestRouteHandler httpRequestRouteHandler = requestMapping.get(path);
    if (httpRequestRouteHandler != null) {
      return httpRequestRouteHandler;
    }

    // Check for wildcard matches
    Set<Map.Entry<String, HttpRequestRouteHandler>> entrySet = requestMapping.entrySet();

    for (Map.Entry<String, HttpRequestRouteHandler> entry : entrySet) {
      String key = entry.getKey();

      if (key.endsWith("/*")) {
        String baseRoute = key.substring(0, key.length() - 1);
        if (path.startsWith(baseRoute)) {
          return entry.getValue();
        }
      } else if (key.endsWith("/**")) {
        String baseRoute = key.substring(0, key.length() - 2);
        if (path.startsWith(baseRoute)) {
          return entry.getValue();
        }
      }
    }

    return null;

  }

}
