package com.vaultpi.admin.controller;

import com.vaultpi.common.ApiPaths;
import com.vaultpi.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * B 端图片上传，供内容管理（Logo、首页图等）使用。
 * 上传后返回可访问的图片 URL，C/B 端用该 URL 展示。
 * 校验扩展名 + 文件头魔数，防止伪装成图片的恶意文件。
 */
@RestController
@RequestMapping(value = { ApiPaths.BASE + "/admin/upload", ApiPaths.V1 + "/admin/upload" })
public class AdminUploadController {

    private static final String UPLOAD_URI_PREFIX = "/uploads/";
    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String[] ALLOWED_EXT = { ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg" };

    private static final byte[] PNG_HEAD = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
    private static final byte[] JPEG_HEAD = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] GIF_HEAD = { 0x47, 0x49, 0x46, 0x38 }; // GIF8
    private static final byte[] WEBP_RIFF = { 0x52, 0x49, 0x46, 0x46 }; // RIFF
    private static final byte[] WEBP_WEBP = { 0x57, 0x45, 0x42, 0x50 }; // WEBP at offset 8

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        if (file == null || file.isEmpty())
            return Result.fail(400, "请选择要上传的图片");
        if (file.getSize() > MAX_SIZE)
            return Result.fail(400, "图片大小不能超过 5MB");

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank())
            return Result.fail(400, "文件名无效");
        String ext = "";
        int i = originalName.lastIndexOf('.');
        if (i >= 0) ext = originalName.substring(i).toLowerCase();
        boolean allowed = false;
        for (String e : ALLOWED_EXT) {
            if (e.equals(ext)) { allowed = true; break; }
        }
        if (!allowed)
            return Result.fail(400, "仅支持图片格式：png, jpg, jpeg, gif, webp, svg");

        if (!isAllowedImageContent(file, ext))
            return Result.fail(400, "文件内容与扩展名不符或非允许的图片类型");

        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "") + ext;
            Path target = dir.resolve(filename);
            file.transferTo(target.toFile());

            String baseUrl = buildBaseUrl(request);
            String url = baseUrl + UPLOAD_URI_PREFIX + filename;
            return Result.ok(Map.of("url", url));
        } catch (IOException e) {
            return Result.fail(500, "保存文件失败：" + e.getMessage());
        }
    }

    /** 根据扩展名与文件头魔数校验，防止恶意文件伪装成图片 */
    private boolean isAllowedImageContent(MultipartFile file, String ext) {
        byte[] head = new byte[256];
        try (InputStream in = file.getInputStream()) {
            int n = in.read(head);
            if (n < 4) return false;
            head = java.util.Arrays.copyOf(head, n);
        } catch (IOException e) {
            return false;
        }
        if (".png".equals(ext)) return startsWith(head, PNG_HEAD);
        if (".jpg".equals(ext) || ".jpeg".equals(ext)) return startsWith(head, JPEG_HEAD);
        if (".gif".equals(ext)) return startsWith(head, GIF_HEAD);
        if (".webp".equals(ext)) return head.length >= 12 && startsWith(head, WEBP_RIFF) && startsWithAt(head, WEBP_WEBP, 8);
        if (".svg".equals(ext)) {
            String start = new String(head, 0, head.length, java.nio.charset.StandardCharsets.UTF_8).trim();
            return start.startsWith("<?xml") || start.startsWith("<svg") || start.contains("<svg");
        }
        return false;
    }

    private static boolean startsWith(byte[] a, byte[] b) {
        if (a.length < b.length) return false;
        for (int i = 0; i < b.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    private static boolean startsWithAt(byte[] a, byte[] b, int offset) {
        if (a.length < offset + b.length) return false;
        for (int i = 0; i < b.length; i++) if (a[offset + i] != b[i]) return false;
        return true;
    }

    private String buildBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int port = request.getServerPort();
        if ("http".equals(scheme) && port == 80 || "https".equals(scheme) && port == 443)
            return scheme + "://" + serverName;
        return scheme + "://" + serverName + ":" + port;
    }
}
