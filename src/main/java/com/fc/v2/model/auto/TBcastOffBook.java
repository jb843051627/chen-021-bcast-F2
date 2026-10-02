package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 运行情况报送册总簿对象 t_bcast_off_book
 * 一册一个批号、进门定死；回执三数（总行/收下/退回）随册物化，只从库里逐行点上来。
 *
 * @author fuce
 * @date 2026-10-02
 */
@TableName("t_bcast_off_book")
@ApiModel(value = "TBcastOffBook", description = "运行情况报送册总簿")
public class TBcastOffBook implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 运行情况报送册批号（全库唯一，进门定死） */
    @TableField("batch_no")
    @ApiModelProperty(value = "运行情况报送册批号")
    private String batchNo;

    /** 报送季 形如 2026Q3 */
    @TableField("season")
    @ApiModelProperty(value = "报送季 形如 2026Q3")
    private String season;

    /** 回执：本册行数（库内逐行点齐，非屏上点数） */
    @TableField("row_count")
    @ApiModelProperty(value = "本册行数")
    private Integer rowCount;

    /** 回执：收下行数 */
    @TableField("accepted_count")
    @ApiModelProperty(value = "收下行数")
    private Integer acceptedCount;

    /** 回执：退回行数（accepted+returned=row_count） */
    @TableField("returned_count")
    @ApiModelProperty(value = "退回行数")
    private Integer returnedCount;

    /** 回执栏 核讫/空册 */
    @TableField("receipt")
    @ApiModelProperty(value = "回执栏 核讫/空册")
    private String receipt;

    /** 册子情形 0在册 1已撤 */
    @TableField("status")
    @ApiModelProperty(value = "册子情形 0在册 1已撤")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public Integer getAcceptedCount() {
        return acceptedCount;
    }

    public void setAcceptedCount(Integer acceptedCount) {
        this.acceptedCount = acceptedCount;
    }

    public Integer getReturnedCount() {
        return returnedCount;
    }

    public void setReturnedCount(Integer returnedCount) {
        this.returnedCount = returnedCount;
    }

    public String getReceipt() {
        return receipt;
    }

    public void setReceipt(String receipt) {
        this.receipt = receipt;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
