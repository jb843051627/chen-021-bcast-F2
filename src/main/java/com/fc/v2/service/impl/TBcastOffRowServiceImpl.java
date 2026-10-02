package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBcastOffBookMapper;
import com.fc.v2.mapper.auto.TBcastOffRowMapper;
import com.fc.v2.mapper.auto.TBcastSiteMapper;
import com.fc.v2.model.auto.TBcastOffBook;
import com.fc.v2.model.auto.TBcastOffRow;
import com.fc.v2.model.auto.TBcastSite;
import com.fc.v2.model.custom.BookReceipt;
import com.fc.v2.service.ITBcastOffRowService;

/**
 * 运行情况报送核收行 Service业务层处理。
 *
 * 收口口径（只有这一处记账，页面不得另立说法）：
 * 1. 一册一个批号，进门定死；批号绝不取自行内字段。同批号第二遍只照面不记账，要改先撤册。
 * 2. 整册进门先点行，超单册上限原册送回、一行不落；进门后行次按先后自动占位，终身不重排。
 * 3. 行去向三选一：未销(0)/收下(1)/退回(2)。收下只认在册台站代号的精确比对；
 *    同册同代号分钟按行次合并，累计高过本季上限的，仅越过的那一行退回、缘由写在行上。
 * 4. 回执三数随册物化，且任何时候都从库里逐行点上来复验，对不齐当场列差异行。
 * 5. 未销行连续两个报送季仍无着落的挪出当前名册（archived=1），老册翻看仍一条不漏。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBcastOffRowServiceImpl implements ITBcastOffRowService {

    /** 单册行数上限：处里定下的数，进门先整册点行 */
    private static final int MAX_ROWS = 500;

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_ACCEPTED = 1;
    private static final int STATUS_RETURNED = 2;

    private static final int FLAG_NO = 0;
    private static final int FLAG_YES = 1;

    /** 册子：0在册 1已撤 */
    private static final int BOOK_ACTIVE = 0;
    private static final int BOOK_WITHDRAWN = 1;

    private static final String RECEIPT_DONE = "核讫";
    private static final String RECEIPT_EMPTY = "空册";

    /**
     * 同批号并发进门的把口。批号有界、锁对象常驻不摘——保证同一批号的进门/撤册永远
     * 串行在同一把锁上（摘锁会让新到者另取新锁，与旧锁上尚未提交的事务并行重账）。
     */
    private static final ConcurrentHashMap<String, Object> GATE_LOCKS = new ConcurrentHashMap<String, Object>();

    @javax.annotation.Resource
    private TBcastOffRowMapper bcastOffRowMapper;

    @javax.annotation.Resource
    private TBcastOffBookMapper bcastOffBookMapper;

    @javax.annotation.Resource
    private TBcastSiteMapper bcastSiteMapper;

    /** 容器里现成的事务管理器；无容器（纯单测）时为 null，inTx 直跑 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    /** 编程式事务：锁在事务外、账在事务内，保证锁释放时事务已提交，同批号并发不重账 */
    private <T> T inTx(java.util.function.Supplier<T> body) {
        if (transactionManager == null) {
            return body.get();
        }
        return new TransactionTemplate(transactionManager).execute(status -> body.get());
    }

    @Override
    public TBcastOffRow selectTBcastOffRowById(Long id) {
        return this.bcastOffRowMapper.selectById(id);
    }

    @Override
    public BookReceipt receiveBook(String batchNo, String season, List<TBcastOffRow> rows, BigDecimal limitMinutes) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new IllegalArgumentException("批号不能为空：一册一个批号，进门定死");
        }
        final String no = batchNo.trim();
        final List<TBcastOffRow> incoming = rows == null ? new ArrayList<TBcastOffRow>() : rows;

        Object gate = GATE_LOCKS.computeIfAbsent(no, k -> new Object());
        synchronized (gate) {
            // 整册点行：超上限原册送回，一行一簿都不落库
            if (incoming.size() > MAX_ROWS) {
                BookReceipt over = baseReceipt(no, season);
                over.setOverLimit(true);
                return over;
            }
            return inTx(() -> {
                TBcastOffBook alive = findAliveBook(no);
                if (alive != null) {
                    // 同批号第二遍：只把手放在比对上，不放在记账上
                    return compareOnly(alive, incoming);
                }
                return bookIn(no, season, incoming, limitMinutes);
            });
        }
    }

    /** 头一遍进门：逐行定去向、落行、立簿、物化回执（同一笔账） */
    private BookReceipt bookIn(String no, String season, List<TBcastOffRow> rows, BigDecimal limitMinutes) {
        // 进门先认台：在册台站代号精确索引，关停台/台名/含糊一律不在册
        Map<String, TBcastSite> activeSites = loadActiveSiteCodes();

        // 行次按先后自动占位（从 1 起），批号进门定死，盖过行内自带值
        int seq = 0;
        for (TBcastOffRow r : rows) {
            seq++;
            r.setBatchNo(no);
            r.setRowNo(seq);
            r.setStatus(STATUS_PENDING);
            r.setArchived(FLAG_NO);
            r.setDelFlag(FLAG_NO);
        }

        // 同册同代号：已收下分钟按行次累计，本行加上后越过上限的仅退回本行；
        // 退回行的分钟不进累计——同一册别的行不受它影响，不许级联退回。
        Map<String, BigDecimal> acceptedSum = new HashMap<String, BigDecimal>();
        List<BookReceipt.RowOutcome> outcomes = new ArrayList<BookReceipt.RowOutcome>();
        int accepted = 0;
        int returned = 0;

        for (TBcastOffRow r : rows) {
            String code = r.getItemCode() == null ? null : r.getItemCode().trim();
            if (code != null && code.isEmpty()) {
                code = null;
            }
            r.setItemCode(code);

            String reason = null;
            BigDecimal merged = null;

            boolean codeOk = code != null && activeSites.containsKey(code);
            boolean qtyOk = r.getQty() != null && r.getQty().compareTo(BigDecimal.ZERO) >= 0;
            boolean hoursOk = r.getHoursNum() != null && r.getHoursNum().compareTo(BigDecimal.ZERO) >= 0;

            if (!codeOk) {
                // 不算对上：台名比的、含糊的、关停台、空代号——先压作未销，等有着落
                reason = code == null
                        ? "监测台代号空缺，未对上在册台站（拿台名比、读着含糊的一律不算对上）"
                        : "监测台代号[" + code + "]对不上在册台站（关停台/台名/含糊不认）";
                r.setStatus(STATUS_PENDING);
            } else if (!qtyOk || !hoursOk) {
                // 两个数得一并发来，谁也不替谁补；数不齐先压作未销
                reason = !qtyOk && !hoursOk
                        ? "停播分钟数与本季满播小时数俱缺，谁也不替谁补"
                        : (!qtyOk ? "停播分钟数缺或非法" : "本季满播小时数缺或非法");
                r.setStatus(STATUS_PENDING);
            } else {
                BigDecimal base = acceptedSum.get(code);
                BigDecimal tentative = (base == null ? BigDecimal.ZERO : base).add(r.getQty());
                merged = tentative;
                if (limitMinutes != null && tentative.compareTo(limitMinutes) > 0) {
                    // 高出部分所在的这一行原路退回，同册别的行不受牵连；其分钟不进累计
                    reason = "同代号已收累计" + (base == null ? BigDecimal.ZERO : base).toPlainString()
                            + "分钟，加本行" + r.getQty().toPlainString() + "得" + tentative.toPlainString()
                            + "，高过本季上限" + limitMinutes.toPlainString() + "分钟，本行原路退回";
                    r.setStatus(STATUS_RETURNED);
                    returned++;
                } else {
                    r.setStatus(STATUS_ACCEPTED);
                    acceptedSum.put(code, tentative);
                    accepted++;
                }
            }

            r.setRemark(reason);
            this.bcastOffRowMapper.insert(r);

            BookReceipt.RowOutcome o = new BookReceipt.RowOutcome();
            o.setRowNo(r.getRowNo());
            o.setItemCode(code);
            o.setQty(r.getQty());
            o.setHoursNum(r.getHoursNum());
            o.setStatus(r.getStatus());
            o.setMergedQty(merged);
            o.setReason(reason);
            outcomes.add(o);
        }

        // 立簿：批号、季度、回执三数同事务物化（三数即本事务刚落下的行，天然同源）
        TBcastOffBook book = new TBcastOffBook();
        book.setBatchNo(no);
        book.setSeason(season);
        book.setRowCount(rows.size());
        book.setAcceptedCount(accepted);
        book.setReturnedCount(returned);
        book.setReceipt(rows.isEmpty() ? RECEIPT_EMPTY : RECEIPT_DONE);
        book.setStatus(BOOK_ACTIVE);
        book.setDelFlag(FLAG_NO);
        this.bcastOffBookMapper.insert(book);

        // 回执三数不许屏上临时数：头一遍也从刚落下的库里逐行点上来、当场轧差异；
        // 合并分钟只是进门判退的展示值，补回行结果上
        BookReceipt receipt = tallyFromStore(book);
        Map<Integer, BigDecimal> mergedByRowNo = new HashMap<Integer, BigDecimal>();
        for (BookReceipt.RowOutcome o : outcomes) {
            if (o.getMergedQty() != null) {
                mergedByRowNo.put(o.getRowNo(), o.getMergedQty());
            }
        }
        for (BookReceipt.RowOutcome o : receipt.getRows()) {
            o.setMergedQty(mergedByRowNo.get(o.getRowNo()));
        }
        return receipt;
    }

    /** 同批号第二遍：来行与库存行照一遍面即停，库里不添不减，去向以头一遍为准 */
    private BookReceipt compareOnly(TBcastOffBook book, List<TBcastOffRow> rows) {
        List<TBcastOffRow> stored = listAliveRowsByBatch(book.getBatchNo());
        Map<Integer, TBcastOffRow> storedByRowNo = new HashMap<Integer, TBcastOffRow>();
        for (TBcastOffRow s : stored) {
            storedByRowNo.put(s.getRowNo(), s);
        }

        Set<Integer> seenRowNo = new HashSet<Integer>();
        List<BookReceipt.RowCompare> compares = new ArrayList<BookReceipt.RowCompare>();
        int seq = 0;
        for (TBcastOffRow in : rows) {
            seq++;
            seenRowNo.add(seq);
            TBcastOffRow s = storedByRowNo.get(seq);
            BookReceipt.RowCompare c = new BookReceipt.RowCompare();
            c.setRowNo(seq);
            if (s == null) {
                c.setConsistent(false);
                c.setDetail("库存无此行次，照面不记账，去向维持库存原状");
            } else {
                String inCode = in.getItemCode() == null ? null : in.getItemCode().trim();
                boolean sameCode = eqStr(inCode, s.getItemCode());
                boolean sameQty = eqNum(in.getQty(), s.getQty());
                boolean sameHours = eqNum(in.getHoursNum(), s.getHoursNum());
                c.setConsistent(sameCode && sameQty && sameHours);
                c.setStoredStatus(s.getStatus());
                if (c.isConsistent()) {
                    c.setDetail("照面一致");
                } else {
                    StringBuilder sb = new StringBuilder("照面不符：");
                    if (!sameCode) {
                        sb.append("代号[来=").append(inCode).append("/库=").append(s.getItemCode()).append("] ");
                    }
                    if (!sameQty) {
                        sb.append("分钟[来=").append(in.getQty()).append("/库=").append(s.getQty()).append("] ");
                    }
                    if (!sameHours) {
                        sb.append("小时[来=").append(in.getHoursNum()).append("/库=").append(s.getHoursNum()).append("] ");
                    }
                    sb.append("（只照面，不抹头一遍结果；要动先撤册）");
                    c.setDetail(sb.toString());
                }
            }
            compares.add(c);
        }
        for (Integer miss : storedByRowNo.keySet()) {
            if (!seenRowNo.contains(miss)) {
                BookReceipt.RowCompare c = new BookReceipt.RowCompare();
                c.setRowNo(miss);
                c.setConsistent(false);
                c.setStoredStatus(storedByRowNo.get(miss).getStatus());
                c.setDetail("本次来册缺此行，库存行保留不动");
                compares.add(c);
            }
        }
        Collections.sort(compares, new Comparator<BookReceipt.RowCompare>() {
            @Override
            public int compare(BookReceipt.RowCompare a, BookReceipt.RowCompare b) {
                return a.getRowNo() - b.getRowNo();
            }
        });

        // 回执仍从库里点，照面遍绝不动三数
        BookReceipt receipt = tallyFromStore(book);
        receipt.setCompareOnly(true);
        receipt.setCompares(compares);
        return receipt;
    }

    @Override
    public int withdrawBook(String batchNo, String reason) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new IllegalArgumentException("批号不能为空");
        }
        final String no = batchNo.trim();
        final String why = reason;
        Object gate = GATE_LOCKS.computeIfAbsent(no, k -> new Object());
        synchronized (gate) {
            Integer result = inTx(() -> {
                TBcastOffBook alive = findAliveBook(no);
                if (alive == null) {
                    return Integer.valueOf(-1);
                }
                List<TBcastOffRow> stored = listAliveRowsByBatch(no);
                for (TBcastOffRow r : stored) {
                    r.setDelFlag(FLAG_YES);
                    this.bcastOffRowMapper.updateById(r);
                }
                alive.setStatus(BOOK_WITHDRAWN);
                alive.setDelFlag(FLAG_YES);
                alive.setRemark("撤册：" + (why == null ? "" : why));
                this.bcastOffBookMapper.updateById(alive);
                return Integer.valueOf(stored.size());
            });
            return result.intValue();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int archiveStaleRows(String currentSeason) {
        int[] cur = parseSeason(currentSeason);
        if (cur == null) {
            throw new IllegalArgumentException("报送季格式应为 yyyyQn，如 2026Q3");
        }
        // 未销且还在现行名册里的行
        List<TBcastOffRow> pendings = this.bcastOffRowMapper.selectList(new QueryWrapper<TBcastOffRow>()
                .eq("del_flag", FLAG_NO)
                .eq("archived", FLAG_NO)
                .eq("status", STATUS_PENDING));
        if (pendings.isEmpty()) {
            return 0;
        }
        // 行所属季度只认册子总簿（行不混册）
        Map<String, String> seasonByBatch = new HashMap<String, String>();
        List<TBcastOffBook> books = this.bcastOffBookMapper.selectList(new QueryWrapper<TBcastOffBook>()
                .eq("del_flag", FLAG_NO));
        for (TBcastOffBook b : books) {
            seasonByBatch.put(b.getBatchNo(), b.getSeason());
        }

        int moved = 0;
        for (TBcastOffRow r : pendings) {
            int[] born = parseSeason(seasonByBatch.get(r.getBatchNo()));
            if (born == null) {
                continue;
            }
            int gap = (cur[0] - born[0]) * 4 + (cur[1] - born[1]);
            if (gap >= 2) {
                r.setArchived(FLAG_YES);
                r.setRemark(appendRemark(r.getRemark(),
                        "连续两个报送季仍未销，截至" + currentSeason + "挪出当前名册另放（老册仍可查）"));
                this.bcastOffRowMapper.updateById(r);
                moved++;
            }
        }
        return moved;
    }

    @Override
    public int submitBatch(String batchNo, List<TBcastOffRow> rows) {
        // 旧入口签名照旧、不加参数；按册收的新口径全走 receiveBook（旧口径不封顶）
        BookReceipt r = receiveBook(batchNo, null, rows, null);
        if (r.isOverLimit()) {
            return -1;
        }
        return r.getAcceptedRows();
    }

    @Override
    public List<TBcastOffRow> listErrors(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            return new ArrayList<TBcastOffRow>();
        }
        return this.bcastOffRowMapper.selectList(new QueryWrapper<TBcastOffRow>()
                .eq("batch_no", batchNo.trim())
                .eq("del_flag", FLAG_NO)
                .eq("status", STATUS_RETURNED)
                .orderByAsc("row_no"));
    }

    @Override
    public BookReceipt getReceipt(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new IllegalArgumentException("批号不能为空");
        }
        TBcastOffBook book = findAliveBook(batchNo.trim());
        if (book == null) {
            throw new IllegalArgumentException("批号[" + batchNo + "]下无在册册");
        }
        return tallyFromStore(book);
    }

    @Override
    public List<TBcastOffRow> listBookRows(String batchNo) {
        // 老册一条不漏：已挪出另放的行仍带出；撤册软删行除外
        return this.bcastOffRowMapper.selectList(new QueryWrapper<TBcastOffRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", FLAG_NO)
                .orderByAsc("row_no"));
    }

    @Override
    public List<TBcastOffRow> listCurrentRows(String itemCode) {
        // 现行名单：未销、未挪走；归档行不再摆出
        QueryWrapper<TBcastOffRow> qw = new QueryWrapper<TBcastOffRow>()
                .eq("del_flag", FLAG_NO)
                .eq("archived", FLAG_NO)
                .eq("status", STATUS_PENDING);
        if (itemCode != null && !itemCode.trim().isEmpty()) {
            qw.eq("item_code", itemCode.trim());
        }
        return this.bcastOffRowMapper.selectList(qw.orderByAsc("batch_no", "row_no"));
    }

    // ------------------------------------------------------------------

    private TBcastOffBook findAliveBook(String no) {
        List<TBcastOffBook> hit = this.bcastOffBookMapper.selectList(new QueryWrapper<TBcastOffBook>()
                .eq("batch_no", no)
                .eq("del_flag", FLAG_NO)
                .eq("status", BOOK_ACTIVE));
        return hit.isEmpty() ? null : hit.get(0);
    }

    private List<TBcastOffRow> listAliveRowsByBatch(String no) {
        return this.bcastOffRowMapper.selectList(new QueryWrapper<TBcastOffRow>()
                .eq("batch_no", no)
                .eq("del_flag", FLAG_NO)
                .orderByAsc("row_no"));
    }

    private Map<String, TBcastSite> loadActiveSiteCodes() {
        List<TBcastSite> sites = this.bcastSiteMapper.selectList(new QueryWrapper<TBcastSite>()
                .eq("del_flag", FLAG_NO)
                .eq("status", 0));
        Map<String, TBcastSite> map = new HashMap<String, TBcastSite>();
        for (TBcastSite s : sites) {
            if (s.getSiteNo() != null && !s.getSiteNo().trim().isEmpty()) {
                map.put(s.getSiteNo().trim(), s);
            }
        }
        return map;
    }

    /**
     * 末了轧账：三数从库里逐行点上来，与册子物化值（同一次落下的）对账；
     * 未销行使收下+退回小于总行数，或物化值与库点不符的，差异当场逐行列明。
     */
    private BookReceipt tallyFromStore(TBcastOffBook book) {
        List<TBcastOffRow> stored = listAliveRowsByBatch(book.getBatchNo());
        int total = stored.size();
        int accepted = 0;
        int returned = 0;
        List<String> diffs = new ArrayList<String>();
        List<BookReceipt.RowOutcome> outcomes = new ArrayList<BookReceipt.RowOutcome>();

        for (TBcastOffRow r : stored) {
            BookReceipt.RowOutcome o = new BookReceipt.RowOutcome();
            o.setRowNo(r.getRowNo());
            o.setItemCode(r.getItemCode());
            o.setQty(r.getQty());
            o.setHoursNum(r.getHoursNum());
            o.setStatus(r.getStatus());
            o.setReason(r.getRemark());
            outcomes.add(o);

            if (r.getStatus() != null && r.getStatus() == STATUS_ACCEPTED) {
                accepted++;
            } else if (r.getStatus() != null && r.getStatus() == STATUS_RETURNED) {
                returned++;
            } else {
                diffs.add("第" + r.getRowNo() + "行（代号" + r.getItemCode()
                        + "）仍为未销，未计入收下/退回");
            }
        }
        if (accepted + returned != total) {
            diffs.add("收下" + accepted + "+退回" + returned + "=" + (accepted + returned)
                    + "，对不上本册行数" + total + "，差" + (total - accepted - returned) + "行（见上列未销行）");
        }
        if (book.getRowCount() != null && book.getRowCount() != total) {
            diffs.add("回执本册行数物化=" + book.getRowCount() + "，库点=" + total);
        }
        if (book.getAcceptedCount() != null && book.getAcceptedCount() != accepted) {
            diffs.add("回执收下物化=" + book.getAcceptedCount() + "，库点=" + accepted);
        }
        if (book.getReturnedCount() != null && book.getReturnedCount() != returned) {
            diffs.add("回执退回物化=" + book.getReturnedCount() + "，库点=" + returned);
        }

        BookReceipt receipt = baseReceipt(book.getBatchNo(), book.getSeason());
        receipt.setReceipt(book.getReceipt());
        receipt.setTotalRows(total);
        receipt.setAcceptedRows(accepted);
        receipt.setReturnedRows(returned);
        receipt.setRows(outcomes);
        receipt.setDiffs(diffs);
        return receipt;
    }

    private BookReceipt baseReceipt(String no, String season) {
        BookReceipt r = new BookReceipt();
        r.setBatchNo(no);
        r.setSeason(season);
        return r;
    }

    private static boolean eqStr(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    private static boolean eqNum(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
    }

    /** "2026Q3" -> [2026,3]；非法返回 null */
    private static int[] parseSeason(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (!t.matches("\\d{4}Q[1-4]")) {
            return null;
        }
        return new int[] {Integer.parseInt(t.substring(0, 4)), Integer.parseInt(t.substring(5))};
    }

    private static String appendRemark(String old, String add) {
        if (old == null || old.isEmpty()) {
            return add;
        }
        return old + "；" + add;
    }
}
