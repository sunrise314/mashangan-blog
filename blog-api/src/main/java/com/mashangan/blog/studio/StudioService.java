package com.mashangan.blog.studio;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangan.blog.domain.entity.Category;
import com.mashangan.blog.mapper.CategoryMapper;
import com.mashangan.blog.service.AdminPostService;
import com.mashangan.blog.service.AttachmentService;
import com.mashangan.blog.web.admin.dto.PostRequest;
import com.mashangan.blog.web.admin.dto.PostResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /studio 一键配图流水线：分析 → 配图 → 转存 → 建文 → 发布。
 * 单实例内存任务表，前端轮询状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudioService {

    private final AiImageClient ai;
    private final AttachmentService attachments;
    private final AdminPostService adminPosts;
    private final CategoryMapper categoryMapper;

    private final Map<String, StudioTask> tasks = new LinkedHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "studio-pipeline");
        t.setDaemon(true);
        return t;
    });

    public StudioTask getTask(String id) {
        return tasks.get(id);
    }

    public StudioTask startImport(StudioImportOptions opt) {
        StudioTask t = new StudioTask();
        t.id = "t" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4);
        t.step = "queued";
        t.message = "排队中";
        t.createdAt = System.currentTimeMillis();
        synchronized (tasks) {
            tasks.put(t.id, t);
            // 淘汰最旧任务，最多留 20 个
            if (tasks.size() > 20) {
                String oldest = tasks.entrySet().stream()
                        .min(Comparator.comparingLong(e -> e.getValue().createdAt))
                        .map(Map.Entry::getKey).orElse(null);
                tasks.remove(oldest);
            }
        }
        executor.submit(() -> runPipeline(t, opt));
        return t;
    }

    // ---------- 流水线 ----------

    private void runPipeline(StudioTask task, StudioImportOptions opt) {
        try {
            String md = opt.markdown() == null ? "" : opt.markdown().replace("\r\n", "\n");
            FrontMatter fm = stripFrontMatter(md);
            String markdown = fm.body().trim();
            List<Block> blocks = splitBlocks(markdown);
            if (blocks.isEmpty()) throw new IllegalStateException("文章内容为空");

            String title = firstNonBlank(opt.title(), fm.title(), extractTitle(markdown));
            title = title.trim();
            String slug = sanitizeSlug(firstNonBlank(opt.slug(), fm.slug(), fallbackSlug(title)));
            if (slug.length() > 80) slug = slug.substring(0, 80);
            String categorySlug = opt.categorySlug() == null || opt.categorySlug().isBlank()
                    ? "default" : opt.categorySlug();
            int imageCount = Math.min(10, Math.max(1, opt.imageCount() == null ? 3 : opt.imageCount()));
            String style = opt.style() == null || opt.style().isBlank() ? "简约通用" : opt.style();

            task.title = title;
            task.slug = slug;
            task.categorySlug = categorySlug;

            // 1. AI 分析点位
            progress(task, "analyze", 10, "AI 正在分析文章配图点位…");
            StringBuilder allNumbered = new StringBuilder();
            for (Block b : blocks) {
                if (allNumbered.length() > 0) allNumbered.append("\n\n");
                allNumbered.append("[").append(b.no).append("] ")
                        .append(b.text.length() > 1500 ? b.text.substring(0, 1500) : b.text);
            }
            String numbered = allNumbered.toString();
            if (numbered.length() > 24000) {
                double avg = (double) numbered.length() / blocks.size();
                int keep = Math.max(4, (int) (24000 / avg));
                double step = (double) blocks.size() / keep;
                StringBuilder picked = new StringBuilder();
                for (int i = 0; i < keep; i++) {
                    Block b = blocks.get(Math.min(blocks.size() - 1, (int) Math.floor(i * step)));
                    if (!picked.isEmpty()) picked.append("\n\n");
                    picked.append("[").append(b.no).append("] ")
                            .append(b.text.length() > 1500 ? b.text.substring(0, 1500) : b.text);
                }
                picked.append("\n\n（注：全文共 ").append(blocks.size())
                        .append(" 个文本块，以上为均匀采样结果，blockNo 仍按原文块号。）");
                numbered = picked.toString();
            }
            task.points = ai.analyzeBlocks(numbered, imageCount, style, blocks.size());

            // 2. 逐点配图
            progress(task, "illustrate", 25, "正在为各点位获取配图…");
            List<ImagePoint> points = task.points;
            for (int i = 0; i < points.size(); i++) {
                ImagePoint p = points.get(i);
                try {
                    if ("photo".equals(p.kind)) {
                        p.imageUrl = ai.searchPexels(p.keywords == null || p.keywords.isBlank() ? p.scene : p.keywords);
                        if (p.imageUrl == null) {
                            p.imageUrl = ai.generateAiImage(p.prompt);
                            p.source = "ai";
                        } else p.source = "pexels";
                    } else {
                        try {
                            p.imageUrl = ai.generateAiImage(p.prompt);
                            p.source = "ai";
                        } catch (Exception e) {
                            p.imageUrl = ai.searchPexels(p.keywords == null ? p.scene : p.keywords);
                            p.source = "pexels";
                        }
                    }
                    if (p.imageUrl == null) throw new IllegalStateException("图库与 AI 均未返回结果");
                    p.status = "done";
                } catch (Exception e) {
                    p.status = "failed";
                    p.error = e.getMessage();
                }
                progress(task, "illustrate", 25 + (i + 1) * 25 / points.size(),
                        "配图进度 " + (i + 1) + "/" + points.size());
            }

            List<ImagePoint> okPoints = points.stream().filter(p -> "done".equals(p.status) && p.imageUrl != null).toList();
            if (okPoints.isEmpty()) throw new IllegalStateException("所有点位配图失败");
            boolean hasCover = okPoints.stream().anyMatch(p -> "cover".equals(p.type));
            if (!hasCover && okPoints.size() > 1) {
                task.warnings.add("封面配图失败，已复用首个正文配图作为封面");
            }

            // 3. 转存附件
            progress(task, "transfer", 55, "正在转存图片到站点附件库…");
            for (int i = 0; i < okPoints.size(); i++) {
                ImagePoint p = okPoints.get(i);
                try {
                    byte[] bytes = ai.downloadImage(p.imageUrl);
                    String filename = "studio-" + task.id + "-" + p.id + ".jpg";
                    var att = attachments.uploadBytes(bytes, filename, "image/jpeg");
                    p.imageUrl = att.getUrlPath();
                } catch (Exception e) {
                    p.status = "failed";
                    p.error = e.getMessage();
                }
                progress(task, "transfer", 55 + (i + 1) * 15 / okPoints.size(),
                        "转存进度 " + (i + 1) + "/" + okPoints.size());
            }
            List<ImagePoint> transferred = points.stream().filter(p -> "done".equals(p.status) && p.imageUrl != null).toList();
            if (transferred.isEmpty()) throw new IllegalStateException("图片转存全部失败");
            long failed = points.stream().filter(p -> "failed".equals(p.status)).count();
            if (failed > 0) task.warnings.add(failed + " 个点位配图失败已跳过");

            // 4. 组装 markdown
            ImagePoint cover = transferred.stream().filter(p -> "cover".equals(p.type)).findFirst()
                    .orElse(transferred.get(0));
            task.coverUrl = cover.imageUrl;
            String finalMd = composeMarkdown(markdown, blocks, transferred);

            List<String> catHaloNames = resolveCategory(categorySlug);

            // 5. 创建或更新文章
            progress(task, "publish", 72, "正在创建文章…");
            PostRequest req = new PostRequest(title, slug, task.coverUrl, finalMd,
                    "", catHaloNames, List.of(), true, false, 0, "PUBLIC", true, null, null);
            PostResponse existing = findBySlug(slug);
            PostResponse saved;
            if (existing != null) {
                saved = adminPosts.update(existing.id(), req);
                task.warnings.add("已存在相同 slug 的文章，内容已被覆盖：" + slug);
            } else {
                saved = adminPosts.create(req);
            }
            task.postId = saved.id();
            task.permalink = "/archives/" + slug;

            task.status = "done";
            task.percent = 100;
            task.step = "done";
            task.message = "发布成功";
        } catch (Exception e) {
            log.error("studio pipeline failed", e);
            task.status = "error";
            task.step = "error";
            task.message = e.getMessage();
            task.error = e.getMessage();
        }
    }

    private PostResponse findBySlug(String slug) {
        try {
            var page = adminPosts.page(1, 100, slug, false, null);
            return page.getRecords().stream().filter(p -> slug.equals(p.slug())).findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> resolveCategory(String categorySlug) {
        if (categorySlug == null || categorySlug.isBlank() || "default".equals(categorySlug)) return List.of();
        Category c = categoryMapper.selectOne(new QueryWrapper<Category>().eq("slug", categorySlug));
        return c == null ? List.of() : List.of(c.getHaloName());
    }

    private void progress(StudioTask t, String step, int percent, String message) {
        t.step = step;
        t.percent = percent;
        t.message = message;
    }

    // ---------- Markdown 分块 / 组装 ----------

    record Block(int no, String text, boolean illustratable) {}

    static List<Block> splitBlocks(String markdown) {
        String[] lines = markdown.split("\n", -1);
        List<Block> blocks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        boolean inCode = false;
        for (String line : lines) {
            if (line.stripLeading().startsWith("```")) {
                if (!inCode) {
                    flush(blocks, current, true);
                    current = new ArrayList<>(List.of(line));
                    inCode = true;
                } else {
                    current.add(line);
                    flush(blocks, current, false);
                    current = new ArrayList<>();
                }
                continue;
            }
            if (inCode) { current.add(line); continue; }
            if (line.isBlank()) flush(blocks, current, true);
            else current.add(line);
        }
        flush(blocks, current, !inCode);
        return blocks;
    }

    private static void flush(List<Block> blocks, List<String> current, boolean illustratable) {
        String text = String.join("\n", current).trim();
        if (!text.isEmpty()) blocks.add(new Block(blocks.size() + 1, text, illustratable));
        current.clear();
    }

    static String composeMarkdown(String markdown, List<Block> blocks, List<ImagePoint> points) {
        Map<Integer, List<ImagePoint>> byBlock = new LinkedHashMap<>();
        for (ImagePoint p : points) {
            if (!"content".equals(p.type) || p.imageUrl == null) continue;
            byBlock.computeIfAbsent(p.blockNo, k -> new ArrayList<>()).add(p);
        }
        String[] lines = markdown.split("\n", -1);
        List<String> out = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int blockIdx = 0;
        boolean inCode = false;
        for (String line : lines) {
            if (line.stripLeading().startsWith("```")) {
                if (!inCode) {
                    emitBlock(out, current, blocks, blockIdx++, byBlock, true);
                    current = new ArrayList<>(List.of(line));
                    inCode = true;
                } else {
                    current.add(line);
                    emitBlock(out, current, blocks, blockIdx++, byBlock, false);
                    current = new ArrayList<>();
                }
                continue;
            }
            if (inCode) { current.add(line); continue; }
            if (line.isBlank()) {
                emitBlock(out, current, blocks, blockIdx++, byBlock, true);
                out.add(line);
            } else current.add(line);
        }
        emitBlock(out, current, blocks, blockIdx++, byBlock, !inCode);
        return String.join("\n", out);
    }

    private static void emitBlock(List<String> out, List<String> current, List<Block> blocks,
                                  int blockIdx, Map<Integer, List<ImagePoint>> byBlock, boolean illustratable) {
        String text = String.join("\n", current).trim();
        if (text.isEmpty()) { out.addAll(current); current.clear(); return; }
        if (blockIdx < blocks.size() && illustratable) {
            Block b = blocks.get(blockIdx);
            List<ImagePoint> pts = byBlock.getOrDefault(b.no, List.of());
            for (ImagePoint p : pts) if ("before".equals(p.position)) out.add(imgLine(p));
            out.addAll(current);
            for (ImagePoint p : pts) if (!"before".equals(p.position)) { out.add(""); out.add(imgLine(p)); }
        } else out.addAll(current);
        current.clear();
    }

    private static String imgLine(ImagePoint p) {
        String alt = p.caption == null ? "" : p.caption.replaceAll("[\\]\\[\n\r]", "");
        return "![" + alt + "](" + p.imageUrl + ")";
    }

    // ---------- 文本工具 ----------

    record FrontMatter(String title, String slug, String body) {}

    private static final Pattern FM = Pattern.compile("^---\\r?\\n([\\s\\S]*?)\\r?\\n---\\r?\\n?");

    static FrontMatter stripFrontMatter(String markdown) {
        Matcher m = FM.matcher(markdown);
        if (!m.find()) return new FrontMatter(null, null, markdown);
        String yaml = m.group(1);
        String title = null, slug = null;
        for (String line : yaml.split("\n")) {
            Matcher kv = Pattern.compile("^(title|slug)\\s*:\\s*(.+)$").matcher(line);
            if (kv.find()) {
                String v = kv.group(2).trim();
                if (v.startsWith("\"") || v.startsWith("'")) v = v.substring(1);
                if (v.endsWith("\"") || v.endsWith("'")) v = v.substring(0, v.length() - 1);
                if ("title".equals(kv.group(1))) title = v; else slug = v;
            }
        }
        return new FrontMatter(title, slug, markdown.substring(m.end()));
    }

    private static String extractTitle(String md) {
        Matcher m = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE).matcher(md);
        String t = m.find() ? m.group(1) : "未命名文章";
        return t.length() > 120 ? t.substring(0, 120) : t;
    }

    private static String fallbackSlug(String title) {
        String s = sanitizeSlug(title);
        return s.isEmpty() ? "post-" + System.currentTimeMillis() : s;
    }

    static String sanitizeSlug(String raw) {
        if (raw == null) return "";
        // 字符类内的 [ 和 ] 必须转义，否则 Java 会把 [ 当嵌套类开启、] 提前闭合 → PatternSyntaxException
        return raw.toLowerCase()
                .replaceAll("[\\\\/:*?\"<>|'#%&{}$!@+=\\[\\];,.^~`\\s]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return "";
    }
}
