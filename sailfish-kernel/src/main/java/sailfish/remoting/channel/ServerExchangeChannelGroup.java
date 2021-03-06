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
package sailfish.remoting.channel;

import java.util.UUID;

import sailfish.remoting.Tracer;
import sailfish.remoting.configuration.NegotiateConfig;
import sailfish.remoting.exceptions.SailfishException;
import sailfish.remoting.handler.MsgHandler;
import sailfish.remoting.protocol.Protocol;

/**
 * @author spccold
 * @version $Id: ServerExchangeChannelGroup.java, v 0.1 2016年11月24日 下午6:22:36 spccold Exp $
 */
public final class ServerExchangeChannelGroup extends ReferenceCountedServerExchangeChannelGroup{
	
	private final ExchangeChannel[] children;
	private final ExchangeChannel[] deadChildren;
	private final ExchangeChannelChooserFactory.ExchangeChannelChooser chooser;
	
	private final MsgHandler<Protocol> msgHandler;
	private final Tracer tracer;
	
	public ServerExchangeChannelGroup(MsgHandler<Protocol> msgHandler, UUID id, int connections){
		this(new Tracer(), msgHandler, id, connections);
	}
	
	public ServerExchangeChannelGroup(Tracer tracer, MsgHandler<Protocol> msgHandler, UUID id, int connections) {
		super(id);
		
		this.msgHandler = msgHandler;
		this.tracer = tracer;
		
		children = new ExchangeChannel[connections];
		deadChildren = new ExchangeChannel[connections];
		
		chooser = DefaultExchangeChannelChooserFactory.INSTANCE.newChooser(children, deadChildren);
	}

	@Override
	public ExchangeChannel next() throws SailfishException {
		return chooser.next();
	}

	@Override
	public boolean isAvailable() {
		if(children.length == 1){//one connection check
			return (null != children[0] && children[0].isAvailable());
		}
		
		// can hit most of the time
		if (deadChildren[0] == null || deadChildren[0].isAvailable()) {
			return true;
		}

		for (int i = 1; i < children.length; i++) {
			if (deadChildren[i] == null || deadChildren[i].isAvailable()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void close(int timeout) {
		throw new UnsupportedOperationException("close with timeout: "+timeout);
	}

	@Override
	public MsgHandler<Protocol> getMsgHander() {
		return msgHandler;
	}

	@Override
	public Tracer getTracer() {
		return tracer;
	}
	
	@Override
	public void addChild(ExchangeChannel channel, NegotiateConfig config) {
		children[config.index()] = channel;
	}
}
