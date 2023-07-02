package org.apache.eventmesh.runtime.core.protocol.http.push;

import org.apache.eventmesh.common.protocol.http.common.ClientRetCode;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.HandleMsgContext;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;

public class SyncHTTPPushRequest extends AbstractHTTPPushRequest{
    public static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    public SyncHTTPPushRequest(HandleMsgContext handleMsgContext) {
        super(handleMsgContext);
    }

    @Override
    public boolean tryHTTPRequest() {
        HttpUriRequest builder = buildHttpUriRequest();
        if (builder == null) {
            return false;
        }

        HttpResponse response;
        try {
            response = eventMeshHTTPServer.getHttpClientPool().getClient().execute(builder);
        } catch (IOException e) {
            MESSAGE_LOGGER.error("push2client err", e);
            return false;
        }

        long cost = System.currentTimeMillis() - lastPushTime;
        eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordHTTPPushTimeCost(cost);

        if (processResponseStatus(response.getStatusLine().getStatusCode(), response)) {
            // this is successful response, process response payload
            String res;
            try {
                res = EntityUtils.toString(response.getEntity(), Charset.forName(EventMeshConstants.DEFAULT_CHARSET));
            } catch (IOException e) {
                MESSAGE_LOGGER.error("push2client res error", e);
                return true;
            }
            ClientRetCode result = processResponseContent(res);
            if (MESSAGE_LOGGER.isInfoEnabled()) {
                MESSAGE_LOGGER.info(
                        "message|eventMesh2client|{}|url={}|topic={}|bizSeqNo={}"
                                + "|uniqueId={}|cost={}",
                        result, currPushUrl, handleMsgContext.getTopic(),
                        handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), cost);
            }
            switch (result) {
                case OK:
                case REMOTE_OK:
                case FAIL:
                    return true;
                case RETRY:
                case NOLISTEN:
                    return false;
                default: // do nothing
                    return true;
            }
        } else {
            eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordHttpPushMsgFailed();
            if (MESSAGE_LOGGER.isInfoEnabled()) {
                MESSAGE_LOGGER.info(
                        "message|eventMesh2client|exception|url={}|topic={}|bizSeqNo={}"
                                + "|uniqueId={}|cost={}", currPushUrl, handleMsgContext.getTopic(),
                        handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), cost);
            }
            return false;
        }
    }

    @Override
    public void retry() throws Exception {

    }
}
