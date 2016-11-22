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
import sailfish.remoting.exceptions.SailfishException;

/**
 * @author spccold
 * @version $Id: DefaultExchangeChannelGroup.java, v 0.1 2016年11月22日 下午4:10:23
 *          spccold Exp $
 */
public class DefaultExchangeChannelGroup extends MultiConnectionsExchangeChannelGroup {

	public DefaultExchangeChannelGroup(Address address, int connections, boolean lazy)
			throws SailfishException {
		this(address, connections, lazy, null);
	}

	public DefaultExchangeChannelGroup(Address address, int connections, boolean lazy, ReadWriteChannelConfig config)
			throws SailfishException {
		super(address, connections, lazy, config);
	}

	public DefaultExchangeChannelGroup(Address address, int connections, boolean lazy, int connectTimeout,
			int reconnectInterval, ReadWriteChannelConfig config) throws SailfishException {
		super(address, connections, lazy, connectTimeout, reconnectInterval, config);
	}

	public DefaultExchangeChannelGroup(Address address, int connections, int idleTimeout, int maxIdleTimeOut,
			boolean lazy, ReadWriteChannelConfig config) throws SailfishException {
		super(address, connections, idleTimeout, maxIdleTimeOut, lazy, config);
	}

	public DefaultExchangeChannelGroup(Address address, int connections, int connectTimeout, int reconnectInterval,
			int idleTimeout, int maxIdleTimeOut, boolean lazy, ReadWriteChannelConfig config) throws SailfishException {
		super(address, connections, connectTimeout, reconnectInterval, idleTimeout, maxIdleTimeOut, lazy, config);
	}

	@Override
	protected ExchangeChannel newChild(Address address, int connectTimeout, int reconnectInterval, int idleTimeout,
			int maxIdleTimeOut, boolean lazy, ReadWriteChannelConfig config) throws SailfishException {
		if (lazy && (null == config || config.write())) {
			return new LazyExchangeChannel(this, address, connectTimeout, reconnectInterval, idleTimeout,
					maxIdleTimeOut, config);
		} 
		return new EagerExchangeChannel(this, address, connectTimeout, reconnectInterval, idleTimeout,
				maxIdleTimeOut, config);
	}
}
