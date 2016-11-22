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

import sailfish.remoting.Address;
import sailfish.remoting.constants.RemotingConstants;
import sailfish.remoting.exceptions.SailfishException;

/**
 * @author spccold
 * @version $Id: MultiConnectionsExchangeChannelGroup.java, v 0.1 2016年11月22日
 *          下午4:01:53 spccold Exp $
 */
public abstract class MultiConnectionsExchangeChannelGroup extends AbstractExchangeChannelGroup {
	
	private final ExchangeChannel[] children;
	private final ExchangeChannel[] deadChildren;
	private final ExchangeChannelChooserFactory.ExchangeChannelChooser chooser;

	protected MultiConnectionsExchangeChannelGroup(Address address, int connections, boolean lazy,
			ReadWriteChannelConfig config) throws SailfishException {
		this(address, connections, RemotingConstants.DEFAULT_CONNECT_TIMEOUT,
				RemotingConstants.DEFAULT_RECONNECT_INTERVAL, RemotingConstants.DEFAULT_IDLE_TIMEOUT,
				RemotingConstants.DEFAULT_MAX_IDLE_TIMEOUT, lazy, config);
	}

	protected MultiConnectionsExchangeChannelGroup(Address address, int connections, boolean lazy, int connectTimeout,
			int reconnectInterval, ReadWriteChannelConfig config) throws SailfishException {
		this(address, connections, connectTimeout, reconnectInterval, RemotingConstants.DEFAULT_IDLE_TIMEOUT,
				RemotingConstants.DEFAULT_MAX_IDLE_TIMEOUT, lazy, config);
	}

	protected MultiConnectionsExchangeChannelGroup(Address address, int connections, int idleTimeout,
			int maxIdleTimeOut, boolean lazy, ReadWriteChannelConfig config) throws SailfishException {
		this(address, connections, RemotingConstants.DEFAULT_CONNECT_TIMEOUT,
				RemotingConstants.DEFAULT_RECONNECT_INTERVAL, idleTimeout, maxIdleTimeOut, lazy, config);
	}

	protected MultiConnectionsExchangeChannelGroup(Address address, int connections, int connectTimeout,
			int reconnectInterval, int idleTimeout, int maxIdleTimeOut, boolean lazy, ReadWriteChannelConfig config)
			throws SailfishException {
		children = new ExchangeChannel[connections];
		deadChildren = new ExchangeChannel[connections];

		ReadWriteChannelConfig configCopy = null;
		for (int i = 0; i < connections; i++) {
			boolean success = false;
			if(null != config){
				configCopy = config.deepCopy();
				configCopy.index(i);
			}
			try {
				children[i] = newChild(address, connectTimeout, reconnectInterval, idleTimeout, maxIdleTimeOut, lazy,
						configCopy);
				success = true;
			} catch (SailfishException cause) {
				throw cause;
			} finally {
				if (!success) {
					close(Integer.MAX_VALUE);
				}
			}
		}

		chooser = DefaultExchangeChannelChooserFactory.INSTANCE.newChooser(children, deadChildren);
	}

	@Override
	public ExchangeChannel next() throws SailfishException {
		return chooser.next();
	}

	/**
	 * Return the number of {@link ExchangeChannel} this implementation uses.
	 * This number is the maps 1:1 to the connections it use.
	 */
	public final int channelOCount() {
		return children.length;
	}

	public void close(int timeout) {
		if (this.isClosed()) {
			return;
		}
		synchronized (this) {
			if (this.isClosed()) {
				return;
			}
			this.closed = true;
			for (int i = 0; i < children.length; i++) {
				deadChildren[i] = null;
				if (null != children[i]) {
					children[i].close(timeout);
				}
			}
		}
	}

	@Override
	public boolean isAvailable() {
		if (this.isClosed()) {
			return false;
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

	/**
	 * Create a new {@link ExchangeChannel} which will later then accessible via
	 * the {@link #next()} method.
	 */
	protected abstract ExchangeChannel newChild(Address address, int connectTimeout, int reconnectInterval,
			int idleTimeout, int maxIdleTimeOut, boolean lazy, ReadWriteChannelConfig config) throws SailfishException;
}
