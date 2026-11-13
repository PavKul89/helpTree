FROM node:22.16.0-alpine3.22 AS build

WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

ARG REACT_APP_API_URL=
ARG REACT_APP_WS_URL=
ENV REACT_APP_API_URL=${REACT_APP_API_URL}
ENV REACT_APP_WS_URL=${REACT_APP_WS_URL}

COPY frontend/ ./
RUN npm run build

FROM nginx:1.29.1-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/build /usr/share/nginx/html

EXPOSE 80
