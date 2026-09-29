package com.mashangan.blog.studio;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** /studio 一键 AI 配图导入流水线。 */
@RestController
@RequestMapping("/api/admin/studio")
@RequiredArgsConstructor
public class StudioController {

    private final StudioService studio;

    /** 提交 markdown，创建配图任务，返回 taskId 供轮询。 */
    @PostMapping("/import")
    public StudioTask importMd(@RequestBody StudioImportOptions body) {
        String md = body.markdown() == null ? "" : body.markdown().trim();
        if (md.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入文章内容");
        if (md.length() > 600_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "文章过长");
        return studio.startImport(body);
    }

    /** 轮询任务进度。 */
    @GetMapping("/status/{id}")
    public StudioTask status(@PathVariable String id) {
        StudioTask t = studio.getTask(id);
        if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        return t;
    }
}
