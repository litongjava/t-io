package com.litongjava.tio.http.common;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Node;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.core.exception.TioDecodeException;
import com.litongjava.tio.http.common.HttpConst.RequestBodyFormat;
import com.litongjava.tio.http.common.utils.HttpParseUtils;
import com.litongjava.tio.http.common.utils.IpUtils;
import com.litongjava.tio.utils.SysConst;
import com.litongjava.tio.utils.environment.EnvironmentUtils;
import com.litongjava.tio.utils.hutool.StrUtil;

/**
 * http server中使用
 * @author tanyaowu
 *
 */
public class HttpRequestDecoder {
  public static enum Step {
    firstline, header, body
  }

  private static Logger log = LoggerFactory.getLogger(HttpRequestDecoder.class);

  /**
   *   头部，最多有多少字节
   */
  public static final int MAX_LENGTH_OF_HEADER = 20480;

  /**
   *      头部，每行最大的字节数
   */
  public static final int MAX_LENGTH_OF_HEADERLINE = 2048;

  /**
   *   请求行的最大长度
   */
  public static final int MAX_LENGTH_OF_REQUESTLINE = 2048;

  /**
   * 
   * @param buffer
   * @param limit
   * @param position
   * @param readableLength
   * @param channelContext
   * @param httpConfig
   * @return
   * @throws TioDecodeException
   * @author tanyaowu
   */
  public static HttpRequest decode(ByteBuffer buffer, int limit, int position, int readableLength,
      ChannelContext channelContext, HttpConfig httpConfig) throws TioDecodeException {
    // int initPosition = position;
    // int count = 0;
    // Step step = Step.firstline;
    // StringBuilder currLine = new StringBuilder();
    RequestLine firstLine = null;
    // StringBuilder headerSb = null;//new StringBuilder(512);

    // boolean appendRequestHeaderString = httpConfig.isAppendRequestHeaderString();

    // if (httpConfig != null) {
    //
    // }
    // if (appendRequestHeaderString) {
    // headerSb = new StringBuilder(512);
    // }

    // request line start
    firstLine = parseRequestLine(buffer, channelContext);
    if (firstLine == null) {
      return null;
    }
    Map<String, String> headers = new HashMap<>();
    int contentLength = 0;
    byte[] bodyBytes = null;
    // request line end

    // HttpRequestHandler httpRequestHandler = (HttpRequestHandler)channelContext.tioConfig.getAttribute(TioConfigKey.HTTP_REQ_HANDLER);
    // if (httpRequestHandler != null) {
    // httpRequest.setHttpConfig(httpRequestHandler.getHttpConfig(httpRequest));
    // }

    // request header start
    boolean headerCompleted = parseHeaderLine(buffer, headers, 0, httpConfig);
    if (!headerCompleted) {
      return null;
    }
    String contentLengthStr = headers.get(HttpConst.RequestHeaderKey.Content_Length);

    if (StrUtil.isBlank(contentLengthStr)) {
      contentLength = 0;
    } else {
      contentLength = Integer.parseInt(contentLengthStr);
      if (contentLength > httpConfig.getMaxLengthOfPostBody()) {
        throw new TioDecodeException("post body length is too big[" + contentLength + "], max length is "
            + httpConfig.getMaxLengthOfPostBody() + " byte");
      }
    }

    int headerLength = (buffer.position() - position);
    int allNeedLength = headerLength + contentLength; // 这个packet所需要的字节长度(含头部和体部)

    int notReceivedLength = allNeedLength - readableLength; // 尚未接收到的数据长度
    if (notReceivedLength > 0) {
      if (notReceivedLength > channelContext.getReadBufferSize()) {
        channelContext.setReadBufferSize(notReceivedLength);
      }

      channelContext.setPacketNeededLength(allNeedLength);
      return null;
    }
    // request header end

    // ----------------------------------------------- request body start

    // httpRequest.setHttpConfig((HttpConfig) channelContext.tioConfig.getAttribute(TioConfigKey.HTTP_SERVER_CONFIG));

    String realIp = IpUtils.getRealIp(channelContext, httpConfig, headers);
    if (Tio.IpBlacklist.isInBlacklist(channelContext.tioConfig, realIp)) {
      throw new TioDecodeException("[" + realIp + "] in black list");
    }
    if (httpConfig.checkHost) {
      if (!headers.containsKey(HttpConst.RequestHeaderKey.Host)) {
        throw new TioDecodeException("there is no host header");
      }
    }

    Node realNode = null;
    if (Objects.equals(realIp, channelContext.getClientNode().getIp())) {
      realNode = channelContext.getClientNode();
    } else {
      realNode = new Node(realIp, channelContext.getClientNode().getPort()); // realNode
      channelContext.setProxyClientNode(realNode);
    }

    HttpRequest httpRequest = new HttpRequest(realNode);
    httpRequest.setRequestLine(firstLine);
    httpRequest.setChannelContext(channelContext);
    httpRequest.setHttpConfig(httpConfig);
    httpRequest.setHeaders(headers);
    httpRequest.setContentLength(contentLength);
    // if (appendRequestHeaderString) {
    // httpRequest.setHeaderString(headerSb.toString());
    // } else {
    // httpRequest.setHeaderString("");
    // }

    String connection = headers.get(HttpConst.RequestHeaderKey.Connection);
    if (connection != null) {
      httpRequest.setConnection(connection.toLowerCase());
    }

    if (StrUtil.isNotBlank(firstLine.queryString)) {
      decodeParams(httpRequest.getParams(), firstLine.queryString, httpRequest.getCharset(), channelContext);
    }

    if (contentLength > 0) {
      bodyBytes = new byte[contentLength];
      buffer.get(bodyBytes);
      httpRequest.setBody(bodyBytes);
      // 解析消息体
      parseBody(httpRequest, firstLine, bodyBytes, channelContext, httpConfig);
    } else {
      // if (StrUtil.isNotBlank(firstLine.getQuery())) {
      // decodeParams(httpRequest.getParams(), firstLine.getQuery(), httpRequest.getCharset(), channelContext);
      // }
    }
    // ----------------------------------------------- request body end

    // 解析User_Agent(浏览器操作系统等信息)
    // String User_Agent = headers.get(HttpConst.RequestHeaderKey.User_Agent);
    // if (StrUtil.isNotBlank(User_Agent)) {
    // // long start = System.currentTimeMillis();
    // UserAgentAnalyzer userAgentAnalyzer = UserAgentAnalyzerFactory.getUserAgentAnalyzer();
    // UserAgent userAgent = userAgentAnalyzer.parse(User_Agent);
    // httpRequest.setUserAgent(userAgent);
    // }

    // StringBuilder logstr = new StringBuilder();
    // logstr.append("\r\n------------------ websocket header start ------------------------\r\n");
    // logstr.append(firstLine.getInitStr()).append(SysConst.CRLF);
    // Set<Entry<String, String>> entrySet = headers.entrySet();
    // for (Entry<String, String> entry : entrySet) {
    // logstr.append(StrUtil.leftPad(entry.getKey(), 30)).append(" : ").append(entry.getValue()).append(SysConst.CRLF);
    // }
    // logstr.append("------------------ websocket header start ------------------------\r\n");
    // log.error(logstr.toString());

    return httpRequest;
  }

