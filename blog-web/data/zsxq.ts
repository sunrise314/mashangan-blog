// 数据已从原独立页内容反解（源：_md2html/_page_zsxq.html），结构对齐原专用页
export interface ZsxqStat {
  number: string;
  label: string;
}

export interface ZsxqProject {
  name: string;
  desc: string;
  status: "complete" | "updating";
  tags: string[];
}

export interface ZsxqBenefit {
  title: string;
  desc: string;
}

export const zsxqConfig = {
  title: "码上岸 · 数字孪生全栈实战",
  subtitle:
    "从 IoT 设备接入到 Cesium 三维可视化，19 章渐进式讲解 Spring Boot 21 + Vue 3 企业级项目。源码专属通道 + 每周源码拆解 + 持续答疑。",
  stats: [
    { number: "19", label: "连载章节已完结" },
    { number: "4层", label: "架构全链路覆盖" },
    { number: "∞", label: "源码+答疑" },
  ] as ZsxqStat[],
  joinUrl: "https://t.zsxq.com/a0qod",
  joinButtonText: "加入「码上岸」知识星球",
  projects: [
    {
      name: "数字孪生全栈实战（19章连载）",
      desc: "从 ThingsBoard IoT 平台搭建、边缘 Modbus/MQTT 连接器、巡检任务状态机、告警规则引擎、WebSocket 实时推送，到 Cesium 三维可视化大屏，完整闭环的企业级数字孪生巡检系统。",
      status: "complete",
      tags: ["Spring Boot 21", "Vue 3", "Cesium", "ThingsBoard"],
    },
  ] as ZsxqProject[],
  benefits: [
    {
      title: "完整源码购买通道",
      desc: "星球成员专属价，含 1 对 1 部署指导，源码结构清晰可二次开发（代码不对外公开）。",
    },
    {
      title: "19 章思维导图 PDF",
      desc: "星球专属福利，把四层架构的 19 章核心逻辑串成一张脑图，面试复习/系统设计一站式搞定。",
    },
    {
      title: "每周三源码拆解",
      desc: "每周拆一个章节的核心代码片段（如状态机 guard、WebSocket 协议补全、审计 AOP），配踩坑笔记。",
    },
    {
      title: "每周六连载答疑",
      desc: "语音直播答疑，学习卡壳直接提问，共性问题整理成文字稿沉淀到星球。",
    },
    {
      title: "博客读者专属价",
      desc: "前 50 名博客读者加入，送 7 天免费体验 + 源码购买立减 100 元。",
    },
    {
      title: "面试考点全景图",
      desc: "星球内分享第 19 章 30 个高频追问 Q&A + 五层架构考点地图，面试数字孪生岗位直接用。",
    },
  ] as ZsxqBenefit[],
};
