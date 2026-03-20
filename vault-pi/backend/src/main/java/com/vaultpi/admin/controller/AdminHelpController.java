package com.vaultpi.admin.controller;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.content.entity.SysHelp;
import com.vaultpi.content.repository.SysHelpRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin/help", ApiPaths.V1 + "/admin/help" })
public class AdminHelpController {

    private final SysHelpRepository sysHelpRepository;

    public AdminHelpController(SysHelpRepository sysHelpRepository) {
        this.sysHelpRepository = sysHelpRepository;
    }

    @GetMapping("/page")
    public Result<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        var page = sysHelpRepository.findAllByOrderBySortAscCreateTimeDesc(PageRequest.of(Math.max(0, pageNo - 1), Math.max(1, pageSize)));
        List<Map<String, Object>> content = page.getContent().stream().map(this::toMap).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }

    @GetMapping("/detail")
    public Result<Map<String, Object>> detail(@RequestParam long id) {
        return sysHelpRepository.findById(id)
            .map(h -> Result.<Map<String, Object>>ok(toMap(h)))
            .orElse(Result.fail(404, "帮助不存在"));
    }

    @PostMapping("/add")
    public Result<SysHelp> add(@RequestBody Map<String, Object> body) {
        SysHelp h = new SysHelp();
        h.setTitle(safeStr(body, "title").trim());
        h.setClassification(safeStr(body, "classification").isEmpty() ? "OTHER" : safeStr(body, "classification"));
        h.setContent(safeStr(body, "content"));
        h.setLang(safeStr(body, "lang").isEmpty() ? "CN" : safeStr(body, "lang"));
        h.setSort(safeInt(body, "sort", 0));
        h.setIsTop(safeStr(body, "isTop").isEmpty() ? "1" : safeStr(body, "isTop"));
        h.setStatus(safeStr(body, "status").isEmpty() ? "NORMAL" : safeStr(body, "status"));
        h = sysHelpRepository.save(h);
        return Result.ok(h);
    }

    private static String safeStr(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : v.toString();
    }

    private static int safeInt(Map<String, Object> body, String key, int def) {
        Object v = body.get(key);
        if (v == null) return def;
        try {
            return v instanceof Number ? ((Number) v).intValue() : Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long safeLong(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return -1;
        try {
            return v instanceof Number ? ((Number) v).longValue() : Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @PostMapping("/update")
    public Result<SysHelp> update(@RequestBody Map<String, Object> body) {
        long id = safeLong(body, "id");
        if (id < 0) return Result.fail(400, "缺少或无效的 id");
        SysHelp h = sysHelpRepository.findById(id).orElse(null);
        if (h == null) return Result.fail(404, "帮助不存在");
        if (body.containsKey("title")) h.setTitle(safeStr(body, "title").trim());
        if (body.containsKey("classification")) h.setClassification(safeStr(body, "classification"));
        if (body.containsKey("content")) h.setContent(safeStr(body, "content"));
        if (body.containsKey("lang")) h.setLang(safeStr(body, "lang"));
        if (body.containsKey("sort")) h.setSort(safeInt(body, "sort", 0));
        if (body.containsKey("isTop")) h.setIsTop(safeStr(body, "isTop"));
        if (body.containsKey("status")) h.setStatus(safeStr(body, "status"));
        h = sysHelpRepository.save(h);
        return Result.ok(h);
    }

    @PostMapping("/top")
    public Result<SysHelp> setTop(@RequestBody Map<String, Object> body) {
        long id = safeLong(body, "id");
        if (id < 0) return Result.fail(400, "缺少或无效的 id");
        SysHelp h = sysHelpRepository.findById(id).orElse(null);
        if (h == null) return Result.fail(404, "帮助不存在");
        h.setIsTop("0");
        h = sysHelpRepository.save(h);
        return Result.ok(h);
    }

    @PostMapping("/untop")
    public Result<SysHelp> cancelTop(@RequestBody Map<String, Object> body) {
        long id = safeLong(body, "id");
        if (id < 0) return Result.fail(400, "缺少或无效的 id");
        SysHelp h = sysHelpRepository.findById(id).orElse(null);
        if (h == null) return Result.fail(404, "帮助不存在");
        h.setIsTop("1");
        h = sysHelpRepository.save(h);
        return Result.ok(h);
    }

    @PostMapping("/delete")
    public Result<String> delete(@RequestBody Map<String, Object> body) {
        long id = safeLong(body, "id");
        if (id < 0) return Result.fail(400, "缺少或无效的 id");
        sysHelpRepository.deleteById(id);
        return Result.ok("已删除");
    }

    private Map<String, Object> toMap(SysHelp h) {
        String time = h.getCreateTime() != null ? h.getCreateTime().toString() : "";
        Map<String, Object> m = new HashMap<>();
        m.put("id", h.getId());
        m.put("title", h.getTitle());
        m.put("classification", h.getClassification());
        m.put("content", h.getContent());
        m.put("lang", h.getLang());
        m.put("sort", h.getSort());
        m.put("isTop", h.getIsTop());
        m.put("status", h.getStatus());
        m.put("createTime", time);
        return m;
    }
}