  /**
   * 
   * @param params
   * @param queryString
   * @param charset
   * @param channelContext
   * @author tanyaowu
   * @throws TioDecodeException 
   */
  public static void decodeParams(Map<String, Object[]> params, String queryString, String charset,
      ChannelContext channelContext) throws TioDecodeException {
    if (StrUtil.isBlank(queryString)) {
      return;
    }

    String[] keyvalues = queryString.split(SysConst.STR_AMP);
    for (String keyvalue : keyvalues) {
      String[] keyvalueArr = keyvalue.split(SysConst.STR_EQ);
      String value1 = null;
      if (keyvalueArr.length == 2) {
        value1 = keyvalueArr[1];
      } else if (keyvalueArr.length > 2) {
        throw new TioDecodeException("含有多个" + SysConst.STR_EQ);
      }

      String key = keyvalueArr[0];
      String value;
      if (StrUtil.isBlank(value1)) {
        value = null;
      } else {
        try {
          value = URLDecoder.decode(value1, charset);
        } catch (UnsupportedEncodingException e) {
          throw new TioDecodeException(e);
        }
      }

      Object[] existValue = params.get(key);
      if (existValue != null) {
        String[] newExistValue = new String[existValue.length + 1];
        System.arraycopy(existValue, 0, newExistValue, 0, existValue.length);
        newExistValue[newExistValue.length - 1] = value;
        params.put(key, newExistValue);
      } else {
        String[] newExistValue = new String[] { value };
        params.put(key, newExistValue);
      }
    }
    return;
  }

