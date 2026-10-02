package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.List;

/**
 * 整册进门收口的回执（服务层唯一提交入口的返回值）。
 * 三计数（total/accepted/returned）一律由库里逐行点出，不在屏上临时数；
 * 失败行必须把纸面上那一行的行次带回来——{@link RowOutcome#rowNo}。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class BookReceipt implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 批号（进门定死，入参带什么都以入口参数为准） */
    private String batchNo;

    /** 报送季，形如 2026Q3 */
    private String season;

    /** false=头一遍进门已记账；true=同批号第二遍，只照面、库里未动 */
    private boolean compareOnly;

    /** 整册点数超过单册行数上限：原册送回，一行一簿都不落库 */
    private boolean overLimit;

    /** 回执栏：核讫 / 空册；超限原册退回时为 null */
    private String receipt;

    /** 本册行数（库点） */
    private int totalRows;

    /** 收下数（库点） */
    private int acceptedRows;

    /** 退回数（库点） */
    private int returnedRows;

    /** 逐行去向（按原报行次升序），头一遍为本次判定，照面遍为库存现状 */
    private List<RowOutcome> rows = new ArrayList<RowOutcome>();

    /** 仅照面遍使用：来行与库存行逐项比对结果 */
    private List<RowCompare> compares = new ArrayList<RowCompare>();

    /** 轧账对不上的差异行说明；空单表示点齐 */
    private List<String> diffs = new ArrayList<String>();

    /** 单行人账结果（失败/退回必带 rowNo 与缘由） */
    public static class RowOutcome implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        /** 进门按先后自动占位的行次；退回、翻册、归档都带着它，不重排 */
        private Integer rowNo;
        /** 监测台代号 */
        private String itemCode;
        /** 本行停播分钟数 */
        private java.math.BigDecimal qty;
        /** 本季满播小时数（与分钟数各走各的，谁也不替谁补） */
        private java.math.BigDecimal hoursNum;
        /** 0未销 1收下 2退回 */
        private Integer status;
        /** 同册同代号合并后的分钟总量（用于超上限判定） */
        private java.math.BigDecimal mergedQty;
        /** 退回缘由，写在行上 */
        private String reason;

        public Integer getRowNo() {
            return rowNo;
        }

        public void setRowNo(Integer rowNo) {
            this.rowNo = rowNo;
        }

        public String getItemCode() {
            return itemCode;
        }

        public void setItemCode(String itemCode) {
            this.itemCode = itemCode;
        }

        public java.math.BigDecimal getQty() {
            return qty;
        }

        public void setQty(java.math.BigDecimal qty) {
            this.qty = qty;
        }

        public java.math.BigDecimal getHoursNum() {
            return hoursNum;
        }

        public void setHoursNum(java.math.BigDecimal hoursNum) {
            this.hoursNum = hoursNum;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public java.math.BigDecimal getMergedQty() {
            return mergedQty;
        }

        public void setMergedQty(java.math.BigDecimal mergedQty) {
            this.mergedQty = mergedQty;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    /** 同批号第二遍照面：一行来纸与一行库存逐项对照，不写库 */
    public static class RowCompare implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        private Integer rowNo;
        /** 逐项是否一致（代号、分钟、小时） */
        private boolean consistent;
        /** 库存行去向 0未销 1收下 2退回；库存无此行次为 null */
        private Integer storedStatus;
        /** 不一致处的文字说明 */
        private String detail;

        public Integer getRowNo() {
            return rowNo;
        }

        public void setRowNo(Integer rowNo) {
            this.rowNo = rowNo;
        }

        public boolean isConsistent() {
            return consistent;
        }

        public void setConsistent(boolean consistent) {
            this.consistent = consistent;
        }

        public Integer getStoredStatus() {
            return storedStatus;
        }

        public void setStoredStatus(Integer storedStatus) {
            this.storedStatus = storedStatus;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }
    }

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

    public boolean isCompareOnly() {
        return compareOnly;
    }

    public void setCompareOnly(boolean compareOnly) {
        this.compareOnly = compareOnly;
    }

    public boolean isOverLimit() {
        return overLimit;
    }

    public void setOverLimit(boolean overLimit) {
        this.overLimit = overLimit;
    }

    public String getReceipt() {
        return receipt;
    }

    public void setReceipt(String receipt) {
        this.receipt = receipt;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getAcceptedRows() {
        return acceptedRows;
    }

    public void setAcceptedRows(int acceptedRows) {
        this.acceptedRows = acceptedRows;
    }

    public int getReturnedRows() {
        return returnedRows;
    }

    public void setReturnedRows(int returnedRows) {
        this.returnedRows = returnedRows;
    }

    public List<RowOutcome> getRows() {
        return rows;
    }

    public void setRows(List<RowOutcome> rows) {
        this.rows = rows;
    }

    public List<RowCompare> getCompares() {
        return compares;
    }

    public void setCompares(List<RowCompare> compares) {
        this.compares = compares;
    }

    public List<String> getDiffs() {
        return diffs;
    }

    public void setDiffs(List<String> diffs) {
        this.diffs = diffs;
    }
}
