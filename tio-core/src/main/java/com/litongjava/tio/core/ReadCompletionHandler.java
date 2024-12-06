package com.litongjava.tio.core;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.tio.constants.TioCoreConfigKeys;
import com.litongjava.tio.core.ChannelContext.CloseCode;
import com.litongjava.tio.core.stat.IpStat;
import com.litongjava.tio.core.utils.ByteBufferUtils;
import com.litongjava.tio.core.utils.TioUtils;
import com.litongjava.tio.utils.SystemTimer;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.CollUtil;

/**
 *
 * @author tanyaowu 2017年4月4日 上午9:22:04
 */
public class ReadCompletionHandler implements CompletionHandler<Integer, ByteBuffer> {
  private static Logger log = LoggerFactory.getLogger(ReadCompletionHandler.class);
  private ChannelContext channelContext = null;

  /**
   *
   * @param channelContext
   * @author tanyaowu
   */
  public ReadCompletionHandler(ChannelContext channelContext) {
    this.channelContext = channelContext;
  }

  @Override
  public void completed(Integer result, ByteBuffer byteBuffer) {
    if (result > 0) {
      TioConfig tioConfig = channelContext.tioConfig;

      if (tioConfig.statOn) {
        tioConfig.groupStat.receivedBytes.addAndGet(result);
        tioConfig.groupStat.receivedTcps.incrementAndGet();

        channelContext.stat.receivedBytes.addAndGet(result);
        channelContext.stat.receivedTcps.incrementAndGet();
      }

      channelContext.stat.latestTimeOfReceivedByte = SystemTimer.currTime;

      if (CollUtil.isNotEmpty(tioConfig.ipStats.durationList)) {
        try {
          for (Long v : tioConfig.ipStats.durationList) {
            IpStat ipStat = tioConfig.ipStats.get(v, channelContext);
            ipStat.getReceivedBytes().addAndGet(result);
            ipStat.getReceivedTcps().incrementAndGet();
            tioConfig.getIpStatListener().onAfterReceivedBytes(channelContext, result, ipStat);
          }
        } catch (Exception e1) {
          log.error(channelContext.toString(), e1);
        }
      }

      if (tioConfig.getAioListener() != null) {
        try {
          tioConfig.getAioListener().onAfterReceivedBytes(channelContext, result);
        } catch (Exception e) {
          log.error("", e);
        }
      }

      byteBuffer.flip();
      if (channelContext.sslFacadeContext == null) {
        if (tioConfig.useQueueDecode) {
          channelContext.decodeRunnable.addMsg(ByteBufferUtils.copy(byteBuffer));
          channelContext.decodeRunnable.execute();
        } else {
          channelContext.decodeRunnable.setNewReceivedByteBuffer(byteBuffer);
          channelContext.decodeRunnable.decode();
        }
      } else {
        ByteBuffer copiedByteBuffer = null;
        try {
          copiedByteBuffer = ByteBufferUtils.copy(byteBuffer);
          log.debug("{}, 丢给SslFacade解密:{}", channelContext, copiedByteBuffer);
          channelContext.sslFacadeContext.getSslFacade().decrypt(copiedByteBuffer);
        } catch (Exception e) {
          log.error(channelContext + ", " + e.toString() + copiedByteBuffer, e);
          Tio.close(channelContext, e, e.toString(), CloseCode.SSL_DECRYPT_ERROR);
        }
      }

      if (TioUtils.checkBeforeIO(channelContext)) {
        read(byteBuffer);
      }

    } else if (result == 0) {
      String message = "The length of the read data is 0";
      log.error("close {}, because {}", channelContext, message);
      Tio.close(channelContext, null, message, CloseCode.READ_COUNT_IS_ZERO);
      return;
    } else if (result < 0) {
      if (result == -1) {
        String message = "The connection closed by peer";
        if (EnvUtils.getBoolean(TioCoreConfigKeys.TIO_CORE_DIAGNOSTIC, false)) {
          log.info("close {}, because {}", channelContext, message);
        }
        Tio.close(channelContext, null, message, CloseCode.CLOSED_BY_PEER);
        return;
      } else {
        String message = "The length of the read data is less than -1";
        Tio.close(channelContext, null, "读数据时返回" + result, CloseCode.READ_COUNT_IS_NEGATIVE);
        log.error("close {}, because {}", channelContext, message);
        return;
      }
    }
  }

  private void read(ByteBuffer readByteBuffer) {
    if (readByteBuffer.capacity() == channelContext.getReadBufferSize()) {
      readByteBuffer.position(0);
      readByteBuffer.limit(readByteBuffer.capacity());
    } else {
      readByteBuffer = ByteBuffer.allocate(channelContext.getReadBufferSize());
    }

    channelContext.asynchronousSocketChannel.read(readByteBuffer, readByteBuffer, this);
  }

  /**
   *
   * @param exc
   * @param byteBuffer
   * @author tanyaowu
   */
  @Override
  public void failed(Throwable exc, ByteBuffer byteBuffer) {
    Tio.close(channelContext, exc, "Failed to read data: " + exc.getClass().getName(), CloseCode.READ_ERROR);
  }
}
