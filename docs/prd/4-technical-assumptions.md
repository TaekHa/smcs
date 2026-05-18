# 4. Technical Assumptions

## 4.1 Repository Structure

- **Monorepo** (단일 저장소): 백엔드/프론트엔드/공유 타입을 한 곳에서 관리. 1인 개발이므로 폴리레포 분리는 불필요한 오버헤드.
- 디렉터리 구조 예시:
  ```
  smcs/
  ├── backend/        # Spring Boot
  ├── frontend/       # React + TS
  ├── shared/         # 공유 타입 정의 (선택)
  ├── docs/           # 문서 (PRD, 아키텍처 등)
  └── docker/         # 배포 설정
  ```

## 4.2 Service Architecture

- **Monolith (단일 배포 단위)**: 1개월 MVP + 1인 개발에서 마이크로서비스는 불필요한 복잡도. Spring Boot 단일 애플리케이션으로 시작.
- 모듈 분리 원칙: 패키지 단위로 `issue`, `user`, `report`, `notification` 등 도메인 경계를 명확히 하여 v2에서 분리 여지 확보.

## 4.3 기술 스택 (확정 — 버전 고정)

> **버전 정책:** 메이저.마이너 버전을 고정하고, 패치는 빌드 시 자동 업데이트를 허용한다(`Spring Boot 3.3.x`, `AntD 5.21.x` 형식). 정확한 시작점은 아래 표를 따른다.

**백엔드 스택**

| 분류 | 선택 | 권장 시작 버전 | 사유 |
|------|------|------|------|
| 언어 | Java | **21 LTS** (21.0.4+) | LTS, 사내 인프라 호환성 |
| 프레임워크 | Spring Boot | **3.3.4** | 안정 라인, Java 21 지원 |
| ORM | Hibernate (Spring Data JPA 번들) | **6.5.x** | Spring Boot 3.3 번들 |
| Validation | Jakarta Validation | **3.0.2** | Bean Validation 표준 |
| 빌드 | Gradle (Kotlin DSL) | **8.10** | 빠른 빌드 |
| 인증 | Spring Security + JJWT | Security 6.3 / **jjwt 0.12.6** | 무상태 JWT |
| Rate Limiting | Bucket4j Spring Boot Starter | **8.10.x** | Spring 통합 용이 |
| DB | PostgreSQL | **16.4** | JSON, 인덱스, GIN |
| Migration | Flyway | **10.17.x** | DB 스키마 버전 관리 |
| PDF | Apache PDFBox | **3.0.3** | 한글 폰트 임베드 |
| 이미지 (EXIF 스트립) | Thumbnailator | **0.4.20** | 메타데이터 자동 제거 |
| 스케줄러 | Spring `@Scheduled` | (번들) | 별도 인프라 불필요 |

**프론트엔드 스택**

| 분류 | 선택 | 권장 시작 버전 | 사유 |
|------|------|------|------|
| 런타임 | Node.js | **20 LTS** (20.18+) | LTS |
| 라이브러리 | React | **18.3.1** | Concurrent, Suspense |
| 언어 | TypeScript | **5.5.x** strict | 타입 안전 |
| 빌드 | Vite | **5.4.x** | esbuild 기반 |
| UI 키트 | **Ant Design** | **5.21.x** | 한국 친화, 폼/테이블 풍부 |
| 라우팅 | React Router | **6.26.x** | 표준 |
| 서버 상태 | TanStack Query | **5.59.x** | 캐싱, 리페치 |
| 클라이언트 상태 | Zustand | **4.5.x** | 가벼움 |
| HTTP | axios | **1.7.x** | 인터셉터 |
| 폼 | React Hook Form + Zod | **7.53** / **3.23** | 검증 일원화 |
| 날짜 | dayjs | **1.11.x** | Ant Design 내장 |
| 차트 | Ant Design Charts | **2.2.x** | Ant 통합 |
| a11y 테스트 | @axe-core/react | **4.10.x** | 자동 a11y 검사 (dev) |

**인프라**

| 분류 | 선택 | 권장 버전 | 사유 |
|------|------|------|------|
| 컨테이너 | Docker / Compose | Docker **27** / Compose v2 | 단순화 |
| 리버스 프록시 | Nginx | **1.27** | 정적 서빙 + TLS |
| 파일 저장 | 로컬 디스크 (Docker volume) | - | 외부 의존 0 |
| 알림 | 인앱 (DB + 30s Polling) | - | 외부 채널 미사용 |
| 모니터링 | Spring Actuator + Logback | (번들) | 외부 APM 미사용 |

> **외부 의존성 0 원칙:** 카카오워크/슬랙/SMTP/SMS/SSO/외부 LLM 등을 일절 사용하지 않으므로, 사내 폐쇄망에서도 완전 동작한다.

## 4.4 Testing Requirements

- **단위 테스트 (Unit):** 핵심 도메인 로직, 보고서 집계 함수, 권한 검증 로직은 필수. JUnit 5 + Mockito.
- **통합 테스트 (Integration):** REST API 주요 엔드포인트(이슈 CRUD, 인증, 상태 전이)는 `@SpringBootTest` + Testcontainers (PostgreSQL).
- **E2E 테스트:** MVP는 **수동 테스트** 중심. 자동화는 v2. 골든 패스 시나리오 문서로 정리.
- **프론트엔드 테스트:** 핵심 컴포넌트(이슈 폼, 리스트)만 React Testing Library로 스모크 테스트. 광범위 커버리지 X.
- **테스트 편의 기능:** Spring Profile `local`에서 시드 데이터 자동 로딩 (CS팀 3명, 현장 5명, 이슈 20건 샘플).

## 4.5 Additional Technical Assumptions and Requests

- **타임존:** 서버는 UTC 저장, 프론트엔드는 KST(Asia/Seoul) 표시. 보고서는 KST 기준 일자 경계.
- **로깅:** SLF4J + Logback, 일별 롤링, 30일 보관.
- **시크릿 관리:** application.yml 분리 (`application-prod.yml`은 Git 미포함, 서버 배포 시 별도 관리).
- **DB 마이그레이션:** Flyway 사용.
- **API 문서:** Spring REST Docs 또는 SpringDoc OpenAPI.
- **CORS:** 동일 도메인 배포 가정(Nginx 리버스 프록시), CORS 설정 최소화.
- **타임아웃:** API 기본 30초.

---
