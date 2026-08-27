# docker-compose(루트)에서 전체 스택을 한 번에 띄울 때 사용하는 이미지.
# 로컬 개발 시 npm run dev를 그대로 써도 되며, 이 파일은 그 방식을 대체하지 않는다.
FROM node:20-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# NEXT_PUBLIC_* 값은 next build 시점에 번들에 그대로 박히므로 빌드 인자로 받는다.
# 브라우저가 직접 Backend를 호출하는 구조라(BFF 없음) 기본값도 호스트에서 접속하는 주소다.
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
RUN npm run build

FROM node:20-slim
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/package.json /app/package-lock.json ./
RUN npm ci --omit=dev
COPY --from=build /app/.next ./.next
COPY --from=build /app/public ./public
EXPOSE 3000
CMD ["npm", "run", "start"]
