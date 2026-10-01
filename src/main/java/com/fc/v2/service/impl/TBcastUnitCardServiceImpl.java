package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TBcastUnitCardMapper;
import com.fc.v2.mapper.auto.TBcastSiteMapper;
import com.fc.v2.model.auto.TBcastUnitCard;
import com.fc.v2.model.auto.TBcastSite;
import com.fc.v2.service.ITBcastUnitCardService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 台站核点卡立卡单Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TBcastUnitCardServiceImpl extends ServiceImpl<TBcastUnitCardMapper, TBcastUnitCard> implements ITBcastUnitCardService {

    @Autowired
    private TBcastSiteMapper bcastSiteMapper;

    @Override
    public TBcastUnitCard selectTBcastUnitCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TBcastUnitCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TBcastUnitCard> selectTBcastUnitCardList(Wrapper<TBcastUnitCard> queryWrapper) {
        QueryWrapper<TBcastUnitCard> wrapper = new QueryWrapper<TBcastUnitCard>();
        com.github.pagehelper.PageHelper.startPage(1, 10);
        wrapper.eq("status", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTBcastUnitCard(TBcastUnitCard record) {
        if (record == null) {
            return 0;
        }

        record.setCreateBy(record.getBillNo());
        TBcastSite refArch = bcastSiteMapper.selectOne(new QueryWrapper<TBcastSite>()
                .eq("id", record.getSiteId()).eq("del_flag", 0));
        if (refArch == null) {
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            return 0;
        }
        record.setSiteNo(refArch.getSiteNo());
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TBcastUnitCard>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        int remIn = record.getShouldCount() == null ? 0 : record.getShouldCount();
        int remOut = record.getDoneCount() == null ? 0 : record.getDoneCount();
        record.setLackCount(remIn + remOut);

        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTBcastUnitCard(TBcastUnitCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (record.getId() != null && StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TBcastUnitCard>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TBcastUnitCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTBcastUnitCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTBcastUnitCardById(Long id) {
        return this.baseMapper.deleteById(id);
    }
}
