package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TBcastOffRowMapper;
import com.fc.v2.mapper.auto.TBcastSiteMapper;
import com.fc.v2.model.auto.TBcastOffRow;
import com.fc.v2.model.auto.TBcastSite;
import com.fc.v2.model.custom.BcastOffRowReceipt;

/**
 * 运行情况报送核收行：收口规矩的单测。库用 mock 顶替，落库的行都截下来当面点验。
 */
@ExtendWith(MockitoExtension.class)
class TBcastOffRowServiceImplTest {

    @Mock
    private TBcastOffRowMapper offRowMapper;

    @Mock
    private TBcastSiteMapper siteMapper;

    @org.mockito.InjectMocks
    private TBcastOffRowServiceImpl service;

    /** 真正"落库"的行都进这个篮子，回执点数也从篮子里点，跟从库里点上来一个意思 */
    private final List<TBcastOffRow> landed = new ArrayList<TBcastOffRow>();

    private TBcastSite site(long id, String siteNo, Integer status) {
        TBcastSite s = new TBcastSite();
        s.setId(id);
        s.setSiteNo(siteNo);
        s.setStatus(status);
        return s;
    }

    private TBcastOffRow row(String itemCode, String qty, String hoursNum) {
        TBcastOffRow r = new TBcastOffRow();
        r.setItemCode(itemCode);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        r.setHoursNum(hoursNum == null ? null : new BigDecimal(hoursNum));
        return r;
    }

    /** 底册：TZ00、TZ02 在册，TZ01 已关停 */
    private void mockRegister() {
        when(siteMapper.selectList(any())).thenReturn(Arrays.asList(
                site(0, "TZ00", 0), site(1, "TZ01", 1), site(2, "TZ02", 0)));
    }

