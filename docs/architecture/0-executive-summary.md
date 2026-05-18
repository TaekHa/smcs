# 0. Executive Summary

SMCS는 **1인 개발 / 1개월 / 사내 폐쇄망 / 70명 사용자** 라는 강한 제약을 가진 내부 CS 이슈 관리 시스템이다. 본 아키텍처는 다음 원칙을 따른다:

1. **Boring Technology 우선** — Spring Boot 3 + PostgreSQL + React + Nginx. 검증된 기술만 사용.
2. **Monolith + Monorepo** — 1인이 풀스택을 운영할 수 있도록 단순화.
3. **External Dependency = 0** — 폐쇄망 운영 가능.
4. **Defense in Depth** — JWT + BCrypt + AES-GCM 컬럼 암호화 + HMAC 검색 해시 + EXIF 스트립.
5. **Living Architecture** — v2에서 SSO, WebSocket, 외부 알림이 추가될 수 있도록 모듈 경계 명확화.

---
