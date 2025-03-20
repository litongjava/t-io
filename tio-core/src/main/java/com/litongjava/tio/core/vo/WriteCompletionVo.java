package com.litongjava.tio.core.vo;

import java.nio.ByteBuffer;

import lombok.Data;

@Data
public class WriteCompletionVo {
  private ByteBuffer byteBuffer = null;
  private Object obj = null;
  public WriteCompletionVo(ByteBuffer byteBuffer, Object obj) {
    this.byteBuffer = byteBuffer;
    this.obj = obj;
  }
}