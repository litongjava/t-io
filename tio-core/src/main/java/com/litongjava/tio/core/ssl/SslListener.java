package com.litongjava.tio.core.ssl;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.aio.Packet;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.ssl.facade.ISSLListener;
import com.litongjava.tio.core.utils.ByteBufferUtils;

/**
 * @author tanyaowu
 *
 */
public class SslListener implements ISSLListener {
  private static Logger log = LoggerFactory.getLogger(SslListener.class);

  private ChannelContext channelContext = null;

  /**
   * 
   */
  public SslListener(ChannelContext channelContext) {
    this.channelContext = channelContext;
  }

  @Override
  public void onWrappedData(SslVo sslVo) {
    // Send these bytes via your host application's transport
    log.info("{}, 收到SSL加密后的数据，准备发送出去，{}", channelContext, sslVo);
    // Packet packet = new Packet();
    // packet.setPreEncodedByteBuffer(wrappedBytes);
    // packet.setSslEncrypted(true);
    // Tio.send(channelContext, packet);

    Object obj = sslVo.getObj();
    if (obj == null) { // 如果是null，则是握手尚未完成时的数据
      Packet p = new Packet();

      p.setPreEncodedByteBuffer(sslVo.getByteBuffer());
      p.setSslEncrypted(true);
      // p.setByteCount(sslVo.getByteBuffer().limit());

      // log.info("clone packet:{}", Json.toJson(obj));

      if (channelContext.tioConfig.useQueueSend) {
        boolean isAdded = channelContext.sendRunnable.addMsg(p);
        if (isAdded) {
          channelContext.sendRunnable.execute();
        }
      } else {
        channelContext.sendRunnable.sendPacket(p);
      }

    }

  }

  @Override
  public void onPlainData(ByteBuffer plainBuffer) {
    // This is the deciphered payload for your app to consume
    // ByteBuffer plainBytes = sslVo.getByteBuffer();
    SslFacadeContext sslFacadeContext = channelContext.sslFacadeContext;
    // plainBytes:java.nio.HeapByteBuffer[pos=0 lim=507 cap=507]

    if (sslFacadeContext.isHandshakeCompleted()) {
      log.info("{}, After receiving the data decrypted by SSL, the SSL handshake is complete and ready to be decoded，{}, isSSLHandshakeCompleted {}", channelContext, plainBuffer,
          sslFacadeContext.isHandshakeCompleted());
      // plainBytes.flip();
      // channelContext.decodeRunnable.setNewByteBuffer(plainBuffer);
      // channelContext.decodeRunnable.run();

      if (channelContext.tioConfig.useQueueDecode) {
        ByteBuffer copiedByteBuffer = ByteBufferUtils.copy(plainBuffer);
        channelContext.decodeRunnable.addMsg(copiedByteBuffer);
        channelContext.decodeRunnable.execute();
      } else {
        channelContext.decodeRunnable.setNewReceivedByteBuffer(plainBuffer);
        channelContext.decodeRunnable.decode();
      }
    } else {
      log.info("{}, SSL decrypted data is received, but the SSL handshake is not complete，{}, isSSLHandshakeCompleted {}", channelContext, plainBuffer,
          sslFacadeContext.isHandshakeCompleted());
    }
  }

}
