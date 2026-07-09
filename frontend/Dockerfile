ARG DOCKER_REGISTRY=docker.io

FROM ${DOCKER_REGISTRY}/node:20-alpine AS builder

WORKDIR /app

COPY package.json .
COPY package-lock.json .

RUN npm config set registry https://mirrors.cloud.tencent.com/npm/ && npm ci

COPY . .

RUN npm run build

FROM ${DOCKER_REGISTRY}/nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8125

CMD ["nginx", "-g", "daemon off;"]
