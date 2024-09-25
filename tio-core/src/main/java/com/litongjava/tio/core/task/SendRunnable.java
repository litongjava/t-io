package com.litongjava.tio.core.task;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.aio.Packet;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.ChannelContext.CloseCode;
import com.litongjava.tio.core.TcpConst;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.core.TioConfig;
import com.litongjava.tio.core.WriteCompletionHandler.WriteCompletionVo;
import com.litongjava.tio.core.intf.AioHandler;
import com.litongjava.tio.core.ssl.SslUtils;
import com.litongjava.tio.core.ssl.SslVo;
import com.litongjava.tio.core.utils.TioUtils;
import com.litongjava.tio.utils.queue.FullWaitQueue;
import com.litongjava.tio.utils.queue.TioFullWaitQueue;
import com.litongjava.tio.utils.thread.pool.AbstractQueueRunnable;

/**
 *
 * @author tanyaowu
 * 2017年4月4日 上午9:19:18
 */
public class SendRunnable extends AbstractQueueRunnable<Packet> {
  private static final Logger log = LoggerFactory.getLogger(SendRunnable.class);
  private ChannelContext channelContext = null;
  private TioConfig tioConfig = null;
  private AioHandler aioHandler = null;
  private boolean isSsl = false;
  /** The msg queue. */
  private ConcurrentLinkedQueue<Packet> forSendAfterSslHandshakeCompleted = null; // new ConcurrentLinkedQueue<>();
  public boolean canSend = true;

  public ConcurrentLinkedQueue<Packet> getForSendAfterSslHandshakeCompleted(boolean forceCreate) {
    if (forSendAfterSslHandshakeCompleted == null && forceCreate) {
      synchronized (this) {
        if (forSendAfterSslHandshakeCompleted == null) {
          forSendAfterSslHandshakeCompleted = new ConcurrentLinkedQueue<>();
        }
      }
    }

    return forSendAfterSslHandshakeCompleted;
  }

  // SSL加密锁
  // private Object sslEncryptLock = new Object();

  /**
   *
   * @param channelContext
   * @param executor
   * @author tanyaowu
   */
  public SendRunnable(ChannelContext channelContext, Executor executor) {
    super(executor);
    this.channelContext = channelContext;
    this.tioConfig = channelContext.tioConfig;
    this.aioHandler = tioConfig.getAioHandler();
    this.isSsl = SslUtils.isSsl(tioConfig);
    getMsgQueue();
  }

  @Override
  public boolean addMsg(Packet packet) {
    if (this.isCanceled()) {
      log.info("{}, The mission has been canceled，{} Failed to add to the send queue. Procedure", channelContext, packet.logstr());
      return false;
    }

    if (channelContext.sslFacadeContext != null && !channelContext.sslFacadeContext.isHandshakeCompleted() && SslUtils.needSslEncrypt(packet, tioConfig)) {
      return this.getForSendAfterSslHandshakeCompleted(true).add(packet);
    } else {
      return msgQueue.add(packet);
    }
  }

  /**
   * 清空消息队列
   */
  @Override
  public void clearMsgQueue() {
    Packet p = null;
    forSendAfterSslHandshakeCompleted = null;
    while ((p = msgQueue.poll()) != null) {
      try {
        channelContext.processAfterSent(p, false);
      } catch (Throwable e) {
        log.error(e.toString(), e);
      }
    }
  }

  private ByteBuffer getByteBuffer(Packet packet) {
    try {
      ByteBuffer byteBuffer = packet.getPreEncodedByteBuffer();
      if (byteBuffer == null) {
        byteBuffer = aioHandler.encode(packet, tioConfig, channelContext);
      }

      if (!byteBuffer.hasRemaining()) {
        byteBuffer.flip();
      }
      return byteBuffer;
    } catch (Exception e) {
      log.error(packet.logstr(), e);
      throw new RuntimeException(e);
    }
  }

  /**
   * 新旧值是否进行了切换
   * @param oldValue
   * @param newValue
   * @return
   */

  //减掉1024是尽量防止溢出的一小部分还分成一个tcp包发出
  private static final int MAX_CAPACITY_MIN = TcpConst.MAX_DATA_LENGTH - 1024;
  private static final int MAX_CAPACITY_MAX = MAX_CAPACITY_MIN * 10;

