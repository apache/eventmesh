package org.apache.eventmesh.common.protocol;

import org.apache.eventmesh.common.protocol.http.HttpCommand;

import java.io.Serializable;

/**
 * <ul>
 *     <li>Tcp transport object{@link org.apache.eventmesh.common.protocol.tcp.Package}</li>
 *     <li>Http transport object{@link HttpCommand}</li>
 * </ul>
 */
public interface ProtocolTransportObject extends Serializable {
}
