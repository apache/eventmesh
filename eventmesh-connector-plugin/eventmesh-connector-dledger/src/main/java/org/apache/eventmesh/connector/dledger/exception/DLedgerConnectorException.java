package org.apache.eventmesh.connector.dledger.exception;

import org.apache.eventmesh.api.exception.ConnectorRuntimeException;

public class DLedgerConnectorException extends ConnectorRuntimeException {
    public DLedgerConnectorException(String message) {
        super(message);
    }

    public DLedgerConnectorException(Throwable throwable) {
        super(throwable);
    }
}
