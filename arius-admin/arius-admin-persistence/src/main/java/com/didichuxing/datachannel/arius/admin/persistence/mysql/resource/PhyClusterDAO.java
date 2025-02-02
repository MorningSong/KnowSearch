package com.didichuxing.datachannel.arius.admin.persistence.mysql.resource;

import com.didichuxing.datachannel.arius.admin.common.bean.dto.cluster.ClusterPhyConditionDTO;
import com.didichuxing.datachannel.arius.admin.common.bean.po.cluster.ClusterPhyPO;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * 物理集群信息 DAO
 *
 * @author ohushenglin_v
 */
@Repository
public interface PhyClusterDAO {

    /**
     * 条件查询物理集群信息
     * @param param 查询条件
     * @return  物理集群列表
     */
    List<ClusterPhyPO> listByCondition(ClusterPhyPO param);

    /**
     * 分页查询物理集群信息
     * @param conditionDTO   查询条件
     * @return  物理集群列表
     */
    List<ClusterPhyPO> pagingByCondition(ClusterPhyConditionDTO conditionDTO);

    /**
     * 跟据查询条件获取物理集群总量
     * @param conditionDTO     查询条件
     * @return  集群数量
     */
    long getTotalHitByCondition(ClusterPhyConditionDTO conditionDTO);

    /**
     * insert
     * @param param 待插入数量
     * @return  变更数量
     */
    int insert(ClusterPhyPO param);

    /**
     * update
     * @param param 待更新数据
     * @return  变更数量
     */
    int update(ClusterPhyPO param);

    /**
     * 更新插件ID
     * @param plugIds 插件ID
     * @param clusterId 集群ID
     * @return 变更数量
     */
    int updatePluginIdsById(@Param("plugIds") String plugIds, @Param("clusterId") Integer clusterId);

    /**
     * 删除集群
     * @param clusterId 集群ID
     * @return 变更数量
     */
    int delete(Integer clusterId);

    /**
     * 跟据ID获取单个集群信息
     * @param clusterId 集群ID
     * @return 集群信息
     */
    ClusterPhyPO getById(Integer clusterId);

    /**
     * 跟据集群名称查询单个集群
     * @param clusterName 集群名称
     * @return 集群信息
     */
    ClusterPhyPO getByName(String clusterName);

    /**
     * 跟据多个集群名称获取集群列表
     * @param names 集群名称列表
     * @return  集群列表
     */
    List<ClusterPhyPO> listByNames(List<String> names);

    /**
     * 查询所有的集群列表
     * @return 集群列表
     */
    List<ClusterPhyPO> listAll();

    /**
     * 查询所有的集群名称
     * @return 集群名称列表
     */
    List<String> listAllName();

    /**
     * 跟据多个集群ID获取集群列表
     * @param ids 集群IDs
     * @return 集群列表
     */
    List<ClusterPhyPO> listByIds(@Param("ids") Set<Long> ids);

    /**
     * 获取绑定对应安装包的集群数量
     * @param packageId 安装包id
     * @return  long count
     */
    long getTotalHitByPackageId(@Param("packageId") Long packageId);

}