  /**
   * 解析消息体
   * @param httpRequest
   * @param firstLine
   * @param bodyBytes
   * @param channelContext
   * @param httpConfig
   * @throws TioDecodeException
   * @author tanyaowu
   */
  private static void parseBody(HttpRequest httpRequest, RequestLine firstLine, byte[] bodyBytes,
      ChannelContext channelContext, HttpConfig httpConfig) throws TioDecodeException {
    parseBodyFormat(httpRequest, httpRequest.getHeaders());
    RequestBodyFormat bodyFormat = httpRequest.getBodyFormat();

    httpRequest.setBody(bodyBytes);

    // if (bodyFormat == RequestBodyFormat.MULTIPART) {
    // if (log.isInfoEnabled()) {
    // String bodyString = null;
    // if (bodyBytes != null && bodyBytes.length > 0) {
    // if (log.isDebugEnabled()) {
    // try {
    // bodyString = new String(bodyBytes, httpRequest.getCharset());
    // log.debug("{} multipart body value\r\n{}", channelContext, bodyString);
    // } catch (UnsupportedEncodingException e) {
    // log.error(channelContext.toString(), e);
    // }
    // }
    // }
    // }
    //
    // //【multipart/form-data; boundary=----WebKitFormBoundaryuwYcfA2AIgxqIxA0】
    // String initboundary = HttpParseUtils.getPerprotyEqualValue(httpRequest.getHeaders(), HttpConst.RequestHeaderKey.Content_Type, "boundary");
    // log.debug("{}, initboundary:{}", channelContext, initboundary);
    // HttpMultiBodyDecoder.decode(httpRequest, firstLine, bodyBytes, initboundary, channelContext, httpConfig);
    // } else {
    // String bodyString = null;
    // if (bodyBytes != null && bodyBytes.length > 0) {
    // try {
    // bodyString = new String(bodyBytes, httpRequest.getCharset());
    // httpRequest.setBodyString(bodyString);
    // if (log.isInfoEnabled()) {
    // log.info("{} body value\r\n{}", channelContext, bodyString);
    // }
    // } catch (UnsupportedEncodingException e) {
    // log.error(channelContext.toString(), e);
    // }
    // }
    //
    // if (bodyFormat == RequestBodyFormat.URLENCODED) {
    // parseUrlencoded(httpRequest, firstLine, bodyBytes, bodyString, channelContext);
    // }
    // }

    switch (bodyFormat) {
    case MULTIPART:
      if (log.isInfoEnabled()) {
        String bodyString = null;
        if (bodyBytes != null && bodyBytes.length > 0) {
          if (log.isDebugEnabled()) {
            try {
              bodyString = new String(bodyBytes, httpRequest.getCharset());
              log.debug("{} multipart body value\r\n{}", channelContext, bodyString);
            } catch (UnsupportedEncodingException e) {
              log.error(channelContext.toString(), e);
            }
          }
        }
      }

      // 【multipart/form-data; boundary=----WebKitFormBoundaryuwYcfA2AIgxqIxA0】
      String contentType = httpRequest.getHeader(HttpConst.RequestHeaderKey.Content_Type);
      String initboundary = HttpParseUtils.getSubAttribute(contentType, "boundary");// .getPerprotyEqualValue(httpRequest.getHeaders(), HttpConst.RequestHeaderKey.Content_Type, "boundary");
      if (log.isDebugEnabled()) {
        log.debug("{}, initboundary:{}", channelContext, initboundary);
      }
      HttpMultiBodyDecoder.decode(httpRequest, firstLine, bodyBytes, initboundary, channelContext, httpConfig);
      break;

    default:
      String bodyString = null;
      if (bodyBytes != null && bodyBytes.length > 0) {
        try {
          bodyString = new String(bodyBytes, httpRequest.getCharset());
          httpRequest.setBodyString(bodyString);
          if (EnvironmentUtils.getBoolean("tio.devMode", false)) {
            if (log.isInfoEnabled()) {
              log.info("{} body value\r\n{}", channelContext, bodyString);
            }
          }

        } catch (UnsupportedEncodingException e) {
          log.error(channelContext.toString(), e);
        }
      }

      if (bodyFormat == RequestBodyFormat.URLENCODED) {
        parseUrlencoded(httpRequest, firstLine, bodyBytes, bodyString, channelContext);
      }
      break;
    }
  }

