package com.vaultpi.admin.controller;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.content.entity.Announcement;
import com.vaultpi.content.repository.AnnouncementRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端 - 公告 CRUD（暂不校验管理员身份，上线前需加鉴权）
 */
@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin/announcement", ApiPaths.V1 + "/admin/announcement" })
public class AdminAnnouncementController {

    private final AnnouncementRepository announcementRepository;

    public AdminAnnouncementController(AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
    }

    @GetMapping("/page")
    public Result<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        var page = announcementRepository.findAllByOrderByCreateTimeDesc(PageRequest.of(Math.max(0, pageNo - 1), Math.max(1, pageSize)));
        List<Map<String, Object>> content = page.getContent().stream()
            .map(this::toMap)
            .collect(Collectors.toList());
        return Result.ok(Map.of(
            "content", content,
            "totalElements", page.getTotalElements(),
            "totalPages", page.getTotalPages()
        ));
    }

    private Map<String, Object> toMap(Announcement a) {
        return Map.of(
            "id", a.getId(),
            "title", a.getTitle(),
            "content", a.getContent() != null ? a.getContent() : "",
            "lang", a.getLang() != null ? a.getLang() : "CN",
            "isTop", a.getIsTop() != null ? a.getIsTop() : "1",
            "status", a.getStatus() != null ? a.getStatus() : "NORMAL",
            "createTime", a.getCreateTime() != null ? a.getCreateTime().toString() : ""
        );
    }

    @PostMapping("/add")
    public Result<Announcement> add(@RequestBody Map<String, Object> body) {
        Announcement a = new Announcement();
        a.setTitle(safeStr(body, "title").trim());
        a.setContent(safeStr(body, "content"));
        a.setLang(safeStr(body, "lang").isEmpty() ? "CN" : safeStr(body, "lang"));
        a.setIsTop(safeStr(body, "isTop").isEmpty() ? "1" : safeStr(body, "isTop"));
        a.setStatus(safeStr(body, "status").isEmpty() ? "NORMAL" : safeStr(body, "status"));
        a = announcementRepository.save(a);
        return Result.ok(a);
    }

    @PostMapping("/update")
    public Result<Announcement> update(@RequestBody Map<String, Object> body) {
        long id = safeLong(body, "id");
        if (id <= 0) return Result.fail(400, "缺少或无效的 id");
        Announcement a = announcementRepository.findById(id).orElse(null);
        if (a == null) return Result.fail(404, "公告不存在");
        if (body.containsKey("title")) a.setTitle(safeStr(body, "title").trim());
        if (body.containsKey("content")) a.setContent(safeStr(body, "content"));
        if (body.containsKey("lang")) a.setLang(safeStr(body, "lang"));
        if (body.containsKey("isTop")) a.setIsTop(safeStr(body, "isTop"));
        if (body.containsKey("status")) a.setStatus(safeStr(body, "status"));
        a = announcementRepository.save(a);
        return Result.ok(a);
    }

    @PostMapping("/top")
    public Result<Announcement> setTop(@RequestBody Map<String, Object> body) {
        long id = safeLong(body, "id");
        if (id <= 0) return Result.fail(400, "缺少或无效的 id");
        Announcement a = announcementRepository.findById(id).orElse(null);
        if (a == null) return Result.fail(404, "公告不存在");
        a.setIsTop("0");
        a = announcementRepository.save(a);
        return Result.ok(a);
    }

    @PostMapping("/untop")
    public Result<Announcement> cancelTop(@RequestBody Map<String, Object> body) {
        long id = safeLong(body, "id");
        if (id <= 0) return Result.fail(400, "缺少或无效的 id");
        Announcement a = announcementRepository.findById(id).orElse(null);
        if (a == null) return Result.fail(404, "公告不存在");
        a.setIsTop("1");
        a = announcementRepository.save(a);
        return Result.ok(a);
    }

    @PostMapping("/delete")
    public Result<String> delete(@RequestBody Map<String, Object> body) {
        long id = safeLong(body, "id");
        if (id <= 0) return Result.fail(400, "缺少或无效的 id");
        announcementRepository.deleteById(id);
        return Result.ok("已删除");
    }

    private static String safeStr(Map<String, Object> body, String key) {
        Object v = body != null ? body.get(key) : null;
        return v == null ? "" : v.toString();
    }

    private static long safeLong(Map<String, Object> body, String key) {
        Object v = body != null ? body.get(key) : null;
        if (v == null) return -1;
        try {
            return v instanceof Number ? ((Number) v).longValue() : Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
