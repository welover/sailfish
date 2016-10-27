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
package sailfish.remoting.exceptions;

/**
 * 
 * @author spccold
 * @version $Id: RemotingException.java, v 0.1 2016年10月27日 下午3:44:04 jileng Exp $
 */
public class RemotingException extends Exception {

    /**  */
    private static final long serialVersionUID = 1L;
    private ExceptionCode     errorCode;

    public RemotingException(String message) {
        super(message);
    }
    
    public RemotingException(Throwable cause){
        super(cause);
    }

    public RemotingException(ExceptionCode errorCode, String message) {
        super(prefix(errorCode.toString()) + message);
        this.errorCode = errorCode;
    }

    public RemotingException(String message, Throwable cause) {
        super(message, cause);
    }

    public RemotingException(ExceptionCode errorCode, String message, Throwable cause) {
        super(prefix(errorCode.toString()) + message, cause);
        this.errorCode = errorCode;
    }

    private static String prefix(String errorCode) {
        return "[errorCode:" + errorCode + "], ";
    }

    public ExceptionCode code() {
        return errorCode;
    }

    public RemoteRemotingException toRemoteException() {
        return new RemoteRemotingException(errorCode, getMessage(), getCause());
    }
    
    /**
     * exception for remote peer
     */
    class RemoteRemotingException extends RemotingException{
        private static final long serialVersionUID = 1L;
        public RemoteRemotingException(ExceptionCode errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }
    }
}