  /**
   * Content-Type : application/x-www-form-urlencoded; charset=UTF-8
   * Content-Type : application/x-www-form-urlencoded; charset=UTF-8
  在 `HttpRequest` 中，请求类型通常指的是请求正文（body）的内容类型（也称为 MIME 类型）。这些类型指定了请求正文的格式，以便服务器能够正确解析。除了您提到的 `application/x-www-form-urlencoded` (URLENCODED), `multipart/form-data` (MULTIPART), 和 `text/plain` (TEXT)，还有其他几种常见的请求类型：
  1. **`application/json`**：用于发送 JSON 格式的数据。在现代的 Web API 中非常常见。
  2. **`application/xml` 或 `text/xml`**：用于发送 XML 格式的数据。
  3. **`application/javascript`**：用于发送 JavaScript 代码。
  4. **`application/octet-stream`**：用于发送二进制数据，比如文件上传。
  5. **`text/html`**：用于发送 HTML 格式的数据。
  6. **`application/graphql`**：用于 GraphQL 请求。
  7. **`image/png`, `image/jpeg` 等**：用于发送特定类型的图像数据。
  
  这些是一些常见的请求类型。实际上，请求类型可以是任何值，但为了确保数据正确解析，通常使用标准的 MIME 类型。不同的服务器和应用程序可能支持不同的请求类型。
   * @param httpRequest
   * @param headers
   * @author tanyaowu
   */
  public static void parseBodyFormat(HttpRequest httpRequest, Map<String, String> headers) {
    String contentType = headers.get(HttpConst.RequestHeaderKey.Content_Type);
    if (contentType != null) {
      contentType = contentType.toLowerCase();
    }

    if (isText(contentType)) {
      httpRequest.setBodyFormat(RequestBodyFormat.TEXT);
    } else if (contentType.startsWith(HttpConst.RequestHeaderValue.Content_Type.multipart_form_data)) {
      httpRequest.setBodyFormat(RequestBodyFormat.MULTIPART);
    } else {
      httpRequest.setBodyFormat(RequestBodyFormat.URLENCODED);
    }

    if (StrUtil.isNotBlank(contentType)) {
      String charset = HttpParseUtils.getSubAttribute(contentType, "charset");// .getPerprotyEqualValue(headers, HttpConst.RequestHeaderKey.Content_Type, "charset");
      if (StrUtil.isNotBlank(charset)) {
        httpRequest.setCharset(charset);
      } else {
        httpRequest.setCharset(SysConst.DEFAULT_ENCODING);
      }
    }
  }

  /**
   * 请求类型是否为文本
   * @param contentType
   * @return
   */
  private static boolean isText(String contentType) {
    return contentType.startsWith(HttpConst.RequestHeaderValue.Content_Type.text_plain)
        //
        || contentType.startsWith(MimeType.TEXT_PLAIN_JSON.getType());
  }