  @Override
  public void runTask() {
    if (msgQueue.isEmpty()) {
      return;
    }

    int queueSize = msgQueue.size();
    if (queueSize == 1) {
      // System.out.println(1);
      Packet packet = msgQueue.poll();
      if (packet != null) {
        sendPacket(packet);
      }
      return;
    }

    int listInitialCapacity = Math.min(queueSize, canSend ? 300 : 1000);

    Packet packet = null;
    List<Packet> packets = new ArrayList<>(listInitialCapacity);
    List<ByteBuffer> byteBuffers = new ArrayList<>(listInitialCapacity);
    // int packetCount = 0;
    int allBytebufferCapacity = 0;
    Boolean needSslEncrypted = null;
    boolean sslChanged = false;
    while ((packet = msgQueue.poll()) != null) {
      ByteBuffer byteBuffer = getByteBuffer(packet);

      packets.add(packet);
      byteBuffers.add(byteBuffer);
      // packetCount++;
      allBytebufferCapacity += byteBuffer.limit();

      if (isSsl) {
        boolean _needSslEncrypted = !packet.isSslEncrypted();
        if (needSslEncrypted == null) {
          sslChanged = false;
        } else {
          sslChanged = needSslEncrypted != _needSslEncrypted;
        }
        needSslEncrypted = _needSslEncrypted;
      }

      if ((canSend && allBytebufferCapacity >= MAX_CAPACITY_MIN) || (allBytebufferCapacity >= MAX_CAPACITY_MAX) || sslChanged) {
        break;
      }
    }

    // System.out.println(packets.size());
    if (allBytebufferCapacity == 0) {
      return;
    }
    ByteBuffer allByteBuffer = ByteBuffer.allocate(allBytebufferCapacity);
    for (ByteBuffer byteBuffer : byteBuffers) {
      allByteBuffer.put(byteBuffer);
    }

    allByteBuffer.flip();

    if (isSsl && needSslEncrypted) {
      SslVo sslVo = new SslVo(allByteBuffer, packets);
      try {
        channelContext.sslFacadeContext.getSslFacade().encrypt(sslVo);
        allByteBuffer = sslVo.getByteBuffer();
      } catch (SSLException e) {
        log.error(channelContext.toString() + ", 进行SSL加密时发生了异常", e);
        Tio.close(channelContext, "进行SSL加密时发生了异常", CloseCode.SSL_ENCRYPTION_ERROR);
        return;
      }
    }

    this.sendByteBuffer(allByteBuffer, packets);
    // queueSize = msgQueue.size();
    // if (queueSize > 0) {
    // repeatCount++;
    // if (repeatCount < 3) {
    // runTask();
    // return;
    // }
    // }
    // repeatCount = 0;
  }

  public boolean sendPacket(Packet packet) {
    ByteBuffer byteBuffer = getByteBuffer(packet);
    if (isSsl) {
      if (!packet.isSslEncrypted()) {
        SslVo sslVo = new SslVo(byteBuffer, packet);
        try {
          channelContext.sslFacadeContext.getSslFacade().encrypt(sslVo);
          byteBuffer = sslVo.getByteBuffer();
        } catch (SSLException e) {
          log.error(channelContext.toString() + ", 进行SSL加密时发生了异常", e);
          Tio.close(channelContext, "进行SSL加密时发生了异常", CloseCode.SSL_ENCRYPTION_ERROR);
          return false;
        }
      }
    }

    sendByteBuffer(byteBuffer, packet);
    return true;
  }

  /**
   *
   * @param byteBuffer
   * @param packets Packet or List<Packet>
   * @author tanyaowu
   */
  public void sendByteBuffer(ByteBuffer byteBuffer, Object packets) {
    if (byteBuffer == null) {
      log.error("{},byteBuffer is null", channelContext);
      return;
    }

    if (!TioUtils.checkBeforeIO(channelContext)) {
      return;
    }

    // if (!byteBuffer.hasRemaining()) {
    // byteBuffer.flip();
    // }

    ReentrantLock lock = channelContext.writeCompletionHandler.lock;
    lock.lock();
    try {
      canSend = false;
      WriteCompletionVo writeCompletionVo = new WriteCompletionVo(byteBuffer, packets);
      channelContext.asynchronousSocketChannel.write(byteBuffer, writeCompletionVo, channelContext.writeCompletionHandler);
      channelContext.writeCompletionHandler.condition.await();
    } catch (InterruptedException e) {
      log.error(e.toString(), e);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + ":" + channelContext.toString();
  }

  /** 
   * @return
   * @author tanyaowu
   */
  @Override
  public String logstr() {
    return toString();
  }

  /** The msg queue. */
  private FullWaitQueue<Packet> msgQueue = null;

  @Override
  public FullWaitQueue<Packet> getMsgQueue() {
    if (msgQueue == null) {
      synchronized (this) {
        if (msgQueue == null) {
          msgQueue = new TioFullWaitQueue<Packet>(Integer.getInteger("tio.fullqueue.capacity", null), false);
        }
      }
    }
    return msgQueue;
  }

}
