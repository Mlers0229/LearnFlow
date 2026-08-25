ARG FRONTEND_BUILD_IMAGE=node:20-alpine3.23
ARG FRONTEND_RUNTIME_IMAGE=nginx:1.30.4-alpine

FROM ${FRONTEND_BUILD_IMAGE} AS build

WORKDIR /app

ARG NPM_REGISTRY=https://registry.npmmirror.com
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

COPY frontend/package*.json ./
RUN npm ci --registry="${NPM_REGISTRY}"

COPY frontend .
RUN npm run build

FROM ${FRONTEND_RUNTIME_IMAGE}

COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

RUN sed -i 's#http://backend:18081#http://127.0.0.1:18081#' /etc/nginx/conf.d/default.conf \`n    && nginx -t \`n    && sed -i 's#http://127.0.0.1:18081#http://backend:18081#' /etc/nginx/conf.d/default.conf

RUN chown -R nginx:nginx /var/cache/nginx /var/run /etc/nginx/conf.d

EXPOSE 8080

USER nginx

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --quiet --output-document=- http://127.0.0.1:8080/health/live >/dev/null || exit 1