  /**
   * 解析请求头的每一行
   * @param buffer
   * @param headers
   * @param hasReceivedHeaderLength
   * @param httpConfig
   * @return 头部是否解析完成，true: 解析完成, false: 没有解析完成
   * @throws TioDecodeException
   * @author tanyaowu
   */
  public static boolean parseHeaderLine(ByteBuffer buffer, Map<String, String> headers, int hasReceivedHeaderLength,
      HttpConfig httpConfig) throws TioDecodeException {
    // if (!buffer.hasArray()) {
    // return parseHeaderLine2(buffer, headers, hasReceivedHeaderLength, httpConfig);
    // }

    byte[] allbs = buffer.array();
    int initPosition = buffer.position();
    int lastPosition = initPosition;
    int remaining = buffer.remaining();
    if (remaining == 0) {
      return false;
    } else if (remaining > 1) {
      byte b1 = buffer.get();
      byte b2 = buffer.get();
      if (SysConst.CR == b1 && SysConst.LF == b2) {
        return true;
      } else if (SysConst.LF == b1) {
        return true;
      }
    } else {
      if (SysConst.LF == buffer.get()) {
        return true;
      }
    }

    String name = null;
    String value = null;
    boolean hasValue = false;

    boolean needIteration = false;
    while (buffer.hasRemaining()) {
      byte b = buffer.get();
      if (name == null) {
        if (b == SysConst.COL) {
          int len = buffer.position() - lastPosition - 1;
          name = StrCache.get(allbs, lastPosition, len);
          // name = new String(allbs, lastPosition, len);
          lastPosition = buffer.position();
        } else if (b == SysConst.LF) {
          byte lastByte = buffer.get(buffer.position() - 2);
          int len = buffer.position() - lastPosition - 1;
          if (lastByte == SysConst.CR) {
            len = buffer.position() - lastPosition - 2;
          }
          name = StrCache.get(allbs, lastPosition, len);
          // name = new String(allbs, lastPosition, len);
          lastPosition = buffer.position();
          headers.put(StrCache.getLowercase(name), "");

          needIteration = true;
          break;
        }
        continue;
      } else if (value == null) {
        if (b == SysConst.LF) {
          byte lastByte = buffer.get(buffer.position() - 2);
          int len = buffer.position() - lastPosition - 1;
          if (lastByte == SysConst.CR) {
            len = buffer.position() - lastPosition - 2;
          }
          value = new String(allbs, lastPosition, len);
          lastPosition = buffer.position();

          headers.put(StrCache.getLowercase(name), StrUtil.trimEnd(value));
          needIteration = true;
          break;
        } else {
          if (!hasValue && b == SysConst.SPACE) {
            lastPosition = buffer.position();
          } else {
            hasValue = true;
          }
        }
      }
    }

    int lineLength = buffer.position() - initPosition; // 这一行(header line)的字节数
    // log.error("lineLength:{}, headerLength:{}, headers:\r\n{}", lineLength, hasReceivedHeaderLength, Json.toFormatedJson(headers));
    if (lineLength > MAX_LENGTH_OF_HEADERLINE) {
      // log.error("header line is too long, max length of header line is " + MAX_LENGTH_OF_HEADERLINE);
      throw new TioDecodeException("header line is too long, max length of header line is " + MAX_LENGTH_OF_HEADERLINE);
    }

    if (needIteration) {
      int headerLength = lineLength + hasReceivedHeaderLength; // header占用的字节数
      // log.error("allHeaderLength:{}", allHeaderLength);
      if (headerLength > MAX_LENGTH_OF_HEADER) {
        // log.error("header is too long, max length of header is " + MAX_LENGTH_OF_HEADER);
        throw new TioDecodeException("header is too long, max length of header is " + MAX_LENGTH_OF_HEADER);
      }
      return parseHeaderLine(buffer, headers, headerLength, httpConfig);
    }

    return false;
  }

  /**
   * 解析请求头的每一行
   * @param line
   * @param headers
   * @return 头部是否解析完成，true: 解析完成, false: 没有解析完成
   * @author tanyaowu
   */
  @SuppressWarnings("unused")
  private static boolean parseHeaderLine2(ByteBuffer buffer, Map<String, String> headers, int headerLength,
      HttpConfig httpConfig) throws TioDecodeException {
    int initPosition = buffer.position();
    int lastPosition = initPosition;
    int remaining = buffer.remaining();
    if (remaining == 0) {
      return false;
    } else if (remaining > 1) {
      byte b1 = buffer.get();
      byte b2 = buffer.get();
      if (SysConst.CR == b1 && SysConst.LF == b2) {
        return true;
      } else if (SysConst.LF == b1) {
        return true;
      }
    } else {
      if (SysConst.LF == buffer.get()) {
        return true;
      }
    }

    String name = null;
    String value = null;
    boolean hasValue = false;

    boolean needIteration = false;
    while (buffer.hasRemaining()) {
      byte b = buffer.get();
      if (name == null) {
        if (b == SysConst.COL) {
          int nowPosition = buffer.position();
          byte[] bs = new byte[nowPosition - lastPosition - 1];
          buffer.position(lastPosition);
          buffer.get(bs);
          name = new String(bs);
          lastPosition = nowPosition;
          buffer.position(nowPosition);
        } else if (b == SysConst.LF) {
          int nowPosition = buffer.position();
          byte[] bs = null;
          byte lastByte = buffer.get(nowPosition - 2);

          if (lastByte == SysConst.CR) {
            bs = new byte[nowPosition - lastPosition - 2];
          } else {
            bs = new byte[nowPosition - lastPosition - 1];
          }

          buffer.position(lastPosition);
          buffer.get(bs);
          name = new String(bs);
          lastPosition = nowPosition;
          buffer.position(nowPosition);

          headers.put(name.toLowerCase(), null);
          needIteration = true;
          break;
          // return true;
        }
        continue;
      } else if (value == null) {
        if (b == SysConst.LF) {
          int nowPosition = buffer.position();
          byte[] bs = null;
          byte lastByte = buffer.get(nowPosition - 2);

          if (lastByte == SysConst.CR) {
            bs = new byte[nowPosition - lastPosition - 2];
          } else {
            bs = new byte[nowPosition - lastPosition - 1];
          }

          buffer.position(lastPosition);
          buffer.get(bs);
          value = new String(bs);
          lastPosition = nowPosition;
          buffer.position(nowPosition);

          headers.put(name.toLowerCase(), StrUtil.trimEnd(value));
          needIteration = true;
          break;
          // return true;
        } else {
          if (!hasValue && b == SysConst.SPACE) {
            lastPosition = buffer.position();
          } else {
            hasValue = true;
          }
        }
      }
    }

    if (needIteration) {
      int myHeaderLength = buffer.position() - initPosition;
      if (myHeaderLength > MAX_LENGTH_OF_HEADER) {
        throw new TioDecodeException("header is too long");
      }
      return parseHeaderLine(buffer, headers, myHeaderLength + headerLength, httpConfig);
    }

    if (remaining > MAX_LENGTH_OF_HEADERLINE) {
      throw new TioDecodeException("header line is too long");
    }
    return false;
  }

