package com.litongjava.tio.http.server.handler;

import com.litongjava.tio.http.common.HttpConfig;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.RequestLine;
import com.litongjava.tio.http.common.handler.HttpRequestHandler;
import com.litongjava.tio.http.server.intf.ThrowableHandler;
import com.litongjava.tio.http.server.util.Resps;

public class SimpleHttpDispatcherHandler implements HttpRequestHandler {

  private HttpRoutes httpRoutes;
  private HttpConfig httpConfig;
  private ThrowableHandler throwableHandler;

  public SimpleHttpDispatcherHandler(HttpConfig httpConfig, HttpRoutes httpRoutes) {
    this.httpRoutes = httpRoutes;
    this.httpConfig = httpConfig;
  }

  @Override
  public HttpResponse handler(HttpRequest httpRequest) throws Exception {
    RequestLine requestLine = httpRequest.getRequestLine();
    String path = requestLine.getPath();
    HttpRequestRouteHandler handler = httpRoutes.find(path);
    if (handler == null) {
      return this.resp404(httpRequest, requestLine);
      
    }
    HttpResponse httpResponse = null;
    try {
      httpResponse = handler.handle(httpRequest);
    } catch (Exception e) {
      e.printStackTrace();
      return this.resp500(httpRequest, requestLine, e);
    }

    return httpResponse;
  }

  @Override
  public HttpResponse resp404(HttpRequest request, RequestLine requestLine) throws Exception {
    if (httpRoutes != null) {
      String page404 = httpConfig.getPage404();
      HttpRequestRouteHandler handler = httpRoutes.find(page404);
      if (handler != null) {
        return handler.handle(request);
      }
    }

    return Resps.resp404(request, requestLine, httpConfig);

  }

  @Override
  public HttpResponse resp500(HttpRequest request, RequestLine requestLine, Throwable throwable) throws Exception {
    if (throwableHandler != null) {
      return throwableHandler.handler(request, requestLine, throwable);
    }

    if (httpRoutes != null) {
      String page404 = httpConfig.getPage404();
      HttpRequestRouteHandler handler = httpRoutes.find(page404);
      if (handler != null) {
        return handler.handle(request);
      }
    }

    return Resps.resp500(request, requestLine, httpConfig, throwable);
  }

  @Override
  public HttpConfig getHttpConfig(HttpRequest request) {
    return null;
  }

  @Override
  public void clearStaticResCache() {

  }

}
