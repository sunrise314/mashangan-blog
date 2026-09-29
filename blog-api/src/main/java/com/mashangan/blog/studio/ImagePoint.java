package com.mashangan.blog.studio;

/** 单个配图点位（AI 分析结果 + 配图执行结果）。 */
public class ImagePoint {
    public String id;
    /** cover=封面（最多 1 个，不插入正文）；content=正文插图 */
    public String type;
    /** 插图锚定的 md 块序号（cover 无意义） */
    public int blockNo;
    /** 在块后（默认）还是块前插入 */
    public String position;
    public String scene;
    /** 中文图注 */
    public String caption;
    /** photo 优先图库，illustration 优先 AI */
    public String kind;
    public String prompt;
    public String keywords;
    public String status = "pending";
    public String source;
    public String imageUrl;
    public String error;
}
