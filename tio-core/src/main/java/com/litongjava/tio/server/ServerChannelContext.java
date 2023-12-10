package com.litongjava.tio.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;

import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Node;
import com.litongjava.tio.core.TioConfig;

/**
 *
 * @author tanyaowu
 *
 */
public class ServerChannelContext extends ChannelContext {

  /**
   * @param tioConfig
   * @param asynchronousSocketChannel
   *
   * @author tanyaowu
   * 2016年12月6日 下午12:17:59
   *
   */
  public ServerChannelContext(TioConfig tioConfig, AsynchronousSocketChannel asynchronousSocketChannel) {
    super(tioConfig, asynchronousSocketChannel);
  }

  /**
   * 创建一个虚拟ChannelContext，主要用来模拟一些操作，真实场景中用得少
   * @param tioConfig
   */
  public ServerChannelContext(TioConfig tioConfig) {
    super(tioConfig);
  }

  /**
   * 创建一个虚拟ChannelContext，主要用来模拟一些操作，譬如压力测试，真实场景中用得少
   * @param tioConfig
   * @param id ChannelContext id
   * @author tanyaowu
   */
  public ServerChannelContext(TioConfig tioConfig, String id) {
    super(tioConfig, id);
  }

  /**
   * @see org.tio.core.ChannelContext#createClientNode(java.nio.channels.AsynchronousSocketChannel)
   *
   * @param asynchronousSocketChannel
   * @return
   * @throws IOException
   * @author tanyaowu
   * 2016年12月6日 下午12:18:08
   *
   */
  @Override
  public Node createClientNode(AsynchronousSocketChannel asynchronousSocketChannel) throws IOException {
    InetSocketAddress inetSocketAddress = (InetSocketAddress) asynchronousSocketChannel.getRemoteAddress();
    Node clientNode = new Node(inetSocketAddress.getHostString(), inetSocketAddress.getPort());
    return clientNode;
  }

  /** 
   * @return
   * @author tanyaowu
   */
  @Override
  public boolean isServer() {
    return true;
  }

}
