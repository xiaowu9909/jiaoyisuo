package com.vaultpi.admin.controller;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.content.entity.SysActivity;
import com.vaultpi.content.repository.SysActivityRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin/activity", ApiPaths.V1 + "/admin/activity" })
public class AdminActivityController {

    private final SysActivityRepository activityRepository;

    public AdminActivityController(SysActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @GetMapping("/all")
    public Result<List<SysActivity>> all() {
        return Result.ok(activityRepository.findAllByOrderByCreateTimeDesc());
    }

    @PostMapping("/add")
    public Result<SysActivity> add(@RequestBody SysActivity act) {
        if (act.getTitle() == null) return Result.fail(400, "标题不能为空");
        return Result.ok(activityRepository.save(act));
    }

    @PostMapping("/update")
    public Result<SysActivity> update(@RequestBody SysActivity act) {
        if (act.getId() == null) return Result.fail(400, "ID 不能为空");
        SysActivity existing = activityRepository.findById(act.getId()).orElse(null);
        if (existing == null) return Result.fail(404, "活动不存在");

        if (act.getTitle() != null) existing.setTitle(act.getTitle());
        if (act.getContent() != null) existing.setContent(act.getContent());
        if (act.getBannerUrl() != null) existing.setBannerUrl(act.getBannerUrl());
        if (act.getStartTime() != null) existing.setStartTime(act.getStartTime());
        if (act.getEndTime() != null) existing.setEndTime(act.getEndTime());
        if (act.getStatus() != null) existing.setStatus(act.getStatus());

        return Result.ok(activityRepository.save(existing));
    }

    @PostMapping("/delete")
    public Result<String> delete(@RequestBody Map<String, Long> body) {
        Long id = body.get("id");
        if (id == null) return Result.fail(400, "ID 不能为空");
        activityRepository.deleteById(id);
        return Result.ok("删除成功");
    }
}
