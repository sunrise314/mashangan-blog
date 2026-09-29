package com.mashangan.blog.migration;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.mashangan.blog.domain.entity.Attachment;
import com.mashangan.blog.domain.entity.Category;
import com.mashangan.blog.domain.entity.Menu;
import com.mashangan.blog.domain.entity.MenuItem;
import com.mashangan.blog.domain.entity.Post;
import com.mashangan.blog.domain.entity.PostCategoryRel;
import com.mashangan.blog.domain.entity.PostTagRel;
import com.mashangan.blog.domain.entity.SinglePage;
import com.mashangan.blog.domain.entity.Tag;
import com.mashangan.blog.mapper.AttachmentMapper;
import com.mashangan.blog.mapper.CategoryMapper;
import com.mashangan.blog.mapper.MenuItemMapper;
import com.mashangan.blog.mapper.MenuMapper;
import com.mashangan.blog.mapper.PostCategoryRelMapper;
import com.mashangan.blog.mapper.PostMapper;
import com.mashangan.blog.mapper.PostTagRelMapper;
import com.mashangan.blog.mapper.SinglePageMapper;
import com.mashangan.blog.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.mashangan.blog.migration.HaloClient.bool;
import static com.mashangan.blog.migration.HaloClient.integer;
import static com.mashangan.blog.migration.HaloClient.text;
import static com.mashangan.blog.migration.HaloClient.time;

/** 全部 DB 写入集中在此，每个方法独立事务；HTTP 拉取由调用方在事务外完成 */
@Service
@RequiredArgsConstructor
public class MigrationWriter {

    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final PostMapper postMapper;
    private final PostCategoryRelMapper postCategoryRelMapper;
    private final PostTagRelMapper postTagRelMapper;
    private final SinglePageMapper singlePageMapper;
    private final MenuMapper menuMapper;
    private final MenuItemMapper menuItemMapper;
    private final AttachmentMapper attachmentMapper;

    public record CategoryStats(int upserted, int linkedParents) {
    }

    @Transactional
    public CategoryStats writeCategories(List<JsonNode> categories) {
        // 第一遍：upsert 本体
        for (JsonNode c : categories) {
            String haloName = text(c, "metadata", "name");
            Category entity = categoryMapper.selectOne(
                    new QueryWrapper<Category>().eq("halo_name", haloName));
            boolean insert = entity == null;
            if (insert) {
                entity = new Category();
                entity.setHaloName(haloName);
                entity.setCreatedAt(time(text(c, "metadata", "creationTimestamp")));
            }
            entity.setSlug(text(c, "spec", "slug"));
            entity.setDisplayName(text(c, "spec", "displayName"));
            entity.setCover(orEmpty(text(c, "spec", "cover")));
            entity.setDescription(orEmpty(text(c, "spec", "description")));
            entity.setPriority(integer(c, 0, "spec", "priority"));
            entity.setHideFromList(bool(c, false, "spec", "hideFromList"));
            entity.setTemplate(blankToNull(text(c, "spec", "template")));
            entity.setPreventParentCascadeQuery(
                    bool(c, false, "spec", "preventParentPostCascadeQuery"));
            entity.setSection(text(c, "metadata", "labels", "haloweb.section"));
            if (insert) {
                categoryMapper.insert(entity);
            } else {
                categoryMapper.updateById(entity);
            }
        }

        // 第二遍：按 spec.children 挂父分类
        Map<String, Long> idByName = categoryIdMap();
        int linked = 0;
        for (JsonNode c : categories) {
            JsonNode children = c.path("spec").path("children");
            if (!children.isArray() || children.isEmpty()) {
                continue;
            }
            Long parentId = idByName.get(text(c, "metadata", "name"));
            if (parentId == null) {
                continue;
            }
            for (JsonNode child : children) {
                Long childId = idByName.get(child.asText());
                if (childId != null) {
                    Category upd = new Category();
                    upd.setId(childId);
                    upd.setParentId(parentId);
                    categoryMapper.updateById(upd);
                    linked++;
                }
            }
        }
        return new CategoryStats(categories.size(), linked);
    }

    @Transactional
    public Map<String, Long> categoryIdMap() {
        Map<String, Long> map = new HashMap<>();
        categoryMapper.selectList(null).forEach(c -> map.put(c.getHaloName(), c.getId()));
        return map;
    }

