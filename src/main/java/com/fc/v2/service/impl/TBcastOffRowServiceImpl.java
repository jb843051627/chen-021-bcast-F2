package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBcastOffRowMapper;
import com.fc.v2.model.auto.TBcastOffRow;
import com.fc.v2.service.ITBcastOffRowService;

/**
 * 运行情况报送核收行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBcastOffRowServiceImpl implements ITBcastOffRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TBcastOffRowMapper bcastOffRowMapper;

    @Override
    public TBcastOffRow selectTBcastOffRowById(Long id) {
        return this.bcastOffRowMapper.selectById(id);
    }

    @Override
    public int submitBatch(String batchNo, List<TBcastOffRow> rows) {
        String no = rows.get(0).getBatchNo();
        java.util.List<TBcastOffRow> errors = new java.util.ArrayList<TBcastOffRow>();
        int seq = 0;
        for (TBcastOffRow r : rows) {
            if (r.getItemCode() == null || r.getItemCode().trim().isEmpty()
                    || r.getQty() == null
                    || r.getQty().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                seq++;
                r.setRowNo(Integer.valueOf(seq));
                r.setBatchNo(no);
                r.setStatus(STATUS_FAIL);
                this.bcastOffRowMapper.insert(r);
                errors.add(r);
            }
        }
        if (!errors.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TBcastOffRow r : rows) {
            r.setBatchNo(no);
            r.setStatus(STATUS_OK);
            this.bcastOffRowMapper.insert(r);
            ok++;
        }
        return ok;
    }

    @Override
    public List<TBcastOffRow> listErrors(String batchNo) {
        return this.bcastOffRowMapper.selectList(new QueryWrapper<TBcastOffRow>()
                .eq("batch_no", batchNo).eq("status", STATUS_FAIL));
    }
}
