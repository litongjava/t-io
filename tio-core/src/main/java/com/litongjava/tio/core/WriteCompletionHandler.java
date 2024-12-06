package com.litongjava.tio.core;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.aio.Packet;
import com.litongjava.aio.Packet.Meta;
import com.litongjava.tio.core.ChannelContext.CloseCode;
import com.litongjava.tio.core.WriteCompletionHandler.WriteCompletionVo;
import com.litongjava.tio.core.stat.IpStat;
import com.litongjava.tio.utils.SystemTimer;
import com.litongjava.tio.utils.hutool.CollUtil;

/**
 *
 * @author tanyaowu
 *
 */
public class WriteCompletionHandler implements CompletionHandler<Integer, WriteCompletionVo> {
  private static Logger log = LoggerFactory.getLogger(WriteCompletionHandler.class);
  private ChannelContext channelContext = null;
  public final ReentrantLock lock = new ReentrantLock();
  public final Condition condition = lock.newCondition();

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
      this.byteBuffer = byteBuffer; // [pos=0 lim=212 cap=212]
      this.obj = obj;
    }
  }

  public WriteCompletionHandler(ChannelContext channelContext) {
    this.channelContext = channelContext;
  }

  @Override
  public void completed(Integer bytesWritten, WriteCompletionVo writeCompletionVo) {
    // Object attachment = writeCompletionVo.getObj();
    if (bytesWritten > 0) {
      channelContext.stat.latestTimeOfSentByte = SystemTimer.currTime;
    }
    if (writeCompletionVo.byteBuffer.hasRemaining()) {
      if (log.isInfoEnabled()) {
        log.info("{} {}/{} has sent", channelContext, writeCompletionVo.byteBuffer.position(), writeCompletionVo.byteBuffer.limit());
      }
      channelContext.asynchronousSocketChannel.write(writeCompletionVo.byteBuffer, writeCompletionVo, this);
    } else {
      handle(bytesWritten, null, writeCompletionVo);
    }
  }

  @Override
  public void failed(Throwable throwable, WriteCompletionVo writeCompletionVo) {
    // Object attachment = writeCompletionVo.getObj();
    handle(0, throwable, writeCompletionVo);
  }

  /**
   * 
   * @param bytesWritten
   * @param throwable
   * @param writeCompletionVo
   * @author tanyaowu
   */
  public void handle(Integer bytesWritten, Throwable throwable, WriteCompletionVo writeCompletionVo) {
    ReentrantLock lock = channelContext.writeCompletionHandler.lock;
    lock.lock();
    try {
      channelContext.writeCompletionHandler.condition.signal();
      channelContext.stat.latestTimeOfSentPacket = SystemTimer.currTime;
      Object attachment = writeCompletionVo.obj;// ();
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
          Tio.close(channelContext, throwable, "写数据返回:" + bytesWritten, CloseCode.WRITE_COUNT_IS_NEGATIVE);
        }
      } catch (Throwable e) {
        log.error(e.toString(), e);
      }

    } finally {
      lock.unlock();
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
    Meta meta = packet.getMeta();
    if (meta != null) {
      meta.setIsSentSuccess(isSentSuccess);
    }

    try {
      channelContext.processAfterSent(packet, isSentSuccess);
//      if (!packet.isKeepConnection()) {
//        String msg = "remove conneciton because KeepedConnection is false:" + packet.logstr();
//        if (EnvUtils.getBoolean(TioCoreConfigKeys.TIO_CORE_DIAGNOSTIC, false)) {
//          log.info(msg);
//        }
//        Tio.close(channelContext, msg);
//      }
    } catch (Throwable e) {
      log.error(e.toString(), e);
    }
  }
}