  /**
   * parse request line(the first line)
   * @param line GET /tio?value=tanyaowu HTTP/1.1
   * @param channelContext
   * @return
   *
   * @author tanyaowu
   * 2017年2月23日 下午1:37:51
   *
   */
  public static RequestLine parseRequestLine(ByteBuffer buffer, ChannelContext channelContext)
      throws TioDecodeException {
    // if (!buffer.hasArray()) {
    // return parseRequestLine2(buffer, channelContext);
    // }

    byte[] allbs = buffer.array();

    int initPosition = buffer.position();

    // int remaining = buffer.remaining();
    String methodStr = null;
    String pathStr = null;
    String queryStr = null;
    String protocol = null;
    String version = null;
    int lastPosition = initPosition;// buffer.position();
    while (buffer.hasRemaining()) {
      byte b = buffer.get();
      if (methodStr == null) {
        if (b == SysConst.SPACE) {
          int len = buffer.position() - lastPosition - 1;
          methodStr = StrCache.get(allbs, lastPosition, len);
          // methodStr = new String(allbs, lastPosition, len);
          lastPosition = buffer.position();
        }
        // GET,POST,PUT,OPTIONS,没有http的方法名会超过10个字节
        if (lastPosition > 10) {
          return null;
        }
        continue;
      } else if (pathStr == null) {
        if (b == SysConst.SPACE || b == SysConst.ASTERISK) {
          int len = buffer.position() - lastPosition - 1;
          pathStr = StrCache.get(allbs, lastPosition, len);
          // pathStr = new String(allbs, lastPosition, len);
          lastPosition = buffer.position();

          if (b == SysConst.SPACE) {
            queryStr = com.litongjava.tio.utils.SysConst.BLANK;
          }
        }
        continue;
      } else if (queryStr == null) {
        if (b == SysConst.SPACE) {
          int len = buffer.position() - lastPosition - 1;
          queryStr = new String(allbs, lastPosition, len);
          lastPosition = buffer.position();
        }
        continue;
      } else if (protocol == null) {
        if (b == SysConst.BACKSLASH) {
          int len = buffer.position() - lastPosition - 1;
          protocol = StrCache.get(allbs, lastPosition, len);
          // protocol = new String(allbs, lastPosition, len);
          lastPosition = buffer.position();
        }
        continue;
      } else if (version == null) {
        if (b == SysConst.LF) {
          byte lastByte = buffer.get(buffer.position() - 2);
          int len = buffer.position() - lastPosition - 1;
          if (lastByte == SysConst.CR) {
            len = buffer.position() - lastPosition - 2;
          }

          version = StrCache.get(allbs, lastPosition, len);
          // version = new String(allbs, lastPosition, len);

          lastPosition = buffer.position();

          RequestLine requestLine = new RequestLine();
          Method method = Method.from(methodStr);
          requestLine.setMethod(method);
          requestLine.setPath(pathStr);
          requestLine.setInitPath(pathStr);
          requestLine.setQueryString(queryStr);
          requestLine.setProtocol(protocol);
          requestLine.setVersion(version);

          // requestLine.setLine(line);

          return requestLine;
        }
        continue;
      }
    }

    if ((buffer.position() - initPosition) > MAX_LENGTH_OF_REQUESTLINE) {
      throw new TioDecodeException("request line is too long");
    }
    return null;
  }