    public record PostStats(int postsUpserted, int tagsUpserted) {
    }

    /** 写一篇文章（含标签补齐与分类/标签关联重建），返回标签新增数 */
    @Transactional
    public int writePost(JsonNode detail, Map<String, Long> categoryIds) {
        String haloName = text(detail, "metadata", "name");
        Post entity = postMapper.selectOne(
                new QueryWrapper<Post>().eq("halo_name", haloName));
        boolean insert = entity == null;
        if (insert) {
            entity = new Post();
            entity.setHaloName(haloName);
            entity.setCreatedAt(time(text(detail, "metadata", "creationTimestamp")));
        }
        entity.setTitle(text(detail, "spec", "title"));
        entity.setSlug(text(detail, "spec", "slug"));
        // cover 键不存在时保持 NULL（输出层省略），存在则按原值（可能 ""）
        JsonNode specNode = detail.path("spec");
        entity.setCover(specNode.has("cover") && !specNode.get("cover").isNull()
                ? specNode.get("cover").asText() : null);
        // spec.excerpt.raw：键存在即值（含空串），无键为 NULL（Halo 对个别快照省略该键）
        JsonNode excerptNode = specNode.path("excerpt");
        entity.setSpecExcerpt(excerptNode.has("raw") && !excerptNode.get("raw").isNull()
                ? excerptNode.get("raw").asText() : null);
        // excerpt 列存 status.excerpt 最终文本
        entity.setExcerpt(firstNonEmpty(
                text(detail, "status", "excerpt"),
                text(detail, "spec", "excerpt", "raw")));
        entity.setAutoExcerpt(bool(detail, true, "spec", "excerpt", "autoGenerate"));
        entity.setContentHtml(orEmpty(text(detail, "content", "content")));
        String raw = text(detail, "content", "raw");
        entity.setContentRaw(raw != null && !raw.isBlank() ? raw : entity.getContentHtml());
        // 公开接口不返回 rawType：raw 与渲染结果相同即 HTML，否则为 Markdown 源文
        entity.setRawType(entity.getContentRaw().equals(entity.getContentHtml()) ? "HTML" : "MARKDOWN");
        entity.setPublished(bool(detail, true, "spec", "publish"));
        entity.setPinned(bool(detail, false, "spec", "pinned"));
        entity.setPriority(integer(detail, 0, "spec", "priority"));
        entity.setVisible(firstNonEmpty(text(detail, "spec", "visible"), "PUBLIC"));
        entity.setAllowComment(bool(detail, true, "spec", "allowComment"));
        entity.setDeleted(bool(detail, false, "spec", "deleted"));
        OffsetDateTime publishTime = time(text(detail, "spec", "publishTime"));
        if (publishTime == null) {
            publishTime = time(text(detail, "status", "publishTime"));
        }
        if (publishTime == null) {
            publishTime = entity.getCreatedAt();
        }
        entity.setPublishTime(publishTime);
        OffsetDateTime lastModify = time(text(detail, "status", "lastModifyTime"));
        entity.setUpdatedAt(lastModify != null ? lastModify : entity.getCreatedAt());
        if (insert) {
            postMapper.insert(entity);
        } else {
            postMapper.updateById(entity);
        }

        // 关联重建（先删后插，保证可重复执行）
        postCategoryRelMapper.delete(new QueryWrapper<PostCategoryRel>().eq("post_id", entity.getId()));
        List<String> catNames = new ArrayList<>();
        detail.path("spec").path("categories").forEach(n -> catNames.add(n.asText()));
        for (String catName : catNames) {
            Long catId = categoryIds.get(catName);
            if (catId != null) {
                PostCategoryRel rel = new PostCategoryRel();
                rel.setPostId(entity.getId());
                rel.setCategoryId(catId);
                postCategoryRelMapper.insert(rel);
            }
        }

        postTagRelMapper.delete(new QueryWrapper<PostTagRel>().eq("post_id", entity.getId()));
        int newTags = 0;
        JsonNode tags = detail.path("tags");
        if (tags.isArray()) {
            for (JsonNode t : tags) {
                String tagName = text(t, "metadata", "name");
                Tag tag = tagMapper.selectOne(new QueryWrapper<Tag>().eq("halo_name", tagName));
                if (tag == null) {
                    tag = new Tag();
                    tag.setHaloName(tagName);
                    tag.setSlug(text(t, "spec", "slug"));
                    tag.setDisplayName(text(t, "spec", "displayName"));
                    tag.setCreatedAt(time(text(t, "metadata", "creationTimestamp")));
                    tagMapper.insert(tag);
                    newTags++;
                }
                PostTagRel rel = new PostTagRel();
                rel.setPostId(entity.getId());
                rel.setTagId(tag.getId());
                postTagRelMapper.insert(rel);
            }
        }
        return newTags;
    }

