#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
为 31 个分类补写原创中文描述（默认分类不动）。
默认 DRY-RUN；正式执行：HALO_PAT=pat_xxx python backfill_categories.py --apply
"""
import argparse
import os
import sys
import time

import requests

HALO_URL = os.environ.get("HALO_URL", "http://49.235.136.65:8090").rstrip("/")
HALO_PAT = os.environ.get("HALO_PAT", "").strip()
EXTENSION_API = "/apis/content.halo.run/v1alpha1"
CONTENT_API = "/apis/api.content.halo.run/v1alpha1"

# slug -> description（原创文案，长度控制在 60~110 字）
DESC = {
    "java-interview": "Java 后端高频面试题系统汇总，覆盖语言基础、集合、并发、JVM、Spring、数据库、中间件与分布式架构，每题附条理化参考答案。",
    "elasticsearch": "Elasticsearch 面试题精讲：倒排索引、分词检索、聚合分析、集群分片与写入查询调优。",
    "high-availability": "高可用架构面试题：限流熔断降级、故障转移、多活容灾与常见线上故障的兜底思路。",
    "high-concurrency": "高并发场景面试题：线程模型、锁优化、无锁化设计、缓存异步化与吞吐量提升实战。",
    "sharding": "分库分表面试题：分片策略、路由扩容、分布式主键、跨片查询与数据一致性方案。",
    "netty": "Netty 面试题：Reactor 线程模型、ByteBuf、零拷贝、编解码与常见内存泄漏排查。",
    "ai-agent": "AI Agent 面试题：智能体架构、工具调用、任务规划、记忆机制与多智能体协作。",
    "rag": "RAG 检索增强生成面试题：向量检索、召回与重排、知识库构建、效果评测与常见坑。",
    "dubbo": "Dubbo 面试题：RPC 原理、注册中心、负载均衡、SPI 扩展与服务治理机制。",
    "os": "操作系统面试题：进程与线程、内存管理、IO 模型、CPU 调度与同步原语。",
    "network": "计算机网络面试题：TCP/IP、HTTP/HTTPS、握手挥手、DNS 与常见网络问题排查。",
    "jvm": "JVM 面试题：内存区域、垃圾回收算法与收集器、类加载机制与线上调优参数。",
    "rabbitmq": "RabbitMQ 面试题：消息可靠性投递、死信队列、镜像队列、消费幂等与积压处理。",
    "zookeeper": "Zookeeper 面试题：数据节点、Watcher 机制、ZAB 协议与分布式锁选举实现。",
    "tomcat": "Tomcat 面试题：整体架构、类加载机制、连接器模型、线程池与性能调优。",
    "spring": "Spring 面试题：IoC 容器、AOP 原理、声明式事务、Bean 生命周期与高频源码。",
    "design-pattern": "设计模式面试题：23 种设计模式的适用场景、实现要点及在主流框架中的真实应用。",
    "java-concurrent": "Java 并发编程面试题：JMM、synchronized 与锁、线程池、AQS 与并发容器。",
    "spring-cloud": "Spring Cloud 微服务面试题：注册中心、网关、配置中心、熔断限流与链路治理。",
    "mybatis": "MyBatis 面试题：动态代理、一二级缓存、动态 SQL、插件机制与批量操作。",
    "rocketmq": "RocketMQ 面试题：消息存储与刷盘、顺序消息、事务消息、重试积压与高可用。",
    "redis": "Redis 面试题：数据结构与底层实现、持久化、主从集群、缓存设计与分布式锁。",
    "mysql": "MySQL 面试题：索引原理、事务隔离、锁机制、SQL 调优、日志体系与分库分表。",
    "java-collection": "Java 集合框架面试题：HashMap、ConcurrentHashMap 等核心容器的结构与源码原理。",
    "java-basic": "Java 基础面试题：语言特性、面向对象、异常体系、泛型、反射与常用 API。",
    "mybatis-plus": "MyBatis-Plus 从入门到实战：条件构造器、通用 CRUD、分页插件、代码生成与多租户。",
    "lombok": "Lombok 使用教程：常用注解与底层原理、IDEA 配置、序列化坑与团队使用规范。",
    "java8": "Java 8 新特性教程：Lambda、Stream API、Optional、新日期时间 API 与函数式编程。",
    "idea": "IntelliJ IDEA 安装配置与高效使用教程，覆盖 Windows、macOS 双系统及常用插件与快捷键。",
    "golang": "Go 语言零基础到进阶教程：基础语法、接口与错误处理、GMP 并发模型与工程实践。",
    "docker": "Docker 容器化实战教程：镜像与容器、Dockerfile、Docker Compose 及常用中间件部署。",
}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    if args.apply and not HALO_PAT:
        print("错误：--apply 需要环境变量 HALO_PAT")
        return 2

    s = requests.Session()
    s.headers.update({"Accept": "application/json"})
    if HALO_PAT:
        s.headers["Authorization"] = f"Bearer {HALO_PAT}"

    cats = s.get(f"{HALO_URL}{CONTENT_API}/categories?size=200", timeout=60).json()["items"]
    updated = skipped = failed = 0
    for c in cats:
        slug = c["spec"]["slug"]
        desc = DESC.get(slug)
        if not desc:
            continue
        name = c["metadata"]["name"]
        old = c["spec"].get("description") or ""
        if old.strip() == desc:
            skipped += 1
            continue
        print(f"- {slug}: {old[:24] or '（空）'} -> {desc[:24]}…")
        if not args.apply:
            updated += 1
            continue
        # 更新前用令牌取完整扩展对象（公开列表缺少部分服务端字段）
        full = s.get(f"{HALO_URL}{EXTENSION_API}/categories/{name}", timeout=60).json()
        full["spec"]["description"] = desc
        r = s.put(f"{HALO_URL}{EXTENSION_API}/categories/{name}", json=full, timeout=60)
        if r.status_code < 300:
            updated += 1
            time.sleep(0.15)
        else:
            failed += 1
            print(f"  更新失败 {r.status_code}: {r.text[:200]}")
    print(f"{'DRY-RUN ' if not args.apply else ''}完成：更新 {updated}，跳过 {skipped}，失败 {failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
