package com.mashangan.blog.studio;

import java.util.ArrayList;
import java.util.List;

/** 内存态任务状态，前端轮询用。 */
public class StudioTask {
    public String id;
    public String status = "running"; // running / done / error
    public String step;
    public int percent;
    public String message;
    public String title;
    public String slug;
    public String categorySlug;
    public List<ImagePoint> points = new ArrayList<>();
    public String coverUrl;
    public Long postId;
    public String permalink;
    public List<String> warnings = new ArrayList<>();
    public String error;
    public long createdAt;
}
