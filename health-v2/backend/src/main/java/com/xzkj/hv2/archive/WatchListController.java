package com.xzkj.hv2.archive;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xzkj.hv2.archive.ArchiveViews.WatchListItem;
import com.xzkj.hv2.common.api.ApiResponse;
import com.xzkj.hv2.common.exception.BizException;

/** 重点监护、今日关注名单的接口（docs/05 第八节）。 */
@RestController
@RequestMapping("/api/watch-list")
public class WatchListController {

    private static final Set<String> TYPES = Set.of("KEY", "TODAY");

    private final WatchListService service;

    public WatchListController(WatchListService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<WatchListItem>> list(@RequestParam String type) {
        return ApiResponse.ok(service.list(checkType(type)));
    }

    /**
     * @param type KEY 重点监护 / TODAY 今日关注
     * @param note 备注，最长 200 字，可空
     */
    public record AddRequest(@NotNull @Pattern(regexp = ArchiveController.CARD) String cardCode,
                             @NotNull String type, @Size(max = 400) String note) {
    }

    @PostMapping
    public ApiResponse<WatchListItem> add(@Valid @RequestBody AddRequest req) {
        return ApiResponse.ok(service.add(req.cardCode(), checkType(req.type()), req.note()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable @Min(1) long id) {
        service.remove(id);
        return ApiResponse.ok();
    }

    private static String checkType(String type) {
        if (!TYPES.contains(type)) {
            throw BizException.badRequest("type 只能是 KEY、TODAY");
        }
        return type;
    }
}
