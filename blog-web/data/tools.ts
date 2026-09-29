// 数据已从原独立页内容反解（源：_md2html/_page_tools.html）
export interface ToolItem {
  name: string;
  url: string;
  tag: string;
  desc: string;
}

export interface ToolGroup {
  id: string;
  name: string;
  items: ToolItem[];
}

export const toolGroups: ToolGroup[] = [
  {
    id: 'format',
    name: '代码格式化',
    items: [
      { name: 'JSON 格式化', url: 'https://json.cn/', tag: '推荐', desc: '' },
      { name: 'SQL 格式化', url: 'https://tool.lu/sql/', tag: '', desc: '' },
      { name: 'HTML 格式化', url: 'https://www.bejson.com/', tag: '', desc: '' },
      { name: 'XML 格式化', url: 'https://codebeautify.org/xmlviewer', tag: '', desc: '' },
      { name: 'JS 格式化', url: 'https://prettier.io/playground/', tag: '', desc: '' },
      { name: 'CSS 格式化', url: 'https://www.cleancss.com/css-beautify/', tag: '', desc: '' },
    ],
  },
  {
    id: 'text',
    name: '文本处理',
    items: [
      { name: '文本比对', url: 'https://text-compare.com/', tag: '推荐', desc: '' },
      { name: '英文大小写转换', url: 'https://tool.lu/txt/', tag: '', desc: '' },
      { name: '繁简体转换', url: 'https://www.qqxiuzi.cn/zh/fanjian/', tag: '', desc: '' },
      { name: '字数统计', url: 'https://www.ziticq.com/zishu/', tag: '', desc: '' },
    ],
  },
  {
    id: 'image',
    name: '图片处理',
    items: [
      { name: '图片压缩', url: 'https://tinypng.com/', tag: '推荐', desc: '' },
      { name: 'SVG 压缩', url: 'https://jakearchibald.github.io/svgomg/', tag: '', desc: '' },
      { name: '图片转 Base64', url: 'https://www.base64-image.de/', tag: '', desc: '' },
      { name: '图片格式转换', url: 'https://cloudconvert.com/', tag: '', desc: '' },
    ],
  },
  {
    id: 'crypto',
    name: '加密解密',
    items: [
      { name: 'MD5 / SHA 加密', url: 'https://1024tools.com/md5', tag: '', desc: '' },
      { name: 'URL 编解码', url: 'https://1024tools.com/urlencode', tag: '', desc: '' },
      { name: 'Base64 编解码', url: 'https://www.base64encode.org/', tag: '', desc: '' },
      { name: 'JWT 解析', url: 'https://jwt.io/', tag: '推荐', desc: '' },
    ],
  },
  {
    id: 'idgen',
    name: 'ID 生成',
    items: [
      { name: 'UUID 生成', url: 'https://www.uuidgenerator.net/', tag: '', desc: '' },
      { name: '随机密码生成', url: 'https://1password.com/password-generator/', tag: '', desc: '' },
      { name: '雪花 ID 生成', url: 'https://1024tools.com/snowflake', tag: '', desc: '' },
    ],
  },
  {
    id: 'convert',
    name: '进制转换',
    items: [
      { name: '进制转换', url: 'https://tool.lu/hexconvert/', tag: '推荐', desc: '' },
      { name: '时间戳转换', url: 'https://tool.lu/timestamp/', tag: '', desc: '' },
      { name: '单位换算', url: 'https://www.unitconverters.net/', tag: '', desc: '' },
    ],
  },
  {
    id: 'dev',
    name: '开发辅助',
    items: [
      { name: '正则表达式测试', url: 'https://regex101.com/', tag: '推荐', desc: '' },
      { name: 'Cron 表达式', url: 'https://crontab.guru/', tag: '', desc: '' },
      { name: '二维码生成', url: 'https://cli.im/', tag: '', desc: '' },
      { name: 'HTTP 状态码查询', url: 'https://httpstatuses.com/', tag: '', desc: '' },
    ],
  },
];
