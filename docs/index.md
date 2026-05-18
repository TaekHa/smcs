# SMCS Documentation Index

마스터 문서 인덱스. 모놀리식 원본 문서는 그대로 유지하고, 샤딩본을 작업에 사용한다.

## Core Documents

| 문서 | 모놀리식 원본 | 샤딩 인덱스 |
|---|---|---|
| Product Requirements | [PRD.md](./PRD.md) | [prd/index.md](./prd/index.md) |
| Architecture | [ARCHITECTURE.md](./ARCHITECTURE.md) | [architecture/index.md](./architecture/index.md) |
| Front-End Spec (UI/UX) | [FRONT-END-SPEC.md](./FRONT-END-SPEC.md) | [front-end-spec/index.md](./front-end-spec/index.md) |

## Architecture Decision Records

- [adr/README.md](./adr/README.md)
- ADR-001 monolith architecture
- ADR-002 zero external dependency
- ADR-003 exif strip on upload
- ADR-004 hmac search hash
- ADR-005 30s polling notifications

## Stories

작업 단위 스토리는 `docs/stories/{epicNum}.{storyNum}.story.md` 패턴으로 작성한다.

| Epic | 스토리 파일 (생성 예정) |
|---|---|
| Epic 1: Foundation & Auth | 1.1 ~ 1.5 |
| Epic 2: Issue Core Workflow | 2.1 ~ 2.7 |
| Epic 3: Auto Reports & Dashboard | 3.1 ~ 3.5 |
| Epic 4: Polish & Production Readiness | 4.1 ~ 4.7 |

각 Epic의 정의는 [docs/prd/](./prd/) 의 `epic-{n}-*.md` 파일을 참조.

## BMAD 핵심 샤드 매핑

`create-next-story` task가 가정하는 BMAD 기본 파일명과 본 프로젝트 실제 파일의 매핑 (참조 시 활용):

| BMAD 기본 경로 | 본 프로젝트 실제 경로 |
|---|---|
| `architecture/tech-stack.md` | `prd/4-technical-assumptions.md` (4.3 기술 스택) |
| `architecture/unified-project-structure.md` | `architecture/4-monorepo-directory-structure.md` |
| `architecture/coding-standards.md` | `prd/9-coding-standards.md` |
| `architecture/testing-strategy.md` | `prd/4-technical-assumptions.md` (4.4 Testing Requirements) |
| `architecture/data-models.md` | `prd/5-data-models.md`, `architecture/5-data-architecture.md` |
| `architecture/database-schema.md` | `architecture/5-data-architecture.md` |
| `architecture/backend-architecture.md` | `architecture/3-container-architecture-c4-level-2.md` |
| `architecture/rest-api-spec.md` | `prd/6-rest-api-spec-요약.md`, `architecture/7-api-design.md` |
| `architecture/external-apis.md` | `prd/8-external-apis.md` |
| `architecture/frontend-architecture.md` | `architecture/9-frontend-architecture.md` |
| `architecture/components.md` | `prd/7-components.md` |
| `architecture/core-workflows.md` | `architecture/8-sequence-flows.md` |
| `architecture/ui-ux-spec.md` | `front-end-spec/index.md` (및 하위 샤드들) |
