package com.litongjava.tio.core.ssl;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.aio.Packet;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.ssl.facade.IHandshakeCompletedListener;
import com.litongjava.tio.core.task.AfterSslHandshakeCompleted;
import com.litongjava.tio.core.task.SendPacketTask;

/**
 * @author tanyaowu
 *
 */
public class SslHandshakeCompletedListener implements IHandshakeCompletedListener {
  private static Logger log = LoggerFactory.getLogger(SslHandshakeCompletedListener.class);

  private ChannelContext channelContext;

  /**
   * 
   */
  public SslHandshakeCompletedListener(ChannelContext channelContext) {
    this.channelContext = channelContext;
  }

  @Override
  public void onComplete() {
    log.info("{}, Complete SSL handshake", channelContext);
    channelContext.sslFacadeContext.setHandshakeCompleted(true);

    if (channelContext.tioConfig.getAioListener() != null) {
      try {
        channelContext.tioConfig.getAioListener().onAfterConnected(channelContext, true, channelContext.isReconnect);
      } catch (Exception e) {
        log.error(e.toString(), e);
      }
    }

    ConcurrentLinkedQueue<Packet> forSendAfterSslHandshakeCompleted = new AfterSslHandshakeCompleted().getForSendAfterSslHandshakeCompleted(false);
    if (forSendAfterSslHandshakeCompleted == null || forSendAfterSslHandshakeCompleted.size() == 0) {
      return;
    }

    log.info("{} There are {} data items pending transmission at the business layer before the SSL handshake", channelContext, forSendAfterSslHandshakeCompleted.size());
    while (true) {
      Packet packet = forSendAfterSslHandshakeCompleted.poll();
      if (packet != null) {
        new SendPacketTask(channelContext).sendPacket(packet);
      } else {
        break;
      }
    }
  }

}
