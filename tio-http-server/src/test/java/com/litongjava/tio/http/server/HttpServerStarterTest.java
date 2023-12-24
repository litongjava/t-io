package com.litongjava.tio.http.server;

import java.io.IOException;

import com.litongjava.tio.http.common.HttpConfig;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.handler.HttpRequestHandler;
import com.litongjava.tio.http.server.handler.HttpRoutes;
import com.litongjava.tio.http.server.handler.SimpleHttpDispatcherHandler;
import com.litongjava.tio.http.server.handler.SimpleHttpRoutes;
import com.litongjava.tio.http.server.util.Resps;

public class HttpServerStarterTest {

  public static void main(String[] args) throws IOException {
    // 手动添加路由
    HttpServerStarterTest controller = new HttpServerStarterTest();

    HttpRoutes simpleHttpRoutes = new SimpleHttpRoutes();
    simpleHttpRoutes.add("/", controller::index);
    simpleHttpRoutes.add("/login", controller::login);
    simpleHttpRoutes.add("/exception", controller::exception);
    //
    HttpConfig httpConfig;
    HttpRequestHandler requestHandler;
    HttpServerStarter httpServerStarter;

    // httpConfig
    httpConfig = new HttpConfig(80, null, null, null);
    requestHandler = new SimpleHttpDispatcherHandler(httpConfig, simpleHttpRoutes);
    httpServerStarter = new HttpServerStarter(httpConfig, requestHandler);
    httpServerStarter.start();
  }

  public HttpResponse index(HttpRequest request) {
    return Resps.txt(request, "index");

  }

  private HttpResponse login(HttpRequest request) {
    return Resps.txt(request, "login");
  }

  private HttpResponse exception(HttpRequest request) {
    throw new RuntimeException("error");
    // return Resps.txt(request, "exception");
  }

}