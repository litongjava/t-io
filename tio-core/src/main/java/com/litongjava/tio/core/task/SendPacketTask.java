package com.litongjava.tio.core.task;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLException;

import com.litongjava.aio.Packet;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.ChannelContext.CloseCode;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.core.TioConfig;
import com.litongjava.tio.core.WriteCompletionHandler.WriteCompletionVo;
import com.litongjava.tio.core.intf.AioHandler;
import com.litongjava.tio.core.ssl.SslUtils;
import com.litongjava.tio.core.ssl.SslVo;
import com.litongjava.tio.core.utils.TioUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SendPacketTask {

  public boolean canSend = true;
  private ChannelContext channelContext = null;
  private TioConfig tioConfig = null;
  private AioHandler aioHandler = null;
  private boolean isSsl = false;

  public SendPacketTask(ChannelContext channelContext) {
    this.channelContext = channelContext;
    this.tioConfig = channelContext.tioConfig;
    this.aioHandler = tioConfig.getAioHandler();
    this.isSsl = SslUtils.isSsl(tioConfig);
  }

  private ByteBuffer getByteBuffer(Packet packet) {
    ByteBuffer byteBuffer = packet.getPreEncodedByteBuffer();
    try {
      if (byteBuffer == null) {
        byteBuffer = aioHandler.encode(packet, tioConfig, channelContext);
      }

      if (!byteBuffer.hasRemaining()) {
        byteBuffer.flip();
      }
      return byteBuffer;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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
          log.error(channelContext.toString() + ", An exception occurred while performing SSL encryption", e);
          Tio.close(channelContext, "An exception occurred during SSL encryption.", CloseCode.SSL_ENCRYPTION_ERROR);
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
  private void sendByteBuffer(ByteBuffer byteBuffer, Object packets) {
    if (byteBuffer == null) {
      log.error("{},byteBuffer is null", channelContext);
      return;
    }

    if (!TioUtils.checkBeforeIO(channelContext)) {
      return;
    }

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

}
