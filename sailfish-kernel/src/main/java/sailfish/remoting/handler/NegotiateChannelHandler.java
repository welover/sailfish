/**
 *
 *	Copyright 2016-2016 spccold
 *
 *	Licensed under the Apache License, Version 2.0 (the "License");
 *	you may not use this file except in compliance with the License.
 *	You may obtain a copy of the License at
 *
 *   	http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software
 *	distributed under the License is distributed on an "AS IS" BASIS,
 *	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *	See the License for the specific language governing permissions and
 *	limitations under the License.
 *
 */
package sailfish.remoting.handler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import sailfish.remoting.ExchangeServer;
import sailfish.remoting.channel.ExchangeChannelGroup;
import sailfish.remoting.channel.ReadWriteServerExchangeChannelGroup;
import sailfish.remoting.channel.ReferenceCountedServerExchangeChannelGroup;
import sailfish.remoting.channel.ServerExchangeChannel;
import sailfish.remoting.channel.ServerExchangeChannelGroup;
import sailfish.remoting.configuration.NegotiateConfig;
import sailfish.remoting.constants.ChannelAttrKeys;
import sailfish.remoting.constants.ChannelAttrKeys.OneTime;
import sailfish.remoting.constants.Opcode;
import sailfish.remoting.protocol.Protocol;
import sailfish.remoting.protocol.RequestProtocol;
import sailfish.remoting.protocol.ResponseProtocol;
import sailfish.remoting.utils.ArrayUtils;
import sailfish.remoting.utils.ChannelUtil;

/**
 * negotiate idleTimeout, maxIdleTimeout and settings about {@link NegotiateConfig} with remote peer
 * 
 * @author spccold
 * @version $Id: NegotiateChannelHandler.java, v 0.1 2016年11月23日 下午10:11:26 spccold Exp $
 */
@ChannelHandler.Sharable
public class NegotiateChannelHandler extends SimpleChannelInboundHandler<Protocol> {

	private static final Logger logger = LoggerFactory.getLogger(NegotiateChannelHandler.class);
	public static final NegotiateChannelHandler INSTANCE = new NegotiateChannelHandler();
	// This way we can reduce the memory usage compared to use Attributes.
	private final ConcurrentMap<ChannelHandlerContext, Boolean> negotiateMap = PlatformDependent.newConcurrentHashMap();

	public static final ConcurrentMap<String, ExchangeChannelGroup> uuid2ChannelGroup = new ConcurrentHashMap<>();

