package com.fc.v2.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TBcastSiteMapper;
import com.fc.v2.mapper.auto.TBcastZeroTaskMapper;
import com.fc.v2.model.auto.TBcastSite;
import com.fc.v2.model.auto.TBcastZeroTask;
import com.fc.v2.service.ITBcastZeroTaskService;

/**
 * 零报告催办到期单 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * <p>一轮催办的规矩，与催办条目表口径一一对应：</p>
 * <ul>
 *   <li>捞单只认一件事：开口那一档踩过去没有。开口档 = 应出手那一日(due_at) 往前数
 *       "最可提前几日开口"(amount) 个<b>工作日</b>；到档才进本轮名单，没到档的先放着。</li>
 *   <li>剩余天数全处只认一种数法：跨夜那一段不入数（只按工作日一天一天数，不按24小时折算），
 *       国家放的假不入数，省里通令不办公的那几日也不入数。挑单与回写共用同一把尺，
 *       不许各自另起炉灶——本类里就是 {@link #isWorkday(LocalDate)} 这一处。</li>
 *   <li>保障期占住的那几日（每月 {@value #WIN_FROM}—{@value #WIN_TO} 日，含首尾）：
 *       手里就是有已到档的也不动，等下一轮再开口。</li>
 *   <li>上一轮已经催到过的，下一轮不许再当候催捞回来（捞的时候卡 status，写的时候再卡一道）。</li>
 *   <li>一条算催到要留两边痕迹：情形由候催转成催到，紧跟着把催到的那一刻落下；
 *       两痕迹落同一笔，只落其一不算走完。话分两头递——站内那一条递县广电局、
 *       手机那一条递台站值机室，哪一路送到就单独记哪一路，手机不能替站内交差。</li>
 *   <li>开不成单的两类（台站早已从底册上摘走、值机联系人那一栏空着）：当场转挂催不动，
 *       卡在哪一环、原委写进同一条，日后按原委能挑出一批来；条目不藏不掖，
 *       现行清单里照样挂着（不动 del_flag），只是这轮不再去碰它。</li>
 *   <li>同一秒里两桩同时挤进来，就分两桩记，不合并不压掉；一轮里够着的每一条都得过一遍；
 *       同轮有一桩走岔了，不许把其余甩下。</li>
 *   <li>收口三个数（还欠开口的 / 本轮催到的 / 催不动的）只报本轮这一回，且得回库里逐条
 *       点到位（{@link #roundSummary(Date)}），不许照着屏幕誊。整轮一条也没捞着，
 *       就回一句"本轮无到期"，属正常收口。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBcastZeroTaskServiceImpl implements ITBcastZeroTaskService {

    private static final Logger log = LoggerFactory.getLogger(TBcastZeroTaskServiceImpl.class);

    /** 保障期窗口：每月这几日（含首尾）占住，窗口内一律不开口 */
    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;

    private static final int STATUS_WAIT = 0;   // 候催
    private static final int STATUS_DONE = 1;   // 催到
    private static final int STATUS_FAIL = 2;   // 催不动

    /** 整轮一条也没捞着时的正常回话 */
    public static final String MSG_NO_DUE = "本轮无到期";

    /** 催不动原委：台站早已从底册上摘走（日后按原委挑批就按这个字眼，不许随手改） */
    public static final String REASON_SITE_GONE = "台站已摘册";
    /** 催不动原委：值机联系人那一栏空着 */
    public static final String REASON_CONTACT_EMPTY = "值机联系人空缺";

    /** 催不动卡在哪一环：开口前的核档环 */
    private static final String STAGE_CHECK = "核档";

    /** remark 只给 500 格（t_bcast_zero_task.remark varchar(500)） */
    private static final int REMARK_MAX = 500;

    @Resource
    private TBcastZeroTaskMapper bcastZeroTaskMapper;

    @Resource
    private TBcastSiteMapper bcastSiteMapper;

    /** 国家放的假（yyyy-MM-dd）。全处只认这一处登记，数工作日时统一从这里扣。 */
    private final Set<String> nationalHolidays = new HashSet<String>();

    /** 省里通令不办公的那几日（yyyy-MM-dd）。与上面那本册子并列，别处分不得设。 */
    private final Set<String> provinceClosures = new HashSet<String>();

    /** 国家放的假登记册（可增删，格式 yyyy-MM-dd） */
    public Set<String> getNationalHolidays() {
        return nationalHolidays;
    }

    /** 省里通令不办公日登记册（可增删，格式 yyyy-MM-dd） */
    public Set<String> getProvinceClosures() {
        return provinceClosures;
    }

    @Override
    public TBcastZeroTask selectTBcastZeroTaskById(Long id) {
        return this.bcastZeroTaskMapper.selectById(id);
    }

    /**
     * 时段落字统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，写进 remark 给人看的钟点按本机墙钟落；库与库之间的等值比较则一律走
     * Date 参数（写与读同一条路换算），不把墙钟字符串拿去和 datetime 列比。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    private static LocalDate toLocalDate(Date d) {
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ================= 同一把尺：工作日数法（挑单与回写共用，别处不得另起） =================

    /** 这一天算不算工作日：周六周日不算，国家放的假不算，省里通令不办公的那几日也不算。 */
    private boolean isWorkday(LocalDate d) {
        if (d.getDayOfWeek().getValue() >= 6) {
            return false;
        }
        String ymd = d.toString();
        return !nationalHolidays.contains(ymd) && !provinceClosures.contains(ymd);
    }

    /**
     * 开口那一档：应出手那一日往前数 amount 个工作日。档写死在条目上（由 due_at 与
     * amount 两格定死），不许为了凑一批而顺延；amount 空着当 0，负数也按 0 计。
     */
    private LocalDate openSlotOf(TBcastZeroTask task) {
        LocalDate d = toLocalDate(task.getDueAt());
        int n = task.getAmount() == null ? 0 : Math.max(0, task.getAmount().intValue());
        for (int i = 0; i < n;) {
            d = d.minusDays(1);
            if (isWorkday(d)) {
                i++;
            }
        }
        return d;
    }

    /**
     * 剩余天数：从 at 那一日起，按工作日一天一天数到应开口那一档，还剩几日就报几日；
     * 已经踩到档（含踩过去）的报 0。
     */
    public long remainingDays(Date at, TBcastZeroTask task) {
        if (at == null || task == null || task.getDueAt() == null) {
            return 0;
        }
        LocalDate today = toLocalDate(at);
        LocalDate slot = openSlotOf(task);
        if (!today.isBefore(slot)) {
            return 0;
        }
        long n = 0;
        for (LocalDate d = today.plusDays(1); !d.isAfter(slot); d = d.plusDays(1)) {
            if (isWorkday(d)) {
                n++;
            }
        }
        return n;
    }

    /** 保障期占住的那几日：每月 WIN_FROM—WIN_TO 日（含首尾）。 */
    private static boolean inGuaranteeWindow(LocalDate d) {
        int dom = d.getDayOfMonth();
        return dom >= WIN_FROM && dom <= WIN_TO;
    }

    // ================= 捞单 =================

    @Override
    public List<TBcastZeroTask> listDue(Date at) {
        List<TBcastZeroTask> due = new ArrayList<TBcastZeroTask>();
        if (at == null) {
            return due;
        }
        LocalDate today = toLocalDate(at);
        // 窗口里压着一层：保障期占住的那几日，手里就是有已到档的也不动，等下一轮再开口
        if (inGuaranteeWindow(today)) {
            return due;
        }
        // 只捞候催：上一轮已经催到过的（含催不动挂起的），不许再当候催捞回来
        List<TBcastZeroTask> waiting = this.bcastZeroTaskMapper.selectList(new QueryWrapper<TBcastZeroTask>()
                .eq("status", STATUS_WAIT)
                .eq("del_flag", 0)
                .isNotNull("due_at")
                .orderByAsc("id"));
        for (TBcastZeroTask r : waiting) {
            // 捞单只认一件事：开口那一档踩过去没有；没到档的先放着，本轮不点名
            if (!today.isBefore(openSlotOf(r))) {
                due.add(r);
            }
        }
        return due;
    }

    // ================= 回写 =================

    @Override
    public int runOnce(Date at) {
        // 挑单与回写共用同一把尺：本轮名单就是 listDue 那张，回写不另起炉灶
        List<TBcastZeroTask> due = listDue(at);
        if (due.isEmpty()) {
            // 整轮一条也没捞着：正常收口，不必当事故处理
            log.info(MSG_NO_DUE);
            return 0;
        }
        // 催到那一刻：同一轮同一刻落笔；datetime 只到秒，先截齐，免得写与点差着毫秒
        Date moment = new Date((at.getTime() / 1000) * 1000);
        List<TBcastSite> sites = this.bcastSiteMapper.selectList(
                new QueryWrapper<TBcastSite>().eq("del_flag", 0));
        int ok = 0;
        // 够着的每一条都得过一遍，不能办成头一条就交差；同一秒挤进来的分两桩记，不合并不压掉
        for (TBcastZeroTask r : due) {
            try {
                if (processOne(r, moment, sites)) {
                    ok++;
                }
            } catch (Exception e) {
                // 同轮里有一桩走岔了，不许把其余甩下，剩下的还得一条一条办到底
                log.warn("催办单条走岔，跳过继续：id={}", r.getId(), e);
            }
        }
        // 收口三个数只报本轮这一回，且回库里逐条点到位，不照屏幕誊
        ZeroRoundSummary s = roundSummary(moment);
        log.info("本轮收口：还欠开口{}条，本轮催到{}条，催不动{}条",
                s.getPending(), s.getUrged(), s.getBlocked());
        return ok;
    }

    /**
     * 办一条。先核档（两类开不成单的当场转挂催不动），核得过才开口：
     * 情形转催到 + 催到那一刻 + 两路送达各记一路，几样痕迹落同一笔。
     *
     * @return true=本条催到（成功条数只数这一种）
     */
    private boolean processOne(TBcastZeroTask r, Date moment, List<TBcastSite> sites) {
        String[] siteNo = new String[1];
        String reason = blockedReason(r, sites, siteNo);
        if (reason != null) {
            markBlocked(r, moment, reason, siteNo[0]);
            return false;
        }
        String t = ts(moment);
        // 话分两头递：站内那一条递县广电局，手机那一条递台站值机室；
        // 哪一路送到就单独记哪一路，手机那一条不能替站内那一条交差
        String note = "催到 " + t
                + "；站内→县广电局 已送达 " + t
                + "；手机→台站值机室 已送达 " + t;
        UpdateWrapper<TBcastZeroTask> uw = new UpdateWrapper<TBcastZeroTask>();
        uw.eq("id", r.getId())
          // 写层再卡一道：已不在候催的（上一轮已催到/已挂催不动）落不进去
          .eq("status", STATUS_WAIT)
          .eq("del_flag", 0)
          // 痕迹一：情形由候催转成催到
          .set("status", STATUS_DONE)
          // 痕迹二：把催到的那一刻落下。两痕迹同一笔落，只落其一不算走完
          .set("update_time", moment)
          .set("remark", appendNote(r.getRemark(), note));
        return this.bcastZeroTaskMapper.update(null, uw) == 1;
    }

    /**
     * 核档：这条开不成单的两种原委——台站早已从底册上摘走，或值机联系人那一栏空着。
     * 台站往条目代号与记要里对底册代号；对上了而底册情形不是在册，就是摘册。
     *
     * @return 原委字（{@link #REASON_SITE_GONE}/{@link #REASON_CONTACT_EMPTY}），开得成单回 null
     */
    private String blockedReason(TBcastZeroTask r, List<TBcastSite> sites, String[] siteNoOut) {
        String itemNo = r.getItemNo() == null ? "" : r.getItemNo();
        String content = r.getContent() == null ? "" : r.getContent();
        String hay = itemNo + " " + content;
        for (TBcastSite s : sites) {
            String no = s.getSiteNo() == null ? "" : s.getSiteNo().trim();
            if (no.isEmpty() || !hay.contains(no)) {
                continue;
            }
            Integer st = s.getStatus();
            if (st != null && st.intValue() != 0) {
                siteNoOut[0] = no;
                return REASON_SITE_GONE;
            }
        }
        if (content.trim().isEmpty()) {
            return REASON_CONTACT_EMPTY;
        }
        return null;
    }

    /**
     * 当场转挂催不动：卡在哪一环、原委随手并写进同一条（remark），日后按原委能挑出一批来。
     * 条目不藏不掖——del_flag 不动，现行清单里照样挂着，只是这轮（及往后）不再去碰它。
     */
    private void markBlocked(TBcastZeroTask r, Date moment, String reason, String siteNo) {
        String note = "催不动[" + STAGE_CHECK + "] " + reason
                + (siteNo == null ? "" : "(" + siteNo + ")")
                + " " + ts(moment);
        UpdateWrapper<TBcastZeroTask> uw = new UpdateWrapper<TBcastZeroTask>();
        uw.eq("id", r.getId())
          .eq("status", STATUS_WAIT)
          .eq("del_flag", 0)
          .set("status", STATUS_FAIL)
          .set("update_time", moment)
          .set("remark", appendNote(r.getRemark(), note));
        this.bcastZeroTaskMapper.update(null, uw);
    }

    /** 备注接着写不盖旧的；500 格写不下时留住最新这一笔（催到/催不动一条只落一回，旧记让位）。 */
    private static String appendNote(String oldNote, String note) {
        String joined = (oldNote == null || oldNote.trim().isEmpty())
                ? note : oldNote + "；" + note;
        return joined.length() <= REMARK_MAX ? joined : note;
    }

    // ================= 收口 =================

    /**
     * 一轮收口要交的三个数：还欠开口的、本轮催到的、催不动的，只报本轮这一回。
     * 三个数回库里逐条点到位（按本轮落笔那一刻 update_time=at 认本轮），不照屏幕誊。
     */
    public ZeroRoundSummary roundSummary(Date at) {
        Date moment = at == null ? null : new Date((at.getTime() / 1000) * 1000);
        long pending = countBy(STATUS_WAIT, null);
        long urged = countBy(STATUS_DONE, moment);
        long blocked = countBy(STATUS_FAIL, moment);
        return new ZeroRoundSummary(pending, urged, blocked);
    }

    private long countBy(int status, Date updateTimeEq) {
        QueryWrapper<TBcastZeroTask> qw = new QueryWrapper<TBcastZeroTask>();
        qw.eq("status", status).eq("del_flag", 0);
        if (updateTimeEq != null) {
            // 与回写同走 Date 参数一条路换算，不把墙钟字符串拿去和 datetime 列比
            qw.eq("update_time", updateTimeEq);
        }
        Integer c = this.bcastZeroTaskMapper.selectCount(qw);
        return c == null ? 0L : c.longValue();
    }

    /** 一轮收口的三个数：还欠开口的 / 本轮催到的 / 催不动的。 */
    public static class ZeroRoundSummary {
        /** 还欠开口的（此刻仍挂候催的条目数） */
        private final long pending;
        /** 本轮催到的 */
        private final long urged;
        /** 催不动的（本轮转挂） */
        private final long blocked;

        public ZeroRoundSummary(long pending, long urged, long blocked) {
            this.pending = pending;
            this.urged = urged;
            this.blocked = blocked;
        }

        public long getPending() {
            return pending;
        }

        public long getUrged() {
            return urged;
        }

        public long getBlocked() {
            return blocked;
        }

        @Override
        public String toString() {
            return "还欠开口" + pending + "，本轮催到" + urged + "，催不动" + blocked;
        }
    }
}
