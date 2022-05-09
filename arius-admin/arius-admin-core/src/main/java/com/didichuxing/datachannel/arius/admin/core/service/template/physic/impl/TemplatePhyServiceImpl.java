package com.didichuxing.datachannel.arius.admin.core.service.template.physic.impl;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.didichuxing.datachannel.arius.admin.common.bean.common.IndexTemplatePhysicalConfig;
import com.didichuxing.datachannel.arius.admin.common.bean.common.Result;
import com.didichuxing.datachannel.arius.admin.common.bean.dto.template.IndexTemplateInfoDTO;
import com.didichuxing.datachannel.arius.admin.common.bean.dto.template.IndexTemplatePhysicalInfoDTO;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhyInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplatePhyInfoWithLogic;
import com.didichuxing.datachannel.arius.admin.common.constant.template.TemplateDeployRoleEnum;
import com.didichuxing.datachannel.arius.admin.common.constant.template.TemplatePhysicalStatusEnum;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.region.ClusterRegion;
import com.didichuxing.datachannel.arius.admin.common.bean.entity.template.IndexTemplateInfo;
import com.didichuxing.datachannel.arius.admin.common.bean.po.template.IndexTemplateInfoPO;
import com.didichuxing.datachannel.arius.admin.common.bean.po.template.IndexTemplatePhysicalInfoPO;
import com.didichuxing.datachannel.arius.admin.core.component.SpringTool;
import com.didichuxing.datachannel.arius.admin.common.event.template.PhysicalTemplateDeleteEvent;
import com.didichuxing.datachannel.arius.admin.common.exception.AdminOperateException;
import com.didichuxing.datachannel.arius.admin.common.exception.ESOperateException;
import com.didichuxing.datachannel.arius.admin.common.util.AriusObjUtils;
import com.didichuxing.datachannel.arius.admin.common.util.ConvertUtil;
import com.didichuxing.datachannel.arius.admin.common.util.IndexNameFactory;
import com.didichuxing.datachannel.arius.admin.common.util.RackUtils;
import com.didichuxing.datachannel.arius.admin.core.component.CacheSwitch;
import com.didichuxing.datachannel.arius.admin.core.component.ResponsibleConvertTool;
import com.didichuxing.datachannel.arius.admin.core.service.cluster.region.RegionRackService;
import com.didichuxing.datachannel.arius.admin.core.service.common.OperateRecordService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESIndexService;
import com.didichuxing.datachannel.arius.admin.core.service.es.ESTemplateService;
import com.didichuxing.datachannel.arius.admin.core.service.template.logic.IndexTemplateInfoService;
import com.didichuxing.datachannel.arius.admin.core.service.template.physic.TemplatePhyService;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.template.IndexTemplateInfoDAO;
import com.didichuxing.datachannel.arius.admin.persistence.mysql.template.IndexTemplatePhysicalInfoDAO;
import com.didiglobal.logi.elasticsearch.client.response.indices.catindices.CatIndexResult;
import com.didiglobal.logi.log.ILog;
import com.didiglobal.logi.log.LogFactory;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.didichuxing.datachannel.arius.admin.common.constant.operaterecord.ModuleEnum.TEMPLATE;
import static com.didichuxing.datachannel.arius.admin.common.constant.operaterecord.OperationEnum.DELETE;

/**
 * @author d06679
 * @date 2019/3/29
 */
@Service
public class TemplatePhyServiceImpl implements TemplatePhyService {

    private static final ILog                              LOGGER                         = LogFactory
        .getLog(TemplatePhyServiceImpl.class);

    public static final Integer                            NOT_CHECK                      = -100;

    private static final String                            MSG                            = "editTemplateFromLogic fail||physicalId={}||expression={}";

    @Autowired
    private IndexTemplatePhysicalInfoDAO indexTemplatePhysicalInfoDAO;

    @Autowired
    private IndexTemplateInfoDAO indexTemplateInfoDAO;

    @Autowired
    private OperateRecordService                           operateRecordService;

    @Autowired
    private ESIndexService                                 esIndexService;

    @Autowired
    private ESTemplateService                              esTemplateService;

    @Autowired
    private ResponsibleConvertTool                         responsibleConvertTool;

    @Autowired
    private IndexTemplateInfoService indexTemplateInfoService;

    @Autowired
    private RegionRackService regionRackService;

