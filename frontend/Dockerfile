FROM node:20-alpine3.23 AS build

WORKDIR /app

ARG NPM_REGISTRY=https://registry.npmmirror.com
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

COPY frontend/package*.json ./
RUN npm ci --registry="${NPM_REGISTRY}"

COPY frontend .
RUN npm run build

FROM nginx:1.30.4-alpine

COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

RUN chown -R nginx:nginx /var/cache/nginx /var/run /etc/nginx/conf.d

EXPOSE 8080

USER nginx
