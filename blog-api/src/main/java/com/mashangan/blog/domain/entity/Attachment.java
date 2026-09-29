package com.mashangan.blog.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("attachments")
public class Attachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String haloName;

    /** 相对附件根目录的存储路径，如 upload/2026/01/a.png */
    private String storagePath;

    /** 公开访问路径，如 /upload/2026/01/a.png */
    private String urlPath;

    private String originalName;

    private String contentType;

    private Long size;

    private Long uploaderId;

    private OffsetDateTime createdAt;
}
