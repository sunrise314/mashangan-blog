-- ---------------------------------------------------------------------------
-- 行业项目地图：/column 增加行业分类与筹备中项目占位。
-- industry   12 个行业（制造/金融/电商零售/互联网社交/政企/医疗/物流/教育/能源电力/出行交通/游戏/SaaS）
-- project    行业下的项目：series_id 非空 = 已开更（挂真实系列）；NULL = 筹备中（落地页=考点清单）
-- project_exam_point   每个项目的面试考点清单（种子数据，后续用 JD 检索 + 面经替换）
-- ---------------------------------------------------------------------------

CREATE TABLE industry (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug       VARCHAR(64)  UNIQUE NOT NULL,   -- 全 ASCII，用于 /column/industry/{slug}
    name       VARCHAR(64)  NOT NULL,
    intro      VARCHAR(512) NOT NULL DEFAULT '',
    sort       INTEGER      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE project (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    industry_id BIGINT       NOT NULL REFERENCES industry (id) ON DELETE CASCADE,
    series_id   BIGINT       REFERENCES series (id) ON DELETE SET NULL,  -- NULL = 筹备中
    slug        VARCHAR(128) UNIQUE NOT NULL,   -- 全 ASCII
    title       VARCHAR(128) NOT NULL,
    jd_freq     VARCHAR(16)  NOT NULL DEFAULT '高频',
    summary     VARCHAR(512) NOT NULL DEFAULT '',
    sort        INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_project_industry ON project (industry_id, sort);
CREATE INDEX idx_project_series ON project (series_id);

CREATE TABLE project_exam_point (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id BIGINT       NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    sort       INTEGER      NOT NULL DEFAULT 0,
    point      VARCHAR(128) NOT NULL,
    detail     VARCHAR(512) NOT NULL DEFAULT ''
);
CREATE INDEX idx_exam_point_project ON project_exam_point (project_id, sort);

-- ---------------------------------------------------------------------------
-- 12 个行业
-- ---------------------------------------------------------------------------
INSERT INTO industry (slug, name, intro, sort) VALUES
('manufacturing',   '制造',       'MES/ERP/APS/SCADA，工业软件是后端工程师扎堆的主战场。', 1),
('finance',         '金融',       '支付、风控、信贷、清结算，对一致性与低延迟要求最高的行业。', 2),
('ecommerce-retail','电商零售',   '秒杀、订单、库存、营销，高并发场景最密集的行业。', 3),
('internet-social', '互联网社交', 'IM、Feed 流、音视频，长连接与高扇出读写的经典挑战。', 4),
('gov-enterprise',  '政企数字化', '政务审批、数据共享、智慧园区，工作流与合规审计是关键词。', 5),
('healthcare',      '医疗健康',   'HIS/LIS/互联网医院，业务严谨、数据安全要求极高的行业。', 6),
('logistics',       '物流供应链', 'WMS/TMS/调度，算法与工程结合最紧密的行业。', 7),
('education',       '教育',       '在线课堂、考试、题库，高并发选课与实时互动场景。', 8),
('energy-power',    '能源电力',   '功率预测、储能 EMS、电力交易，新能源行业正处于风口。', 9),
('travel-transport','出行交通',   '网约车、票务、充电桩，地理计算与高并发票务的经典场景。', 10),
('gaming',          '游戏',       '网关、排行、匹配，长连接与实时性要求苛刻的行业。', 11),
('saas',            'SaaS',       '多租户、工作流、开放平台，To B 产品的通用底座。', 12);

-- ---------------------------------------------------------------------------
-- 项目：已开更锚点（挂真实系列）+ 筹备中占位
-- ---------------------------------------------------------------------------
INSERT INTO project (industry_id, series_id, slug, title, jd_freq, summary, sort) VALUES
-- 制造
((SELECT id FROM industry WHERE slug='manufacturing'), (SELECT id FROM series WHERE slug='cell-mes'),    'cell-mes',        '给 20GWh 电芯工厂打造 MES',        '高频', '锂电电芯产线的制造执行系统：工单派工、过站防呆、批次追溯与设备采集联动，连载 14 章。', 1),
((SELECT id FROM industry WHERE slug='manufacturing'), (SELECT id FROM series WHERE slug='digital-twin'), 'digital-twin',    '数字孪生巡检平台实战',              '高频', '面向装备制造的数字孪生巡检平台：MQTT 遥测、多协议接入、告警联动与 Cesium 三维可视化，连载 19 章。', 2),
((SELECT id FROM industry WHERE slug='manufacturing'), NULL, 'erp-oms',        'ERP 进销存与订单中台',    '高频', '面向中小制造企业的进销存 ERP：采购/销售/库存一体，订单全生命周期中台化。', 3),
((SELECT id FROM industry WHERE slug='manufacturing'), NULL, 'scada-collect',  'SCADA 设备数据采集平台',  '高频', '工业设备数据采集与监控：多协议接入、万级点位管理、实时曲线与历史回放。', 4),
((SELECT id FROM industry WHERE slug='manufacturing'), NULL, 'aps-scheduling', 'APS 智能排产调度系统',    '中频', '有限产能排产：设备/模具/班次约束建模，甘特图可视化与拖拽改派。', 5),
((SELECT id FROM industry WHERE slug='manufacturing'), NULL, 'qms-trace',      'QMS 质量追溯系统',        '中频', '从来料到成品的质量闭环：IQC/IPQC/OQC 检验、不良品处置与批次追溯。', 6),
-- 金融
((SELECT id FROM industry WHERE slug='finance'), NULL, 'payment-platform',  '聚合支付平台',        '高频', '对接微信/支付宝/银联的聚合收单：统一下单、异步回调、分账与对账。', 1),
((SELECT id FROM industry WHERE slug='finance'), NULL, 'risk-engine',       '实时风控引擎',        '高频', '交易反欺诈：规则+特征双引擎毫秒级决策，名单库与设备指纹。', 2),
((SELECT id FROM industry WHERE slug='finance'), NULL, 'credit-system',     '消费信贷审批系统',    '高频', '进件、授信、支用、还款全流程：额度模型、还款计划与贷后管理。', 3),
((SELECT id FROM industry WHERE slug='finance'), NULL, 'trading-gateway',   '行情与交易网关',      '中频', '行情订阅分发与订单接入：低延迟推送、委托队列与成交回报。', 4),
((SELECT id FROM industry WHERE slug='finance'), NULL, 'settlement-center', '清结算对账中心',      '高频', 'T+1 清算与差错处理：百万级流水双边对账的批处理设计。', 5),
((SELECT id FROM industry WHERE slug='finance'), NULL, 'fund-custody',      '基金申购赎回系统',    '中频', '基金代销交易：申购赎回确认、份额登记与净值估值联动。', 6),
-- 电商零售
((SELECT id FROM industry WHERE slug='ecommerce-retail'), NULL, 'seckill-system',    '秒杀系统',            '高频', '大促秒杀：库存预热、限流削峰、Redis+Lua 扣减与订单异步落库。', 1),
((SELECT id FROM industry WHERE slug='ecommerce-retail'), NULL, 'order-center',      '电商订单中台',        '高频', '购物车、下单、履约、售后：订单全链路与多端订单模型设计。', 2),
((SELECT id FROM industry WHERE slug='ecommerce-retail'), NULL, 'inventory-center',  '库存中台与超卖治理',  '高频', '多仓库存：可售/预占/实扣三层模型与扣减链路。', 3),
((SELECT id FROM industry WHERE slug='ecommerce-retail'), NULL, 'marketing-coupon',  '营销优惠券系统',      '高频', '券模板、发放、核销：大额券防超发、叠加互斥规则与营销 ROI。', 4),
((SELECT id FROM industry WHERE slug='ecommerce-retail'), NULL, 'mall-search',       '商品搜索与推荐',      '中频', 'ES 商品搜索：多条件筛选、聚合导航、拼音纠错与排序策略。', 5),
((SELECT id FROM industry WHERE slug='ecommerce-retail'), NULL, 'member-growth',     '会员与积分体系',      '中频', '等级、成长值、积分商城：积分防刷与过期批处理、权益引擎。', 6),
-- 互联网社交
((SELECT id FROM industry WHERE slug='internet-social'), (SELECT id FROM series WHERE slug='starlab'), 'starlab', 'StarLab：LEO 卫星互联网仿真实战', '中频', '4000 颗低轨卫星的通信仿真：星历计算、过境调度、频率复用与相控阵，连载 9 章。', 1),
((SELECT id FROM industry WHERE slug='internet-social'), NULL, 'im-platform',  'IM 即时通讯系统',   '高频', '万级并发的单聊/群聊：长连接网关、消息投递、已读回执与离线消息。', 2),
((SELECT id FROM industry WHERE slug='internet-social'), NULL, 'feed-system',  'Feed 流信息流系统', '高频', '关注流+推荐流：推拉结合的 fanout 设计、游标分页与去重。', 3),
((SELECT id FROM industry WHERE slug='internet-social'), NULL, 'short-video',  '短视频点播系统',    '高频', '上传转码分发链路：分片上传、转码流水线、CDN 播放与防盗链。', 4),
((SELECT id FROM industry WHERE slug='internet-social'), NULL, 'user-center',  '统一账号与增长平台','中频', '注册登录/第三方授权/风控：账号体系合并与高并发登录优化。', 5),
-- 政企数字化
((SELECT id FROM industry WHERE slug='gov-enterprise'), (SELECT id FROM series WHERE slug='oa-seal-practice'), 'oa-seal-practice', 'OA 印章管理系统实战', '中频', '企业印章全流程管控：Camunda 审批流、动态表单、审计留痕与会签进阶，连载 12 章。', 1),
((SELECT id FROM industry WHERE slug='gov-enterprise'), NULL, 'gov-approval',   '政务审批工作流平台',  '高频', '事项申报、多级审批、出件：Flowable 工作流、电子证照与好差评。', 2),
((SELECT id FROM industry WHERE slug='gov-enterprise'), NULL, 'data-exchange',  '数据共享交换平台',    '高频', '跨部门数据共享：目录管理、申请审批、API 网关化供数与动态脱敏。', 3),
((SELECT id FROM industry WHERE slug='gov-enterprise'), NULL, 'smart-park',     '智慧园区综合管理平台','中频', '门禁/停车/能耗/访客一体：IoT 设备接入与园区一屏统管。', 4),
((SELECT id FROM industry WHERE slug='gov-enterprise'), NULL, 'gov-hotline',    '12345 诉求工单系统',  '中频', '诉求受理、派单、办理、回访：智能派单、超期预警与满意度回访。', 5),
-- 医疗健康
((SELECT id FROM industry WHERE slug='healthcare'), NULL, 'his-clinic',        'HIS 门诊住院系统',      '高频', '挂号、收费、医生站、护士站：医疗业务闭环与医保接口对接。', 1),
((SELECT id FROM industry WHERE slug='healthcare'), NULL, 'lis-laboratory',    'LIS 检验信息系统',      '中频', '检验申请到报告：条码流转、仪器双向通讯与危急值闭环。', 2),
((SELECT id FROM industry WHERE slug='healthcare'), NULL, 'internet-hospital', '互联网医院在线问诊',    '高频', '在线复诊：图文/视频问诊、电子处方流转与药品配送。', 3),
((SELECT id FROM industry WHERE slug='healthcare'), NULL, 'health-archive',    '居民健康档案平台',      '中频', '全生命周期健康档案：多源数据归集、患者主索引与随访管理。', 4),
((SELECT id FROM industry WHERE slug='healthcare'), NULL, 'vaccine-platform',  '疫苗接种管理平台',      '中频', '预约、接种、留观：疫苗批次效期管理与冷链示踪。', 5),
-- 物流供应链
((SELECT id FROM industry WHERE slug='logistics'), NULL, 'wms-warehouse',      'WMS 仓储管理系统',    '高频', '入库、上架、拣货、出库：库位库龄管理与波次拣货优化。', 1),
((SELECT id FROM industry WHERE slug='logistics'), NULL, 'tms-transport',      'TMS 运输管理系统',    '高频', '订单、调度、在途、签收：运力调度、计费与回单管理。', 2),
((SELECT id FROM industry WHERE slug='logistics'), NULL, 'dispatch-platform',  '智能调度与路径规划',  '高频', '配送调度中台：多点配送路径规划、时效承诺与动态改派。', 3),
((SELECT id FROM industry WHERE slug='logistics'), NULL, 'track-platform',     '全程轨迹追踪平台',    '中频', '快递/货运全程可视化：节点埋点采集、轨迹合成与 ETA 预测。', 4),
((SELECT id FROM industry WHERE slug='logistics'), NULL, 'supply-chain-plan',  '供应链计划与补货',    '中频', '需求预测与补货建议：销量预测、安全库存与补货单生成。', 5),
-- 教育
((SELECT id FROM industry WHERE slug='education'), NULL, 'online-classroom', '在线课堂与直播系统',  '高频', '直播授课：推拉流、连麦互动、课件同步与课后回放。', 1),
((SELECT id FROM industry WHERE slug='education'), NULL, 'exam-system',      '在线考试与判卷系统',  '高频', '组卷、考试、判卷、成绩：防作弊与万人同时交卷的削峰设计。', 2),
((SELECT id FROM industry WHERE slug='education'), NULL, 'lms-platform',     '教务与课程管理平台',  '中频', '排课选课：课程/班级/教师的冲突检测与高并发选课。', 3),
((SELECT id FROM industry WHERE slug='education'), NULL, 'question-bank',    '题库刷题平台',        '高频', '题目管理到刷题练习：知识点标签体系、错题本与做题统计。', 4),
((SELECT id FROM industry WHERE slug='education'), NULL, 'homework-grading', '作业批改与学情分析',  '中频', '布置、提交、批改、学情报告：客观题自动判分与班级统计。', 5),
-- 能源电力
((SELECT id FROM industry WHERE slug='energy-power'), (SELECT id FROM series WHERE slug='gansu-ems'), 'gansu-ems', '甘肃新能源场站 EMS（改造 OpenEMS）', '中频', '改造开源 OpenEMS：功率预测三源仲裁、AGC 秒级仲裁与一天 13 亿个点的时序存储，连载 14 章。', 1),
((SELECT id FROM industry WHERE slug='energy-power'), NULL, 'pv-monitor',       '光伏电站监控运维平台', '高频', '电站组串级监控：逆变器数据采集、发电对比与故障定位。', 2),
((SELECT id FROM industry WHERE slug='energy-power'), NULL, 'energy-storage',   '储能 EMS 能量管理系统','高频', '工商业储能：充放电策略、峰谷套利与电池簇管理。', 3),
((SELECT id FROM industry WHERE slug='energy-power'), NULL, 'power-trading',    '电力现货交易系统',     '中频', '现货申报与结算：日前/实时市场申报、出清结果与结算对账。', 4),
((SELECT id FROM industry WHERE slug='energy-power'), NULL, 'carbon-management','碳资产管理平台',       '中频', '碳核算与碳资产：排放数据采集、碳足迹核算与配额履约。', 5),
-- 出行交通
((SELECT id FROM industry WHERE slug='travel-transport'), NULL, 'ride-hailing',     '网约车调度平台',    '高频', '乘客叫车、派单、行程、支付：地理围栏、派单算法与计费引擎。', 1),
((SELECT id FROM industry WHERE slug='travel-transport'), NULL, 'ticket-booking',   '12306 式票务系统',  '高频', '高并发抢票：余票区间扣减、候补队列与防黄牛。', 2),
((SELECT id FROM industry WHERE slug='travel-transport'), NULL, 'fleet-monitor',    '车队 GPS 监控平台', '中频', '万级车辆轨迹：JT808 协议接入、轨迹存储与电子围栏告警。', 3),
((SELECT id FROM industry WHERE slug='travel-transport'), NULL, 'charging-network', '充电桩运营平台',    '高频', '桩联网：充电启停、订单计费、有序充电与站点运营。', 4),
((SELECT id FROM industry WHERE slug='travel-transport'), NULL, 'transit-payment',  '公交地铁支付清分系统','中频', '一码通行：乘车码、闸机交互与多运营商清分对账。', 5),
-- 游戏
((SELECT id FROM industry WHERE slug='gaming'), NULL, 'game-gateway',       '游戏网关与大厅服务', '高频', '长连接网关+大厅：登录鉴权、断线重连与跨服消息路由。', 1),
((SELECT id FROM industry WHERE slug='gaming'), NULL, 'rank-leaderboard',   '排行榜与好友系统',   '高频', '全服/好友排行榜：ZSET 方案、分数更新风暴与赛季结算。', 2),
((SELECT id FROM industry WHERE slug='gaming'), NULL, 'match-server',       '匹配与对战服务',     '中频', '实时对战匹配：分段匹配池、房间管理与帧同步校验。', 3),
((SELECT id FROM industry WHERE slug='gaming'), NULL, 'game-gm-platform',   '游戏运营 GM 平台',   '中频', '账号/邮件/公告/封禁：运营工具链与敏感操作的审计。', 4),
((SELECT id FROM industry WHERE slug='gaming'), NULL, 'game-payment',       '游戏充值与商城系统', '高频', '充值发货：渠道回调验签、发货幂等与对客补单流程。', 5),
-- SaaS
((SELECT id FROM industry WHERE slug='saas'), (SELECT id FROM series WHERE slug='aap-agent-practice'), 'aap-agent-practice', 'AI Agent 调度中台实战', '高频', '从零搭建 AI Agent 调度中台：DAG 工作流、RAG 多路召回、A2A 协议与多租户隔离，连载 14 章。', 1),
((SELECT id FROM industry WHERE slug='saas'), NULL, 'saas-tenant',        '多租户 SaaS 中台',    '高频', '多租户底座：租户隔离方案、套餐计费与配额管理。', 2),
((SELECT id FROM industry WHERE slug='saas'), NULL, 'crm-platform',       'CRM 客户管理平台',    '高频', '线索、商机、成交：公海池、跟进记录与销售漏斗。', 3),
((SELECT id FROM industry WHERE slug='saas'), NULL, 'bi-report-engine',   'BI 报表引擎',         '高频', '自助报表：数据集建模、拖拽出表、大数据量导出与订阅。', 4),
((SELECT id FROM industry WHERE slug='saas'), NULL, 'workflow-engine',    '低代码工作流引擎',    '高频', '可视化流程设计器：条件分支、并行网关与流程版本管理。', 5),
((SELECT id FROM industry WHERE slug='saas'), NULL, 'openapi-gateway',    '开放平台与 API 网关', '中频', 'SaaS 开放生态：应用创建、签名鉴权、限流计费与开发者门户。', 6);

-- ---------------------------------------------------------------------------
-- 面试考点种子（每项目 6-7 条，后续用 JD 检索 + 面经持续替换）
-- ---------------------------------------------------------------------------
INSERT INTO project_exam_point (project_id, sort, point, detail)
SELECT p.id, v.sort, v.point, v.detail
FROM (VALUES
  -- cell-mes
  ('cell-mes', 1, '过站防呆与状态机设计', '工单/批次过站流转的状态机建模，防跳站漏站的落库校验方案'),
  ('cell-mes', 2, '工艺路线与 BOM 展开', '替代料替换、派工级联展开与改派校验的实现思路'),
  ('cell-mes', 3, '批次追溯三链', '向前/向后追溯与召回边界，PostgreSQL CTE 递归查询实战'),
  ('cell-mes', 4, 'OPC UA/Modbus/SECS 采集', '涂布机/化成柜/卷绕机的协议适配层与采集稳定性设计'),
  ('cell-mes', 5, 'OEE 三因子统计', 'SEMI TR88.03 停机编码、24h 班次口径与可用率修正'),
  ('cell-mes', 6, 'SPC 与 Cpk 过程能力', '化成容量控制图、西电规则判定与过程能力指数计算'),
  ('cell-mes', 7, 'IoTDB 分级存储', '秒级/分钟级/小时级归档策略与时序数据生命周期管理'),
  -- digital-twin
  ('digital-twin', 1, 'MQTT 遥测链路', '一条遥测从设备上报到平台落库的完整链路，含 ThingsBoard 源码拆解'),
  ('digital-twin', 2, '多协议设备接入', 'PLC 网关、无人机 MAVLink、机器狗 rosbridge 的连接器机制剖析'),
  ('digital-twin', 3, '设备影子', '离线设备的最后状态托管与期望值下发的设计'),
  ('digital-twin', 4, '告警规则引擎与工单闭环', '阈值规则、告警分级与设备联动工单的落地'),
  ('digital-twin', 5, 'WebSocket 毫秒级实时通道', '实时看板推送的协议设计与前后端联调'),
  ('digital-twin', 6, 'Cesium 三维可视化', '从零到三维球：航线绘制、轨迹回放与告警标记'),
  ('digital-twin', 7, '多租户与角色权限', 'JWT 登录闭环、三角色权限与数据隔离'),
  -- erp-oms
  ('erp-oms', 1, '库存两段式扣减', '预占/实扣两段式设计与超卖治理'),
  ('erp-oms', 2, '订单状态机', '创建/审核/发货/退货的流转建模与幂等设计'),
  ('erp-oms', 3, '单据流水与期末对账', '出入库流水设计与期末对账的核对逻辑'),
  ('erp-oms', 4, '多组织数据权限', '部门/仓库维度的数据权限隔离方案'),
  ('erp-oms', 5, '报表聚合性能', '大表聚合查询的索引优化与汇总表设计'),
  ('erp-oms', 6, '采购与销售协同', '以销定采的业务闭环与缺料预警'),
  -- scada-collect
  ('scada-collect', 1, '多协议接入适配器', 'Modbus/OPC UA 等协议的适配器模式设计'),
  ('scada-collect', 2, '万级点位元数据管理', '点位注册/变更/校验的元数据模型'),
  ('scada-collect', 3, '时序库选型', 'InfluxDB/TDengine/IoTDB 的写入与降采样对比'),
  ('scada-collect', 4, '断线重连与补采', '采集端可靠性与数据完整性保证'),
  ('scada-collect', 5, '实时曲线推送', '高频点位的推送与前端绘图性能优化'),
  ('scada-collect', 6, '告警联动', '点位超限的告警分级与通知路由'),
  -- aps-scheduling
  ('aps-scheduling', 1, '有限产能排产算法', '启发式规则调度与排程求解的工程落地'),
  ('aps-scheduling', 2, '约束建模', '设备日历、换型时间、班次日历的数据结构'),
  ('aps-scheduling', 3, '插单重排与冲突检测', '紧急插单后的重排策略与冲突告警'),
  ('aps-scheduling', 4, '甘特图大规模渲染', '大规模任务的可视化与拖拽交互'),
  ('aps-scheduling', 5, '多人并发排产', '乐观锁与版本号控制多人同时排产'),
  ('aps-scheduling', 6, '齐套检查', '物料齐套率计算与开工可行性校验'),
  -- qms-trace
  ('qms-trace', 1, '批次正反向追溯', '批次图谱的正反向追溯与召回边界圈定'),
  ('qms-trace', 2, '检验单流程', '抽样方案与判定规则的规则引擎实现'),
  ('qms-trace', 3, '不良品处置', '评审/返工/报废的状态流转设计'),
  ('qms-trace', 4, 'SPC 统计控制', '控制图数据聚合与超限告警'),
  ('qms-trace', 5, '8D 报告闭环', '质量问题 8D 流程的建模与跟踪'),
  ('qms-trace', 6, '供应商质量协同', '来料不良的供应商协同与索赔流程'),
  -- payment-platform
  ('payment-platform', 1, '统一下单网关', '多渠道适配器模式与参数路由设计'),
  ('payment-platform', 2, '异步回调幂等', '回调重试/乱序场景下的幂等落库'),
  ('payment-platform', 3, '支付状态机', '待支付/成功/退款的一致性流转'),
  ('payment-platform', 4, '资金对账', '渠道账单与流水的日对账差异处理'),
  ('payment-platform', 5, '退款流程', '部分退款与退款回调的并发控制'),
  ('payment-platform', 6, '敏感数据加密', '卡号/商户密钥的加密存储与脱敏展示'),
  -- risk-engine
  ('risk-engine', 1, '毫秒级决策链路', '规则+特征计算的低延迟架构设计'),
  ('risk-engine', 2, '规则引擎热更新', 'Drools/自研表达式规则的动态生效'),
  ('risk-engine', 3, '实时特征计算', '滑动窗口计数的 Flink/Redis 实现'),
  ('risk-engine', 4, '名单库多级缓存', '黑白名单的本地缓存+Redis 两级设计'),
  ('risk-engine', 5, '决策异步落库', '高并发下的异步持久化与审计追踪'),
  ('risk-engine', 6, '设备指纹', '设备维度的风险画像与关联识别'),
  -- credit-system
  ('credit-system', 1, '授信工作流', '进件编排与外部征信接口的调用设计'),
  ('credit-system', 2, '额度并发管理', '授信/占用/释放的并发一致性'),
  ('credit-system', 3, '还款计划生成', '等额本息/先息后本的计划与核销'),
  ('credit-system', 4, '逾期批处理', '逾期天数计算与催收分案调度'),
  ('credit-system', 5, '合同留痕', '电子合同签署与操作审计的合规设计'),
  ('credit-system', 6, '外部接口容错', '征信/运营商接口的超时降级与补偿'),
  -- trading-gateway
  ('trading-gateway', 1, '行情分发架构', '百万级订阅的 Netty 推送架构'),
  ('trading-gateway', 2, '委托队列', '订单合法性校验与限流排队'),
  ('trading-gateway', 3, '成交回报同步', '回报推送与订单状态的一致性'),
  ('trading-gateway', 4, '低延迟优化', '无锁队列与对象池的实践'),
  ('trading-gateway', 5, '断线补发', '行情快照+增量补发的可靠性设计'),
  ('trading-gateway', 6, '行情压缩', '增量行情的编码压缩与带宽优化'),
  -- settlement-center
  ('settlement-center', 1, '双边对账模型', '对账文件解析与高效匹配算法'),
  ('settlement-center', 2, '差错池处理', '长款/短款/金额不平的差错分类处理'),
  ('settlement-center', 3, 'Spring Batch 分片', '百万级流水批处理的分片与断点续跑'),
  ('settlement-center', 4, '试算平衡', '账务平衡校验与总分核对'),
  ('settlement-center', 5, '幂等重跑', '批次可重复执行的幂等设计'),
  ('settlement-center', 6, '资金调拨', '内部户之间的资金划拨与凭证生成'),
  -- fund-custody
  ('fund-custody', 1, 'T+1 交易确认', '申购/赎回的确认流程与状态回写'),
  ('fund-custody', 2, '份额登记', '份额增减与冻结的并发控制'),
  ('fund-custody', 3, '净值联动', '净值文件解析与金额计算的精度处理'),
  ('fund-custody', 4, '巨额赎回', '阈值判定与顺延/部分成交规则'),
  ('fund-custody', 5, 'BigDecimal 陷阱', '金融计算的舍入规则与除法精度'),
  ('fund-custody', 6, '分红处理', '现金分红/红利再投的份额变动'),
  -- seckill-system
  ('seckill-system', 1, '库存预热', '活动库存提前加载到 Redis 的方案'),
  ('seckill-system', 2, '限流削峰', '网关限流、令牌桶与队列削峰的取舍'),
  ('seckill-system', 3, 'Redis+Lua 原子扣减', '库存扣减+限购校验的原子脚本'),
  ('seckill-system', 4, '异步下单', 'MQ 削峰与订单落库的最终一致性'),
  ('seckill-system', 5, '一人一单防刷', '黄牛识别与设备/账号维度的防刷'),
  ('seckill-system', 6, '热点隔离与兜底', '热点商品隔离、库存回补与降级预案'),
  -- order-center
  ('order-center', 1, '订单主子表建模', '主订单/子订单拆分与多端订单聚合'),
  ('order-center', 2, '下单链路一致性', '库存/优惠券/订单的事务边界设计'),
  ('order-center', 3, '超时关单', '延迟消息与定时扫表的方案对比'),
  ('order-center', 4, '履约拆单', '按仓/商家拆单的规则引擎'),
  ('order-center', 5, '售后状态机', '退款/退货状态流转与资金回流'),
  ('order-center', 6, '订单分库分表', '买家/卖家双维度查询的路由设计'),
  -- inventory-center
  ('inventory-center', 1, '库存三层模型', '可售/预占/实扣的状态迁移设计'),
  ('inventory-center', 2, '扣减方案对比', 'DB 乐观锁 vs Redis 预扣的选型'),
  ('inventory-center', 3, '预占释放', '订单取消/超时的预占回补时序'),
  ('inventory-center', 4, '分仓路由', '收货地址到发货仓的分配规则'),
  ('inventory-center', 5, '库存对账', '库存流水与快照的日终核对'),
  ('inventory-center', 6, '移库调拨', '多仓之间的调拨在途与收发差异'),
  -- marketing-coupon
  ('marketing-coupon', 1, '券模板模型', '面额/门槛/有效期/适用范围的模型设计'),
  ('marketing-coupon', 2, '发券防超发', '发放的原子扣减与库存兜底'),
  ('marketing-coupon', 3, '叠加互斥规则', '券可用性校验的规则引擎'),
  ('marketing-coupon', 4, '锁券核销时序', '下单锁券与退款回退券的并发问题'),
  ('marketing-coupon', 5, '防羊毛党', '设备/IP/账号维度的领取风控'),
  ('marketing-coupon', 6, '营销 ROI 统计', '券核销与 GMV 归因的数据统计'),
  -- mall-search
  ('mall-search', 1, '商品索引设计', 'ES mapping 设计与嵌套文档的取舍'),
  ('mall-search', 2, '多条件筛选聚合', 'filter context 与分面聚合导航'),
  ('mall-search', 3, '准实时同步', 'binlog/MQ 到 ES 的数据同步链路'),
  ('mall-search', 4, '相关性打分', '业务权重与 function_score 排序'),
  ('mall-search', 5, '拼音纠错补全', '拼音分词与 suggest 搜索建议'),
  ('mall-search', 6, '搜索无结果兜底', '零结果页的降级与推荐策略'),
  -- member-growth
  ('member-growth', 1, '积分流水账本', '流水式记账与余额一致性保证'),
  ('member-growth', 2, '积分批次过期', '按批次过期的扫描批处理设计'),
  ('member-growth', 3, '等级权益引擎', '等级规则配置化的权益判定'),
  ('member-growth', 4, '异常积分识别', '刷分行为的规则与模型识别'),
  ('member-growth', 5, '幂等发放', '活动触发积分发放的幂等设计'),
  ('member-growth', 6, '成长值体系', '成长值的获取规则与等级升降级'),
  -- starlab
  ('starlab', 1, '开普勒六参数轨道计算', '轨道根数到星下点位置的数学推导与 Java 实现'),
  ('starlab', 2, 'SGP4-lite 与 CI 断言', '简化轨道递推的误差控制与回归测试策略'),
  ('starlab', 3, '万级卫星防御式解析', '一万颗真卫星 TLE 数据的容错解析与内存控制'),
  ('starlab', 4, '可见性窗口调度', 'Greedy 与 Predictive 调度策略的真实对决'),
  ('starlab', 5, '频率复用与干扰分析', '205 个同频邻居的 C/I 计算与频率规划实验'),
  ('starlab', 6, '相控阵波束赋形', '偏轴干扰抑制的仿真建模'),
  ('starlab', 7, '批量计算加速', '把三小时巡检仿真压成三十秒的工程优化'),
  -- im-platform
  ('im-platform', 1, '长连接网关', 'Netty 连接管理与用户到实例的路由设计'),
  ('im-platform', 2, '写扩散 vs 读扩散', '消息 fanout 模型的取舍与大 V 特殊处理'),
  ('im-platform', 3, '消息可靠投递', 'seq 序号+ACK+重推的可靠性保证'),
  ('im-platform', 4, '离线与漫游消息', '离线消息拉取协议与多端漫游'),
  ('im-platform', 5, '万人群聊扇出', '大群消息的写扩散优化与合并推送'),
  ('im-platform', 6, '会话顺序性', '同会话消息单调递增的 seq 设计'),
  -- feed-system
  ('feed-system', 1, '推拉结合模型', '大 V 用拉、普通用户用推的分界策略'),
  ('feed-system', 2, '收件箱 timeline', 'Redis ZSET 存储与游标分页设计'),
  ('feed-system', 3, '翻页一致性', '插删内容下游标分页的翻页去重'),
  ('feed-system', 4, '内容去重', '布隆过滤器在已读/降重中的应用'),
  ('feed-system', 5, '热点扇出削峰', '明星发文百万粉丝的异步 fanout'),
  ('feed-system', 6, '推荐流混排', '关注流与广告/推荐内容的混排策略'),
  -- short-video
  ('short-video', 1, '分片上传与秒传', '断点续传、MD5 秒传与合并校验'),
  ('short-video', 2, '转码流水线', 'MQ 驱动的多分辨率转码任务编排'),
  ('short-video', 3, '播放调度', '多码率自适应与 CDN 边缘调度'),
  ('short-video', 4, '防盗链', '签名 URL 与 Referer/时间戳校验'),
  ('short-video', 5, '机审人审队列', '内容审核的异步队列与状态回写'),
  ('short-video', 6, '热门池构建', '播放完成率驱动的热门内容池'),
  -- user-center
  ('user-center', 1, '账号合并', '多渠道注册的账号绑定与合并策略'),
  ('user-center', 2, 'JWT vs Session', '会话方案选型与登出失效处理'),
  ('user-center', 3, '高并发登录', '缓存穿透与验证码防刷的登录优化'),
  ('user-center', 4, '二次验证', '敏感操作的风控分级与二次校验'),
  ('user-center', 5, '手机号安全', '手机号加密存储与脱敏展示'),
  ('user-center', 6, '第三方授权登录', 'OAuth 流程与 UnionID 体系'),
  -- oa-seal-practice
  ('oa-seal-practice', 1, 'Camunda 7 集成踩坑', '若依+Camunda 整合与 fat jar 下 BPMN 部署的坑'),
  ('oa-seal-practice', 2, '印章业务建模', '8 张核心表讲透印章全生命周期'),
  ('oa-seal-practice', 3, '动态表单引擎', 'VForm JSON 表单与 Java 后端的对接方案'),
  ('oa-seal-practice', 4, 'BusinessKey 流程解耦', '审批闭环中 BusinessKey 为什么是解耦核心'),
  ('oa-seal-practice', 5, '注解式审计留痕', '一条注解实现不可篡改的操作日志'),
  ('oa-seal-practice', 6, '会签与排他网关', '流程进阶：会签、排他网关与监听器回写'),
  -- gov-approval
  ('gov-approval', 1, 'Flowable 流程建模', '流程定义/会签/驳回退回的建模实践'),
  ('gov-approval', 2, '动态表单引擎', '动态表单的数据存储与前端渲染方案'),
  ('gov-approval', 3, '电子证照', '证照模板渲染与电子签章集成'),
  ('gov-approval', 4, '超时催办', '办理时限的定时扫描与升级提醒'),
  ('gov-approval', 5, '好差评体系', '办件评价的采集与差评整改闭环'),
  ('gov-approval', 6, '等保审计', '操作留痕与审计日志的合规设计'),
  -- data-exchange
  ('data-exchange', 1, '数据目录与血缘', '元数据管理与数据血缘的落地'),
  ('data-exchange', 2, '三种供数模式', '库表/文件/API 模式的网关封装'),
  ('data-exchange', 3, '动态脱敏', '按申请方权限的动态脱敏实现'),
  ('data-exchange', 4, '共享申请审批', '授权流程与有效期管理'),
  ('data-exchange', 5, '调用计次审计', '供数调用的统计与留痕'),
  ('data-exchange', 6, '批量文件交换', '大文件的安全传输与校验'),
  -- smart-park
  ('smart-park', 1, 'IoT 设备接入', '门禁/道闸设备的协议适配与状态管理'),
  ('smart-park', 2, '车牌识别联动', '识别回调与闸门联动的可靠性保证'),
  ('smart-park', 3, '能耗分项计量', '水电气数据的采集与聚合分析'),
  ('smart-park', 4, '一屏统管大屏', '多源数据聚合与缓存的大屏方案'),
  ('smart-park', 5, '告警跨系统联动', '消防/安防事件的多系统联动处置'),
  ('smart-park', 6, '访客预约', '访客邀请链路与通行授权'),
  -- gov-hotline
  ('gov-hotline', 1, '智能派单规则', '关键词/辖区/职责矩阵的派单引擎'),
  ('gov-hotline', 2, '工单状态机', '多部门会办/退单/重派的状态流转'),
  ('gov-hotline', 3, '办理时限预警', '超期预警的定时扫描与升级机制'),
  ('gov-hotline', 4, '满意度回访', '回访任务生成与重派机制'),
  ('gov-hotline', 5, '诉求热点分析', '诉求数据的聚合分析与报表'),
  ('gov-hotline', 6, '工单催办', '多级催办的规则与消息触达'),
  -- his-clinic
  ('his-clinic', 1, '号源池并发扣号', '分时段预约的号源模型与并发扣号'),
  ('his-clinic', 2, '医嘱闭环', '医嘱开立/执行/校对的状态流转'),
  ('his-clinic', 3, '医保结算接口', '费用明细拆分与医保接口对接'),
  ('his-clinic', 4, '电子病历结构化', '结构化存储与模板渲染'),
  ('his-clinic', 5, '患者隐私安全', '数据加密与访问审计的合规设计'),
  ('his-clinic', 6, '门诊缴费对账', '收费流水与财务的对账处理'),
  -- lis-laboratory
  ('lis-laboratory', 1, '标本条码状态机', '标本采集到报告的全流程条码流转'),
  ('lis-laboratory', 2, '仪器双向通讯', 'ASTM/HL7 协议的解析与上机指令下发'),
  ('lis-laboratory', 3, '危急值闭环', '危急值推送与医护确认的闭环管理'),
  ('lis-laboratory', 4, '复检规则', '异常结果的复检判定与审核放行'),
  ('lis-laboratory', 5, '室内质控', '质控数据统计与失控告警'),
  ('lis-laboratory', 6, '报告模板引擎', '检验报告的模板化生成'),
  -- internet-hospital
  ('internet-hospital', 1, '分诊排队', '问诊队列与接单超时自动释放'),
  ('internet-hospital', 2, '处方药师审核', '处方开立的合规审核流程'),
  ('internet-hospital', 3, '处方流转外配', '处方外配到药房的接口与电子签名'),
  ('internet-hospital', 4, '问诊 IM 可靠性', '图文/视频问诊的消息可靠投递'),
  ('internet-hospital', 5, '复诊资格校验', '复诊条件与既往就诊记录核验'),
  ('internet-hospital', 6, '药品配送对接', '物流状态回传与订单同步'),
  -- health-archive
  ('health-archive', 1, 'EMPI 患者主索引', '多源数据的患者匹配合并策略'),
  ('health-archive', 2, '多源数据归集', '多机构数据的标准化清洗入档'),
  ('health-archive', 3, '跨机构调阅授权', '档案调阅的授权与脱敏控制'),
  ('health-archive', 4, '随访计划引擎', '随访规则配置与任务自动生成'),
  ('health-archive', 5, '海量档案分库分表', '千万级档案的水平拆分实践'),
  ('health-archive', 6, '档案数据质量', '数据完整性校验与补录机制'),
  -- vaccine-platform
  ('vaccine-platform', 1, '号源分批放号', '预约放号策略与防黄牛措施'),
  ('vaccine-platform', 2, '批次效期管理', '疫苗批次/效期的先进先出出库'),
  ('vaccine-platform', 3, '冷链示踪', '温度记录采集与超温告警联动'),
  ('vaccine-platform', 4, '人证核验', '接种前的人证核验与电子知情同意'),
  ('vaccine-platform', 5, 'AEFI 上报', '不良反应上报的流程与追踪'),
  ('vaccine-platform', 6, '接种证电子化', '电子接种证的生成与查验'),
  -- wms-warehouse
  ('wms-warehouse', 1, '多维库存模型', '库区/库位/批次的多维库存设计'),
  ('wms-warehouse', 2, '波次拣货优化', '波次生成与拣货路径排序算法'),
  ('wms-warehouse', 3, 'PDA 扫码防错', '出入库强校验规则与扫码交互'),
  ('wms-warehouse', 4, '动碰盘点', '盘点策略与差异处理流程'),
  ('wms-warehouse', 5, '并发库位分配', '同一库位的并发分配控制'),
  ('wms-warehouse', 6, '上架策略', '上架库位推荐的规则引擎'),
  -- tms-transport
  ('tms-transport', 1, '运力匹配调度', '车辆/司机资源的匹配算法'),
  ('tms-transport', 2, '计费规则引擎', '里程/重量/时效的计费规则配置化'),
  ('tms-transport', 3, '轨迹压缩停留识别', 'GPS 上报的轨迹压缩与停留点识别'),
  ('tms-transport', 4, '电子回单', '回单拍照上传与核验流程'),
  ('tms-transport', 5, '异常件处理', '破损/拒收的异常处理与责任判定'),
  ('tms-transport', 6, '在途时效监控', '运输时效的预警与干预'),
  -- dispatch-platform
  ('dispatch-platform', 1, 'VRP 路径规划', '多点配送路径规划的工程落地'),
  ('dispatch-platform', 2, 'ETA 预估', '基于历史时效的到达时间预测'),
  ('dispatch-platform', 3, '动态改派', '骑手/车辆异常时的任务转移机制'),
  ('dispatch-platform', 4, '抢单 vs 派单', '两种派单模式的对比与混合方案'),
  ('dispatch-platform', 5, '地址网格化', '地址到网格的映射与运力负载均衡'),
  ('dispatch-platform', 6, '时效承诺', '承诺时效与运力成本的平衡'),
  -- track-platform
  ('track-platform', 1, '节点埋点高吞吐', '扫描枪/分拣线埋点的高吞吐写入'),
  ('track-platform', 2, '多源轨迹合成', 'GPS 与节点扫描的轨迹拼接算法'),
  ('track-platform', 3, '分段 ETA 预测', '基于历史时效的分段到达预测'),
  ('track-platform', 4, '运单查询缓存', '运单号查询的多级缓存设计'),
  ('track-platform', 5, '轨迹降存储', '历史轨迹的抽稀与冷热分层'),
  ('track-platform', 6, '消息订阅推送', '运单状态变更的订阅通知'),
  -- supply-chain-plan
  ('supply-chain-plan', 1, '需求预测方法', '移动平均/指数平滑的工程落地'),
  ('supply-chain-plan', 2, '安全库存公式', '服务水平与安全库存的计算'),
  ('supply-chain-plan', 3, '补货单生成', '在途/可用库存联动的补货计算'),
  ('supply-chain-plan', 4, '呆滞库存识别', '慢动销 SKU 的识别与处理策略'),
  ('supply-chain-plan', 5, '全量并行计算', '全量 SKU 补货运算的并行化'),
  ('supply-chain-plan', 6, '计划与执行闭环', '计划调整与采购/生产执行的联动'),
  -- online-classroom
  ('online-classroom', 1, '直播推拉流', 'RTMP 推流与 HLS/FLV 分发链路'),
  ('online-classroom', 2, '连麦低延迟', 'RTC 连麦互动的方案选型'),
  ('online-classroom', 3, '课件同步信令', '课件翻页的信令通道同步'),
  ('online-classroom', 4, '录制回放', '录制转码与切片存储方案'),
  ('online-classroom', 5, '高并发观看', '边缘节点调度与限流策略'),
  ('online-classroom', 6, '课堂互动', '举手/答题/红包的互动消息设计'),
  -- exam-system
  ('exam-system', 1, '随机组卷算法', '按知识点/难度的约束组卷实现'),
  ('exam-system', 2, '万人交卷削峰', '同时交卷的 MQ 削峰与异步判分'),
  ('exam-system', 3, '防作弊体系', '切屏检测/人脸核验/试题乱序'),
  ('exam-system', 4, '客观题自动判分', '秒级出分的判分与成绩发布'),
  ('exam-system', 5, '成绩统计分析', '难度/区分度与知识点掌握度'),
  ('exam-system', 6, '断电续考', '考试中断的答案暂存与恢复'),
  -- lms-platform
  ('lms-platform', 1, '三维排课冲突检测', '教室/教师/班级的冲突检测算法'),
  ('lms-platform', 2, '高并发选课', '名额扣减与候补队列的设计'),
  ('lms-platform', 3, '周期课表建模', '周期性课表的数据建模与查询'),
  ('lms-platform', 4, '学分审核规则', '培养方案与学分修读的规则校验'),
  ('lms-platform', 5, '课表多级缓存', '课表/课程详情的缓存设计'),
  ('lms-platform', 6, '调停课流程', '调课申请与通知的审批流'),
  -- question-bank
  ('question-bank', 1, '多题型统一建模', '单选/多选/编程题的统一存储设计'),
  ('question-bank', 2, '知识点标签树', '多级知识点与题目关联体系'),
  ('question-bank', 3, '错题本与重练', '做题记录与错题重练的推荐'),
  ('question-bank', 4, '刷题数据统计', '连续打卡与正确率趋势的聚合'),
  ('question-bank', 5, '题目全文检索', '题目搜索与相似题推荐'),
  ('question-bank', 6, '题目导入导出', '批量题目导入的格式校验与去重'),
  -- homework-grading
  ('homework-grading', 1, '作业流转状态机', '班级/学科/截止时间的状态管理'),
  ('homework-grading', 2, '客观题自动判分', '答案比对判分与成绩回写'),
  ('homework-grading', 3, '学情报告聚合', '班级/个人维度的统计聚合查询'),
  ('homework-grading', 4, '图片压缩存储', '作业图片的压缩与对象存储'),
  ('homework-grading', 5, '作业提醒推送', '截止提醒的多端消息触达'),
  ('homework-grading', 6, '主观题辅助批改', '评分点标注与批改效率工具'),
  -- gansu-ems
  ('gansu-ems', 1, 'OpenEMS 三层架构与二开', 'OSGi 模块解剖与 fork 二开的许可证合规'),
  ('gansu-ems', 2, 'Modbus 协议栈接入', '700 台逆变器归并成 8 条连接的设备接入设计'),
  ('gansu-ems', 3, 'IEC 104 子站与 AGC', '省调对接与功率指令的两级仲裁'),
  ('gansu-ems', 4, '调度优化器移植', '适应度函数分层账本与考核罚款建模'),
  ('gansu-ems', 5, '功率预测三源仲裁', '多预测源与气象数据源的仲裁融合'),
  ('gansu-ems', 6, 'IoTDB 写入优化', '一天 13 亿个点的分级存储与写入调优'),
  ('gansu-ems', 7, '安全分区落地', '14 号令横向隔离与数据桥的设计'),
  -- pv-monitor
  ('pv-monitor', 1, '逆变器数据采集', '逆变器/汇流箱的多协议采集链路'),
  ('pv-monitor', 2, '组串离散率分析', '组串发电对比与损失电量计算'),
  ('pv-monitor', 3, '低效组串定位', '故障组串的规则识别与告警'),
  ('pv-monitor', 4, '电站报表自动化', '日报/月报的自动生成与推送'),
  ('pv-monitor', 5, '多电站地图聚合', '地图总览与逐级钻取的接口设计'),
  ('pv-monitor', 6, '清洗决策', '灰尘损失的量化与清洗时机建议'),
  -- energy-storage
  ('energy-storage', 1, '充放电策略引擎', '峰谷时段策略的配置化引擎'),
  ('energy-storage', 2, 'BMS 数据对接', '电池簇数据解析与健康度告警'),
  ('energy-storage', 3, '峰谷套利测算', '峰谷价差收益模型的工程实现'),
  ('energy-storage', 4, 'AGC 功率响应', '电网调度指令的秒级响应执行'),
  ('energy-storage', 5, '电池安全联动', '电池告警分级与自动停机'),
  ('energy-storage', 6, '收益报表', '充放电收益的统计与对账'),
  -- power-trading
  ('power-trading', 1, '日前申报模型', '96 时段申报的数据模型与校验'),
  ('power-trading', 2, '出清结果解析', '出清文件解析与偏差分析'),
  ('power-trading', 3, '电量电费结算', '结算规则引擎与对账处理'),
  ('power-trading', 4, '节点电价分析', '价差数据的多维分析'),
  ('power-trading', 5, '申报截止调度', '申报时限的任务调度与提醒'),
  ('power-trading', 6, '偏差考核', '申报偏差的考核计算与优化建议'),
  -- carbon-management
  ('carbon-management', 1, '排放因子核算引擎', '排放源识别与因子计算的规则引擎'),
  ('carbon-management', 2, '能耗数据归集', '表计数据的自动采集与清洗'),
  ('carbon-management', 3, '碳报告模板生成', '碳排放报告的模板化生成'),
  ('carbon-management', 4, '配额履约跟踪', '履约进度与缺口的预警'),
  ('carbon-management', 5, '碳足迹追溯', '产品碳足迹的边界与计算'),
  ('carbon-management', 6, '数据可信存证', '核算数据存证的方案取舍'),
  -- ride-hailing
  ('ride-hailing', 1, '附近车辆搜索', 'GEO 哈希的附近搜索与性能优化'),
  ('ride-hailing', 2, '派单算法', '就近派单与司机偏好/公平性的权衡'),
  ('ride-hailing', 3, '动态计费引擎', '起步价/里程/时段的计费规则'),
  ('ride-hailing', 4, '轨迹上报推送', '行程轨迹的实时位置推送'),
  ('ride-hailing', 5, '高峰叫车削峰', '早高峰的排队叫车与扩容策略'),
  ('ride-hailing', 6, '行程安全', '行程分享与一键报警的链路'),
  -- ticket-booking
  ('ticket-booking', 1, '区间座位模型', '余票区间占用的高效建模'),
  ('ticket-booking', 2, '高并发扣票', 'Redis+DB 的高并发扣票方案'),
  ('ticket-booking', 3, '候补队列', '候补下单与兑现触发机制'),
  ('ticket-booking', 4, '防黄牛', '设备指纹/验证码/限购的组合拳'),
  ('ticket-booking', 5, '座位分配策略', '同排相邻座位的分配算法'),
  ('ticket-booking', 6, '购票风控', '高频请求的限流与人机识别'),
  -- fleet-monitor
  ('fleet-monitor', 1, 'JT808 协议解析', '部标协议的 Netty 解析与指令下发'),
  ('fleet-monitor', 2, '海量轨迹存储', '轨迹点的存储模型与抽稀策略'),
  ('fleet-monitor', 3, '电子围栏', '进出围栏的空间判定与告警'),
  ('fleet-monitor', 4, 'OBD 车况监控', '车况数据的实时采集与预警'),
  ('fleet-monitor', 5, '轨迹回放', '轨迹回放接口与前端渲染性能'),
  ('fleet-monitor', 6, '疲劳驾驶识别', '驾驶时长规则的预警设计'),
  -- charging-network
  ('charging-network', 1, '云快充协议接入', '充电桩协议的接入与心跳管理'),
  ('charging-network', 2, '充电启停可靠性', '启停指令的可靠送达与状态确认'),
  ('charging-network', 3, '分时计费', '尖峰平谷电价+服务费的计费引擎'),
  ('charging-network', 4, '有序充电调度', '台区容量约束下的功率分配'),
  ('charging-network', 5, '站点运营分析', '利用率/故障率的运营指标'),
  ('charging-network', 6, '私桩共享', '私桩共享的分账与权限设计'),
  -- transit-payment
  ('transit-payment', 1, '脱机乘车码', '脱机二维码的安全与时效设计'),
  ('transit-payment', 2, '进出站配对', '闸机进出站记录配对与补扣'),
  ('transit-payment', 3, '清分规则', '多线路运营商的分账规则引擎'),
  ('transit-payment', 4, '票款对账', '交易流水与票款的日对账'),
  ('transit-payment', 5, '瞬时刷码压力', '早高峰闸机刷码的高并发应对'),
  ('transit-payment', 6, '异地互通', '跨城市乘车码的互联互通方案'),
  -- game-gateway
  ('game-gateway', 1, '万级长连接网关', 'Netty 网关的连接管理与内存优化'),
  ('game-gateway', 2, '登录鉴权与顶号', 'token 校验与顶号踢下线处理'),
  ('game-gateway', 3, '断线重连恢复', '会话保持与战斗状态恢复'),
  ('game-gateway', 4, '跨服消息路由', '玩家-服-房间的寻址与转发'),
  ('game-gateway', 5, '灰度与合服', '多服数据合并的迁移方案'),
  ('game-gateway', 6, '心跳与弱网优化', '心跳策略与弱网下的重连体验'),
  -- rank-leaderboard
  ('rank-leaderboard', 1, 'ZSET 排行方案', '同分排序与榜外排名的查询设计'),
  ('rank-leaderboard', 2, '分数更新风暴', '副本结算瞬时更新的削峰方案'),
  ('rank-leaderboard', 3, '好友榜计算', '好友圈子的榜单计算与缓存'),
  ('rank-leaderboard', 4, '赛季结算', '定时结算与奖励发放的可靠性'),
  ('rank-leaderboard', 5, '防刷分', '异常分数的识别与回档处理'),
  ('rank-leaderboard', 6, '多榜单架构', '总榜/周榜/活动榜的统一设计'),
  -- match-server
  ('match-server', 1, '分段匹配池', '段位+延迟多维的匹配算法'),
  ('match-server', 2, '房间生命周期', '房间创建/销毁与状态同步'),
  ('match-server', 3, '帧同步校验', '指令广播与一致性校验'),
  ('match-server', 4, '对局重连', '对局中断线的恢复策略'),
  ('match-server', 5, '机器人填充', '低段位机器人策略的权衡'),
  ('match-server', 6, '匹配等待体验', '预估等待时间与匹配扩大策略'),
  -- game-gm-platform
  ('game-gm-platform', 1, '邮件发放系统', '全服/定向邮件的发放与领取'),
  ('game-gm-platform', 2, '多维封禁体系', '账号/设备/IP 的封禁与解封'),
  ('game-gm-platform', 3, '补偿精准投放', '事故补偿的筛选与幂等发放'),
  ('game-gm-platform', 4, 'GM 操作审计', '敏感操作的全链路留痕'),
  ('game-gm-platform', 5, '玩家画像查询', '跨库玩家数据的聚合查询'),
  ('game-gm-platform', 6, '公告推送', '滚动公告与定点弹窗的管理'),
  -- game-payment
  ('game-payment', 1, '渠道回调验签', '多渠道 SDK 回调的验签与去重'),
  ('game-payment', 2, '发货幂等与补单', '掉单补发的最终一致性'),
  ('game-payment', 3, '虚拟货币账本', '钻石/金币的流水账设计'),
  ('game-payment', 4, '退款套利风控', '退款套利与黑产充值的识别'),
  ('game-payment', 5, '渠道对账', '渠道账单与发货流水的核对'),
  ('game-payment', 6, '礼包与限购', '商品上架/限购/首充翻倍的规则'),
  -- saas-tenant
  ('saas-tenant', 1, '租户隔离方案对比', '独库/共享表/行级隔离的取舍'),
  ('saas-tenant', 2, '租户上下文路由', '请求到租户的解析与上下文透传'),
  ('saas-tenant', 3, '套餐与功能开关', '套餐订阅与功能开关的控制'),
  ('saas-tenant', 4, '用量配额控制', '存储/调用量配额的超限处理'),
  ('saas-tenant', 5, '租户生命周期', '试用/到期/注销的数据处理'),
  ('saas-tenant', 6, '租户数据导出', '数据迁移与注销时的导出'),
  -- crm-platform
  ('crm-platform', 1, '公海池规则', '线索回收规则与抢占的并发控制'),
  ('crm-platform', 2, '商机漏斗', '商机阶段流转与胜率统计'),
  ('crm-platform', 3, '数据可见性权限', '销售/主管/管理员的数据权限'),
  ('crm-platform', 4, '跟进提醒', '跟进计划的定时提醒触达'),
  ('crm-platform', 5, '大批量导入', '客户数据的异步导入与查重'),
  ('crm-platform', 6, '客户查重', '手机号/企业维度的重复客户识别'),
  -- bi-report-engine
  ('bi-report-engine', 1, '数据集 SQL 模型', '参数化 SQL 模型与注入防护'),
  ('bi-report-engine', 2, '聚合下推优化', '千万级明细的聚合查询下推'),
  ('bi-report-engine', 3, '大报表异步导出', '异步导出与断点续传设计'),
  ('bi-report-engine', 4, '定时报表订阅', '报表生成的调度与推送'),
  ('bi-report-engine', 5, '行列级数据权限', '行级/列级权限的查询改写'),
  ('bi-report-engine', 6, '图表组件化', '前端图表渲染与大数据量降采样'),
  -- workflow-engine
  ('workflow-engine', 1, '流程模型存储', 'BPMN 子集的建模与 JSON 存储'),
  ('workflow-engine', 2, '条件路由表达式', '表达式引擎的选择与安全沙箱'),
  ('workflow-engine', 3, '会签或签完成条件', '并行网关的完成条件判定'),
  ('workflow-engine', 4, '代理与委托', '流程代理/委托的变体实现'),
  ('workflow-engine', 5, '流程版本管理', '流程升级对在途实例的影响处理'),
  ('workflow-engine', 6, '可视化设计器', '拖拽设计器与后端模型的映射'),
  -- openapi-gateway
  ('openapi-gateway', 1, '签名鉴权与防重放', 'appKey 签名算法与时间戳防重放'),
  ('openapi-gateway', 2, '应用级限流', '令牌桶限流的分布式实现'),
  ('openapi-gateway', 3, '调用计费出账', '按调用量计费的统计与出账'),
  ('openapi-gateway', 4, 'API 版本共存', '版本共存与废弃策略'),
  ('openapi-gateway', 5, '开发者门户', '文档/调试台/密钥管理'),
  ('openapi-gateway', 6, '网关架构', '过滤器链与插件化的网关设计')
) AS v(project_slug, sort, point, detail)
JOIN project p ON p.slug = v.project_slug;