    /** 头一遍进门：查重落空；落库的行截进篮子；回执收下数从篮子里点 */
    private void mockFirstPass() {
        mockRegister();
        landed.clear();
        when(offRowMapper.insert(any())).thenAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock inv) {
                landed.add(inv.getArgument(0));
                return Integer.valueOf(1);
            }
        });
        when(offRowMapper.selectCount(any())).thenAnswer(new Answer<Integer>() {
            private int calls = 0;

            @Override
            public Integer answer(InvocationOnMock inv) {
                calls++;
                if (calls == 1) {
                    return Integer.valueOf(0);
                }
                int ok = 0;
                for (TBcastOffRow r : landed) {
                    if (r.getStatus() != null && r.getStatus().intValue() == 1) {
                        ok++;
                    }
                }
                return Integer.valueOf(ok);
            }
        });
    }

    private TBcastOffRow landedAt(int idx) {
        return landed.get(idx);
    }

    // ================= 进门规矩 =================

    @Test
    void 整册进门_批号落死_行次自动占位_对不上代号的一律未销() {
        mockFirstPass();
        TBcastOffRow a = row("TZ00", "10", "100");
        a.setBatchNo("别册的号");
        a.setRowNo(Integer.valueOf(99));
        TBcastOffRow b = row("TZ99", "5", "50");
        TBcastOffRow c = row("  ", "5", "50");

        int ok = service.submitBatch(" 册甲 ", Arrays.asList(a, b, c));

        assertEquals(1, ok);
        assertEquals(3, landed.size());
        // 批号进门就定死，不许混册
        for (TBcastOffRow r : landed) {
            assertEquals("册甲", r.getBatchNo());
        }
        // 行次自动占位按先后排定，来报的行号不作数
        assertEquals(Integer.valueOf(1), landedAt(0).getRowNo());
        assertEquals(Integer.valueOf(2), landedAt(1).getRowNo());
        assertEquals(Integer.valueOf(3), landedAt(2).getRowNo());
        // 代号精确对上的收下；对不上的、空缺的一律未销
        assertEquals(Integer.valueOf(1), landedAt(0).getStatus());
        assertEquals(Integer.valueOf(0), landedAt(1).getStatus());
        assertEquals(Integer.valueOf(0), landedAt(2).getStatus());
        assertNull(landedAt(2).getItemCode());
    }

    @Test
    void 同台两行分钟并册_高过季限的那一行原路退回_同册别行不受牵连() {
        mockFirstPass();
        // 册面上的那个例子：头一行四十六、第二行十九，并起来六十五，高过季限六十
        int ok = service.submitBatch("册乙", Arrays.asList(
                row("TZ00", "46", "100"),
                row("TZ00", "19", "100"),
                row("TZ02", "5", "100")));

        assertEquals(2, ok);
        assertEquals(Integer.valueOf(1), landedAt(0).getStatus());
        // 高出那部分所在的一行原路退回，缘由写在行上，原先的行次还带着
        assertEquals(Integer.valueOf(2), landedAt(1).getStatus());
        assertEquals(Integer.valueOf(2), landedAt(1).getRowNo());
        assertTrue(landedAt(1).getRemark().contains("65"));
        assertTrue(landedAt(1).getRemark().contains("60"));
        // 同一册里别的行不受它影响
        assertEquals(Integer.valueOf(1), landedAt(2).getStatus());
    }

    @Test
    void 并册正好够到季限不算高过_越限后再来的同台行也跟着退回() {
        mockFirstPass();
        int ok = service.submitBatch("册丙", Arrays.asList(
                row("TZ00", "46", "100"),
                row("TZ00", "14", "100"),
                row("TZ00", "1", "100")));

        // 46+14=60 正好够到线，不算高过；再加 1 并成 61，高过了
        assertEquals(2, ok);
        assertEquals(Integer.valueOf(1), landedAt(0).getStatus());
        assertEquals(Integer.valueOf(1), landedAt(1).getStatus());
        assertEquals(Integer.valueOf(2), landedAt(2).getStatus());
    }

    @Test
    void 两个数一并发_缺了为负的一律未销_零报告是正经报() {
        mockFirstPass();
        int ok = service.submitBatch("册丁", Arrays.asList(
                row("TZ00", "0", "0"),
                row("TZ00", null, "100"),
                row("TZ00", "-1", "100"),
                row("TZ00", "10", null)));

        assertEquals(1, ok);
        assertEquals(Integer.valueOf(1), landedAt(0).getStatus());
        assertEquals(Integer.valueOf(0), landedAt(1).getStatus());
        assertEquals(Integer.valueOf(0), landedAt(2).getStatus());
        assertEquals(Integer.valueOf(0), landedAt(3).getStatus());
    }

    @Test
    void 已关停的台对上了也不算在册() {
        mockFirstPass();
        int ok = service.submitBatch("册戊", Arrays.asList(row("TZ01", "3", "30")));

        assertEquals(0, ok);
        assertEquals(Integer.valueOf(0), landedAt(0).getStatus());
    }

    @Test
    void 整册点行超了上限_原册送回一行不落() {
        List<TBcastOffRow> rows = new ArrayList<TBcastOffRow>();
        for (int i = 0; i < 501; i++) {
            rows.add(row("TZ00", "1", "1"));
        }
        int ok = service.submitBatch("册己", rows);

        assertEquals(-1, ok);
        verify(offRowMapper, never()).insert(any());
    }

    @Test
    void 空批次与缺批号() {
        assertEquals(0, service.submitBatch("册庚", new ArrayList<TBcastOffRow>()));
        assertEquals(0, service.submitBatch("册庚", null));
        assertEquals(-1, service.submitBatch(null, Arrays.asList(row("TZ00", "1", "1"))));
        assertEquals(-1, service.submitBatch("  ", Arrays.asList(row("TZ00", "1", "1"))));
        verify(offRowMapper, never()).insert(any());
    }

    // ================= 第二遍只照面 =================

    @Test
    void 同一批号走第二遍_只照面不记账_头一遍落下的抹不掉() {
        when(offRowMapper.selectCount(any())).thenReturn(Integer.valueOf(3), Integer.valueOf(2));
        when(offRowMapper.selectList(any())).thenReturn(Arrays.asList(
                landedRow("册辛", 1, "TZ00", "10", "100", 1),
                landedRow("册辛", 2, "TZ00", "5", "50", 2),
                landedRow("册辛", 3, "TZ02", "1", "10", 1)));

        int ok = service.submitBatch("册辛", Arrays.asList(
                row("TZ00", "10", "100"),
                row("TZ00", "5", "50"),
                row("TZ02", "1", "10")));

        // 回执以库里已落定的为准：收下 2 行
        assertEquals(2, ok);
        // 库里既不添行也不减行
        verify(offRowMapper, never()).insert(any());
        verify(offRowMapper, never()).delete(any());
        verify(offRowMapper, never()).update(any(), any());
    }

    private TBcastOffRow landedRow(String batchNo, int rowNo, String code, String qty, String hours, int status) {
        TBcastOffRow r = row(code, qty, hours);
        r.setBatchNo(batchNo);
        r.setRowNo(Integer.valueOf(rowNo));
        r.setStatus(Integer.valueOf(status));
        r.setDelFlag(Integer.valueOf(0));
        r.setArcFlag(Integer.valueOf(0));
        return r;
    }

    // ================= 回执轧账 =================

    @Test
    void 回执三个数从库里点_对不齐的差行当场列出() {
        when(offRowMapper.selectList(any())).thenReturn(Arrays.asList(
                landedRow("册壬", 1, "TZ00", "10", "100", 1),
                landedRow("册壬", 2, "TZ00", "60", "100", 2),
                landedRow("册壬", 3, "TZ99", "5", "50", 0)));

        BcastOffRowReceipt receipt = service.receipt("册壬");

        assertEquals(3, receipt.getTotalRows());
        assertEquals(1, receipt.getAcceptedRows());
        assertEquals(1, receipt.getReturnedRows());
        assertEquals(1, receipt.getPendingRows());
        // 收下+退回 并不上本册行数：轧不齐，差的那行当场列出来
        assertFalse(receipt.isBalanced());
        assertEquals(1, receipt.getDiffRows().size());
        assertEquals(Integer.valueOf(3), receipt.getDiffRows().get(0).getRowNo());
        // 有数（收下≥1）的册才落核讫
        assertEquals(BcastOffRowReceipt.WORD_VERIFIED, receipt.getWord());
    }

    @Test
    void 一行都收不着的册回执落空册_核讫不给它用() {
        when(offRowMapper.selectList(any())).thenReturn(Arrays.asList(
                landedRow("册癸", 1, "TZ00", "70", "100", 2),
                landedRow("册癸", 2, "TZ99", "5", "50", 2)));

        BcastOffRowReceipt receipt = service.receipt("册癸");

        assertEquals(BcastOffRowReceipt.WORD_EMPTY, receipt.getWord());
        assertEquals(0, receipt.getAcceptedRows());
        // 收下0+退回2 == 本册2行，轧得齐
        assertTrue(receipt.isBalanced());
        assertTrue(receipt.getDiffRows().isEmpty());
    }

    // ================= 撤册 =================

    @Test
    void 撤册之后同一批号才能按新册重新进门() {
        when(offRowMapper.delete(any())).thenReturn(Integer.valueOf(2));
        assertEquals(2, service.withdrawBatch("册子"));
        assertEquals(0, service.withdrawBatch("  "));
    }

    // ================= 挪出 =================

    @Test
    void 连着两个报送季未销的挪出现行名册_挪走不是抹掉() {
        ArgumentCaptor<TBcastOffRow> rowCap = ArgumentCaptor.forClass(TBcastOffRow.class);
        ArgumentCaptor<UpdateWrapper> wrCap = ArgumentCaptor.forClass(UpdateWrapper.class);
        when(offRowMapper.update(rowCap.capture(), wrCap.capture())).thenReturn(Integer.valueOf(4));

        int moved = service.archiveStalePending(new Date());

        assertEquals(4, moved);
        // 只挪，不删：落的是挪出标记，不是删除标记
        assertEquals(Integer.valueOf(1), rowCap.getValue().getArcFlag());
        assertNull(rowCap.getValue().getDelFlag());
        // 挪的只是未销且现行、压着过了宽限的：条件里带着行情形、挪出标记与落库时刻
        String where = wrCap.getValue().getSqlSegment();
        assertTrue(where.contains("status"));
        assertTrue(where.contains("arc_flag"));
        assertTrue(where.contains("create_time"));
        // 界点为空不办事
        assertEquals(0, service.archiveStalePending(null));
    }
}
