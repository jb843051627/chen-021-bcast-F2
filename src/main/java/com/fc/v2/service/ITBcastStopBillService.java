package com.fc.v2.service;

import com.fc.v2.model.auto.TBcastStopBill;

/**
 * 停播检修核准单 Service接口（approval-chain 形状：多阶段签批，无增删改查入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITBcastStopBillService {

    /** 按主键回查单据 */
    TBcastStopBill selectTBcastStopBillById(Long id);

    /** 签批一票：返回更新后的单据；被拒返回 null */
    TBcastStopBill approve(Long id, String approver, String comment);

    /** 否决：返回更新后的单据；被拒返回 null */
    TBcastStopBill reject(Long id, String approver, String comment);

    /** 退回上一环节：返回更新后的单据；被拒返回 null */
    TBcastStopBill rollback(Long id, String comment);
}
