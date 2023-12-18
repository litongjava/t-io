package com.litongjava.tio.http.server.handler;

public interface HttpRoutes {

  /**
   * 添加路由
   * @param path
   * @param handler
   */
  public void add(String path, RouteHandler handler);

  /**
   * 查找路由
   * @param path
   * @param handler
   * @return
   */
  public RouteHandler find(String path);
}
