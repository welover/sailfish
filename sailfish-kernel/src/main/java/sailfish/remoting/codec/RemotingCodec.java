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
package sailfish.remoting.codec;

import io.netty.buffer.ByteBuf;
import sailfish.remoting.exceptions.RemotingException;
import sailfish.remoting.protocol.Protocol;

/**
 * 
 * @author spccold
 * @version $Id: RemotingCodec.java, v 0.1 2016年10月15日 下午4:45:54 jileng Exp $
 */
public interface RemotingCodec {
    public void encode(Protocol protocol, ByteBuf buffer) throws RemotingException;
    public Protocol decode(ByteBuf buffer) throws RemotingException;
}
