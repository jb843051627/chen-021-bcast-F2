package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TBcastOffRow;
import com.fc.v2.model.custom.BcastOffRowReceipt;

/**
 * 运行情况报送核收行 Service接口（batch-process 形状：整册提交，无单条增删改）
 *
 * <p>收口规矩：</p>
 * <ul>
 *   <li>一册一个批号，进门就定死；一季来几册都行，这一册的行不许混进那一册。</li>
 *   <li>整册进门只认本接口这一个提交入口，册内的行不给出单条新增或改动的把手。</li>
 *   <li>行次在进册时自动占位，按先后排定；退回去的行往后翻它仍旧带着原先那个行次。</li>
 *   <li>每一行只有一个去向：未销(0)、收下(1)、退回(2)。收下只认监测台代号精确对上
 *       底册在册台；拿台名去比的、读起来含含糊糊的，一律不算对上。</li>
 *   <li>同一个批号走第二遍只照面不记账：库里既不添行也不减行，头一遍落下的结果抹不掉；
 *       确实要动，先办撤册（{@link #withdrawBatch}）那道手续。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITBcastOffRowService {

    /** 按主键回查单行（抽一行单看，看到的就是库里落定的去向） */
    TBcastOffRow selectTBcastOffRowById(Long id);

    /**
     * 整册提交：一册走下来收着了几行，以本方法的返回值为准——写定后从库里一行一行点上来，
     * 不在屏上临时数。
     *
     * <p>行上的停播分钟数与本季满播小时数两个数一并发过来，谁也不替谁补；缺了、为负的，
     * 这一行收不着去向，落未销。同一监测台在一册里占几行的，分钟数往一块儿并，并到一块儿
     * 高过这一季定下的上限的，高出那部分所在的一行原路退回、缘由写在行上（remark），
     * 同册别的行不受它影响。</p>
     *
     * <p>返回值约定：</p>
     * <ul>
     *   <li>&gt;=0：本册落在库里的收下数；空批次（一行没有）返回 0；</li>
     *   <li>-1：整册不收——批号空缺，或整册点行超了处里定下的上限，原册送回去、一行也不落库；</li>
     *   <li>同一批号走第二遍：只与库里已落下的行照一遍面，不添行不减行，返回库里已落定的收下数。</li>
     * </ul>
     *
     * @param batchNo 册批号（进门就定死，册内每行都落这个号）
     * @param rows    册内各行，按纸面先后排定；行次由进册时自动占位，不必自带
     * @return 本册落在库里的收下数；整册不收返回 -1
     */
    int submitBatch(String batchNo, List<TBcastOffRow> rows);

    /**
     * 本册被原路退回的行（失败的行把纸面上那一行的行次带回来），按原报行次升序；
     * 行上的 remark 写着退回缘由。
     */
    List<TBcastOffRow> listErrors(String batchNo);

    /**
     * 老册整册翻页：该批号落定的全部行（含已挪出现行名册的），按原报行次升序，一条不漏。
     */
    List<TBcastOffRow> listByBatch(String batchNo);

    /**
     * 现行名单：未销且仍压着（未挪出）的行。屏上摆的就是这一份。
     */
    List<TBcastOffRow> listCurrentPending();

    /**
     * 回执轧账：本册的行数、收下的、退回的，三个数从库里一行一行点上来点齐；
     * 后两个并起来应当等于头一个，对不齐的，差在哪几行当场列在回执里。
     * 整册点下来一行都收不着的，回执用字落"空册"；"核讫"是给有数的册准备的。
     */
    BcastOffRowReceipt receipt(String batchNo);

    /**
     * 撤册：整册作废出库。办过这道手续，同一批号才可按新册重新进门；
     * 不办手续直接把同批号再走一遍的，只照面不记账。
     *
     * @return 出库的行数
     */
    int withdrawBatch(String batchNo);

    /**
     * 挪出：连着两个报送季都没有着落（始终未销）的行，挪出当前名册另放一处，
     * 屏上的现行名单不再摆它。挪走不是抹掉——老册翻起来仍旧一条不漏。
     *
     * @param at 以哪一刻为界点算（为空不办事，返回 0）
     * @return 本轮挪出的行数
     */
    int archiveStalePending(Date at);
}
