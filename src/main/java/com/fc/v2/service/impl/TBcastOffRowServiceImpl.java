package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TBcastOffRowMapper;
import com.fc.v2.mapper.auto.TBcastSiteMapper;
import com.fc.v2.model.auto.TBcastOffRow;
import com.fc.v2.model.auto.TBcastSite;
import com.fc.v2.model.custom.BcastOffRowReceipt;
import com.fc.v2.service.ITBcastOffRowService;

/**
 * 运行情况报送核收行 Service业务层处理（batch-process 形状：整册提交）
 *
 * <p>整册进门只认 {@link #submitBatch} 这一个提交入口；同一个批号走第二遍只照面不记账，
 * 这一条也只在本层成立，页面上不另摆一套说法。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBcastOffRowServiceImpl implements ITBcastOffRowService {

    private static final Logger log = LoggerFactory.getLogger(TBcastOffRowServiceImpl.class);

    /** 单册行数上限：处里定下的那个数，进门先整册点行，超了原册送回、一行不落库 */
    private static final int MAX_ROWS = 500;

    /**
     * 这一季定下的停播分钟数上限：同一监测台在一册里的分钟数并到一块儿，
     * 高过这条线的，高出那部分所在的一行原路退回。换季调限改这里。
     */
    private static final BigDecimal QUARTER_QTY_CAP = new BigDecimal("60");

    /** 未销行压着的宽限：连着两个报送季（按六个月计）都没着落的，挪出现行名册 */
    private static final int STALE_PENDING_MONTHS = 6;

    /** 行落地情形：未销（收不着去向，先压着） */
    private static final int STATUS_PENDING = 0;
    /** 行落地情形：已收下 */
    private static final int STATUS_OK = 1;
    /** 行落地情形：已退回 */
    private static final int STATUS_RETURN = 2;

    /** 挪出标记：现行 */
    private static final int ARC_CURRENT = 0;
    /** 挪出标记：已挪出（另放一处，不是抹掉） */
    private static final int ARC_OUT = 1;

    @javax.annotation.Resource
    private TBcastOffRowMapper bcastOffRowMapper;

    @javax.annotation.Resource
    private TBcastSiteMapper bcastSiteMapper;

    @Override
    public TBcastOffRow selectTBcastOffRowById(Long id) {
        return this.bcastOffRowMapper.selectById(id);
    }

    /**
     * 整册提交。一册的行与回执报出来的数同一次落下来：要么整册落定，要么整册不动。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TBcastOffRow> rows) {
        // 一册一个批号，进门就定死；报不出批号的册进不了门
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return -1;
        }
        String no = batchNo.trim();
        // 空批次：一行没有，什么都不落
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 进门先整册点行：超了处里定下的上限，原册送回去、一行也不落在库里
        if (rows.size() > MAX_ROWS) {
            log.info("批号{}整册{}行超了单册上限{}行，原册送回、一行不落", no, rows.size(), MAX_ROWS);
            return -1;
        }
        // 同一个批号走第二遍：只把手放在比对上，不把手放在记账上
        Integer landed = this.bcastOffRowMapper.selectCount(new QueryWrapper<TBcastOffRow>()
                .eq("batch_no", no).eq("del_flag", 0));
        if (landed != null && landed > 0) {
            compareOnly(no, rows);
            // 头一遍落下的结果抹不掉：回执以库里已落定的为准
            return countAccepted(no);
        }

        // 底册在册台代号：收下只认代号精确对上；关停的不算，拿台名去比的更不算
        Set<String> liveCodes = loadLiveSiteCodes();

        // 逐行定去向。行次自动占位，按先后排定；批号逐行落死，不许混册
        Map<String, BigDecimal> mergedQty = new HashMap<String, BigDecimal>();
        List<TBcastOffRow> landedRows = new ArrayList<TBcastOffRow>(rows.size());
        int seq = 0;
        for (TBcastOffRow raw : rows) {
            TBcastOffRow r = raw == null ? new TBcastOffRow() : raw;
            seq++;
            r.setBatchNo(no);
            r.setRowNo(Integer.valueOf(seq));
            r.setDelFlag(Integer.valueOf(0));
            r.setArcFlag(Integer.valueOf(ARC_CURRENT));

            String code = r.getItemCode() == null ? "" : r.getItemCode().trim();
            r.setItemCode(code.isEmpty() ? null : code);
            boolean codeOk = !code.isEmpty() && liveCodes.contains(code);
            boolean qtyOk = r.getQty() != null && r.getQty().compareTo(BigDecimal.ZERO) >= 0;
            boolean hoursOk = r.getHoursNum() != null && r.getHoursNum().compareTo(BigDecimal.ZERO) >= 0;

            if (codeOk && qtyOk) {
                // 同一监测台在一册里占几行的，行上的分钟数往一块儿并
                BigDecimal total = mergedQty.get(code);
                total = total == null ? r.getQty() : total.add(r.getQty());
                mergedQty.put(code, total);
                if (hoursOk) {
                    if (total.compareTo(QUARTER_QTY_CAP) > 0) {
                        // 并到一块儿高过本季上限：高出那部分所在的一行原路退回，缘由写在行上
                        r.setStatus(Integer.valueOf(STATUS_RETURN));
                        r.setRemark("与本册同台并算累计停播" + total.stripTrailingZeros().toPlainString()
                                + "分钟，高过这一季定下的上限" + QUARTER_QTY_CAP.stripTrailingZeros().toPlainString()
                                + "分钟，高出部分所在行原路退回");
                    } else {
                        r.setStatus(Integer.valueOf(STATUS_OK));
                    }
                } else {
                    // 两个数一并发过来，谁也不替谁补：缺了、为负的，收不着去向
                    r.setStatus(Integer.valueOf(STATUS_PENDING));
                    r.setRemark("停播分钟数与本季满播小时数须两个一齐报齐且不为负，未销待核");
                }
            } else {
                // 代号对不上底册在册台（含代号空缺）：一律不算对上，收不着去向
                r.setStatus(Integer.valueOf(STATUS_PENDING));
                r.setRemark("监测台代号对不上底册在册台，未销待核");
            }
            landedRows.add(r);
        }
        for (TBcastOffRow r : landedRows) {
            this.bcastOffRowMapper.insert(r);
        }
        // 收着了几行以库里点上来的为准，不在屏上临时数
        return countAccepted(no);
    }

    @Override
    public List<TBcastOffRow> listErrors(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return new ArrayList<TBcastOffRow>();
        }
        return this.bcastOffRowMapper.selectList(new QueryWrapper<TBcastOffRow>()
                .eq("batch_no", batchNo.trim())
                .eq("status", STATUS_RETURN)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    @Override
    public List<TBcastOffRow> listByBatch(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return new ArrayList<TBcastOffRow>();
        }
        // 老册翻起来一条不漏：已挪出的也照翻，只滤掉删除标记
        return this.bcastOffRowMapper.selectList(new QueryWrapper<TBcastOffRow>()
                .eq("batch_no", batchNo.trim())
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    @Override
    public List<TBcastOffRow> listCurrentPending() {
        // 屏上的现行名单：未销且仍压着（未挪出）的
        return this.bcastOffRowMapper.selectList(new QueryWrapper<TBcastOffRow>()
                .eq("status", STATUS_PENDING)
                .eq("del_flag", 0)
                .eq("arc_flag", ARC_CURRENT)
                .orderByAsc("batch_no", "row_no"));
    }

    @Override
    public BcastOffRowReceipt receipt(String batchNo) {
        BcastOffRowReceipt receipt = new BcastOffRowReceipt();
        receipt.setBatchNo(batchNo);
        List<TBcastOffRow> rows = listByBatch(batchNo);
        int accepted = 0;
        int returned = 0;
        List<TBcastOffRow> diff = new ArrayList<TBcastOffRow>();
        // 三个数从库里一行一行点上来点齐
        for (TBcastOffRow r : rows) {
            Integer st = r.getStatus();
            if (st != null && st.intValue() == STATUS_OK) {
                accepted++;
            } else if (st != null && st.intValue() == STATUS_RETURN) {
                returned++;
            } else {
                // 未销的就是轧不齐时差的那几行，当场列出来
                diff.add(r);
            }
        }
        receipt.setTotalRows(rows.size());
        receipt.setAcceptedRows(accepted);
        receipt.setReturnedRows(returned);
        receipt.setPendingRows(diff.size());
        receipt.setBalanced(accepted + returned == rows.size());
        receipt.setDiffRows(diff);
        // 一行都收不着落"空册"；"核讫"是给有数的册准备的
        receipt.setWord(accepted >= 1 ? BcastOffRowReceipt.WORD_VERIFIED : BcastOffRowReceipt.WORD_EMPTY);
        return receipt;
    }

    @Override
    public int withdrawBatch(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return 0;
        }
        // 撤册：整册作废出库。办过这道手续，同一批号才可按新册重新进门
        return this.bcastOffRowMapper.delete(new QueryWrapper<TBcastOffRow>()
                .eq("batch_no", batchNo.trim()));
    }

    @Override
    public int archiveStalePending(Date at) {
        if (at == null) {
            return 0;
        }
        // 连着两个报送季（按六个月计）都没着落：从落库那一刻起始终未销、压到现在的
        Calendar cal = Calendar.getInstance();
        cal.setTime(at);
        cal.add(Calendar.MONTH, -STALE_PENDING_MONTHS);
        Date cutoff = cal.getTime();
        // 挪出当前名册另放一处：只挪未销且现行的；挪走不是抹掉，老册照翻
        TBcastOffRow move = new TBcastOffRow();
        move.setArcFlag(Integer.valueOf(ARC_OUT));
        int moved = this.bcastOffRowMapper.update(move, new UpdateWrapper<TBcastOffRow>()
                .eq("status", STATUS_PENDING)
                .eq("del_flag", 0)
                .eq("arc_flag", ARC_CURRENT)
                .lt("create_time", cutoff));
        if (moved > 0) {
            log.info("以{}为界，连着两个报送季未销的行挪出现行名册{}行", cutoff, moved);
        }
        return moved;
    }

    /**
     * 同一个批号走第二遍：来的一行一行与库里已经落下的照一遍面，照完就停手——
     * 该是什么去向还是什么去向，库里既不添行也不减行。出入只记日志，不落库。
     */
    private void compareOnly(String batchNo, List<TBcastOffRow> rows) {
        List<TBcastOffRow> stored = listByBatch(batchNo);
        Map<Integer, TBcastOffRow> byRowNo = new HashMap<Integer, TBcastOffRow>();
        for (TBcastOffRow s : stored) {
            byRowNo.put(s.getRowNo(), s);
        }
        int same = 0;
        int diff = 0;
        for (int i = 0; i < rows.size(); i++) {
            TBcastOffRow in = rows.get(i);
            TBcastOffRow db = byRowNo.get(Integer.valueOf(i + 1));
            if (in != null && db != null && sameFace(in, db)) {
                same++;
            } else {
                diff++;
            }
        }
        // 库里多出来、来册没带到的行，也算出入
        if (stored.size() > rows.size()) {
            diff += stored.size() - rows.size();
        }
        log.info("批号{}第二遍进门只照面不记账：来{}行对库{}行，一致{}行、出入{}行，库不动",
                batchNo, rows.size(), stored.size(), same, diff);
    }

    /** 照面：代号、停播分钟数、满播小时数三样都对得上才算同一行 */
    private boolean sameFace(TBcastOffRow in, TBcastOffRow db) {
        String inCode = in.getItemCode() == null ? "" : in.getItemCode().trim();
        String dbCode = db.getItemCode() == null ? "" : db.getItemCode().trim();
        if (!inCode.equals(dbCode)) {
            return false;
        }
        return sameNumber(in.getQty(), db.getQty()) && sameNumber(in.getHoursNum(), db.getHoursNum());
    }

    private boolean sameNumber(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    /** 底册在册台代号：只认代号；已关停（status=1）的不算在册 */
    private Set<String> loadLiveSiteCodes() {
        List<TBcastSite> sites = this.bcastSiteMapper.selectList(new QueryWrapper<TBcastSite>()
                .eq("del_flag", 0));
        Set<String> codes = new HashSet<String>();
        for (TBcastSite s : sites) {
            if (s.getStatus() != null && s.getStatus().intValue() == 1) {
                continue;
            }
            if (s.getSiteNo() != null && !s.getSiteNo().trim().isEmpty()) {
                codes.add(s.getSiteNo().trim());
            }
        }
        return codes;
    }

    /** 本册落在库里的收下数：从库里一行一行点上来 */
    private int countAccepted(String batchNo) {
        Integer cnt = this.bcastOffRowMapper.selectCount(new QueryWrapper<TBcastOffRow>()
                .eq("batch_no", batchNo)
                .eq("status", STATUS_OK)
                .eq("del_flag", 0));
        return cnt == null ? 0 : cnt.intValue();
    }
}
