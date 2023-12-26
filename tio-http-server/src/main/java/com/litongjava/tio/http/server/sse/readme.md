##SSE
SSE（Server-Sent Events）是一种浏览器与服务器之间的通信技术，它允许服务器推送事件和数据给浏览器。与传统的HTTP请求/响应模型不同，SSE建立了一个长连接，服务器可以随时向客户端发送新的数据，而无需等待客户端的请求。SSE的工作原理很简单，客户端通过使用EventSource对象在JavaScript中建立到服务器的连接。服务器使用HTTP流来推送事件和数据，这使得实时数据推送成为可能。