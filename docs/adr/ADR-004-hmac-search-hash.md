# ADR-004: HMAC 해시 컬럼으로 발신자 전화번호 검색

- **Status:** Accepted
- **Date:** 2026-05-15
- **Author:** Architect Winston (사용자 결정 반영)
- **관련 문서:** ARCHITECTURE §5.3, §6.4, §6.5, PRD FR15 / NFR2

## Context

PRD는 다음을 동시에 요구한다:

- **FR15:** 이슈 검색은 제목/내용/발신자명/전화번호 부분일치 지원
- **NFR2:** 발신자 이름/전화번호는 암호화 저장

암호화된 컬럼은 평문 검색이 불가능하다. 검색 가능 암호화(searchable encryption) 방식을 결정해야 한다.

## Decision

발신자 **전화번호에 한해 HMAC-SHA256 해시 컬럼을 추가**하여 정확 매칭 검색을 지원한다.

구체적으로:

- 평문 저장 없음 (`caller_phone_enc`: AES-GCM 암호화 바이트)
- 검색 해시 컬럼: `caller_phone_hash CHAR(64)` — HMAC-SHA256(normalized_phone, key) 의 hex
- 정규화: 숫자만 추출 (예: `010-1234-5678` → `01012345678`)
- HMAC 키는 AES 키와 **분리**된 별도 환경변수 `SMCS_HMAC_KEY`
- 검색 시: 사용자 입력 → 동일 정규화 → HMAC → `WHERE caller_phone_hash = ?`

**발신자 이름은 검색 불가** (MVP). 평문 노출 위험과 부분 매칭 필요성을 고려하여 v2로 미룬다.

## Consequences

### 긍정적
- ✅ 정확 매칭 검색 가능 — 동일 발신자의 이전 이슈 추적
- ✅ DB에 평문 노출 0
- ✅ 인덱스 활용 (`caller_phone_hash` 단일 인덱스)
- ✅ 단순 무지개 테이블 공격 차단 (HMAC 키 모르면 사전 계산 불가)

### 부정적
- ⚠️ 부분 매칭 불가 — "1234"로 끝나는 번호 검색 불가능
- ⚠️ HMAC 키 유출 시 모든 전화번호 사전 공격 가능 (단, 평문은 여전히 AES로 보호됨)
- ⚠️ 키 회전 시 모든 해시 재계산 필요 (배치 작업)

### 키 관리
- `SMCS_DATA_KEY` (AES 컬럼 암호화) ≠ `SMCS_HMAC_KEY` (검색 해시) — 분리 보관
- 키 회전 절차: 새 키 추가 → 기존 데이터에 새 키 해시 추가 컬럼 → 마이그레이션 → 기존 컬럼 drop. OPERATIONS.md에 상세 절차

### 진화 경로
- v2: 발신자 이름 검색 필요 시 같은 방식(n-gram HMAC 또는 정확 매칭) 적용 검토
- v2: 부분 매칭 필요해지면 OPE(Order-Preserving Encryption) 또는 검색 가능 암호화 라이브러리(CipherSweet 등) 평가

## Alternatives Considered

### 대안 1: 평문 저장 (암호화 X)
- ❌ 기각 — NFR2 위반. 개인정보 최소화 원칙 위배

### 대안 2: SHA-256 (no HMAC)
- ❌ 기각 — 키 없는 해시는 무지개 테이블 공격 가능. 전화번호 키 공간이 작아 사전 공격 쉬움 (10⁸ ~ 10¹⁰)

### 대안 3: AES 평문 검색 (every row decrypt)
- ❌ 기각 — 성능 재앙. 100k 행 검색 시 100k 복호화 호출

### 대안 4: 검색 미지원 (전화번호 검색 포기)
- ❌ 기각 — FR15가 명시. CS 운영에 자주 사용 (동일 발신자 이력 조회)

### 대안 5: 와일드카드 검색 가능 라이브러리 (CipherSweet 등)
- ❌ 기각 — MVP에 과한 복잡도. 정확 매칭만으로 80% 유스케이스 커버
