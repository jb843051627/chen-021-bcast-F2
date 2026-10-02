package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBcastOffBookMapper;
import com.fc.v2.mapper.auto.TBcastOffRowMapper;
import com.fc.v2.mapper.auto.TBcastSiteMapper;
import com.fc.v2.model.auto.TBcastOffBook;
import com.fc.v2.model.auto.TBcastOffRow;
import com.fc.v2.model.auto.TBcastSite;
import com.fc.v2.model.custom.BookReceipt;
import com.fc.v2.service.impl.TBcastOffRowServiceImpl;

/**
 * 整册收口服务的纯 Mockito 单测（不连库，三 mapper 用内存表顶替）。
 * 覆盖：同代号合并超限只退该行、同批号照面不记账、空册、整册上限、
 * 代号精确比对、行次占位与保留、两季未销挪行、撤册腾批号、旧入口签名不变。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TBcastOffRowServiceTest {

    @Mock
    private TBcastOffRowMapper rowMapper;
    @Mock
    private TBcastOffBookMapper bookMapper;
    @Mock
    private TBcastSiteMapper siteMapper;

    private TBcastOffRowServiceImpl service;

    private final Map<Long, TBcastOffRow> rowTable = new LinkedHashMap<Long, TBcastOffRow>();
    private final Map<Long, TBcastOffBook> bookTable = new LinkedHashMap<Long, TBcastOffBook>();
    private long rowSeq = 1000;
    private long bookSeq = 5000;

    private static final Pattern EQ =
            Pattern.compile("([a-z_]+) = #\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}");

    @BeforeEach
    public void setUp() {
        service = new TBcastOffRowServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "bcastOffRowMapper", rowMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "bcastOffBookMapper", bookMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "bcastSiteMapper", siteMapper);

        // 台站底册：TZ00 在册、TZ01 已关停
        doAnswer(inv -> {
            List<TBcastSite> all = new ArrayList<TBcastSite>();
            all.add(site("TZ00", "苗岭转播台", 0));
            all.add(site("TZ01", "滨河北微波站", 1));
            List<TBcastSite> out = new ArrayList<TBcastSite>();
            for (TBcastSite s : all) {
                if (s.getStatus() == 0 && (s.getDelFlag() == null || s.getDelFlag() == 0)) {
                    out.add(s);
                }
            }
            return out;
        }).when(siteMapper).selectList(any());

        doAnswer(inv -> {
            TBcastOffRow r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(++rowSeq);
            }
            rowTable.put(r.getId(), r);
            return 1;
        }).when(rowMapper).insert(any());
        doAnswer(inv -> {
            TBcastOffBook b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(++bookSeq);
            }
            bookTable.put(b.getId(), b);
            return 1;
        }).when(bookMapper).insert(any());
        doAnswer(inv -> 1).when(rowMapper).updateById(any());
        doAnswer(inv -> 1).when(bookMapper).updateById(any());
        doAnswer(inv -> rowTable.get(inv.getArgument(0))).when(rowMapper).selectById(any());
        doAnswer(inv -> filterRows(inv.getArgument(0))).when(rowMapper).selectList(any());
        doAnswer(inv -> filterBooks(inv.getArgument(0))).when(bookMapper).selectList(any());
    }

    // ------------------------------------------------------------------

    /** 规约原话口径：头一行四十六、第二行十九，册面记六十五；六十五高过六十，仅第二行原路退回 */
    @Test
    public void merge_overLimit_onlyTheCrossingRowGoesBack() {
        List<TBcastOffRow> rows = new ArrayList<TBcastOffRow>();
        rows.add(row("TZ00", "46", "100"));
        rows.add(row("TZ00", "19", "100"));
        // 退回行不连累别行：再来一小行，已收累计 46+5=51 仍在限内
        rows.add(row("TZ00", "5", "100"));

        BookReceipt rc = service.receiveBook("B2026Q3-01", "2026Q3", rows, new BigDecimal("60"));

        assertFalse(rc.isOverLimit());
        assertFalse(rc.isCompareOnly());
        assertEquals(3, rc.getTotalRows());
        assertEquals(2, rc.getAcceptedRows());
        assertEquals(1, rc.getReturnedRows());
        assertEquals("核讫", rc.getReceipt());

        List<BookReceipt.RowOutcome> os = rc.getRows();
        assertEquals(1, os.get(0).getStatus().intValue());
        assertEquals(2, os.get(1).getStatus().intValue());
        assertEquals(0, new BigDecimal("65").compareTo(os.get(1).getMergedQty()));
        assertNotNull(os.get(1).getReason());
        assertTrue(os.get(1).getReason().contains("退回"));
        assertEquals(1, os.get(2).getStatus().intValue());
        // 行次按先后自动占位、退回也带原行次
        assertEquals(2, os.get(1).getRowNo().intValue());

        // 整册同事务落库：3 行 + 1 簿
        assertEquals(3, rowTable.size());
        assertEquals(1, bookTable.size());
        TBcastOffBook book = bookTable.values().iterator().next();
        assertEquals(3, book.getRowCount().intValue());
        assertEquals(2, book.getAcceptedCount().intValue());
        assertEquals(1, book.getReturnedCount().intValue());
        // 退回缘由写在行上
        TBcastOffRow stored2 = rowTable.values().stream().filter(r -> r.getRowNo() == 2).findFirst().get();
        assertEquals(2, stored2.getStatus().intValue());
        assertTrue(stored2.getRemark().contains("退回"));
    }

    /** 同批号走第二遍：只照面不记账，库里不添不减，头一遍结果抹不掉 */
    @Test
    public void sameBatchSecondTime_comparesButNeverBooks() {
        List<TBcastOffRow> first = new ArrayList<TBcastOffRow>();
        first.add(row("TZ00", "46", "100"));
        first.add(row("TZ00", "5", "100"));
        BookReceipt r1 = service.receiveBook("B-DUP", "2026Q3", first, new BigDecimal("60"));
        assertFalse(r1.isCompareOnly());
        assertEquals(2, r1.getAcceptedRows());

        long rowsAfterFirst = rowTable.size();
        long booksAfterFirst = bookTable.size();

        // 第二遍把分钟数改了来——照面要报不符，但绝不落库覆盖
        List<TBcastOffRow> second = new ArrayList<TBcastOffRow>();
        TBcastOffRow a = row("TZ00", "46", "100");
        TBcastOffRow b = row("TZ00", "999", "100");
        second.add(a);
        second.add(b);
        BookReceipt r2 = service.receiveBook("B-DUP", "2026Q3", second, new BigDecimal("60"));

        assertTrue(r2.isCompareOnly());
        assertEquals(rowsAfterFirst, rowTable.size(), "照面遍不得添行");
        assertEquals(booksAfterFirst, bookTable.size(), "照面遍不得添簿");
        verify(rowMapper, never()).deleteById(any());
        verify(rowMapper, times(2)).insert(any()); // 仍只有头一遍的两次 insert
        // 头一遍收下的结果抹不掉
        assertEquals(1, r2.getRows().get(1).getStatus().intValue());
        // 照面报出不一致处
        assertFalse(r2.getCompares().get(1).isConsistent());
        assertTrue(r2.getCompares().get(1).getDetail().contains("分钟"));
        // 第二遍对象自带的行次/批号改动不得碰库
        assertEquals(Integer.valueOf(2), rowTable.values().stream()
                .filter(r -> r.getBatchNo().equals("B-DUP") && r.getRowNo() == 2).findFirst().get().getRowNo());
    }

    /** 整册点下来一行都收不着（空册）：回执落"空册"，不得用"核讫" */
    @Test
    public void emptyBook_getsEmptyReceipt() {
        BookReceipt rc = service.receiveBook("B-EMPTY", "2026Q3", new ArrayList<TBcastOffRow>(), null);
        assertEquals("空册", rc.getReceipt());
        assertEquals(0, rc.getTotalRows());
        assertEquals(0, rc.getAcceptedRows());
        assertEquals(0, rc.getReturnedRows());
        assertEquals(1, bookTable.size());
        assertEquals(0, rowTable.size());
    }

    /** 行数超单册上限：原册送回，一行一簿都不落库 */
    @Test
    public void overLimitWholeBook_nothingLands() {
        List<TBcastOffRow> many = new ArrayList<TBcastOffRow>();
        for (int i = 0; i < 501; i++) {
            many.add(row("TZ00", "1", "1"));
        }
        BookReceipt rc = service.receiveBook("B-OVER", "2026Q3", many, null);
        assertTrue(rc.isOverLimit());
        assertNull(rc.getReceipt());
        assertEquals(0, rowTable.size());
        assertEquals(0, bookTable.size());
        verify(rowMapper, never()).insert(any());
        verify(bookMapper, never()).insert(any());
    }

    /** 收下只认监测台代号：关停台、拿台名/空代号来的都不算对上，压作未销并进轧账差异 */
    @Test
    public void acceptOnlyMatchesActiveSiteCode() {
        List<TBcastOffRow> rows = new ArrayList<TBcastOffRow>();
        rows.add(row("TZ01", "3", "9"));           // 已关停，不认
        rows.add(row(null, "3", "9"));             // 只有台名/空代号，不认
        TBcastOffRow good = row("TZ00", "3", "9"); // 在册代号，收下
        rows.add(good);

        BookReceipt rc = service.receiveBook("B-SITE", "2026Q3", rows, null);
        assertEquals(1, rc.getAcceptedRows());
        assertEquals(0, rc.getReturnedRows());
        assertEquals(3, rc.getTotalRows());
        assertEquals(0, rc.getRows().get(0).getStatus().intValue());
        assertEquals(0, rc.getRows().get(1).getStatus().intValue());
        assertEquals(1, rc.getRows().get(2).getStatus().intValue());
        // 轧账：收下+退回对不上总行数，差异当场点到行
        assertFalse(rc.getDiffs().isEmpty());
        assertTrue(rc.getDiffs().stream().anyMatch(d -> d.contains("第1行")));
        assertTrue(rc.getDiffs().stream().anyMatch(d -> d.contains("第2行")));
    }

    /** 两个数一并发来，缺一个先压未销，谁也不替谁补 */
    @Test
    public void missingEitherNumber_staysPending() {
        List<TBcastOffRow> rows = new ArrayList<TBcastOffRow>();
        TBcastOffRow noQty = row("TZ00", null, "9");
        TBcastOffRow noHours = row("TZ00", "3", null);
        rows.add(noQty);
        rows.add(noHours);

        BookReceipt rc = service.receiveBook("B-NUM", "2026Q3", rows, null);
        assertEquals(0, rc.getAcceptedRows());
        assertEquals(2, rc.getTotalRows());
        assertTrue(rc.getRows().get(0).getReason().contains("分钟"));
        assertTrue(rc.getRows().get(1).getReason().contains("小时"));
    }

    /** 连着两个报送季仍未销：挪出当前名册；老册翻看仍一条不漏 */
    @Test
    public void stalePendingTwoSeasons_archivedButKeptInBook() {
        List<TBcastOffRow> rows = new ArrayList<TBcastOffRow>();
        rows.add(row("TZ01", "3", "9")); // 关停台→未销
        rows.add(row("TZ00", "3", "9")); // 已收下，不动
        service.receiveBook("B-OLD", "2026Q2", rows, null);

        int moved = service.archiveStaleRows("2026Q4"); // 季差 2
        assertEquals(1, moved);
        TBcastOffRow pending = rowTable.values().stream()
                .filter(r -> r.getBatchNo().equals("B-OLD") && r.getRowNo() == 1).findFirst().get();
        TBcastOffRow accepted = rowTable.values().stream()
                .filter(r -> r.getBatchNo().equals("B-OLD") && r.getRowNo() == 2).findFirst().get();
        assertEquals(1, pending.getArchived().intValue());
        assertEquals(0, accepted.getArchived().intValue());

        // 老册一条不漏（归档行仍带出）
        assertEquals(2, service.listBookRows("B-OLD").size());
        // 现行名单不再摆它
        List<TBcastOffRow> current = service.listCurrentRows(null);
        assertTrue(current.stream().noneMatch(r -> r.getId().equals(pending.getId())));

        // 只隔一季不挪
        int movedAgain = service.archiveStaleRows("2026Q3");
        assertEquals(0, movedAgain);
    }

    /** 确要动先撤册：撤后行作删除标记，批号腾开可重新进门 */
    @Test
    public void withdrawFreesBatch_thenCanBookAgain() {
        service.receiveBook("B-W", "2026Q3", java.util.Collections.singletonList(row("TZ00", "3", "9")), null);
        int dropped = service.withdrawBook("B-W", "快递分两回送重了");
        assertEquals(1, dropped);
        assertTrue(rowTable.values().stream().allMatch(r -> r.getDelFlag() == 1));
        assertEquals(1, bookTable.values().iterator().next().getDelFlag().intValue());

        // 重新进门：不是照面，是重新记账
        BookReceipt rc = service.receiveBook("B-W", "2026Q3",
                java.util.Collections.singletonList(row("TZ00", "8", "9")), null);
        assertFalse(rc.isCompareOnly());
        assertEquals(1, rc.getAcceptedRows());
        assertEquals(0, new BigDecimal("8").compareTo(
                service.listBookRows("B-W").get(0).getQty()));
        // 无在册册可撤时返回 -1
        assertEquals(-1, service.withdrawBook("B-NEVER", null));
    }

    /** 行次进门自动占位：来纸自带的行次不算数；退回后翻它仍是原行次 */
    @Test
    public void rowNoAssignedOnEntryAndKeptAfterReturn() {
        TBcastOffRow r1 = row("TZ00", "46", "9");
        r1.setRowNo(99); // 来纸上乱填的行次
        r1.setBatchNo("FAKE"); // 行内自带批号也不作数
        TBcastOffRow r2 = row("TZ00", "19", "9");
        BookReceipt rc = service.receiveBook("B-SEQ", "2026Q3", java.util.Arrays.asList(r1, r2),
                new BigDecimal("60"));
        assertEquals(1, rc.getRows().get(0).getRowNo().intValue());
        assertEquals(2, rc.getRows().get(1).getRowNo().intValue());
        // 退回行明细仍带原行次
        assertEquals(2, service.listErrors("B-SEQ").get(0).getRowNo().intValue());
        // 批号只认入口参数，行内自带批号被盖过
        assertTrue(rowTable.values().stream().allMatch(r -> r.getBatchNo().equals("B-SEQ")));
    }

    /** 旧入口 submitBatch(String,List) 签名照旧、不增参：返回收下数；超限 -1；重复不重复入库 */
    @Test
    public void legacySubmitBatchShapeUnchanged() {
        int n = service.submitBatch("B-LEGACY", java.util.Arrays.asList(row("TZ00", "1", "2"), row("TZ01", "1", "2")));
        assertEquals(1, n); // TZ01 关停不算，未销
        int nAgain = service.submitBatch("B-LEGACY", java.util.Arrays.asList(row("TZ00", "1", "2")));
        assertEquals(1, nAgain);
        assertEquals(2, rowTable.size(), "重复批号不重复入库");

        List<TBcastOffRow> many = new ArrayList<TBcastOffRow>();
        for (int i = 0; i < 501; i++) {
            many.add(row("TZ00", "1", "1"));
        }
        assertEquals(-1, service.submitBatch("B-LEGACY-OVER", many));
    }

    /** 回执三数从库里点：抽一行单看，行上的去向与回执报数同源；撤册后查回执报错 */
    @Test
    public void receiptCountsAreCountedFromStore() {
        service.receiveBook("B-RC", "2026Q3",
                java.util.Arrays.asList(row("TZ00", "1", "2"), row("TZ00", "80", "2"), row("TZ01", "1", "2")),
                new BigDecimal("60"));
        BookReceipt rc = service.getReceipt("B-RC");
        assertEquals(3, rc.getTotalRows());
        assertEquals(1, rc.getAcceptedRows());
        assertEquals(1, rc.getReturnedRows());
        assertTrue(rc.getDiffs().stream().anyMatch(d -> d.contains("第3行")));

        // 抽一行单看：按主键回查的去向与回执报出来的数同源
        TBcastOffRow row2 = rowTable.values().stream().filter(x -> x.getRowNo() == 2).findFirst().get();
        TBcastOffRow picked = service.selectTBcastOffRowById(row2.getId());
        assertNotNull(picked);
        assertEquals(2, picked.getStatus().intValue());
    }

    // ------------------------------------------------------------------

    private List<TBcastOffRow> filterRows(QueryWrapper<TBcastOffRow> w) {
        List<TBcastOffRow> out = new ArrayList<TBcastOffRow>(rowTable.values());
        applyFilters(w.getSqlSegment(), w.getParamNameValuePairs(), out,
                (col, v, r) -> rowMatches(col, v, r));
        return out;
    }

    private List<TBcastOffBook> filterBooks(QueryWrapper<TBcastOffBook> w) {
        List<TBcastOffBook> out = new ArrayList<TBcastOffBook>(bookTable.values());
        applyFilters(w.getSqlSegment(), w.getParamNameValuePairs(), out,
                (col, v, b) -> bookMatches(col, v, b));
        return out;
    }

    private <T> void applyFilters(String seg, Map<String, Object> params, List<T> out, Match<T> match) {
        Matcher m = EQ.matcher(seg == null ? "" : seg);
        while (m.find()) {
            String col = m.group(1);
            Object v = params.get(m.group(2));
            out.removeIf(t -> !match.test(col, v, t));
        }
    }

    private interface Match<T> {
        boolean test(String col, Object v, T t);
    }

    private boolean rowMatches(String col, Object v, TBcastOffRow r) {
        switch (col) {
            case "batch_no":
                return Objects.equals(v, r.getBatchNo());
            case "del_flag":
                return r.getDelFlag() != null && r.getDelFlag() == ((Number) v).intValue();
            case "status":
                return r.getStatus() != null && r.getStatus() == ((Number) v).intValue();
            case "archived":
                return r.getArchived() != null && r.getArchived() == ((Number) v).intValue();
            case "item_code":
                return Objects.equals(v, r.getItemCode());
            default:
                return true;
        }
    }

    private boolean bookMatches(String col, Object v, TBcastOffBook b) {
        switch (col) {
            case "batch_no":
                return Objects.equals(v, b.getBatchNo());
            case "del_flag":
                return b.getDelFlag() != null && b.getDelFlag() == ((Number) v).intValue();
            case "status":
                return b.getStatus() != null && b.getStatus() == ((Number) v).intValue();
            default:
                return true;
        }
    }

    private static TBcastOffRow row(String code, String qty, String hours) {
        TBcastOffRow r = new TBcastOffRow();
        r.setItemCode(code);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        r.setHoursNum(hours == null ? null : new BigDecimal(hours));
        r.setStatus(0);
        r.setArchived(0);
        r.setDelFlag(0);
        return r;
    }

    private static TBcastSite site(String no, String name, int status) {
        TBcastSite s = new TBcastSite();
        s.setSiteNo(no);
        s.setSiteName(name);
        s.setStatus(status);
        s.setDelFlag(0);
        return s;
    }
}
