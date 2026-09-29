package com.mashangan.blog.migration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Halo 数据迁移入口：--spring.profiles.active=migrate-halo
 * 读取 Halo 公开只读 API（无需 PAT），按 halo_name 幂等 upsert 进关系表，执行完毕自动退出。
 */
@Slf4j
@Component
@Profile("migrate-halo")
@RequiredArgsConstructor
public class HaloDataMigrator implements ApplicationRunner {

    private final MigrationProperties properties;
    private final MigrationWriter writer;

    @Override
    public void run(ApplicationArguments args) {
        long started = System.currentTimeMillis();
        try {
            execute();
            log.info("迁移成功完成，耗时 {} 秒，进程退出", (System.currentTimeMillis() - started) / 1000);
            System.exit(0);
        } catch (Exception e) {
            log.error("迁移失败（已写入的内容按实体提交，重跑即可续上）", e);
            System.exit(1);
        }
    }

    private void execute() throws Exception {
        if (properties.getHaloBaseUrl() == null || properties.getHaloBaseUrl().isBlank()) {
            throw new IllegalStateException("缺少 app.migration.halo-base-url 配置");
        }
        HaloClient client = new HaloClient(properties.getHaloBaseUrl(), properties.getPageSize());

        // 1. 分类（含父子挂接）
        JsonNode categoryEnvelope = client.categories();
        var catStats = writer.writeCategories(elements(categoryEnvelope.path("items")));
        Map<String, Long> categoryIds = writer.categoryIdMap();
        log.info("分类：{} 个，父子挂接 {} 条", catStats.upserted(), catStats.linkedParents());

        // 2. 文章（列表仅摘要，逐篇取详情正文）
        List<JsonNode> posts = client.allPosts();
        int newTags = 0;
        int done = 0;
        for (JsonNode listed : posts) {
            String name = HaloClient.text(listed, "metadata", "name");
            JsonNode detail = client.postDetail(name);
            newTags += writer.writePost(detail, categoryIds);
            done++;
            if (done % 25 == 0 || done == posts.size()) {
                log.info("文章进度 {}/{}，累计新标签 {}", done, posts.size(), newTags);
            }
        }

        // 3. 独立页面
        List<JsonNode> pages = client.allSinglePages();
        for (JsonNode listed : pages) {
            String name = HaloClient.text(listed, "metadata", "name");
            writer.writeSinglePage(client.singlePageDetail(name));
        }
        log.info("独立页面：{} 个", pages.size());

        // 4. 主菜单（引用需要文章/分类/页面的本地 id）
        JsonNode menu = client.primaryMenu();
        Map<String, Long> postIds = writer.postIdMap();
        Map<String, Long> pageIds = writer.singlePageIdMap();
        var menuStats = writer.writeMenu(menu, categoryIds, postIds, pageIds);
        log.info("菜单：菜单项 {} 个，子项挂接 {} 条，targetRef 关联 {} 条",
                menuStats.itemsUpserted(), menuStats.linkedChildren(), menuStats.linkedRefs());

        // 5. 附件目录扫描（可选）
        if (properties.getAttachmentDir() != null && !properties.getAttachmentDir().isBlank()) {
            Path dir = Path.of(properties.getAttachmentDir());
            if (Files.isDirectory(dir)) {
                var attStats = writer.writeAttachments(dir);
                log.info("附件：扫描 {}，新增 {}，已存在跳过 {}",
                        attStats.scanned(), attStats.inserted(), attStats.skipped());
            } else {
                log.warn("附件目录不存在，跳过：{}", dir);
            }
        }
    }

    private static List<JsonNode> elements(JsonNode array) {
        var list = new java.util.ArrayList<JsonNode>();
        array.forEach(list::add);
        return list;
    }
}