  @SuppressWarnings("unused")
  private static RequestLine parseRequestLine2(ByteBuffer buffer, ChannelContext channelContext)
      throws TioDecodeException {
    int initPosition = buffer.position();
    // int remaining = buffer.remaining();
    String methodStr = null;
    String pathStr = null;
    String queryStr = null;
    String protocol = null;
    String version = null;
    int lastPosition = initPosition;// buffer.position();
    while (buffer.hasRemaining()) {
      byte b = buffer.get();
      if (methodStr == null) {
        if (b == SysConst.SPACE) {
          int nowPosition = buffer.position();
          byte[] bs = new byte[nowPosition - lastPosition - 1];
          buffer.position(lastPosition);
          buffer.get(bs);
          methodStr = new String(bs);
          lastPosition = nowPosition;
          buffer.position(nowPosition);
        }
        continue;
      } else if (pathStr == null) {
        if (b == SysConst.SPACE || b == SysConst.ASTERISK) {
          int nowPosition = buffer.position();
          byte[] bs = new byte[nowPosition - lastPosition - 1];
          buffer.position(lastPosition);
          buffer.get(bs);
          pathStr = new String(bs);
          lastPosition = nowPosition;
          buffer.position(nowPosition);

          if (b == SysConst.SPACE) {
            queryStr = "";
          }
        }
        continue;
      } else if (queryStr == null) {
        if (b == SysConst.SPACE) {
          int nowPosition = buffer.position();
          byte[] bs = new byte[nowPosition - lastPosition - 1];
          buffer.position(lastPosition);
          buffer.get(bs);
          queryStr = new String(bs);
          lastPosition = nowPosition;
          buffer.position(nowPosition);
        }
        continue;
      } else if (protocol == null) {
        if (b == '/') {
          int nowPosition = buffer.position();
          byte[] bs = new byte[nowPosition - lastPosition - 1];
          buffer.position(lastPosition);
          buffer.get(bs);
          protocol = new String(bs);
          lastPosition = nowPosition;
          buffer.position(nowPosition);
        }
        continue;
      } else if (version == null) {
        if (b == SysConst.LF) {
          int nowPosition = buffer.position();
          byte[] bs = null;
          byte lastByte = buffer.get(nowPosition - 2);

          if (lastByte == SysConst.CR) {
            bs = new byte[nowPosition - lastPosition - 2];
          } else {
            bs = new byte[nowPosition - lastPosition - 1];
          }

          buffer.position(lastPosition);
          buffer.get(bs);
          version = new String(bs);
          lastPosition = nowPosition;
          buffer.position(nowPosition);

          RequestLine requestLine = new RequestLine();
          Method method = Method.from(methodStr);
          requestLine.setMethod(method);
          requestLine.setPath(pathStr);
          requestLine.setInitPath(pathStr);
          requestLine.setQueryString(queryStr);
          requestLine.setProtocol(protocol);
          requestLine.setVersion(version);

          // requestLine.setLine(line);

          return requestLine;
        }
        continue;
      }
    }

    if ((buffer.position() - initPosition) > MAX_LENGTH_OF_REQUESTLINE) {
      throw new TioDecodeException("request line is too long");
    }
    return null;
  }

  /**
   * 解析URLENCODED格式的消息体
   * 形如： 【Content-Type : application/x-www-form-urlencoded; charset=UTF-8】
   * @author tanyaowu
   * @throws TioDecodeException 
   */
  private static void parseUrlencoded(HttpRequest httpRequest, RequestLine firstLine, byte[] bodyBytes,
      String bodyString, ChannelContext channelContext) throws TioDecodeException {
    decodeParams(httpRequest.getParams(), bodyString, httpRequest.getCharset(), channelContext);
  }

  // /**
  // * 解析查询
  // * @param httpRequest
  // * @param requestLine
  // * @param channelContext
  // */
  // private static void parseQueryString(HttpRequest httpRequest, RequestLine requestLine, ChannelContext channelContext) {
  // String queryString = requestLine.getQueryString();
  // decodeParams(httpRequest.getParams(), queryString, httpRequest.getCharset(), channelContext);
  // }

  /**
   * @author tanyaowu
   * 2017年2月22日 下午4:06:42
   *
   */
  public HttpRequestDecoder() {

  }

}
