package com.mashangan.blog.web.admin;

import com.mashangan.blog.domain.entity.Attachment;
import com.mashangan.blog.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

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
}

