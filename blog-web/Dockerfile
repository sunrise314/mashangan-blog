# Nuxt Nitro 构建产物 .output 自包含运行时，无需在镜像内再次安装依赖
FROM node:20-alpine
WORKDIR /app

# 以非 root 用户运行，缩小容器失陷后的影响面
RUN addgroup -S app && adduser -S app -G app
COPY --chown=app:app .output .output
USER app

ENV NODE_ENV=production \
    NUXT_HOST=0.0.0.0 \
    NUXT_PORT=3000 \
    NUXT_PUBLIC_HALO_API_BASE=http://halo:8090
EXPOSE 3000
CMD ["node", ".output/server/index.mjs"]
