package com.vaultpi.content;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.LangResolver;
import com.vaultpi.common.Result;
import com.vaultpi.content.entity.SysHelp;
import com.vaultpi.content.repository.SysHelpRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/help", ApiPaths.V1 + "/help" })
public class HelpController {

    private final SysHelpRepository sysHelpRepository;

    public HelpController(SysHelpRepository sysHelpRepository) {
        this.sysHelpRepository = sysHelpRepository;
    }

    @GetMapping("/page")
    public Result<Map<String, Object>> page(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String lang,
            @RequestParam(required = false) String classification) {
        String resolvedLang = LangResolver.resolve(request, lang);
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        var pageable = PageRequest.of(safePageNo - 1, safePageSize);
        var page = classification != null && !classification.isBlank()
            ? sysHelpRepository.findByStatusAndLangAndClassificationOrderByIsTopAscSortAscCreateTimeDesc("NORMAL", resolvedLang, classification.trim(), pageable)
            : sysHelpRepository.findByStatusAndLangOrderByIsTopAscSortAscCreateTimeDesc("NORMAL", resolvedLang, pageable);
        List<Map<String, Object>> content = page.getContent().stream().map(this::toMap).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return Result.ok(result);
    }

    @GetMapping("/classifications")
    public Result<List<String>> classifications(@RequestParam(required = false) String lang) {
        List<SysHelp> list = sysHelpRepository.findByStatusOrderBySortAscCreateTimeDesc("NORMAL", PageRequest.of(0, 500));
        List<String> cats = list.stream()
            .map(SysHelp::getClassification)
            .filter(c -> c != null && !c.isEmpty())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        return Result.ok(cats);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable long id) {
        return sysHelpRepository.findById(id)
            .filter(h -> "NORMAL".equals(h.getStatus()))
            .map(h -> Result.<Map<String, Object>>ok(toMap(h)))
            .orElse(Result.fail(404, "帮助不存在"));
    }

    private Map<String, Object> toMap(SysHelp h) {
        String time = h.getCreateTime() != null
            ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.of("Asia/Shanghai")).format(h.getCreateTime())
            : "";
        return Map.of(
            "id", h.getId(),
            "title", h.getTitle() != null ? h.getTitle() : "",
            "classification", h.getClassification() != null ? h.getClassification() : "",
            "content", h.getContent() != null ? h.getContent() : "",
            "sort", h.getSort(),
            "isTop", h.getIsTop() != null ? h.getIsTop() : "1",
            "createTime", time
        );
    }
}