	private NegotiateChannelHandler() {
	};

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		if (ChannelUtil.clientSide(ctx)) {
			negotiate(ctx);
		}
		ctx.fireChannelActive();
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		if (!ChannelUtil.clientSide(ctx)) {
			String uuidStr = ctx.channel().attr(ChannelAttrKeys.uuidStr).get();
			synchronized (uuidStr.intern()) {
				ReferenceCountedServerExchangeChannelGroup channelGroup = (ReferenceCountedServerExchangeChannelGroup) uuid2ChannelGroup
						.get(uuidStr);
				if (null != channelGroup) {
					channelGroup.release();
				}
			}
		}
		ctx.fireChannelInactive();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Protocol msg) throws Exception {
		if (!ChannelUtil.clientSide(ctx) && msg.request() && msg.heartbeat()) {
			dealNegotiate(ctx, msg);
			return;
		}
		// no sense to Protocol in fact
		ReferenceCountUtil.retain(msg);
		ctx.fireChannelRead(msg);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.warn("Failed to negotiate. Closing: " + ctx.channel(), cause);
		ctx.close();
	}

	private boolean negotiate(ChannelHandlerContext ctx) throws Exception {
		if (negotiateMap.putIfAbsent(ctx, Boolean.TRUE) == null) { // Guard against re-entrance.
			ctx.channel().attr(OneTime.awaitNegotiate).get().countDown();
			try {
				NegotiateConfig channelConfig = ctx.channel().attr(OneTime.channelConfig).get();
				ctx.writeAndFlush(channelConfig.toNegotiateRequest());
			} catch (Throwable cause) {
				exceptionCaught(ctx, cause);
			} finally {
				remove(ctx);
			}
			return true;
		}
		return false;
	}

	@SuppressWarnings("deprecation")
	private void dealNegotiate(ChannelHandlerContext ctx, Protocol msg) throws Exception {
		try {
			RequestProtocol requestProtocol = (RequestProtocol) msg;
			if (requestProtocol.opcode() == Opcode.HEARTBEAT_WITH_NEGOTIATE) {
				NegotiateConfig config = NegotiateConfig.fromNegotiate(requestProtocol.body());

				// the client side shall prevail
				byte serverSideIdleTimeout = ctx.channel().attr(OneTime.idleTimeout).get();
				byte serverSideIdleMaxTimeout = ctx.channel().attr(ChannelAttrKeys.maxIdleTimeout).get();
				if (config.idleTimeout() != serverSideIdleTimeout) {
					ChannelHandlerContext idleHandlerContext = ctx.pipeline().context(IdleStateHandler.class);
					ctx.pipeline().replace(IdleStateHandler.class, idleHandlerContext.name(),
							new IdleStateHandler(config.idleTimeout(), 0, 0));
				}
				if (config.maxIdleTimeout() != serverSideIdleMaxTimeout) {
					ctx.channel().attr(ChannelAttrKeys.maxIdleTimeout).set(config.maxIdleTimeout());
				}

				String uuidStr = config.uuid().toString();
				// bind uuidStr to Channel
				ctx.channel().attr(ChannelAttrKeys.uuidStr).set(uuidStr);
				ExchangeServer exchangeServer = ctx.channel().attr(ChannelAttrKeys.exchangeServer).get();
				synchronized (uuidStr.intern()) {
					if (config.isReadWrite()) {
						negotiateReadWriteChannel(ctx, uuidStr, exchangeServer, config);
					} else if (config.isRead() || config.isWrite()) {
						negotiateReadOrWriteChannel(ctx, uuidStr, exchangeServer, config);
					}
				}
			}
			// normal heart beat request
			ctx.writeAndFlush(ResponseProtocol.newHeartbeat());
		} catch (Exception cause) {
			exceptionCaught(ctx, cause);
		} finally {
			ctx.channel().attr(OneTime.idleTimeout).remove();
		}
	}

	private void negotiateReadWriteChannel(ChannelHandlerContext ctx, String uuidStr, ExchangeServer exchangeServer,
			NegotiateConfig config) {
		ExchangeChannelGroup channelGroup = uuid2ChannelGroup.get(uuidStr);
		if (null == channelGroup) {
			uuid2ChannelGroup.put(uuidStr, channelGroup = new ServerExchangeChannelGroup(null,
					exchangeServer.getMsgHandler(), config.uuid(), config.connections()));
		}
		// bind current channel to ExchangeChannelGroup
		ctx.channel().attr(ChannelAttrKeys.channelGroup).set(channelGroup);
		int finalChannelIndex = config.index();
		if (config.reverseIndex()) {
			// Reverse channel index to get better io performance
			finalChannelIndex = ArrayUtils.reverseArrayIndex(config.connections(), finalChannelIndex);
		}

		// add child
		((ServerExchangeChannelGroup) channelGroup).retain();
		((ServerExchangeChannelGroup) channelGroup).addChild(new ServerExchangeChannel(channelGroup, ctx.channel()),
				finalChannelIndex);
	}

	private void negotiateReadOrWriteChannel(ChannelHandlerContext ctx, String uuidStr, ExchangeServer exchangeServer,
			NegotiateConfig config) {
		// negotiate read write splitting settings
		ExchangeChannelGroup channelGroup = uuid2ChannelGroup.get(uuidStr);
		if (null == channelGroup) {
			uuid2ChannelGroup.putIfAbsent(uuidStr,
					channelGroup = new ReadWriteServerExchangeChannelGroup(exchangeServer.getMsgHandler(),
							config.uuid(), config.connections(), config.writeConnections()));
		}
		// bind current channel to ExchangeChannelGroup
		ctx.channel().attr(ChannelAttrKeys.channelGroup).set(channelGroup);

		int finalChannelIndex = config.index();
		if (config.isRead()) {// read to write
			ServerExchangeChannelGroup writeGroup = ((ReadWriteServerExchangeChannelGroup) channelGroup)
					.getWriteGroup();
			if (config.reverseIndex()) {
				// Reverse channel index to get better io performance
				finalChannelIndex = ArrayUtils.reverseArrayIndex(config.connections() - config.writeConnections(),
						finalChannelIndex);
			}
			((ReadWriteServerExchangeChannelGroup) channelGroup).retain();
			writeGroup.addChild(new ServerExchangeChannel(writeGroup, ctx.channel()), finalChannelIndex);
		} else if (config.isWrite()) {// write to read
			ServerExchangeChannelGroup readGroup = ((ReadWriteServerExchangeChannelGroup) channelGroup).getReadGroup();
			if (config.reverseIndex()) {
				// Reverse channel index to get better io performance
				finalChannelIndex = ArrayUtils.reverseArrayIndex(config.writeConnections(), finalChannelIndex);
			}
			((ReadWriteServerExchangeChannelGroup) channelGroup).retain();
			readGroup.addChild(new ServerExchangeChannel(readGroup, ctx.channel()), finalChannelIndex);
		}
	}

	@SuppressWarnings("deprecation")
	private void remove(ChannelHandlerContext ctx) {
		try {
			// remove onetime attrs
			ctx.channel().attr(OneTime.channelConfig).remove();

			ChannelPipeline pipeline = ctx.pipeline();
			if (pipeline.context(this) != null) {
				pipeline.remove(this);
			}
		} finally {
			negotiateMap.remove(ctx);
		}
	}
}
