package com.didi.cloud.fastdump.rest.rest.action.index;

import static org.elasticsearch.rest.RestRequest.Method.PUT;

import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;
import org.springframework.stereotype.Component;

import com.didi.cloud.fastdump.common.bean.common.Result;
import com.didi.cloud.fastdump.common.content.ResultType;
import com.didi.cloud.fastdump.common.content.metadata.QueryContext;
import com.didi.cloud.fastdump.common.exception.BaseException;
import com.didi.cloud.fastdump.core.action.movetask.PauseIndexMoveAction;
import com.didi.cloud.fastdump.rest.rest.BaseCommonHttpAction;

/**
 * Created by linyunan on 2022/9/5
 */
@Component
public class RestPauseIndexMoveAction extends BaseCommonHttpAction {
    private final PauseIndexMoveAction pauseIndexMoveAction;

    public RestPauseIndexMoveAction(PauseIndexMoveAction pauseIndexMoveAction) {
        this.pauseIndexMoveAction = pauseIndexMoveAction;
    }

    @Override
    protected void register() {
        restHandlerFactory.registerHandler(PUT, "index-move/{taskId}/stop", this);
    }

    @Override
    protected String name() {
        return "stop-index-move";
    }

    @Override
    protected void handleRequest(QueryContext queryContext, RestChannel channel) throws Exception {
        RestRequest request = queryContext.getRequest();
        String taskId = request.param("taskId");
        if (null == taskId || "".equals(taskId)) {
            throw new BaseException(String.format("taskId[%s] is illegal", taskId), ResultType.ILLEGAL_PARAMS);
        }

        pauseIndexMoveAction.doAction(taskId);
        sendResponse(channel, Result.buildSucc());
    }
}
