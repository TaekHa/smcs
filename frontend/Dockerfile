# syntax=docker/dockerfile:1.7
# Story 4.6 — Frontend container image (multi-stage build).
# Builder produces the Vite bundle; runtime serves it via nginx (alpine).
# This nginx serves static assets only — SSL termination and /api proxy live
# in the external nginx container (Deviation #2, option B: split nginx).

# ---- Stage 1: builder (Node 20 + Vite) ----
FROM node:20-alpine AS builder
WORKDIR /app

# Install deps from lockfile first so source edits don't bust the cache.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# Build the production bundle (tsc -b && vite build -> dist/).
COPY tsconfig.json vite.config.ts index.html ./
COPY src ./src
RUN npm run build

# ---- Stage 2: runtime (nginx alpine) ----
FROM nginx:1.27-alpine

ENV TZ=Asia/Seoul
RUN apk add --no-cache tzdata wget \
 && cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
 && echo "Asia/Seoul" > /etc/timezone

# SPA fallback + asset cache config.
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
RUN rm -f /etc/nginx/conf.d/default.conf.bak

COPY --from=builder /app/dist /usr/share/nginx/html

# Used by docker compose healthcheck + external nginx upstream — AC5.
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
