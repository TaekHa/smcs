# Architecture Decision Records (ADR)

이 폴더는 SMCS 프로젝트의 주요 아키텍처 결정을 기록한다.

## 형식

각 ADR은 다음 구조를 따른다:

- **Title**: ADR-NNN: 결정 요약
- **Status**: Proposed / Accepted / Deprecated / Superseded by ADR-XXX
- **Date**: YYYY-MM-DD
- **Context**: 결정이 필요한 배경
- **Decision**: 무엇을 결정했는가
- **Consequences**: 결과 (긍정/부정/트레이드오프)
- **Alternatives Considered**: 검토된 대안과 기각 사유

## 색인

| # | 제목 | 상태 | 일자 |
|---|------|------|------|
| [ADR-001](./ADR-001-monolith-architecture.md) | Monolith 아키텍처 채택 | Accepted | 2026-05-15 |
| [ADR-002](./ADR-002-zero-external-dependency.md) | 외부 의존성 0 원칙 | Accepted | 2026-05-15 |
| [ADR-003](./ADR-003-exif-strip-on-upload.md) | 이미지 업로드 시 EXIF 자동 스트립 | Accepted | 2026-05-15 |
| [ADR-004](./ADR-004-hmac-search-hash.md) | HMAC 해시 컬럼으로 발신자 전화번호 검색 | Accepted | 2026-05-15 |
| [ADR-005](./ADR-005-30s-polling-notifications.md) | 30초 Polling 기반 인앱 알림 | Accepted | 2026-05-15 |

## 작성 가이드

- 새 결정이 생기면 가장 큰 번호 +1로 새 파일 생성
- 기존 ADR을 뒤집을 때는 기존을 `Superseded by ADR-XXX`로 표시하고 새 ADR을 만든다 (수정하지 않는다 — 역사 보존)
- 사소한 결정은 ADR이 아닌 코드 코멘트로 충분