    @Transactional
    public void writeSinglePage(JsonNode detail) {
        String haloName = text(detail, "metadata", "name");
        SinglePage entity = singlePageMapper.selectOne(
                new QueryWrapper<SinglePage>().eq("halo_name", haloName));
        boolean insert = entity == null;
        if (insert) {
            entity = new SinglePage();
            entity.setHaloName(haloName);
            entity.setCreatedAt(time(text(detail, "metadata", "creationTimestamp")));
        }
        entity.setTitle(text(detail, "spec", "title"));
        entity.setSlug(text(detail, "spec", "slug"));
        entity.setContentHtml(orEmpty(text(detail, "content", "content")));
        String raw = text(detail, "content", "raw");
        entity.setContentRaw(raw != null && !raw.isBlank() ? raw : entity.getContentHtml());
        entity.setRawType(entity.getContentRaw().equals(entity.getContentHtml()) ? "HTML" : "MARKDOWN");
        entity.setPublished(bool(detail, true, "spec", "publish"));
        OffsetDateTime publishTime = time(text(detail, "spec", "publishTime"));
        if (publishTime == null) {
            publishTime = entity.getCreatedAt();
        }
        entity.setPublishTime(publishTime);
        OffsetDateTime lastModify = time(text(detail, "status", "lastModifyTime"));
        entity.setUpdatedAt(lastModify != null ? lastModify : entity.getCreatedAt());
        if (insert) {
            singlePageMapper.insert(entity);
        } else {
            singlePageMapper.updateById(entity);
        }
    }

    @Transactional
    public Map<String, Long> singlePageIdMap() {
        Map<String, Long> map = new HashMap<>();
        singlePageMapper.selectList(null).forEach(p -> map.put(p.getHaloName(), p.getId()));
        return map;
    }

    @Transactional
    public Map<String, Long> postIdMap() {
        Map<String, Long> map = new HashMap<>();
        postMapper.selectList(null).forEach(p -> map.put(p.getHaloName(), p.getId()));
        return map;
    }

    public record MenuStats(int itemsUpserted, int linkedChildren, int linkedRefs) {
    }

    /** 主菜单整单重建（menus/- 聚合结构，菜单项为树） */
    @Transactional
    public MenuStats writeMenu(JsonNode menuNode, Map<String, Long> categoryIds,
                               Map<String, Long> postIds, Map<String, Long> pageIds) {
        String haloName = text(menuNode, "metadata", "name");
        Menu menu = menuMapper.selectOne(new QueryWrapper<Menu>().eq("halo_name", haloName));
        if (menu == null) {
            menu = new Menu();
            menu.setHaloName(haloName);
            menu.setCreatedAt(time(text(menuNode, "metadata", "creationTimestamp")));
            menu.setIsPrimary(true);
            menu.setDisplayName(text(menuNode, "spec", "displayName"));
            menuMapper.insert(menu);
        } else {
            menu.setDisplayName(text(menuNode, "spec", "displayName"));
            menu.setIsPrimary(true);
            menuMapper.updateById(menu);
        }

        menuItemMapper.delete(new QueryWrapper<MenuItem>().eq("menu_id", menu.getId()));

        Map<String, Long> itemIdByName = new HashMap<>();
        List<String[]> parentLinks = new ArrayList<>();
        int[] refs = {0};

        for (JsonNode item : menuNode.path("menuItems")) {
            upsertMenuItem(item, null, menu.getId(), itemIdByName, parentLinks, refs,
                    categoryIds, postIds, pageIds);
        }
        for (String[] link : parentLinks) {
            Long child = itemIdByName.get(link[0]);
            Long parent = itemIdByName.get(link[1]);
            if (child != null && parent != null) {
                MenuItem upd = new MenuItem();
                upd.setId(child);
                upd.setParentId(parent);
                menuItemMapper.updateById(upd);
            }
        }
        return new MenuStats(itemIdByName.size(), parentLinks.size(), refs[0]);
    }

