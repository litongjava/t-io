# t-io
The package is distributed through Maven Central.
[t-io](https://central.sonatype.com/artifact/com.litongjava/t-io),
[tio-core](https://central.sonatype.com/artifact/com.litongjava/tio-core),
[tio-http-server](https://central.sonatype.com/artifact/com.litongjava/tio-http-server),
[tio-websocket-server](https://central.sonatype.com/artifact/com.litongjava/tio-websocket-server),
[tio-server](https://central.sonatype.com/artifact/com.litongjava/tio--server),

## 
## TIO-Server Web 开发入门指南

#### 简介

本文档旨在为初学者介绍如何使用 `tio-server` 进行基本的 Web 开发。`tio-server` 是一个 Java 库，用于创建和管理服务器，特别是在处理 HTTP 请求和响应方面。以下是一个简单示例，展示了如何使用 `tio-server` 设置一个基本的 HTTP 服务器，并定义了一些简单的路由和处理程序。

#### 环境准备

- **Java 开发环境**：确保你的机器上安装了 Java 1.8 或更高版本。
- **Maven**：本示例使用 Maven 来管理项目依赖和构建过程。
- **IDE**：推荐使用 IntelliJ IDEA、Eclipse 或任何支持 Maven 的 IDE。

#### 项目结构

以下是项目的基本结构：

```
- src
  - main
    - java
      - com.litongjava.tio.http.server
        - HttpServerStarterTest.java
      - com.litongjava.tio.http.server.controller
        - IndexController.java
- pom.xml
```

- `HttpServerStarterTest.java`：服务器启动类。
- `IndexController.java`：包含用于处理 HTTP 请求的方法。
- `pom.xml`：Maven 配置文件，定义了项目的依赖和构建配置。

#### Maven 配置

`pom.xml` 文件定义了项目的依赖和其他 Maven 配置。以下是一个基本的 `pom.xml` 配置示例：

```xml
<properties>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  <java.version>1.8</java.version>
  <maven.compiler.source>${java.version}</maven.compiler.source>
  <maven.compiler.target>${java.version}</maven.compiler.target>
</properties>

<dependencies>
  <dependency>
    <groupId>com.litongjava</groupId>
    <artifactId>tio-server</artifactId>
    <version>3.7.3.v20231218-RELEASE</version>
  </dependency>
</dependencies>
```

编写代码,启动类和Controller
```
package com.litongjava.tio.http.server.controller;

import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.server.util.Resps;

public class IndexController {

  public HttpResponse index(HttpRequest request) {
    return Resps.txt(request, "index");

  }

  public HttpResponse login(HttpRequest request) {
    return Resps.txt(request, "login");
  }

  public HttpResponse exception(HttpRequest request) {
    throw new RuntimeException("error");
  }
}

```
```
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
```
#### 核心代码解释

###### `IndexController.java`

`IndexController` 类包含了几个方法，每个方法对应一个 HTTP 路由的处理逻辑。

- `index(HttpRequest request)`：处理根路由 `/` 的请求。
- `login(HttpRequest request)`：处理 `/login` 路由的请求。
- `exception(HttpRequest request)`：处理 `/exception` 路由的请求，抛出一个异常。

###### `HttpServerStarterTest.java`

`HttpServerStarterTest` 类是服务器的入口点，包含 `main` 方法来启动服务器。

1. 创建 `IndexController` 实例和 `SimpleHttpRoutes` 实例。
2. 为不同的路径（如 `/`, `/login`, `/exception`）添加路由和对应的处理方法。
3. 配置 `HttpConfig`，设置服务器端口（例如 80）。
4. 创建 `HttpRequestHandler` 实例，负责分发 HTTP 请求到对应的处理方法。
5. 实例化 `HttpServerStarter` 并启动服务器。

#### 运行服务器

要运行服务器，请在 IDE 中运行 `HttpServerStarterTest.java`，或通过命令行使用 Maven 命令 `mvn exec:java -Dexec.mainClass="com.litongjava.tio.http.server.HttpServerStarterTest"`。

一旦服务器启动，你可以通过浏览器或任何 HTTP 客户端向 `http://localhost` 发送请求，以测试不同的路由。
## import other dependency
```
<dependency>
  <groupId>com.litongjava</groupId>
  <artifactId>tio-http-server</artifactId>
  <version>${tio-version}</version>
</dependency>

<dependency>
  <groupId>com.litongjava</groupId>
  <artifactId>tio-websocket-server</artifactId>
  <version>${tio-version}</version>
</dependency>

<dependency>
  <groupId>com.litongjava</groupId>
  <artifactId>tio-http-common</artifactId>
  <version>${tio-version}</version>
</dependency>

<dependency>
  <groupId>com.litongjava</groupId>
  <artifactId>tio-websocket-common</artifactId>
  <version>${tio-version}</version>
</dependency>

<dependency>
  <groupId>com.litongjava</groupId>
  <artifactId>tio-utils</artifactId>
  <version>${tio-version}</version>
</dependency>

<dependency>
  <groupId>com.litongjava</groupId>
  <artifactId>tio-core</artifactId>
  <version>${tio-version}</version>
</dependency>
```
