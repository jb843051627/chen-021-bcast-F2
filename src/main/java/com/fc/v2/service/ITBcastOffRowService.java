package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.List;

import com.fc.v2.model.auto.TBcastOffRow;
import com.fc.v2.model.custom.BookReceipt;

/**
 * 运行情况报送核收行 Service接口。
 *
 * 形状约束（一条收口）：
 * 整册进门只认 {@link #receiveBook} 这一个提交入口，册内的行没有单条新增/改动把手；
 * 一册收着几行、退回几行，一律以该入口返回的 {@link BookReceipt} 为准。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITBcastOffRowService {

    /** 按主键回查单行（抽行单看：行上的去向须与回执报数同源） */
    TBcastOffRow selectTBcastOffRowById(Long id);

    /**
     * 整册进门收口（新名字，按册收）——全处唯一的记账入口。
     *
     * 进门先整册点行：行数超过单册上限的，原册送回，一行一簿都不落库（receipt.overLimit=true）。
     * 一个批号进门定死：头一遍逐行核收落账（同事务物化册子回执三数）；
     * 同批号走第二遍只照面不记账——逐行与库存照一遍即停，库里不添不减，去向维持头一遍结果。
     * 确要改动，先走 {@link #withdrawBook} 撤册。
     *
     * 行去向：认监测台代号（与在册台站底册 site_no 精确比对，拿台名比的、含糊的、关停台一律不算对上）；
     * 同一代号在一册占多行的，分钟数按行次顺序合并，合并后高于本季上限 limitMinutes 的，
     * 仅致超的那一行原路退回（缘由写在行上），同册别的行不受牵连。
     *
     * @param batchNo      批号（进门定死，行内自带批号一律被它盖过）
     * @param season       报送季，形如 2026Q3；旧入口调用可传 null
     * @param rows         册内各行，行次在进门时按先后自动占位（从 1 起），退回也带原行次
     * @param limitMinutes 本季停播分钟上限；null 表示本季不封顶
     */
    BookReceipt receiveBook(String batchNo, String season, List<TBcastOffRow> rows, BigDecimal limitMinutes);

    /**
     * 旧提交入口：方法签名照旧、不加参数；内部委托 {@link #receiveBook}（不封顶口径）。
     * 返回收下行数；同批号重复提交不重复入库（拿库存现值）；超单册上限返回 -1 且整批不入库；空批次返回 0。
     */
    int submitBatch(String batchNo, List<TBcastOffRow> rows);

    /** 该批次退回行明细（按原报行次升序），失败/退回行必带行次与缘由 */
    List<TBcastOffRow> listErrors(String batchNo);

    /**
     * 回执三数（本册行数/收下/退回）：不许屏上临时数，从库里逐行点上来；
     * 与册子物化值对不齐时，diffs 当场列出差在哪几行。
     */
    BookReceipt getReceipt(String batchNo);

    /** 册内逐行（老册翻起来一条不漏：已挪出另放的归档行仍在，按原行次升序；撤册软删行除外） */
    List<TBcastOffRow> listBookRows(String batchNo);

    /** 现行名单：尚未着落（未销）且未被挪走的行，归档行不再摆出 */
    List<TBcastOffRow> listCurrentRows(String itemCode);

    /**
     * 撤册：把该册库内行作删除标记、腾开批号。头一遍落下的结果只能由这道手续作废，
     * 撤完同批号方可重新进门。空册（无行的空簿）撤簿本身。
     * @return 撤掉的在册行数；批号下无在册册返回 -1
     */
    int withdrawBook(String batchNo, String reason);

    /**
     * 季末挪行：截至 currentSeason，连着两个报送季（季差≥2）仍未销的行，
     * 挪出当前名册（archived=1）。挪走不是抹掉——老册按批号翻仍一条不漏。
     * @return 实际挪走的行数
     */
    int archiveStaleRows(String currentSeason);
}
