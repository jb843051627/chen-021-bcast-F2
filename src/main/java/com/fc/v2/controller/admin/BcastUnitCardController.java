package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TBcastUnitCard;
import com.fc.v2.service.ITBcastUnitCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 台站核点卡立卡单 Controller
 *
 * @author fuce
 * @date 2026-09-12
 */
@Api(value = "台站核点卡立卡单")
@Controller
@RequestMapping("/BcastUnitCardController")
public class BcastUnitCardController extends BaseController {

    private final String prefix = "admin/bcastUnitCard";

    @Autowired
    private ITBcastUnitCardService bcastUnitCardService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("bcast:bcastUnitCard:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "台站核点卡立卡单集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("bcast:bcastUnitCard:list")
    @ResponseBody
    public ResultTable list(TBcastUnitCard record) {
        QueryWrapper<TBcastUnitCard> queryWrapper = new QueryWrapper<TBcastUnitCard>();
        startPage();
        com.github.pagehelper.PageInfo<TBcastUnitCard> page =
                new com.github.pagehelper.PageInfo<TBcastUnitCard>(bcastUnitCardService.selectTBcastUnitCardList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "台站核点卡立卡单新增", action = "add")
    @ApiOperation(value = "新增", notes = "新增")
    @PostMapping("/add")
    @RequiresPermissions("bcast:bcastUnitCard:add")
    @ResponseBody
    public AjaxResult add(TBcastUnitCard record) {
        return toAjax(bcastUnitCardService.insertTBcastUnitCard(record));
    }

    @Log(title = "台站核点卡立卡单修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存")
    @PostMapping("/edit")
    @RequiresPermissions("bcast:bcastUnitCard:edit")
    @ResponseBody
    public AjaxResult editSave(TBcastUnitCard record) {
        return toAjax(bcastUnitCardService.updateTBcastUnitCard(record));
    }

    @Log(title = "台站核点卡立卡单删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("bcast:bcastUnitCard:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(bcastUnitCardService.deleteTBcastUnitCardByIds(ids));
    }
}
