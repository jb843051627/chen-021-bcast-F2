package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBcastCaseFlowMapper;
import com.fc.v2.model.auto.TBcastCaseFlow;
import com.fc.v2.service.ITBcastCaseFlowService;

/**
 * 播出事故处置单 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBcastCaseFlowServiceImpl implements ITBcastCaseFlowService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TBcastCaseFlowMapper bcastCaseFlowMapper;

    @Override
    public TBcastCaseFlow selectTBcastCaseFlowById(Long id) {
        return this.bcastCaseFlowMapper.selectById(id);
    }

    @Override
    public List<TBcastCaseFlow> selectTBcastCaseFlowList(QueryWrapper<TBcastCaseFlow> queryWrapper) {
        return this.bcastCaseFlowMapper.selectList(queryWrapper);
    }

    @Override
    public TBcastCaseFlow advance(Long id, String remark) {
        TBcastCaseFlow r = this.bcastCaseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        r.setStage(Math.min(st + 2, MAX_STAGE));
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.bcastCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public TBcastCaseFlow rollback(Long id, String remark) {
        TBcastCaseFlow r = this.bcastCaseFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        r.setStage(0);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.bcastCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TBcastCaseFlow r = this.bcastCaseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.bcastCaseFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TBcastCaseFlow r = this.bcastCaseFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.bcastCaseFlowMapper.deleteById(id) > 0;
    }

}
