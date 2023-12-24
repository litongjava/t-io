package com.litongjava.tio.http.server.sse;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.litongjava.tio.core.TioConfig;
import com.litongjava.tio.core.intf.Packet;

public class SsePacket extends Packet {
  private static final long serialVersionUID = 1L;
  private Charset charset = StandardCharsets.UTF_8;
  private String eventId;
  private String name;
  private String data;

  public SsePacket() {
    super();
  }

  public SsePacket(String id, String name, String data) {
    super();
    this.eventId = id;
    this.name = name;
    this.data = data;
  }

  public SsePacket(Charset charset, String id, String name, String data) {
    super();
    this.charset = charset;
    this.eventId = id;
    this.name = name;
    this.data = data;
  }

  public SsePacket eventId(String id) {
    this.eventId = id;
    return this;
  }

  public SsePacket name(String name) {
    this.name = name;
    return this;
  }

  public SsePacket data(String data) {
    this.data = data;
    return this;
  }

  public Charset getCharset() {
    return charset;
  }

  public void setCharset(Charset charset) {
    this.charset = charset;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String id) {
    this.eventId = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getData() {
    return data;
  }

  public void setData(String data) {
    this.data = data;
  }

  public ByteBuffer toByteBuffer(TioConfig tioConfig) {
    // String message = "id:2\n" + "event:message\n" + "data:This is message 1" + "\n\n";
    StringBuffer stringBuffer = new StringBuffer();
    if (eventId != null) {
      stringBuffer.append("id:").append(eventId + "\n");
    }
    if (name != null) {
      stringBuffer.append("name:").append(name + "\n");
    }
    if (data != null) {
      stringBuffer.append("data:").append(data + "\n\n");
    }

    byte[] bytes = stringBuffer.toString().getBytes();

    // ByteBuffer的总长度是消息体长度
    int bodyLength = bytes.length;

    // 创建一个新的ByteBuffer
    ByteBuffer buffer = ByteBuffer.allocate(bodyLength);
    // 设置字节序
    buffer.order(tioConfig.getByteOrder());
    // 消息消息体
    buffer.put(bytes);
    return buffer;
  }

}
