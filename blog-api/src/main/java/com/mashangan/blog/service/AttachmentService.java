package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.Attachment;
import com.mashangan.blog.mapper.AttachmentMapper;
import com.mashangan.blog.security.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final Set<String> ALLOWED_EXT = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "svg",
            "pdf", "doc", "docx", "xls", "xlsx", "zip");

    private final AttachmentMapper attachmentMapper;

    @Value("${app.attachment.dir}")
    private String attachmentDir;

    @Value("${app.attachment.max-size-bytes}")
    private long maxSizeBytes;

    public Attachment upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "文件超过大小限制");
        }
        String original = file.getOriginalFilename();
        String ext = ext(original);
        if (!ALLOWED_EXT.contains(ext.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的文件类型: " + ext);
        }

        String dateDir = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String rel = dateDir + "/" + fileName;

        Path target = Path.of(attachmentDir, "upload", dateDir, fileName);
        Files.createDirectories(target.getParent());
        file.transferTo(target);

        Long uploaderId = currentUserId();
        Attachment a = new Attachment();
        a.setStoragePath("upload/" + rel);
        a.setUrlPath("/upload/" + rel);
        a.setOriginalName(original);
        a.setContentType(file.getContentType());
        a.setSize(file.getSize());
        a.setUploaderId(uploaderId);
        a.setCreatedAt(OffsetDateTime.now());
        attachmentMapper.insert(a);
        return a;
    }

    /** 字节流上传（studio 流水线下载网图后转存用），文件名用于推断扩展名 */
    public Attachment uploadBytes(byte[] bytes, String filename, String contentType) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        if (bytes.length > maxSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "文件超过大小限制");
        }
        String ext = ext(filename);
        if (!ALLOWED_EXT.contains(ext.toLowerCase())) {
            // 网图兜底：按 content-type 推断
            if (contentType != null && contentType.contains("png")) ext = "png";
            else ext = "jpg";
        }
        String dateDir = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String rel = dateDir + "/" + storedName;
        Path target = Path.of(attachmentDir, "upload", dateDir, storedName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);

        Long uploaderId = currentUserId();
        Attachment a = new Attachment();
        a.setStoragePath("upload/" + rel);
        a.setUrlPath("/upload/" + rel);
        a.setOriginalName(filename);
        a.setContentType(contentType);
        a.setSize((long) bytes.length);
        a.setUploaderId(uploaderId);
        a.setCreatedAt(OffsetDateTime.now());
        attachmentMapper.insert(a);
        return a;
    }

    /** 列出全部附件，按上传时间倒序。 */
    public List<Attachment> list() {
        return attachmentMapper.selectList(new QueryWrapper<Attachment>().orderByDesc("created_at"));
    }

    /** 删除附件：先删物理文件（尽力而为），再删数据库记录。 */
    public void delete(Long id) {
        Attachment a = attachmentMapper.selectById(id);
        if (a == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
        if (a.getStoragePath() != null && !a.getStoragePath().isBlank()) {
            try {
                Path file = Path.of(attachmentDir).resolve(a.getStoragePath()).normalize();
                // 限制只能删附件目录内，防止路径穿越
                Path base = Path.of(attachmentDir).normalize();
                if (file.startsWith(base)) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException ignored) {
                // 文件删失败不阻断数据库删除
            }
        }
        attachmentMapper.deleteById(id);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminPrincipal p) {
            return p.id();
        }
        return null;
    }

    private static String ext(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1) : "";
    }
}
