package com.fc.v2.model.custom;

import java.math.BigDecimal;
import java.util.List;

import com.fc.v2.model.auto.TBcastOffRow;

/**
 * 整册进门请求体：批号 + 报送季 + 本季停播分钟上限 + 册内各行。
 * 行不给出单条新增/改动的把手，只随整册一次交进服务层那一个入口。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class BookInVo implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 批号（进门定死，行内自带不认） */
    private String batchNo;

    /** 报送季，形如 2026Q3 */
    private String season;

    /** 本季停播分钟上限；空表示本季不封顶 */
    private BigDecimal limitMinutes;

    /** 册内各行，行次进门时按先后自动占位；每行带停播分钟与满播小时，两个数一并发来 */
    private List<TBcastOffRow> rows;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public BigDecimal getLimitMinutes() {
        return limitMinutes;
    }

    public void setLimitMinutes(BigDecimal limitMinutes) {
        this.limitMinutes = limitMinutes;
    }

    public List<TBcastOffRow> getRows() {
        return rows;
    }

    public void setRows(List<TBcastOffRow> rows) {
        this.rows = rows;
    }
}
