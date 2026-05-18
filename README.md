# SMCS

Spring Boot 백엔드 + React 프론트엔드 모노레포.

## 사전 요구사항

- JDK 21 LTS (`JAVA_HOME` 설정)
- Node.js 20 LTS
- Docker (Docker Desktop 또는 동등)

## 최초 1회 셋업

```bash
cp backend/src/main/resources/application-prod.yml.example backend/src/main/resources/application-prod.yml.local
# application-prod.yml.local 의 placeholder 를 실제 값으로 채운다 (커밋 금지).
```

## 매일 실행 순서

```bash
# 1) PostgreSQL 컨테이너 기동
docker compose -f docker/docker-compose.local.yml up -d postgres

# 2) 백엔드 (포트 8080)
cd backend && ./gradlew bootRun --args="--spring.profiles.active=local"

# 3) 프론트엔드 (포트 5173)
cd frontend && npm install && npm run dev
```

## 동작 검증

백엔드 헬스체크:

```bash
curl http://localhost:8080/api/health   # → {"status":"UP"}
```

프론트엔드: 브라우저에서 `http://localhost:5173` 접속 → SMCS 로그인 화면 표시. 시드 사용자(예: `agent1/dev1234`) 로 로그인 → 역할별 메인 화면(`/issues` 또는 `/m`) 으로 자동 이동.

시드 사용자(local profile 한정, 모든 비밀번호 = `dev1234`):

| Username | Role |
|---|---|
| agent1 / agent2 / agent3 | AGENT |
| field1 / field2 / field3 / field4 | FIELD |
| admin1 | ADMIN |

## 포트

| 서비스 | 포트 |
|---|---|
| PostgreSQL | 5432 |
| Backend | 8080 |
| Frontend (Vite dev) | 5173 |

호스트의 5432 포트가 사용 중이면 PostgreSQL 컨테이너 기동이 실패한다.
