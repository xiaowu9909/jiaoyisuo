package com.vaultpi.admin.controller;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.content.entity.SysAdvertise;
import com.vaultpi.content.repository.SysAdvertiseRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin/advertise", ApiPaths.V1 + "/admin/advertise" })
public class AdminAdvertiseController {

    private final SysAdvertiseRepository advertiseRepository;

    public AdminAdvertiseController(SysAdvertiseRepository advertiseRepository) {
        this.advertiseRepository = advertiseRepository;
    }

    @GetMapping("/all")
    public Result<List<SysAdvertise>> all() {
        return Result.ok(advertiseRepository.findAllByOrderByCreateTimeDesc());
    }

    @PostMapping("/add")
    public Result<SysAdvertise> add(@RequestBody SysAdvertise ad) {
        if (ad.getName() == null || ad.getUrl() == null) return Result.fail(400, "必填项不能为空");
        if (ad.getStatus() == null) ad.setStatus(0);
        if (ad.getLang() == null) ad.setLang("CN");
        return Result.ok(advertiseRepository.save(ad));
    }

    @PostMapping("/update")
    public Result<SysAdvertise> update(@RequestBody SysAdvertise ad) {
        if (ad.getId() == null) return Result.fail(400, "ID 不能为空");
        SysAdvertise existing = advertiseRepository.findById(ad.getId()).orElse(null);
        if (existing == null) return Result.fail(404, "广告不存在");

        if (ad.getName() != null) existing.setName(ad.getName());
        if (ad.getUrl() != null) existing.setUrl(ad.getUrl());
        if (ad.getLinkUrl() != null) existing.setLinkUrl(ad.getLinkUrl());
        if (ad.getSortOrder() != null) existing.setSortOrder(ad.getSortOrder());
        if (ad.getStatus() != null) existing.setStatus(ad.getStatus());
        if (ad.getLang() != null) existing.setLang(ad.getLang());

        return Result.ok(advertiseRepository.save(existing));
    }

    @PostMapping("/delete")
    public Result<String> delete(@RequestBody Map<String, Long> body) {
        Long id = body.get("id");
        if (id == null) return Result.fail(400, "ID 不能为空");
        advertiseRepository.deleteById(id);
        return Result.ok("删除成功");
    }
}
