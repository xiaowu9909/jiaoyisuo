package com.vaultpi.content;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.LangResolver;
import com.vaultpi.common.Result;
import com.vaultpi.content.entity.Announcement;
import com.vaultpi.content.repository.AnnouncementRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/announcement", ApiPaths.V1 + "/announcement" })
public class AnnouncementController {

    private final AnnouncementRepository announcementRepository;

    public AnnouncementController(AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
    }

    @GetMapping("/page")
    public Result<Map<String, Object>> page(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String lang) {
        String resolvedLang = LangResolver.resolve(request, lang);
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        var page = announcementRepository.findByLangOrderByCreateTimeDesc(resolvedLang, PageRequest.of(safePageNo - 1, safePageSize));
        List<Map<String, Object>> content = page.getContent().stream().map(this::toMap).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(
            HttpServletRequest request,
            @PathVariable long id,
            @RequestParam(required = false) String lang) {
        String resolvedLang = LangResolver.resolve(request, lang);
        return announcementRepository.findById(id)
            .filter(a -> resolvedLang.equals(a.getLang()))
            .map(a -> Result.<Map<String, Object>>ok(toMap(a)))
            .orElse(Result.fail(404, "公告不存在"));
    }

    private Map<String, Object> toMap(Announcement a) {
        String time = a.getCreateTime() != null
            ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.of("Asia/Shanghai")).format(a.getCreateTime())
            : "";
        return Map.of(
            "id", a.getId(),
            "title", a.getTitle() != null ? a.getTitle() : "",
            "content", a.getContent() != null ? a.getContent() : "",
            "createTime", time
        );
    }
}
