package com.litongjava.tio.core;

import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.litongjava.aio.AioId;
import com.litongjava.aio.Packet;
import com.litongjava.tio.client.ClientTioConfig;
import com.litongjava.tio.constants.TioCoreConfigKeys;
import com.litongjava.tio.core.cache.IpStatMapCacheRemovalListener;
import com.litongjava.tio.core.intf.AioHandler;
import com.litongjava.tio.core.intf.AioListener;
import com.litongjava.tio.core.intf.GroupListener;
import com.litongjava.tio.core.maintain.BsIds;
import com.litongjava.tio.core.maintain.ClientNodes;
import com.litongjava.tio.core.maintain.Groups;
import com.litongjava.tio.core.maintain.Ids;
import com.litongjava.tio.core.maintain.IpBlacklist;
import com.litongjava.tio.core.maintain.IpStats;
import com.litongjava.tio.core.maintain.Ips;
import com.litongjava.tio.core.maintain.Tokens;
import com.litongjava.tio.core.maintain.Users;
import com.litongjava.tio.core.ssl.SslConfig;
import com.litongjava.tio.core.stat.DefaultIpStatListener;
import com.litongjava.tio.core.stat.GroupStat;
import com.litongjava.tio.core.stat.IpStatListener;
import com.litongjava.tio.server.ServerTioConfig;
import com.litongjava.tio.utils.SystemTimer;
import com.litongjava.tio.utils.cache.CacheFactory;
import com.litongjava.tio.utils.cache.RemovalListenerWrapper;
import com.litongjava.tio.utils.cache.mapcache.ConcurrentMapCacheFactory;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.tio.utils.lock.MapWithLock;
import com.litongjava.tio.utils.lock.SetWithLock;
import com.litongjava.tio.utils.prop.MapWithLockPropSupport;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @author tanyaowu 2016年10月10日 下午5:25:43
 */
@Slf4j
public abstract class TioConfig extends MapWithLockPropSupport {
  /**
   * 本jvm中所有的ServerTioConfig对象
   */
  public static final Set<ServerTioConfig> ALL_SERVER_GROUPCONTEXTS = new HashSet<>();
  /**
   * 本jvm中所有的ClientTioConfig对象
   */
  public static final Set<ClientTioConfig> ALL_CLIENT_GROUPCONTEXTS = new HashSet<>();
  /**
   * 本jvm中所有的TioConfig对象
   */
  public static final Set<TioConfig> ALL_GROUPCONTEXTS = new HashSet<>();
  /**
   * 默认的接收数据的buffer size
   */
  public static final int READ_BUFFER_SIZE = EnvUtils.getInt("tio.default.read.buffer.size", 8192);
  private final static AtomicInteger ID_ATOMIC = new AtomicInteger();
  private ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;
  public boolean isShortConnection = false;
  public SslConfig sslConfig = null;
  public boolean debug = false;
  public GroupStat groupStat = null;
  public boolean statOn = true;
  public PacketConverter packetConverter = null;

  /**
   * 缓存工厂
   */
  private CacheFactory cacheFactory;

  /**
   * 移除IP监听
   */
  @SuppressWarnings("rawtypes")
  private RemovalListenerWrapper ipRemovalListenerWrapper;
  /**
   * 启动时间
   */
  public long startTime = SystemTimer.currTime;

  /**
   * 心跳超时时间(单位: 毫秒)，如果用户不希望框架层面做心跳相关工作，请把此值设为0或负数
   */
  public long heartbeatTimeout = 1000 * 120;
  /**
   * 解码出现异常时，是否打印异常日志
   */
  public boolean logWhenDecodeError = false;
  
  /**
   * 接收数据的buffer size
   */
  private int readBufferSize = READ_BUFFER_SIZE;
  private GroupListener groupListener = null;
  private AioId tioUuid = new DefaultTAioId();
  public ClientNodes clientNodes = new ClientNodes();
  public SetWithLock<ChannelContext> connections = new SetWithLock<ChannelContext>(new HashSet<ChannelContext>());
  public Groups groups = new Groups();
  public Users users = new Users();
  public Tokens tokens = new Tokens();
  public Ids ids = new Ids();
  public BsIds bsIds = new BsIds();
  public Ips ips = new Ips();
  public IpStats ipStats = new IpStats(this, null);;
  protected String id;
  /**
   * 解码异常多少次就把ip拉黑
   */
  protected int maxDecodeErrorCountForIp = 10;
  protected String name = "Untitled";
  private IpStatListener ipStatListener = DefaultIpStatListener.me;
  private boolean isStopped = false;
  /**
   * ip黑名单
   */
  public IpBlacklist ipBlacklist = null;
  public MapWithLock<Integer, Packet> waitingResps = new MapWithLock<Integer, Packet>(new HashMap<Integer, Packet>());

  public boolean disgnostic = EnvUtils.getBoolean(TioCoreConfigKeys.TIO_CORE_DIAGNOSTIC);

  public TioConfig() {
  }