    @Autowired
    private CacheSwitch                                    cacheSwitch;
    private Cache<String, List<?>> templatePhyListCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).maximumSize(10).build();

    /**
     * 条件查询
     *
     * @param param 参数
     * @return 物理模板列表
     */
    @Override
    public List<IndexTemplatePhyInfo> getByCondt(IndexTemplatePhysicalInfoDTO param) {
        return ConvertUtil.list2List(
            indexTemplatePhysicalInfoDAO.listByCondition(ConvertUtil.obj2Obj(param, IndexTemplatePhysicalInfoPO.class)),
            IndexTemplatePhyInfo.class);
    }

    /**
     * 查询指定逻辑模板对应的物理模板
     *
     * @param logicId 逻辑模板
     * @return result
     */
    @Override
    public List<IndexTemplatePhyInfo> getTemplateByLogicId(Integer logicId) {
        return ConvertUtil.list2List(indexTemplatePhysicalInfoDAO.listByLogicId(logicId), IndexTemplatePhyInfo.class);
    }

    /**
     * 从缓存中查询指定逻辑模板对应的物理模板
     * @param logicId 逻辑模板
     * @return list
     */
    @Override
    public List<IndexTemplatePhyInfo> getValidTemplatesByLogicId(Integer logicId) {
        if (logicId == null) {
            return Lists.newArrayList();
        }

        List<IndexTemplatePhyInfo> indexTemplatePhies = listTemplate();
        if (CollectionUtils.isEmpty(indexTemplatePhies)) {
            return Lists.newArrayList();
        }

        return indexTemplatePhies.parallelStream()
            .filter(i -> i.getLogicId() != null && logicId.intValue() == i.getLogicId().intValue())
            .collect(Collectors.toList());
    }

    @Override
    public IndexTemplatePhyInfo getTemplateById(Long templatePhyId) {
        return ConvertUtil.obj2Obj(indexTemplatePhysicalInfoDAO.getById(templatePhyId), IndexTemplatePhyInfo.class);
    }

    /**
     * 查询指定id的模板 包含逻辑模板信息
     *
     * @param physicalId 物理模板id
     * @return result
     */
    @Override
    public IndexTemplatePhyInfoWithLogic getTemplateWithLogicById(Long physicalId) {
        IndexTemplatePhysicalInfoPO physicalPO = indexTemplatePhysicalInfoDAO.getById(physicalId);
        return buildIndexTemplatePhysicalWithLogic(physicalPO);
    }

    @Override
    public Result<Long> insert(IndexTemplatePhysicalInfoDTO param) throws AdminOperateException {
        IndexTemplatePhysicalInfoPO newTemplate = ConvertUtil.obj2Obj(param, IndexTemplatePhysicalInfoPO.class);
        boolean succ;
        try {
            succ = (1 == indexTemplatePhysicalInfoDAO.insert(newTemplate));
        } catch (DuplicateKeyException e) {
            LOGGER.warn("class=TemplatePhyServiceImpl||method=insert||errMsg={}", e.getMessage());
            throw new AdminOperateException(String.format("保存物理模板【%s】失败：物理模板已存在！", newTemplate.getName()));
        }
        return Result.build(succ,newTemplate.getId());
    }

    @Override
    public void deleteDirtyByClusterAndName(String cluster, String name) {
        indexTemplatePhysicalInfoDAO.deleteDirtyByClusterAndName(cluster, name);
    }

    /**
     * 删除
     *
     * @param physicalId 物理模板id
     * @param operator   操作人
     * @return result
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delTemplate(Long physicalId, String operator) throws ESOperateException {
        IndexTemplatePhysicalInfoPO oldPO = indexTemplatePhysicalInfoDAO.getById(physicalId);
        if (oldPO == null) {
            return Result.buildNotExist("template not exist");
        }

        boolean succ = 1 == indexTemplatePhysicalInfoDAO.updateStatus(physicalId,
            TemplatePhysicalStatusEnum.INDEX_DELETING.getCode());
        if (succ) {
            // 删除集群中的模板
            try {
                esTemplateService.syncDelete(oldPO.getCluster(), oldPO.getName(), 1);
            }catch (ESOperateException e){
                String msg = e.getMessage();
                if (StringUtils.indexOf(msg, "index_template_missing_exception") != -1) {
                    LOGGER.warn("class=TemplatePhyServiceImpl||method=delTemplate||physicalId={}||operator={}||msg= index template not found!", physicalId, operator);
                } else {
                    LOGGER.error("class=TemplatePhyServiceImpl||method=delTemplate||physicalId={}||operator={}||msg=delete physical template failed! {}", physicalId, operator, msg);
                    throw new ESOperateException(String.format("删除集群【%s】中物理模板【%s】失败！", oldPO.getCluster(), oldPO.getName()));
                }
            }

            operateRecordService.save(TEMPLATE, DELETE, oldPO.getLogicId(),
                String.format("下线物理集群[%s]中模板[%s]", oldPO.getCluster(), oldPO.getName()), operator);

            SpringTool.publish(new PhysicalTemplateDeleteEvent(this, ConvertUtil.obj2Obj(oldPO, IndexTemplatePhyInfo.class),
                indexTemplateInfoService.getLogicTemplateWithPhysicalsById(oldPO.getLogicId())));
        }

        return Result.build(succ);
    }

    /**
     * 删除
     *
     * @param logicId  id
     * @param operator 操作人
     * @return result
     * @throws ESOperateException e
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> delTemplateByLogicId(Integer logicId, String operator) throws ESOperateException {
        List<IndexTemplatePhysicalInfoPO> physicalPOs = indexTemplatePhysicalInfoDAO.listByLogicId(logicId);

        boolean succ = true;
        if (CollectionUtils.isEmpty(physicalPOs)) {
            LOGGER.info("class=TemplatePhyServiceImpl||method=delTemplateByLogicId||logicId={}||msg=template no physical info!", logicId);
        } else {
            LOGGER.info("class=TemplatePhyServiceImpl||method=delTemplateByLogicId||logicId={}||physicalSize={}||msg=template has physical info!",
                logicId, physicalPOs.size());
            for (IndexTemplatePhysicalInfoPO physicalPO : physicalPOs) {
                if (delTemplate(physicalPO.getId(), operator).failed()) {
                    succ = false;
                }

            }
        }

        return Result.build(succ);
    }

    /**
     * 修改由于逻辑模板修改而物理模板需要同步修改的属性
     * 目前有:
     * expression
     *
     * @param param    参数
     * @param operator 操作人
     * @return result
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> editTemplateFromLogic(IndexTemplateInfoDTO param, String operator) throws ESOperateException {
        if (param == null) {
            return Result.buildFail("参数为空！");
        }
        List<IndexTemplatePhysicalInfoPO> physicalPOs = indexTemplatePhysicalInfoDAO.listByLogicId(param.getId());
        if (CollectionUtils.isEmpty(physicalPOs)) {
            return Result.buildSucc();
        }

        for (IndexTemplatePhysicalInfoPO physicalPO : physicalPOs) {
            IndexTemplatePhysicalInfoPO updateParam = new IndexTemplatePhysicalInfoPO();
            updateParam.setId(physicalPO.getId());

            if(null != param.getWriteRateLimit() && StringUtils.isNotBlank(physicalPO.getConfig())) {
                IndexTemplatePhysicalConfig indexTemplatePhysicalConfig = JSON.parseObject(physicalPO.getConfig(),
                        IndexTemplatePhysicalConfig.class);

                indexTemplatePhysicalConfig.setManualPipeLineRateLimit(param.getWriteRateLimit());
                updateParam.setConfig(JSON.toJSONString(indexTemplatePhysicalConfig));
                boolean succeed = (1 == indexTemplatePhysicalInfoDAO.update(updateParam));
                if (!succeed) {
                    LOGGER.warn("class=TemplatePhyServiceImpl||method=editTemplateFromLogic||msg=editTemplateFromLogic fail||physicalId={}||expression={}||writeRateLimit={}", physicalPO.getId(),
                            param.getExpression(), param.getWriteRateLimit());
                    return Result.build(false);
                }
            }

            Result<Void> buildExpression = updateExpressionTemplatePhysical(param, updateParam, physicalPO);
            if (buildExpression.failed()) {return buildExpression;}

            Result<Void> buildShardNum   = updateShardNumTemplatePhy(param, updateParam, physicalPO);
            if (buildShardNum.failed()) {return buildShardNum;}
        }

        return Result.buildSucc();
    }

    /**
     * 通过集群和名字查询
     *
     * @param cluster      集群
     * @param templateName 名字
     * @return result 不存在返回null
     */
    @Override
    public IndexTemplatePhyInfo getTemplateByClusterAndName(String cluster, String templateName) {
        return ConvertUtil.obj2Obj(indexTemplatePhysicalInfoDAO.getByClusterAndName(cluster, templateName),
            IndexTemplatePhyInfo.class);
    }

    /**
     * 通过集群和名字查询
     *
     * @param cluster      集群
     * @param templateName 名字
     * @return result 不存在返回null
     */
    @Override
    public IndexTemplatePhyInfoWithLogic getTemplateWithLogicByClusterAndName(String cluster, String templateName) {
        return buildIndexTemplatePhysicalWithLogic(indexTemplatePhysicalInfoDAO.getByClusterAndName(cluster, templateName));
    }

    /**
     * 根据物理模板状态获取模板列表
     *
     * @param cluster 集群
     * @param status  状态
     * @return list
     */
    @Override
    public List<IndexTemplatePhyInfo> getTemplateByClusterAndStatus(String cluster, int status) {
        return ConvertUtil.list2List(indexTemplatePhysicalInfoDAO.listByClusterAndStatus(cluster, status),
            IndexTemplatePhyInfo.class);
    }

    /**
     * 获取状态正常的模板列表
     *
     * @param cluster 集群
     * @return list
     */
    @Override
    public List<IndexTemplatePhyInfo> getNormalTemplateByCluster(String cluster) {
        return ConvertUtil.list2List(
            indexTemplatePhysicalInfoDAO.listByClusterAndStatus(cluster, TemplatePhysicalStatusEnum.NORMAL.getCode()),
            IndexTemplatePhyInfo.class);
    }

    @Override
    public Set<String> getMatchNormalLogicIdByCluster(String cluster) {
        return ConvertUtil.list2Set(
                indexTemplatePhysicalInfoDAO.listByMatchClusterAndStatus(cluster, TemplatePhysicalStatusEnum.NORMAL.getCode()),
                x -> x.getLogicId().toString());
    }

    /**
     * 根据集群和分区获取模板列表
     *
     * @param cluster 集群
     * @param racks
     * @return list
     */
    @Override
    public List<IndexTemplatePhyInfo> getNormalTemplateByClusterAndRack(String cluster, Collection<String> racks) {
        if (CollectionUtils.isEmpty(racks)) {
            return Lists.newArrayList();
        }
        List<IndexTemplatePhyInfo> templatePhysicals = getNormalTemplateByCluster(cluster);
        if (CollectionUtils.isEmpty(templatePhysicals)) {
            return Lists.newArrayList();
        }
        return templatePhysicals.stream()
            .filter(templatePhysical -> RackUtils.hasIntersect(templatePhysical.getRack(), racks))
            .collect(Collectors.toList());
    }

    /**
     * 获取模板匹配的索引列表，按着时间排序
     * 注意：
     * 该方法只能识别出那些时间后缀是一样的情况；
     * 如果模板中途修改过时间后缀，则无法识别之前时间后缀的索引
     *
     * @param physicalId 物理模板id
     * @return list
     */
    @Override
    public List<String> getMatchNoVersionIndexNames(Long physicalId) {
        IndexTemplatePhyInfoWithLogic templatePhysicalWithLogic = getTemplateWithLogicById(physicalId);
        if (templatePhysicalWithLogic == null) {
            return Lists.newArrayList();
        }
        Set<String> indices = esIndexService.syncGetIndexNameByExpression(templatePhysicalWithLogic.getCluster(),
            templatePhysicalWithLogic.getExpression());
        if (CollectionUtils.isEmpty(indices)) {
            return Lists.newArrayList();
        }

        Set<String> noVersionIndices = indices.stream()
            .map(indexName -> IndexNameFactory.genIndexNameClear(indexName, templatePhysicalWithLogic.getExpression()))
            .collect(Collectors.toSet());

        List<String> matchIndices = Lists.newArrayList();
        for (String noVersionIndex : noVersionIndices) {
            if (IndexNameFactory.noVersionIndexMatchExpression(noVersionIndex,
                templatePhysicalWithLogic.getExpression(),
                templatePhysicalWithLogic.getLogicTemplate().getDateFormat())) {
                matchIndices.add(noVersionIndex);
            }
        }

        Collections.sort(matchIndices);

        return matchIndices;
    }

    /**
     * 获取模板匹配的索引列表，按着时间排序
     *
     * @param physicalId 物理模板id
     * @return list
     */
    @Override
    public List<String> getMatchIndexNames(Long physicalId) {
        IndexTemplatePhyInfoWithLogic templatePhysicalWithLogic = getNormalAndDeletingTemplateWithLogicById(physicalId);
        if (templatePhysicalWithLogic == null) {
            return Lists.newArrayList();
        }

        List<CatIndexResult> indices = esIndexService.syncCatIndexByExpression(templatePhysicalWithLogic.getCluster(),
            templatePhysicalWithLogic.getExpression());
        if (CollectionUtils.isEmpty(indices)) {
            return Lists.newArrayList();
        }

        List<String> matchIndices = Lists.newArrayList();
        for (CatIndexResult indexResult : indices) {
            LOGGER.info("class=TemplatePhyServiceImpl||method=getMatchIndexNames||msg=fetch should be deleted indices||template={}||status={}||"
                        + "cluster={}||docCount={}||docSize={}",
                templatePhysicalWithLogic.getName(), templatePhysicalWithLogic.getStatus(),
                templatePhysicalWithLogic.getCluster(), indexResult.getDocsCount(), indexResult.getStoreSize());

            if (IndexNameFactory.indexMatchExpression(indexResult.getIndex(), templatePhysicalWithLogic.getExpression(),
                templatePhysicalWithLogic.getLogicTemplate().getDateFormat())) {
                matchIndices.add(indexResult.getIndex());
            }
        }

        Collections.sort(matchIndices);

        return matchIndices;
    }

    /**
     * 批量获取模板信息
     *
     * @param physicalIds 物理模板id
     * @return list
     */
    @Override
    public List<IndexTemplatePhyInfoWithLogic> getTemplateWithLogicByIds(List<Long> physicalIds) {
        if (CollectionUtils.isEmpty(physicalIds)) {
            return Lists.newArrayList();
        }
        List<IndexTemplatePhysicalInfoPO> indexTemplatePhysicalInfoPOS = indexTemplatePhysicalInfoDAO.listByIds(physicalIds);
        return batchBuildTemplatePhysicalWithLogic(indexTemplatePhysicalInfoPOS);
    }

    @Override
    public List<IndexTemplatePhyInfoWithLogic> getTemplateByPhyCluster(String phyCluster) {
        if (phyCluster == null) {
            return new ArrayList<>();
        }

        return batchBuildTemplatePhysicalWithLogic(indexTemplatePhysicalInfoDAO.listByClusterAndStatus(phyCluster, TemplatePhysicalStatusEnum.NORMAL.getCode()));
    }

    /**
     * 根据名字查询
     *
     * @param template 名字
     * @return list
     */
    @Override
    public List<IndexTemplatePhyInfoWithLogic> getTemplateWithLogicByName(String template) {
        List<IndexTemplatePhysicalInfoPO> indexTemplatePhysicalInfoPOS = indexTemplatePhysicalInfoDAO.listByName(template);
        return batchBuildTemplatePhysicalWithLogic(indexTemplatePhysicalInfoPOS);
    }

    /**
     * 获取全量
     *
     * @return list
     */
    @Override
    public List<IndexTemplatePhyInfo> listTemplate() {
        return ConvertUtil.list2List(indexTemplatePhysicalInfoDAO.listAll(), IndexTemplatePhyInfo.class);
    }

    @Override
    public List<IndexTemplatePhyInfo> listTemplateWithCache() {
        try {
            return (List<IndexTemplatePhyInfo>) templatePhyListCache.get("listTemplate", this::listTemplate);
        } catch (Exception e) {
            return listTemplate();
        }
    }

    /**
     * 获取IndexTemplatePhysicalWithLogic
     *
     * @return list
     */
    @Override
    public List<IndexTemplatePhyInfoWithLogic> listTemplateWithLogic() {
        List<IndexTemplatePhysicalInfoPO> indexTemplatePhysicalInfoPOS = indexTemplatePhysicalInfoDAO.listAll();
        return batchBuildTemplatePhysicalWithLogic(indexTemplatePhysicalInfoPOS);
    }

    @Override
    public List<IndexTemplatePhyInfoWithLogic> listTemplateWithLogicWithCache() {
        try {
            return (List<IndexTemplatePhyInfoWithLogic>) templatePhyListCache.get("listTemplateWithLogic", this::listTemplateWithLogic);
        } catch (Exception e) {
            return listTemplateWithLogic();
        }
    }

    @Override
    public Map<String, Integer> getClusterTemplateCountMap() {
        Map<String, Integer> templateCountMap = Maps.newHashMap();
        List<IndexTemplatePhyInfoWithLogic> indexTemplateList = listTemplateWithLogic();
        if (CollectionUtils.isNotEmpty(indexTemplateList)) {
            for (IndexTemplatePhyInfoWithLogic indexTemplate : indexTemplateList) {
                Integer templateCount = templateCountMap.get(indexTemplate.getCluster());
                templateCount = templateCount == null ? 1 : templateCount + 1;
                templateCountMap.put(indexTemplate.getCluster(), templateCount);
            }
        }
        return templateCountMap;
    }

    /**
     * 根绝逻辑模板id列表查询
     *
     * @param logicIds 列表
     * @return list
     */
    @Override
    public List<IndexTemplatePhyInfo> getTemplateByLogicIds(List<Integer> logicIds) {
        return ConvertUtil.list2List(indexTemplatePhysicalInfoDAO.listByLogicIds(logicIds), IndexTemplatePhyInfo.class);
    }

    @Override
    public Result<Void> updateTemplateName(IndexTemplatePhyInfo physical, String operator) throws ESOperateException {
        Result<Void> validResult = validParamUpdateTemplateName(physical);
        if(validResult.failed()) {
            return Result.buildFrom(validResult);
        }

        IndexTemplatePhysicalInfoPO editParam = new IndexTemplatePhysicalInfoPO();
        editParam.setId(physical.getId());
        editParam.setName(physical.getName());

        //更新数据库中物理模板的名称
        boolean succ = 1 == indexTemplatePhysicalInfoDAO.update(editParam);
        if (!succ) {
            return Result.buildFail("修改物理模板失败");
        }

        //更新ES当中存储的物理模板的名称
        IndexTemplatePhysicalInfoPO oldPhysicalPO = indexTemplatePhysicalInfoDAO.getById(physical.getId());
        return Result.build(
            esTemplateService.syncUpdateName(physical.getCluster(), oldPhysicalPO.getName(), physical.getName(), 0));
    }

    @Override
    public Result<Void> updateTemplateExpression(IndexTemplatePhyInfo indexTemplatePhyInfo, String expression, String operator) throws ESOperateException {
        IndexTemplatePhysicalInfoPO updateParam = new IndexTemplatePhysicalInfoPO();
        updateParam.setId(indexTemplatePhyInfo.getId());
        updateParam.setExpression(expression);
        boolean succeed = (1 == indexTemplatePhysicalInfoDAO.update(updateParam));

        if (succeed) {
            esTemplateService.syncUpdateExpression(indexTemplatePhyInfo.getCluster(), indexTemplatePhyInfo.getName(),
                    expression, 0);
        } else {
            LOGGER.warn("class=TemplatePhyServiceImpl||method=updateTemplateExpression||msg=", MSG, indexTemplatePhyInfo.getId(),
                    expression);
            return Result.buildFail("数据库更新失败");
        }
        return Result.buildSucc();
    }

    @Override
    public Result<Void> updateTemplateShardNum(IndexTemplatePhyInfo indexTemplatePhyInfo, Integer shardNum, String operator) throws ESOperateException {
        IndexTemplatePhysicalInfoPO updateParam = new IndexTemplatePhysicalInfoPO();
        updateParam.setId(indexTemplatePhyInfo.getId());
        updateParam.setShard(shardNum);
        boolean succeed = 1 == indexTemplatePhysicalInfoDAO.update(updateParam);
        if (succeed) {
            LOGGER.info("class=TemplatePhyServiceImpl||method=editTemplateFromLogic succeed||physicalId={}||preShardNum={}||currentShardNum={}",
                    indexTemplatePhyInfo.getId(), indexTemplatePhyInfo.getShard(), shardNum);

            esTemplateService.syncUpdateShardNum(indexTemplatePhyInfo.getCluster(), indexTemplatePhyInfo.getName(),
                    shardNum, 0);
        } else {
            LOGGER.warn("class=TemplatePhyServiceImpl||method=updateTemplateShardNum||editTemplateFromLogic fail||physicalId={}||shardNum={}", indexTemplatePhyInfo.getId(),
                    shardNum);
            return Result.buildFail("数据库更新失败");
        }
        return Result.buildSucc();
    }

    @Override
    public Result<Void> updateTemplateRole(IndexTemplatePhyInfo indexTemplatePhyInfo, TemplateDeployRoleEnum templateDeployRoleEnum, String operator) {
        IndexTemplatePhysicalInfoPO updateParam = new IndexTemplatePhysicalInfoPO();
        updateParam.setId(indexTemplatePhyInfo.getId());
        updateParam.setRole(templateDeployRoleEnum.getCode());
        return Result.build(1 == indexTemplatePhysicalInfoDAO.update(updateParam));
    }

    @Override
    public Result<Void> update(IndexTemplatePhysicalInfoDTO indexTemplatePhysicalInfoDTO) {
        IndexTemplatePhysicalInfoPO updateParam = ConvertUtil.obj2Obj(indexTemplatePhysicalInfoDTO, IndexTemplatePhysicalInfoPO.class);
        return Result.build(1 == indexTemplatePhysicalInfoDAO.update(updateParam));
    }

    @Override
    public IndexTemplatePhyInfoWithLogic buildIndexTemplatePhysicalWithLogic(IndexTemplatePhysicalInfoPO physicalPO) {
        if (physicalPO == null) {
            return null;
        }

        IndexTemplatePhyInfoWithLogic indexTemplatePhyWithLogic = ConvertUtil.obj2Obj(physicalPO,
            IndexTemplatePhyInfoWithLogic.class);

        IndexTemplateInfoPO logicPO = indexTemplateInfoDAO.getById(physicalPO.getLogicId());
        if (logicPO == null) {
            LOGGER.warn("class=TemplatePhyServiceImpl||method=buildIndexTemplatePhysicalWithLogic||logic template not exist||logicId={}", physicalPO.getLogicId());
            return indexTemplatePhyWithLogic;
        }
        indexTemplatePhyWithLogic.setLogicTemplate(ConvertUtil.obj2Obj(logicPO, IndexTemplateInfo.class));
        return indexTemplatePhyWithLogic;
    }

    @Override
    public List<IndexTemplatePhyInfo> getTemplateByRegionId(Long regionId) {
        ClusterRegion region = regionRackService.getRegionById(regionId);
        if (AriusObjUtils.isNull(region)) {
            return Lists.newArrayList();
        }

        return getNormalTemplateByClusterAndRack(region.getPhyClusterName(), RackUtils.racks2List(region.getRacks()));
    }

    @Override
    public Map<Integer, Integer> getAllLogicTemplatesPhysicalCount() {
        Map<Integer, Integer> map = new HashMap<>();
        List<IndexTemplatePhysicalInfoPO> list = indexTemplatePhysicalInfoDAO.countListByLogicId();
        if (CollectionUtils.isNotEmpty(list)) {
            map = list.stream().collect(Collectors.toMap(IndexTemplatePhysicalInfoPO::getLogicId, o -> 1, Integer::sum));
        }
        return map;
    }


    /**************************************************** private method ****************************************************/
    private List<IndexTemplatePhyInfoWithLogic> batchBuildTemplatePhysicalWithLogic(List<IndexTemplatePhysicalInfoPO> indexTemplatePhysicalInfoPOS) {
        if (CollectionUtils.isEmpty(indexTemplatePhysicalInfoPOS)) {
            return Lists.newArrayList();
        }

        List<Integer> logicIds = indexTemplatePhysicalInfoPOS.stream().map(IndexTemplatePhysicalInfoPO::getLogicId)
            .collect(Collectors.toList());
        List<IndexTemplateInfoPO> indexTemplateInfoPOS = indexTemplateInfoDAO.listByIds(logicIds);
        Map<Integer, IndexTemplateInfoPO> id2IndexTemplateLogicPOMap = ConvertUtil.list2Map(indexTemplateInfoPOS,
            IndexTemplateInfoPO::getId);

        List<IndexTemplatePhyInfoWithLogic> physicalWithLogics = Lists.newArrayList();
        for (IndexTemplatePhysicalInfoPO indexTemplatePhysicalInfoPO : indexTemplatePhysicalInfoPOS) {
            IndexTemplatePhyInfoWithLogic physicalWithLogic = ConvertUtil.obj2Obj(indexTemplatePhysicalInfoPO,
                IndexTemplatePhyInfoWithLogic.class);
            physicalWithLogic.setLogicTemplate(ConvertUtil
                .obj2Obj(id2IndexTemplateLogicPOMap.get(indexTemplatePhysicalInfoPO.getLogicId()), IndexTemplateInfo.class));

            physicalWithLogics.add(physicalWithLogic);
        }

        return physicalWithLogics;
    }

    private IndexTemplatePhyInfoWithLogic getNormalAndDeletingTemplateWithLogicById(Long physicalId) {
        IndexTemplatePhysicalInfoPO physicalPO = indexTemplatePhysicalInfoDAO.getNormalAndDeletingById(physicalId);
        return buildIndexTemplatePhysicalWithLogic(physicalPO);
    }

    /**
     * 判定是否是合法的shard number.
     *
     * @param shardNum
     * @return
     */
    private boolean isValidShardNum(Integer shardNum) {
        return shardNum != null && shardNum > 0;
    }

    /**
     * 更新物理模板名称时，做参数的校验
     * @param physical 索引物理模板
     * @return 校验结果
     */
    private Result<Void> validParamUpdateTemplateName(IndexTemplatePhyInfo physical) {
        if (AriusObjUtils.isNull(physical)) {
            return Result.buildParamIllegal("输入的物理模板为空");
        }
        if (AriusObjUtils.isNull(physical.getId())) {
            return Result.buildParamIllegal("需要修改的物理模板的Id为空");
        }
        if (AriusObjUtils.isNull(physical.getName())) {
            return Result.buildParamIllegal("需要修改的物理模板的名称为空");
        }

        IndexTemplatePhysicalInfoPO oldIndexTemplatePhy = indexTemplatePhysicalInfoDAO.getById(physical.getId());
        if (AriusObjUtils.isNull(oldIndexTemplatePhy)) {
            return Result.buildParamIllegal("需要修改的物理模板的id对应的原数据不存在");
        }

        return Result.buildSucc();
    }

    private Result<Void> updateShardNumTemplatePhy(IndexTemplateInfoDTO param, IndexTemplatePhysicalInfoPO updateParam, IndexTemplatePhysicalInfoPO physicalPO) throws ESOperateException {
        if (isValidShardNum(param.getShardNum())
                && AriusObjUtils.isChanged(param.getShardNum(), physicalPO.getShard())) {
            updateParam.setId(physicalPO.getId());
            updateParam.setShard(param.getShardNum());
            boolean succeed = 1 == indexTemplatePhysicalInfoDAO.update(updateParam);
            if (succeed) {
                LOGGER.info("class=TemplatePhyServiceImpl||method=updateShardNumTemplatePhy||editTemplateFromLogic succeed||physicalId={}||preShardNum={}||currentShardNum={}",
                        physicalPO.getId(), physicalPO.getShard(), param.getShardNum());

                esTemplateService.syncUpdateRackAndShard(physicalPO.getCluster(), physicalPO.getName(),
                        physicalPO.getRack(), param.getShardNum(), physicalPO.getShardRouting(), 0);
            } else {
                LOGGER.warn("class=TemplatePhyServiceImpl||method=updateShardNumTemplatePhy||msg=", MSG, physicalPO.getId(),
                        param.getExpression());
                return Result.build(false);
            }
        }
        return Result.buildSucc();
    }

    private Result<Void> updateExpressionTemplatePhysical(IndexTemplateInfoDTO param, IndexTemplatePhysicalInfoPO updateParam, IndexTemplatePhysicalInfoPO physicalPO) throws ESOperateException {
        if (AriusObjUtils.isChanged(param.getExpression(), physicalPO.getExpression())) {
            updateParam.setId(physicalPO.getId());
            updateParam.setExpression(param.getExpression());
            boolean succeed = (1 == indexTemplatePhysicalInfoDAO.update(updateParam));
            if (succeed) {
                esTemplateService.syncUpdateExpression(physicalPO.getCluster(), physicalPO.getName(),
                        param.getExpression(), 0);
            } else {
                LOGGER.warn("class=TemplatePhyServiceImpl||method=updateExpressionTemplatePhysical||msg=", MSG, physicalPO.getId(),
                        param.getExpression());
                return Result.build(false);
            }
        }
        return Result.buildSucc();
    }
}
