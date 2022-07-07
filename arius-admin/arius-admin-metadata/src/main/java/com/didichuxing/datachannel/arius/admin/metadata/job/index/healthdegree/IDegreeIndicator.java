package com.didichuxing.datachannel.arius.admin.metadata.job.index.healthdegree;

import com.didichuxing.datachannel.arius.admin.common.constant.IndicatorsType;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.index.BaseDegree;
import com.didichuxing.datachannel.arius.admin.metadata.job.index.healthdegree.degreeindicator.DegreeParam;

public interface IDegreeIndicator {
    IndicatorsType getType();

    <T extends BaseDegree> T getRealTimePO();

    <T extends BaseDegree> T exec(DegreeParam degreeParam);
}