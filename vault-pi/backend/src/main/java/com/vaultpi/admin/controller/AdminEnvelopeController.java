package com.vaultpi.admin.controller;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import com.vaultpi.content.entity.SysEnvelope;
import com.vaultpi.content.repository.SysEnvelopeRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin/envelope", ApiPaths.V1 + "/admin/envelope" })
public class AdminEnvelopeController {

    private final SysEnvelopeRepository envelopeRepository;

    public AdminEnvelopeController(SysEnvelopeRepository envelopeRepository) {
        this.envelopeRepository = envelopeRepository;
    }

    @GetMapping("/all")
    public Result<List<SysEnvelope>> all() {
        return Result.ok(envelopeRepository.findAllByOrderByCreateTimeDesc());
    }

    @PostMapping("/update")
    public Result<SysEnvelope> update(@RequestBody SysEnvelope env) {
        if (env.getId() == null) return Result.fail(400, "ID 不能为空");
        SysEnvelope existing = envelopeRepository.findById(env.getId()).orElse(null);
        if (existing == null) return Result.fail(404, "红包不存在");
        
        if (env.getStatus() != null) existing.setStatus(env.getStatus());
        return Result.ok(envelopeRepository.save(existing));
    }

    @PostMapping("/delete")
    public Result<String> delete(@RequestBody Map<String, Long> body) {
        Long id = body.get("id");
        if (id == null) return Result.fail(400, "ID 不能为空");
        envelopeRepository.deleteById(id);
        return Result.ok("删除成功");
    }
}
