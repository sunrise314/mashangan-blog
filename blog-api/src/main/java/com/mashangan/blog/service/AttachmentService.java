package com.mashangan.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.Attachment;
import com.mashangan.blog.domain.entity.Category;
import com.mashangan.blog.domain.entity.Post;
import com.mashangan.blog.domain.entity.PostRevision;
import com.mashangan.blog.domain.entity.Series;
import com.mashangan.blog.domain.entity.SinglePage;
import com.mashangan.blog.domain.entity.SiteConfig;
import com.mashangan.blog.mapper.AttachmentMapper;
import com.mashangan.blog.mapper.CategoryMapper;
import com.mashangan.blog.mapper.PostMapper;
import com.mashangan.blog.mapper.PostRevisionMapper;
import com.mashangan.blog.mapper.SeriesMapper;
import com.mashangan.blog.mapper.SinglePageMapper;
import com.mashangan.blog.mapper.SiteConfigMapper;
import com.mashangan.blog.security.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final Set<String> ALLOWED_EXT = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "svg",
            "pdf", "doc", "docx", "xls", "xlsx", "zip");

    private final AttachmentMapper attachmentMapper;
    private final PostMapper postMapper;
    private final PostRevisionMapper postRevisionMapper;
    private final SinglePageMapper singlePageMapper;
    private final CategoryMapper categoryMapper;
    private final SeriesMapper seriesMapper;
    private final SiteConfigMapper siteConfigMapper;

    @Value("${app.attachment.dir}")
    private String attachmentDir;

    @Value("${app.attachment.max-size-bytes}")
    private long maxSizeBytes;

    /** 水印开关，回退方式：服务器 compose 设 APP_WATERMARK_ENABLED=false 重建容器 */
    @Value("${app.watermark.enabled:true}")
    private boolean watermarkEnabled;

    private volatile BufferedImage watermarkTileCache;

    /**
     * 全图平铺斜纹水印：模板是打包在 classpath 的透明 PNG（构建期已渲染好文字，
     * 运行时零字体依赖，headless 容器安全），仅处理 jpg/png，gif/webp/svg 跳过。
     * 任何异常一律回退原图字节，绝不阻断上传。
     */
    private byte[] applyWatermark(byte[] bytes, String ext) {
        if (!watermarkEnabled) return bytes;
        String e = ext == null ? "" : ext.toLowerCase();
        boolean png = "png".equals(e);
        if (!png && !"jpg".equals(e) && !"jpeg".equals(e)) return bytes;
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
            if (src == null) return bytes;
            int w = src.getWidth(), h = src.getHeight();
            if (w < 200 || h < 120) return bytes;
            BufferedImage tile = watermarkTile();
            if (tile == null) return bytes;

            BufferedImage out = new BufferedImage(w, h,
                    png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(src, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            float scale = Math.min(1f, w / 480f);
            int tw = Math.max(60, Math.round(tile.getWidth() * scale));
            int th = Math.max(36, Math.round(tile.getHeight() * scale));
            int row = 0;
            for (int y = -th; y < h + th; y += Math.max(th + 24, 150), row++) {
                int shift = (row % 2 == 0) ? 0 : tw / 2;
                for (int x = -tw + shift; x < w + tw; x += tw + 80) {
                    g.drawImage(tile, x, y, tw, th, null);
                }
            }
            g.dispose();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (png) {
                ImageIO.write(out, "png", bos);
            } else {
                ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.92f);
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(out, null, null), param);
                } finally {
                    writer.dispose();
                }
            }
            byte[] result = bos.toByteArray();
            return result.length > 0 ? result : bytes;
        } catch (Exception ex) {
            log.warn("水印绘制失败，使用原图: {}", ex.getMessage());
            return bytes;
        }
    }

    private BufferedImage watermarkTile() throws IOException {
        BufferedImage t = watermarkTileCache;
        if (t != null) return t;
        var res = getClass().getResourceAsStream("/watermark/watermark-tile.png");
        if (res == null) return null;
        try (BufferedInputStream in = new BufferedInputStream(res)) {
            t = ImageIO.read(in);
        }
        watermarkTileCache = t;
        return t;
    }

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
        byte[] stored = applyWatermark(file.getBytes(), ext);
        Files.write(target, stored);

        Long uploaderId = currentUserId();
        Attachment a = new Attachment();
        a.setStoragePath("upload/" + rel);
        a.setUrlPath("/upload/" + rel);
        a.setOriginalName(original);
        a.setContentType(file.getContentType());
        a.setSize((long) stored.length);
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
        byte[] wmBytes = applyWatermark(bytes, ext);
        Files.write(target, wmBytes);

        Long uploaderId = currentUserId();
        Attachment a = new Attachment();
        a.setStoragePath("upload/" + rel);
        a.setUrlPath("/upload/" + rel);
        a.setOriginalName(filename);
        a.setContentType(contentType);
        a.setSize((long) wmBytes.length);
        a.setUploaderId(uploaderId);
        a.setCreatedAt(OffsetDateTime.now());
        attachmentMapper.insert(a);
        return a;
    }

    /** 列出全部附件，按上传时间倒序。 */
    public List<Attachment> list() {
        return attachmentMapper.selectList(new QueryWrapper<Attachment>().orderByDesc("created_at"));
    }

    /**
     * 删除附件：先做引用检查（被引用即 409 拒绝，fail-closed），再删物理文件（尽力而为），
     * 最后删数据库记录。
     */
    public void delete(Long id) {
        Attachment a = attachmentMapper.selectById(id);
        if (a == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
        assertNotReferenced(a);
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

    /**
     * 删除前引用检查：附件 URL 以文本形式散落在文章正文/封面、单页面、分类、系列、站点配置里，
     * 无外键可约束，删除前逐表扫描，命中即 409 拒绝并列出引用方。
     * 匹配键取 url_path 去掉开头 "/" 后的路径（如 upload/2026/09/x.png），
     * 相对路径与历史遗留的绝对域名（api/cdn/IP:8090）形态均可命中。
     */
    private void assertNotReferenced(Attachment a) {
        String urlPath = a.getUrlPath();
        if (urlPath == null || urlPath.isBlank()) return;
        String suffix = urlPath.startsWith("/") ? urlPath.substring(1) : urlPath;
        String like = likeEscape(suffix);
        List<String> refs = new ArrayList<>();

        List<Post> posts = postMapper.selectList(new QueryWrapper<Post>()
                .select("id", "title")
                .and(w -> w.like("content_html", like)
                        .or().like("content_raw", like)
                        .or().like("cover", like)));
        for (Post p : posts) refs.add("文章《" + p.getTitle() + "》");

        List<SinglePage> pages = singlePageMapper.selectList(new QueryWrapper<SinglePage>()
                .select("id", "title")
                .and(w -> w.like("content_html", like).or().like("content_raw", like)));
        for (SinglePage page : pages) refs.add("页面《" + page.getTitle() + "》");

        List<Category> cats = categoryMapper.selectList(new QueryWrapper<Category>()
                .select("id", "display_name").like("cover", like));
        for (Category c : cats) refs.add("分类「" + c.getDisplayName() + "」");

        List<Series> seriesList = seriesMapper.selectList(new QueryWrapper<Series>()
                .select("id", "title").like("cover", like));
        for (Series s : seriesList) refs.add("系列「" + s.getTitle() + "」");

        SiteConfig sc = siteConfigMapper.selectOne(new QueryWrapper<SiteConfig>()
                .select("id", "logo_url", "favicon_url", "planet_qrcode_url", "planet_intro_html"));
        if (sc != null) {
            if (contains(sc.getLogoUrl(), suffix)) refs.add("站点配置 logo_url");
            if (contains(sc.getFaviconUrl(), suffix)) refs.add("站点配置 favicon_url");
            if (contains(sc.getPlanetQrcodeUrl(), suffix)) refs.add("站点配置 planet_qrcode_url");
            if (contains(sc.getPlanetIntroHtml(), suffix)) refs.add("站点配置 planet_intro_html");
        }

        if (refs.isEmpty()) return;
        Long revCount = postRevisionMapper.selectCount(new QueryWrapper<PostRevision>()
                .and(w -> w.like("content_html", like)
                        .or().like("content_raw", like)
                        .or().like("cover", like)));

        StringBuilder msg = new StringBuilder("该附件仍被 " + refs.size() + " 处引用，无法删除：");
        msg.append(String.join("、", refs.subList(0, Math.min(5, refs.size()))));
        if (refs.size() > 5) msg.append(" 等");
        if (revCount != null && revCount > 0) {
            msg.append("；另有 ").append(revCount).append(" 条文章修订记录含此图");
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, msg.toString());
    }

    private static boolean contains(String s, String suffix) {
        return s != null && s.contains(suffix);
    }

    /** LIKE 通配符转义（PostgreSQL LIKE 默认转义符为反斜杠） */
    private static String likeEscape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
