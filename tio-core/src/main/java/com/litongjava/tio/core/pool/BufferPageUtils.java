package com.litongjava.tio.core.pool;

import com.litongjava.enhance.buffer.BufferPage;
import com.litongjava.enhance.buffer.BufferPagePool;
import com.litongjava.enhance.buffer.VirtualBuffer;

public class BufferPageUtils {

  public static int cpuNum = Runtime.getRuntime().availableProcessors();
  public static BufferPagePool pool = new BufferPagePool(0, 4096 * cpuNum, true);
  public static BufferPage bufferPage = pool.allocateBufferPage();

  public static VirtualBuffer allocate(Integer readBufferSize) {
    return bufferPage.allocate(readBufferSize);

  }
}
