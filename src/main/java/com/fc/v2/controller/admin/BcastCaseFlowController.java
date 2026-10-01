package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TBcastCaseFlow;
import com.fc.v2.service.ITBcastCaseFlowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 播出事故处置单 Controller（state-machine 形状：流转入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "播出事故处置单")
@Controller
@RequestMapping("/bcastCaseFlow")
public class BcastCaseFlowController extends BaseController {

    private final String prefix = "admin/bcastCaseFlow";

    @Autowired
    private ITBcastCaseFlowService bcastCaseFlowService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("bcastCaseFlow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "播出事故处置单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("bcastCaseFlow:list")
    @ResponseBody
    public ResultTable list(TBcastCaseFlow record) {
        QueryWrapper<TBcastCaseFlow> queryWrapper = new QueryWrapper<TBcastCaseFlow>();
        startPage();
        com.github.pagehelper.PageInfo<TBcastCaseFlow> page =
                new com.github.pagehelper.PageInfo<TBcastCaseFlow>(bcastCaseFlowService.selectTBcastCaseFlowList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "播出事故处置单推进", action = "advance")
    @ApiOperation(value = "推进一档", notes = "推进一档")
    @PostMapping("/advance")
    @RequiresPermissions("bcastCaseFlow:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        return toAjax(bcastCaseFlowService.advance(id, remark) != null ? 1 : 0);
    }

    @Log(title = "播出事故处置单回退", action = "rollback")
    @ApiOperation(value = "回退一档", notes = "回退一档")
    @PostMapping("/rollback")
    @RequiresPermissions("bcastCaseFlow:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        return toAjax(bcastCaseFlowService.rollback(id, remark) != null ? 1 : 0);
    }
}