    private void upsertMenuItem(JsonNode item, String parentName, Long menuId,
                                Map<String, Long> itemIdByName, List<String[]> parentLinks,
                                int[] refs, Map<String, Long> categoryIds,
                                Map<String, Long> postIds, Map<String, Long> pageIds) {
        MenuItem entity = new MenuItem();
        String haloName = text(item, "metadata", "name");
        entity.setHaloName(haloName);
        entity.setMenuId(menuId);
        entity.setDisplayName(firstNonEmpty(text(item, "status", "displayName"),
                text(item, "spec", "displayName"), text(item, "displayName")));
        entity.setHref(firstNonEmpty(text(item, "status", "href"), text(item, "spec", "href")));
        entity.setTarget(blankToNull(text(item, "spec", "target")));
        entity.setPriority(integer(item, 0, "spec", "priority"));
        entity.setCreatedAt(time(text(item, "metadata", "creationTimestamp")));

        JsonNode targetRef = item.path("spec").path("targetRef");
        if (!targetRef.isMissingNode() && !targetRef.isNull()) {
            String kind = text(targetRef, "kind");
            String refName = text(targetRef, "name");
            Long refId = switch (kind == null ? "" : kind) {
                case "Post" -> postIds.get(refName);
                case "Category" -> categoryIds.get(refName);
                case "SinglePage" -> pageIds.get(refName);
                default -> null;
            };
            if (refId != null) {
                entity.setRefKind(switch (kind) {
                    case "Post" -> "post";
                    case "Category" -> "category";
                    case "SinglePage" -> "page";
                    default -> null;
                });
                entity.setRefId(refId);
                refs[0]++;
            }
        }
        menuItemMapper.insert(entity);
        itemIdByName.put(haloName, entity.getId());
        if (parentName != null) {
            parentLinks.add(new String[]{haloName, parentName});
        }

        JsonNode children = item.path("spec").path("children");
        if (!children.isArray()) {
            children = item.path("children");
        }
        if (children.isArray()) {
            for (JsonNode child : children) {
                upsertMenuItem(child, haloName, menuId, itemIdByName, parentLinks, refs,
                        categoryIds, postIds, pageIds);
            }
        }
    }

    public record AttachmentStats(int scanned, int inserted, int skipped) {
    }

    /** 扫描附件根目录下的 upload/，按相对路径登记；已存在（url_path 唯一）则跳过 */
    @Transactional
    public AttachmentStats writeAttachments(Path attachmentRoot) throws IOException {
        Path uploadRoot = attachmentRoot.resolve("upload");
        if (!Files.isDirectory(uploadRoot)) {
            return new AttachmentStats(0, 0, 0);
        }
        int[] counters = {0, 0, 0};
        try (Stream<Path> walk = Files.walk(uploadRoot)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                counters[0]++;
                String rel = uploadRoot.relativize(file).toString().replace('\\', '/');
                String urlPath = "/upload/" + rel;
                Long exists = attachmentMapper.selectCount(
                        new QueryWrapper<Attachment>().eq("url_path", urlPath));
                if (exists != null && exists > 0) {
                    counters[2]++;
                    return;
                }
                Attachment a = new Attachment();
                a.setStoragePath("upload/" + rel);
                a.setUrlPath(urlPath);
                a.setOriginalName(file.getFileName().toString());
                a.setContentType(guessContentType(file.getFileName().toString()));
                try {
                    a.setSize(Files.size(file));
                } catch (IOException e) {
                    a.setSize(0L);
                }
                a.setCreatedAt(OffsetDateTime.now());
                attachmentMapper.insert(a);
                counters[1]++;
            });
        }
        return new AttachmentStats(counters[0], counters[1], counters[2]);
    }

    private static String guessContentType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lower.endsWith(".gif")) {
            return "image/gif";
        } else if (lower.endsWith(".webp")) {
            return "image/webp";
        } else if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (lower.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lower.endsWith(".zip")) {
            return "application/zip";
        }
        return "application/octet-stream";
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
