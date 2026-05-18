# 4. Monorepo Directory Structure

```
smcs/
├── README.md                         # 1페이지 셋업/실행 가이드
├── CLAUDE.md                         # 개발 가이드라인 (기존)
├── docs/
│   ├── PRD.md                        # v0.2
│   ├── ARCHITECTURE.md               # 본 문서
│   └── OPERATIONS.md                 # 운영 가이드 (Week 4 작성)
│
├── backend/                          # Spring Boot 모놀리스
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle/
│   └── src/
│       ├── main/
│       │   ├── java/com/smcs/
│       │   │   ├── SmcsApplication.java
│       │   │   ├── config/           # SecurityConfig, JpaConfig, WebMvcConfig
│       │   │   ├── common/           # 예외, ErrorResponse, 페이징, 시간 유틸
│       │   │   ├── security/         # JwtService, JwtFilter, UserDetailsService
│       │   │   ├── crypto/           # AesGcmCipher, HmacHasher, EncryptedString JPA Converter
│       │   │   ├── audit/            # IssueEvent AOP/Listener
│       │   │   │
│       │   │   ├── user/             # User 도메인 (엔티티/리포지토리/서비스/컨트롤러)
│       │   │   ├── auth/             # 로그인/로그아웃 컨트롤러
│       │   │   ├── category/         # 3단계 카테고리
│       │   │   ├── issue/            # Issue 도메인
│       │   │   ├── comment/          # Comment 도메인
│       │   │   ├── attachment/       # Attachment 업로드/저장/EXIF 스트립
│       │   │   ├── notification/     # 인앱 알림
│       │   │   ├── report/           # PDF 생성, 스케줄러, 보관함
│       │   │   └── stats/            # 대시보드 통계 API
│       │   │
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-local.yml
│       │       ├── application-prod.yml.example
│       │       ├── db/migration/      # Flyway: V1__init.sql, V2__seed_categories.sql, ...
│       │       └── fonts/             # NanumGothic.ttf (PDF 한글 임베드)
│       └── test/
│           └── java/com/smcs/...     # 단위 + 통합 테스트 (Testcontainers)
│
├── frontend/                         # React SPA
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── index.html
│   ├── public/
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── routes.tsx
│       │
│       ├── api/                      # apiClient (axios 인스턴스), 도메인별 API 함수
│       │   ├── client.ts
│       │   ├── auth.ts
│       │   ├── issues.ts
│       │   ├── notifications.ts
│       │   └── ...
│       │
│       ├── auth/                     # AuthProvider, useAuth, RequireRole
│       ├── shared/                   # UI 키트(PriorityBadge, StatusBadge), 훅, 유틸
│       ├── features/
│       │   ├── issue-list/
│       │   ├── issue-form/
│       │   ├── issue-detail/
│       │   ├── mobile-field/         # 현장 작업자 모바일 화면
│       │   ├── dashboard/
│       │   ├── reports/
│       │   ├── notifications/        # 벨 아이콘 + 알림 페이지
│       │   └── admin/                # users, categories
│       │
│       └── types/                    # API DTO와 동기화된 TS 타입
│
├── docker/
│   ├── Dockerfile.backend            # multi-stage: gradle build → JRE 21 slim
│   ├── Dockerfile.frontend           # multi-stage: node build → nginx with static
│   ├── docker-compose.yml            # prod
│   ├── docker-compose.local.yml      # local dev (DB만)
│   └── nginx/
│       └── nginx.conf
│
└── scripts/
    ├── backup-db.sh                  # pg_dump cron
    ├── cleanup-old-files.sh          # 90일 지난 보고서/알림 정리
    └── seed-prod-data.sh             # 프로덕션 초기 데이터 (카테고리 등)
```

## 4.1 패키지 분리 원칙 (백엔드)

- **도메인 패키지 단위**로 묶고, 각 도메인은 다음을 가질 수 있다:
  - `entity/` — JPA 엔티티
  - `repository/` — Spring Data 리포지토리
  - `service/` — 비즈니스 로직 (트랜잭션 경계)
  - `controller/` — REST 컨트롤러
  - `dto/` — Request/Response DTO
- **도메인 간 직접 의존은 Service 레이어를 통해서만**. 다른 도메인의 Repository/Entity 직접 접근 금지.
- 예: `comment` 도메인이 `issue`를 참조해야 한다면 `IssueService`를 주입받고 `issueRepository`는 직접 사용하지 않는다.

> 이 규칙이 v2에서 도메인을 별도 서비스로 분리할 때 비용을 크게 줄인다.

## 4.2 프론트엔드 구조 (Feature-Sliced)

- **`features/`** 가 최우선 경계. 각 feature 디렉터리 안에 그 feature의 컴포넌트/훅/타입이 자기완결적으로 존재.
- **`shared/`** 는 두 개 이상의 feature가 사용하는 것만. "공통 같아 보여서" 미리 넣지 않는다(YAGNI).
- API 호출은 항상 `api/` 레이어를 통해서만. 컴포넌트에서 직접 `fetch`/`axios` 호출 금지(PRD §9.1).

---
