# mashangan — Repository Guidelines

This workspace hosts the **mashangan.com blog**. Legacy Halo CMS was retired 2026-09-27; its source is gone from this repo — do not reference or recreate it. The old `halo` container was **deleted from the server on 2026-09-30** (compose service kept under `profiles: ["legacy"]`, image + data preserved). The `/studio` 配图流水线 v1 (via `blog-web/server/utils/halo.ts`) is therefore dead — do not invoke or extend it; new-article publishing goes through direct SQL scripts in `_md2html/` until pipeline v2 (blog-api direct) exists.

## Modules

|       Module        | Path                          | Tech & Notes                                                                      |
|---------------------|-------------------------------|-----------------------------------------------------------------------------------|
| **Frontend**        | `web/`                        | Nuxt 3 site source. Deployed as the `halo-web` container on server `49.235.136.65`. |
| **Backend API**     | `blog-api/`                   | Spring Boot 4 / Java 21 / MyBatis-Plus / Flyway. Includes embedded admin console (`admin/`). |
| Ops scripts         | `_md2html/` (gitignored)      | Deployment/diagnostic scripts with real server credentials — never commit.          |
| Image artifacts     | `_img_293/`, `_img_294_clean/`, `_img_wm_clean5/` | Final versions of processed article images.                                         |
| Docs                | `AI文章配图工具 MVP PRD.md`   | AI image pipeline design doc.                                                      |

## Quick Commands

```powershell
# Frontend
cd web; pnpm install; pnpm dev    # local dev
cd web; pnpm build                 # production build → .output/

# Backend
cd blog-api; ./gradlew build       # compile + test
cd blog-api; ./gradlew bootRun     # local run (uses application.yml dev defaults)
cd blog-api; ./gradlew flywayMigrate  # run migrations only
```

## Deployment (server 49.235.136.65, user ubuntu)

- **halo-web**: `pnpm build` in `web/` → tar `.output/` → upload to `/home/ubuntu/halo-web` → `docker build` → `docker compose up -d --force-recreate --no-deps halo-web`.
- **blog-api**: edit locally → SFTP changed files to `/home/ubuntu/blog-api` → `nohup docker build` (never pipe/tail) → force-recreate.
- After recreating `halo-web`, its container IP may change — `docker kill -s HUP halo-nginx` to re-resolve, otherwise 502.
- Nuxt routeRules SWR cache: home 60s, posts 3600s — `docker restart halo-web` before verifying DB-driven changes.
- Multi-line server config edits: SFTP download → edit with Python → upload back. Validate nginx with `docker exec halo-nginx nginx -t` before reload.

## Hard Rules

- Never touch server-side `halo2/attachments/` (blog-api mounts it) or the `halo-db` postgres container.
- Page-internal `route.params`/`route.query` in `web/` must be read via `computed`/ref — plain destructuring breaks client-side navigation.
- New `site_config` fields must be added to the whitelist in `web/server/api/site-config.get.ts`.
- Never upload watermarked images; authors write in first person as "我".
- Do not commit `_md2html/`, `.trae/`, or server credentials. `application.yml` dev fallback creds are safe to commit — production values come from env vars.