  public TioConfig(CacheFactory cacheFactory) {
    this.cacheFactory = cacheFactory;
  }

  public TioConfig(CacheFactory cacheFactory, RemovalListenerWrapper<?> ipRemovalListenerWrapper) {
    this.cacheFactory = cacheFactory;
    this.ipRemovalListenerWrapper = ipRemovalListenerWrapper;
  }

  public TioConfig(String name) {
    this.name = name;
  }

  /**
   * 获取AioHandler对象
   * 
   * @return
   */
  public abstract AioHandler getAioHandler();

  /**
   * 获取AioListener对象
   */
  public abstract AioListener getAioListener();

  public ByteOrder getByteOrder() {
    return byteOrder;
  }

  /**
   * @return the groupListener
   */
  public GroupListener getGroupListener() {
    return groupListener;
  }

  public String getId() {
    return id;
  }

  /**
   * @return the tioUuid
   */
  public AioId getTioUuid() {
    return tioUuid;
  }

  /**
   * @return the syns
   */
  public MapWithLock<Integer, Packet> getWaitingResps() {
    return waitingResps;
  }

  /**
   * @return the isStop
   */
  public boolean isStopped() {
    return isStopped;
  }

  /**
   *
   * @param byteOrder
   * @author tanyaowu
   */
  public void setByteOrder(ByteOrder byteOrder) {
    this.byteOrder = byteOrder;
  }

  /**
   * @param groupListener the groupListener to set
   */
  public void setGroupListener(GroupListener groupListener) {
    this.groupListener = groupListener;
  }

  /**
   * @param heartbeatTimeout the heartbeatTimeout to set
   */
  public void setHeartbeatTimeout(long heartbeatTimeout) {
    this.heartbeatTimeout = heartbeatTimeout;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  /**
   * @param readBufferSize the readBufferSize to set
   */
  public void setReadBufferSize(int readBufferSize) {
    this.readBufferSize = Math.min(readBufferSize, TcpConst.MAX_DATA_LENGTH);
  }

  /**
   * @param isShortConnection the isShortConnection to set
   */
  public void setShortConnection(boolean isShortConnection) {
    this.isShortConnection = isShortConnection;
  }

  /**
   * @param isStop the isStop to set
   */
  public void setStopped(boolean isStopped) {
    this.isStopped = isStopped;
  }

  /**
   * @param tioUuid the tioUuid to set
   */
  public void setTioUuid(AioId tioUuid) {
    this.tioUuid = tioUuid;
  }

  public void setSslConfig(SslConfig sslConfig) {
    this.sslConfig = sslConfig;
  }

  public IpStatListener getIpStatListener() {
    return ipStatListener;
  }

  public void setIpStatListener(IpStatListener ipStatListener) {
    this.ipStatListener = ipStatListener;
    setDefaultIpRemovalListenerWrapper();
  }

  public GroupStat getGroupStat() {
    return groupStat;
  }
  /**
   * 是服务器端还是客户端
   * 
   * @return
   * @author tanyaowu
   */
  public abstract boolean isServer();

  public int getReadBufferSize() {
    return readBufferSize;
  }

  public boolean isSsl() {
    return sslConfig != null;
  }

  public void setCacheFactory(CacheFactory cacheFactory) {
    this.cacheFactory = cacheFactory;
  }

  public CacheFactory getCacheFactory() {
    return cacheFactory;
  }

  public void setIpRemovalListenerWrapper(RemovalListenerWrapper<?> ipRemovalListenerWrapper) {
    this.ipRemovalListenerWrapper = ipRemovalListenerWrapper;
  }

  public RemovalListenerWrapper<?> getIpRemovalListenerWrapper() {
    return ipRemovalListenerWrapper;
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  public void setDefaultIpRemovalListenerWrapper() {
    this.ipRemovalListenerWrapper = new RemovalListenerWrapper();
    IpStatMapCacheRemovalListener ipStatMapCacheRemovalListener = new IpStatMapCacheRemovalListener(this, ipStatListener);
    ipRemovalListenerWrapper.setListener(ipStatMapCacheRemovalListener);
  }

  public void init() {
    if (cacheFactory == null) {
      // mapCacheFactory
      this.cacheFactory = ConcurrentMapCacheFactory.INSTANCE;
    }

    if (ipRemovalListenerWrapper == null) {
      setDefaultIpRemovalListenerWrapper();
    }

    ALL_GROUPCONTEXTS.add(this);
    if (this instanceof ServerTioConfig) {
      ALL_SERVER_GROUPCONTEXTS.add((ServerTioConfig) this);
    } else {
      ALL_CLIENT_GROUPCONTEXTS.add((ClientTioConfig) this);
    }

    if (ALL_GROUPCONTEXTS.size() > 20) {
      log.warn("You have created {} TioConfig objects, you might be misusing t-io.", ALL_GROUPCONTEXTS.size());
    }
    this.id = ID_ATOMIC.incrementAndGet() + "";

    if (this.ipStats == null) {
      this.ipStats = new IpStats(this, null);
    }
  }
}
