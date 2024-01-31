package com.litongjava.tio.server;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.litongjava.tio.core.Node;
import com.litongjava.tio.utils.JvmUtils;
import com.litongjava.tio.utils.SysConst;
import com.litongjava.tio.utils.hutool.DateUtil;
import com.litongjava.tio.utils.hutool.StrUtil;

/**
 * @author tanyaowu
 *
 */
public class TioServer {
  private static Logger log = LoggerFactory.getLogger(TioServer.class);
  private ServerTioConfig serverTioConfig;
  private AsynchronousServerSocketChannel serverSocketChannel;
  private AsynchronousChannelGroup channelGroup = null;
  private Node serverNode;
  private boolean isWaitingStop = false;
  private boolean checkLastVersion = true;

  /**
   *
   * @param serverTioConfig
   *
   * @author tanyaowu
   * 2017年1月2日 下午5:53:06
   *
   */
  public TioServer(ServerTioConfig serverTioConfig) {
    super();
    this.serverTioConfig = serverTioConfig;
  }

  /**
   * @return the serverTioConfig
   */
  public ServerTioConfig getServerTioConfig() {
    return serverTioConfig;
  }

  /**
   * @return the serverNode
   */
  public Node getServerNode() {
    return serverNode;
  }

  /**
   * @return the serverSocketChannel
   */
  public AsynchronousServerSocketChannel getServerSocketChannel() {
    return serverSocketChannel;
  }

  /**
   * @return the isWaitingStop
   */
  public boolean isWaitingStop() {
    return isWaitingStop;
  }

  /**
   * @param serverTioConfig the serverTioConfig to set
   */
  public void setServerTioConfig(ServerTioConfig serverTioConfig) {
    this.serverTioConfig = serverTioConfig;
  }

  /**
   * @param isWaitingStop the isWaitingStop to set
   */
  public void setWaitingStop(boolean isWaitingStop) {
    this.isWaitingStop = isWaitingStop;
  }

  public void start(String serverIp, int serverPort) throws IOException {
    long start = System.currentTimeMillis();
    this.serverNode = new Node(serverIp, serverPort);
    channelGroup = AsynchronousChannelGroup.withThreadPool(serverTioConfig.groupExecutor);
    serverSocketChannel = AsynchronousServerSocketChannel.open(channelGroup);

    serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
    serverSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 64 * 1024);

    InetSocketAddress listenAddress = null;

    if (StrUtil.isBlank(serverIp)) {
      listenAddress = new InetSocketAddress(serverPort);
    } else {
      listenAddress = new InetSocketAddress(serverIp, serverPort);
    }

    serverSocketChannel.bind(listenAddress, 0);

    AcceptCompletionHandler acceptCompletionHandler = serverTioConfig.getAcceptCompletionHandler();
    serverSocketChannel.accept(this, acceptCompletionHandler);

    serverTioConfig.startTime = System.currentTimeMillis();

    // 下面这段代码有点无聊，写得随意，纯粹是为了打印好看些
    String baseStr = "|----------------------------------------------------------------------------------------|";
    int baseLen = baseStr.length();
    StackTraceElement[] ses = Thread.currentThread().getStackTrace();
    StackTraceElement se = ses[ses.length - 1];
    int xxLen = 18;
    int aaLen = baseLen - 3;
    List<String> infoList = new ArrayList<>();
    infoList.add(StrUtil.fillAfter("t-io on gitee", ' ', xxLen) + "| " + SysConst.TIO_URL_GITEE);
    infoList.add(StrUtil.fillAfter("t-io on github", ' ', xxLen) + "| " + SysConst.TIO_URL_GITHUB);

    infoList.add(StrUtil.fillAfter("-", '-', aaLen));

    infoList.add(StrUtil.fillAfter("TioConfig name", ' ', xxLen) + "| " + serverTioConfig.getName());
    infoList.add(StrUtil.fillAfter("Started at", ' ', xxLen) + "| " + DateUtil.formatDateTime(new Date()));
    infoList.add(StrUtil.fillAfter("Listen on", ' ', xxLen) + "| " + this.serverNode);
    infoList.add(StrUtil.fillAfter("Main Class", ' ', xxLen) + "| " + se.getClassName());

    try {
      String pid = null;
      if (JvmUtils.isStandardJava()) {
        // 仅在标准Java环境中执行
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        String runtimeName = runtimeMxBean.getName();
        pid = runtimeName.split("@")[0];
        long startTime = runtimeMxBean.getStartTime();
        long startCost = System.currentTimeMillis() - startTime;
        infoList.add(StrUtil.fillAfter("Jvm start time", ' ', xxLen) + "| " + startCost + "ms");
      } else {
        // Android或其他非标准Java环境的处理逻辑
        // 例如，你可以记录一条日志或者用其他方式获取所需信息
        infoList.add(StrUtil.fillAfter("Jvm start time", ' ', xxLen) + "| Not available in Android");
      }
      // 其他共通的代码
      infoList
          .add(StrUtil.fillAfter("Tio start time", ' ', xxLen) + "| " + (System.currentTimeMillis() - start) + "ms");
      infoList.add(
          StrUtil.fillAfter("Pid", ' ', xxLen) + "| " + (JvmUtils.isStandardJava() ? pid : "Not available in Android"));
    } catch (Exception e) {

    }
    // 100
    String printStr = SysConst.CRLF + baseStr + SysConst.CRLF;
    // printStr += "|--" + leftStr + " " + info + " " + rightStr + "--|\r\n";
    for (String string : infoList) {
      printStr += "| " + StrUtil.fillAfter(string, ' ', aaLen) + "|\r\n";
    }
    printStr += baseStr + SysConst.CRLF;
    if (log.isInfoEnabled()) {
      log.info(printStr);
    } else {
      System.out.println(printStr);
    }
  }

  /**
   * 
   * @return
   * @author tanyaowu
   */
  public boolean stop() {
    isWaitingStop = true;
    boolean ret = true;

    try {
      channelGroup.shutdownNow();
    } catch (Exception e) {
      log.error("channelGroup.shutdownNow()时报错", e);
    }

    try {
      serverSocketChannel.close();
    } catch (Exception e1) {
      log.error("serverSocketChannel.close()时报错", e1);
    }

    try {
      serverTioConfig.groupExecutor.shutdown();
    } catch (Exception e1) {
      log.error(e1.toString(), e1);
    }
    try {
      serverTioConfig.tioExecutor.shutdown();
    } catch (Exception e1) {
      log.error(e1.toString(), e1);
    }

    serverTioConfig.setStopped(true);
    try {
      ret = ret && serverTioConfig.groupExecutor.awaitTermination(6000, TimeUnit.SECONDS);
      ret = ret && serverTioConfig.tioExecutor.awaitTermination(6000, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      log.error(e.getLocalizedMessage(), e);
    }

    log.info(this.serverNode + " stopped");
    return ret;
  }

  public boolean isCheckLastVersion() {
    return checkLastVersion;
  }

  public void setCheckLastVersion(boolean checkLastVersion) {
    log.debug("community edition is no longer supported");
    // this.checkLastVersion = checkLastVersion;
  }
}
