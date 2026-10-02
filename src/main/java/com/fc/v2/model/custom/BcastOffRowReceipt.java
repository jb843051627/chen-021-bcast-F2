package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TBcastOffRow;

/**
 * 运行情况报送册回执（轧账结果）。
 *
 * 回执那一栏只落三个数：本册的行数、收下的、退回的，后两个并起来应当等于头一个。
 * 三个数一律从库里一行一行点上来点齐，不许在屏上临时数；对不齐的，差在哪几行当场列在
 * {@link #diffRows} 里，一句"差不多"不算交代。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class BcastOffRowReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 回执用字：核讫。给有数的册准备的——收下数≥1 才落这两个字 */
    public static final String WORD_VERIFIED = "核讫";

    /** 回执用字：空册。整册点下来一行都收不着，回执那一栏就落这两个字 */
    public static final String WORD_EMPTY = "空册";

    /** 运行情况报送册批号 */
    private String batchNo;

    /** 本册的行数（库里该批号落定的全部行） */
    private int totalRows;

    /** 收下的（行落地情形=已收下） */
    private int acceptedRows;

    /** 退回的（行落地情形=已退回） */
    private int returnedRows;

    /** 未销的（压着还没去向的） */
    private int pendingRows;

    /** 回执用字：收下数≥1 落"核讫"，一行都收不着落"空册" */
    private String word;

    /** 轧账是否对齐：收下的+退回的 == 本册的行数 */
    private boolean balanced;

    /** 对不齐时差在哪几行（未销行，按原报行次升序）；对齐时为空表 */
    private List<TBcastOffRow> diffRows = new ArrayList<TBcastOffRow>();

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
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

    public int getPendingRows() {
        return pendingRows;
    }

    public void setPendingRows(int pendingRows) {
        this.pendingRows = pendingRows;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public boolean isBalanced() {
        return balanced;
    }

    public void setBalanced(boolean balanced) {
        this.balanced = balanced;
    }

    public List<TBcastOffRow> getDiffRows() {
        return diffRows;
    }

    public void setDiffRows(List<TBcastOffRow> diffRows) {
        this.diffRows = diffRows;
    }
}
