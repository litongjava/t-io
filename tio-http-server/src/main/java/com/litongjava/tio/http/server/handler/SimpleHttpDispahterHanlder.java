package com.litongjava.tio.http.server.handler;

import com.litongjava.tio.http.common.HttpConfig;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.RequestLine;
import com.litongjava.tio.http.common.handler.HttpRequestHandler;
import com.litongjava.tio.http.server.util.Resps;

public class SimpleHttpDispahterHanlder implements HttpRequestHandler {

  private HttpRoutes httpRoutes;
  private HttpConfig httpConfig;

  public SimpleHttpDispahterHanlder(HttpConfig httpConfig, HttpRoutes httpRoutes) {
    this.httpRoutes = httpRoutes;
    this.httpConfig = httpConfig;
  }

  @Override
  public HttpResponse handler(HttpRequest httpRequest) throws Exception {
    String path = httpRequest.getRequestLine().getPath();
    RouteHandler handler = httpRoutes.find(path);
    HttpResponse httpResponse = handler.handle(httpRequest);
    return httpResponse;
  }

  @Override
  public HttpResponse resp404(HttpRequest request, RequestLine requestLine) throws Exception {
    return Resps.resp404(request, requestLine, httpConfig);
  }

  @Override
  public HttpResponse resp500(HttpRequest request, RequestLine requestLine, Throwable throwable) throws Exception {
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
