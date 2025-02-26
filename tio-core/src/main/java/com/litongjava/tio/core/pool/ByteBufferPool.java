package com.litongjava.tio.core.pool;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.LinkedBlockingQueue;

public class ByteBufferPool {

  public static int cpuNum = Runtime.getRuntime().availableProcessors();
  public static final ByteBufferPool BUFFER_POOL = new ByteBufferPool(4096 * cpuNum, 8192);

  private final LinkedBlockingQueue<ByteBuffer> pool;
  private final int bufferSize;

  public ByteBufferPool(int poolSize, int bufferSize) {
    this.pool = new LinkedBlockingQueue<>(poolSize);
    this.bufferSize = bufferSize;
    for (int i = 0; i < poolSize; i++) {
      pool.offer(ByteBuffer.allocateDirect(bufferSize));
    }
  }

  public ByteBuffer acquire() {
    ByteBuffer buffer = pool.poll();
    if (buffer == null) {
      // 当池中无可用缓冲区时，临时分配
      return ByteBuffer.allocate(bufferSize);
    }
    buffer.clear();
    return buffer;
  }

  public ByteBuffer acquire(ByteOrder order) {
    ByteBuffer buffer = pool.poll();
    if (buffer == null) {
      // 当池中无可用缓冲区时，临时分配
      ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
      byteBuffer.order(order);
      return byteBuffer;
    }
    buffer.clear();
    buffer.order(order);
    return buffer;
  }

  public void release(ByteBuffer buffer) {
    if (buffer != null) {
      buffer.clear();
      pool.offer(buffer);
    }
  }
}
