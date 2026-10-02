package com.fc.v2.controller.admin;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.custom.BookInVo;
import com.fc.v2.model.custom.BookReceipt;
import com.fc.v2.service.ITBcastOffRowService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 运行情况报送核收 Controller（batch-process 形状：整册收口）。
 *
 * 只摆一个提交把手：整册 {@code /receive} 进服务层那一个入口；
 * 册内的行没有单条新增/修改/删除端点，页面上也不另立"照面/记账"的第二套说法。
 *
 * @author fuce
 * @date 2026-10-02
 */
@Api(value = "运行情况报送核收")
@Controller
@RequestMapping("/bcastOffRow")
public class BcastOffRowController extends BaseController {

    private final String prefix = "admin/bcastOffRow";

    @Autowired
    private ITBcastOffRowService bcastOffRowService;

    @ApiOperation(value = "核收台账跳转", notes = "核收台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("bcastOffRow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "运行情况报送册整册进门", action = "receive")
    @ApiOperation(value = "整册进门收口（唯一提交入口）",
            notes = "一册一批号进门定死；同批号第二遍只照面不记账；超整册行数上限原册退回零行；空册落空册")
    @PostMapping("/receive")
    @RequiresPermissions("bcastOffRow:receive")
    @ResponseBody
    public AjaxResult receive(@RequestBody BookInVo vo) {
        if (vo == null) {
            return error("请求体为空：整册进门须把批号与各行一并交来");
        }
        try {
            BookReceipt receipt = bcastOffRowService.receiveBook(
                    vo.getBatchNo(), vo.getSeason(), vo.getRows(), vo.getLimitMinutes());
            if (receipt.isOverLimit()) {
                return AjaxResult.success(200, "整册行数超上限，原册送回、一行未落库", receipt);
            }
            if (receipt.isCompareOnly()) {
                return AjaxResult.success(200, "同批号第二遍：只照面未记账，库内不添不减，去向维持头一遍", receipt);
            }
            String tip = "空册".equals(receipt.getReceipt())
                    ? "空册：一行未收，回执落\"空册\""
                    : "整册进门完成：核讫";
            if (!receipt.getDiffs().isEmpty()) {
                tip = tip + "；注意：轧账有" + receipt.getDiffs().size() + "条差异，见回执";
            }
            return AjaxResult.success(200, tip, receipt);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "报送册回执轧账", action = "receipt")
    @ApiOperation(value = "回执三数与轧账差异", notes = "三数从库里逐行点上来，对不齐当场列差异行")
    @GetMapping("/receipt")
    @RequiresPermissions("bcastOffRow:list")
    @ResponseBody
    public AjaxResult receipt(@RequestParam("batchNo") String batchNo) {
        try {
            return AjaxResult.success(200, "回执（库点）", bcastOffRowService.getReceipt(batchNo));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "册内逐行查询", action = "bookRows")
    @ApiOperation(value = "册内逐行", notes = "按原报行次升序；已挪出另放的行仍带出，老册一条不漏")
    @GetMapping("/bookRows")
    @RequiresPermissions("bcastOffRow:list")
    @ResponseBody
    public AjaxResult bookRows(@RequestParam("batchNo") String batchNo) {
        return AjaxResult.success(200, "册内逐行", bcastOffRowService.listBookRows(batchNo));
    }

    @Log(title = "现行名单查询", action = "current")
    @ApiOperation(value = "现行未销名单", notes = "只摆未销且未挪走的行；归档行不再出现")
    @GetMapping("/current")
    @RequiresPermissions("bcastOffRow:list")
    @ResponseBody
    public ResultTable current(String itemCode) {
        startPage();
        java.util.List<com.fc.v2.model.auto.TBcastOffRow> list = bcastOffRowService.listCurrentRows(itemCode);
        com.github.pagehelper.PageInfo<com.fc.v2.model.auto.TBcastOffRow> page =
                new com.github.pagehelper.PageInfo<com.fc.v2.model.auto.TBcastOffRow>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "报送册撤册", action = "withdraw")
    @ApiOperation(value = "撤册", notes = "头一遍落下的结果只能由这道手续作废；撤后同批号方可重新进门")
    @PostMapping("/withdraw")
    @RequiresPermissions("bcastOffRow:withdraw")
    @ResponseBody
    public AjaxResult withdraw(String batchNo, String remark) {
        try {
            int n = bcastOffRowService.withdrawBook(batchNo, remark);
            if (n < 0) {
                return error("批号[" + batchNo + "]下无在册册，无需撤");
            }
            return AjaxResult.success("撤册完成，" + n + "行作删除标记，批号腾开可重新进门");
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "季末未销行挪出另放", action = "archive")
    @ApiOperation(value = "季末挪行", notes = "连续两个报送季仍未销的行挪出当前名册；挪走不是抹掉，老册仍一条不漏")
    @PostMapping("/archive")
    @RequiresPermissions("bcastOffRow:archive")
    @ResponseBody
    public AjaxResult archive(@RequestParam("currentSeason") String currentSeason) {
        try {
            int n = bcastOffRowService.archiveStaleRows(currentSeason);
            return AjaxResult.success("季末挪行完成，共挪出" + n + "行另放；老册翻看不受影响");
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }
}
