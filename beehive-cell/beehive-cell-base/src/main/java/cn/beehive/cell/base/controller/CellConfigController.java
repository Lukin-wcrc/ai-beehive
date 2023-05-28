package cn.beehive.cell.base.controller;

import cn.beehive.base.handler.response.R;
import cn.beehive.cell.base.domain.vo.CellConfigVO;
import cn.beehive.cell.base.service.CellConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author hncboy
 * @date 2023/5/29
 * Cell 配置项控制器
 */
@AllArgsConstructor
@Tag(name = "Cell 配置项相关接口")
@RequestMapping("/cell_config")
@RestController
public class CellConfigController {

    private final CellConfigService cellConfigService;

    @Operation(summary = "cell 配置项列表")
    @GetMapping("/list")
    public R<List<CellConfigVO>> listCellConfig(@RequestParam Integer cellId) {
        return R.data(cellConfigService.listCellConfig(cellId));
    }
}
