package com.mashangan.blog.web.admin;

import com.mashangan.blog.domain.entity.Attachment;
import com.mashangan.blog.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping
    public List<Attachment> list() {
        return attachmentService.list();
    }

    @PostMapping("/upload")
    public Attachment upload(@RequestParam("file") MultipartFile file) throws IOException {
        return attachmentService.upload(file);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        attachmentService.delete(id);
    }

    /**
     * 把 ResponseStatusException 的 reason 包装进响应体（Spring Boot 默认
     * include-message=never，中文提示不会出现在 body 里，前端拿不到具体原因）。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : "请求失败";
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
    }
}

