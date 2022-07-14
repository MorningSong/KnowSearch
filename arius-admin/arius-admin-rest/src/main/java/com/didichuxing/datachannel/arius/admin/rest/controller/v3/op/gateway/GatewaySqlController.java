package com.didichuxing.datachannel.arius.admin.rest.controller.v3.op.gateway;

import com.didichuxing.datachannel.arius.admin.biz.gateway.GatewayManager;
import com.didichuxing.datachannel.arius.admin.common.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.common.constant.AuthConstant;
import com.didiglobal.logi.security.util.HttpRequestUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.servlet.http.HttpServletRequest;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import static com.didichuxing.datachannel.arius.admin.common.constant.ApiVersion.V3;

@NoArgsConstructor
@RestController()
@RequestMapping(V3 + "/gateway/sql")
@Api(tags = "Gateway中sql语句的翻译和查询")
public class GatewaySqlController {
    @Autowired
    private GatewayManager gatewayManager;

    @PostMapping(value = { "/{phyClusterName}", ""})
    @ResponseBody
    @ApiOperation(value = "根据sql语句查询gateway集群")
    public Result<String> directSqlSearch(@RequestBody String sql,
                                          @PathVariable(required = false) String phyClusterName,
                                          HttpServletRequest request) {
        return gatewayManager.directSqlSearch(sql, phyClusterName, HttpRequestUtil.getProjectId(request));
    }

    @PostMapping("/explain")
    @ResponseBody
    @ApiOperation(value = "根据sql语句解释")
    public Result<String> sqlExplain(@RequestBody String sql) {
        return gatewayManager.sqlExplain(sql, AuthConstant.SUPER_PROJECT_ID);
    }
}