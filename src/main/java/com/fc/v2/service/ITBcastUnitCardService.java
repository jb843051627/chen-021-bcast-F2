package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TBcastUnitCard;

import java.util.List;

/**
 * 台站核点卡立卡单 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITBcastUnitCardService {

    /** 按主键查询 */
    TBcastUnitCard selectTBcastUnitCardById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TBcastUnitCard> selectTBcastUnitCardList(Wrapper<TBcastUnitCard> queryWrapper);

    /** 新增 */
    int insertTBcastUnitCard(TBcastUnitCard record);

    /** 修改 */
    int updateTBcastUnitCard(TBcastUnitCard record);

    /** 批量删除 */
    int deleteTBcastUnitCardByIds(String ids);

    /** 按主键删除 */
    int deleteTBcastUnitCardById(Long id);
}
