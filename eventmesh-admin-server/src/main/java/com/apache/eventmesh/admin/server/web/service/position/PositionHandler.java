package com.apache.eventmesh.admin.server.web.service.position;

import org.apache.eventmesh.common.remote.job.DataSourceType;

public abstract class PositionHandler implements IReportPositionHandler,IFetchPositionHandler {
    protected abstract DataSourceType getSourceType();
}
