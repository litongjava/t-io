package com.litongjava.tio.core;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.litongjava.aio.Packet;
import com.litongjava.aio.PacketMeta;
import com.litongjava.tio.constants.TioCoreConfigKeys;
import com.litongjava.tio.core.ChannelContext.CloseCode;
import com.litongjava.tio.core.WriteCompletionHandler.WriteCompletionVo;
import com.litongjava.tio.core.stat.IpStat;
import com.litongjava.tio.core.task.SendPacketTask;
import com.litongjava.tio.utils.SystemTimer;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.hutool.CollUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * @author tanyaowu
 */
@Slf4j
public class WriteCompletionHandler implements CompletionHandler<Integer, WriteCompletionVo> {
  private final static boolean DIAGNOSTIC_LOG_ENABLED = EnvUtils.getBoolean(TioCoreConfigKeys.TIO_CORE_DIAGNOSTIC, false);
  private ChannelContext channelContext = null;
  public final ReentrantLock lock = new ReentrantLock();
  public final Condition condition = lock.newCondition();

  public WriteCompletionHandler(ChannelContext channelContext) {
    this.channelContext = channelContext;
  }

  public static class WriteCompletionVo {
    private ByteBuffer byteBuffer = null;

    private Object obj = null;

    /**
     * @param byteBuffer
     * @param obj
     * @author tanyaowu
     */
    public WriteCompletionVo(ByteBuffer byteBuffer, Object obj) {
      super();
      this.byteBuffer = byteBuffer;
      this.obj = obj;
    }
  }

  @Override

  public void completed(Integer bytesWritten, WriteCompletionVo writeCompletionVo) {
    if (EnvUtils.getBoolean(TioCoreConfigKeys.TIO_CORE_DIAGNOSTIC, false)) {
      log.info("write:{},{}", channelContext.getClientNode(), bytesWritten);
    }

    if (bytesWritten > 0) {
      channelContext.stat.latestTimeOfSentByte = SystemTimer.currTime;
    }
    if (writeCompletionVo.byteBuffer.hasRemaining()) {
      channelContext.asynchronousSocketChannel.write(writeCompletionVo.byteBuffer, writeCompletionVo, this);
    } else {
      handle(bytesWritten, null, writeCompletionVo);
    }
    processNextPacket(channelContext);
  }

  @Override
  public void failed(Throwable throwable, WriteCompletionVo writeCompletionVo) {
    handle(0, throwable, writeCompletionVo);
    processNextPacket(channelContext);
  }

  private void processNextPacket(ChannelContext channelContext) {
    channelContext.isSending.set(false);
    // 递归处理队列中的下一个包
    new SendPacketTask(channelContext).processSendQueue();
  }

  /**
   * 
   * @param bytesWritten
   * @param throwable
   * @param writeCompletionVo
   * @author tanyaowu
   */
  public void handle(Integer bytesWritten, Throwable throwable, WriteCompletionVo writeCompletionVo) {
    channelContext.stat.latestTimeOfSentPacket = SystemTimer.currTime;
    Object attachment = writeCompletionVo.obj;
    TioConfig tioConfig = channelContext.tioConfig;
    boolean isSentSuccess = bytesWritten > 0;
    if (isSentSuccess) {
      if (tioConfig.statOn) {
        tioConfig.groupStat.sentBytes.addAndGet(bytesWritten);
        channelContext.stat.sentBytes.addAndGet(bytesWritten);
      }

      if (CollUtil.isNotEmpty(tioConfig.ipStats.durationList)) {
        for (Long v : tioConfig.ipStats.durationList) {
          IpStat ipStat = (IpStat) channelContext.tioConfig.ipStats.get(v, channelContext);
          ipStat.getSentBytes().addAndGet(bytesWritten);
        }
      }
    }

    try {
      boolean isPacket = attachment instanceof Packet;
      if (isPacket) {
        if (isSentSuccess) {
          if (CollUtil.isNotEmpty(tioConfig.ipStats.durationList)) {
            for (Long v : tioConfig.ipStats.durationList) {
              IpStat ipStat = (IpStat) channelContext.tioConfig.ipStats.get(v, channelContext);
              ipStat.getSentPackets().incrementAndGet();
            }
          }
        }
        handleOne(bytesWritten, throwable, (Packet) attachment, isSentSuccess);
      } else {
        List<?> ps = (List<?>) attachment;
        for (Object obj : ps) {
          handleOne(bytesWritten, throwable, (Packet) obj, isSentSuccess);
        }
      }

      if (!isSentSuccess) {
        Tio.close(channelContext, throwable, "Write data return:" + bytesWritten, CloseCode.WRITE_COUNT_IS_NEGATIVE);
      }
    } catch (Throwable e) {
      log.error(e.toString(), e);
    }

  }

  /**
   * 
   * @param result
   * @param throwable
   * @param packet
   * @param isSentSuccess
   * @author tanyaowu
   */
  public void handleOne(Integer result, Throwable throwable, Packet packet, Boolean isSentSuccess) {
    PacketMeta meta = packet.getMeta();
    if (meta != null) {
      meta.setIsSentSuccess(isSentSuccess);
      if (meta.getCountDownLatch() != null) {
        meta.getCountDownLatch().countDown();
      }
    }
    try {
      channelContext.processAfterSent(packet, isSentSuccess);
    } catch (Throwable e) {
      log.error(e.toString(), e);
    }

    if (!packet.isKeepConnection()) {
      String msg = "remove conneciton because KeepedConnection is false:" + packet.logstr();
      if (DIAGNOSTIC_LOG_ENABLED) {
        log.info(msg);
      }
      Tio.close(channelContext, msg);
    }

  }
}
