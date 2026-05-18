# 12. Developer Experience

## 12.1 로컬 실행 (목표: 1분 이내)

```bash
# 최초 1회
git clone ... && cd smcs
cp backend/src/main/resources/application-prod.yml.example backend/src/main/resources/application-prod.yml.local

# 매일
docker compose -f docker/docker-compose.local.yml up -d   # postgres만 띄움
cd backend && ./gradlew bootRun --args="--spring.profiles.active=local"
cd frontend && npm run dev   # Vite :5173, /api는 :8080으로 proxy
```

## 12.2 시드 데이터 활용

- `application-local.yml` 프로파일에서 `DataLoader` Bean이 자동 실행
- 8명 사용자 (각 역할별), 20건 다양한 상태/우선순위/카테고리 조합 이슈
- 비밀번호는 모두 `dev1234` (로그에 표시)
- 매 부팅마다 reset (선택)

## 12.3 코드 품질 도구

| 도구 | 적용 |
|------|------|
| Spotless (Java) | google-java-format 자동 적용 |
| ktlint | 미사용 (Java 단일) |
| ESLint + Prettier | 프론트엔드 자동 적용 |
| Pre-commit (선택) | husky + lint-staged |

## 12.4 디버깅

- Backend: IntelliJ + Spring Boot run config
- Frontend: Chrome DevTools + React DevTools
- DB: pgAdmin 또는 DBeaver
- API: REST Client (VSCode 확장) — `.http` 파일을 `docs/api-samples/` 에 보관

---
