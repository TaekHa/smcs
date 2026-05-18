# 8. Sequence Flows

## 8.1 로그인 흐름

```mermaid
sequenceDiagram
    participant U as Browser
    participant N as Nginx
    participant B as Backend
    participant DB as PostgreSQL

    U->>N: POST /api/auth/login {username,password}
    N->>B: forward
    B->>DB: SELECT * FROM users WHERE username=? AND active=true
    DB-->>B: user row
    B->>B: BCrypt.matches(password, hash)
    alt 일치
        B->>B: JWT 생성 (8h)
        B->>DB: INSERT into login_audit (성공)
        B-->>U: 200 { token, user }
    else 불일치
        B->>DB: INSERT into login_audit (실패) + 카운터 증가
        B-->>U: 401 { code:"INVALID_CREDENTIALS" }
    end
    Note over U: localStorage.setItem("smcs.token", token)
    U->>N: GET /api/me (Authorization: Bearer ...)
    N->>B: forward
    B->>B: JwtFilter 검증
    B-->>U: 200 { user }
```

## 8.2 이슈 등록 + 알림 트리거 (배정 포함 시)

```mermaid
sequenceDiagram
    participant U as CS Agent
    participant B as Backend
    participant DB as PostgreSQL

    U->>B: POST /api/issues { title, callerPhone, ... , assigneeId? }
    B->>B: 권한 확인 (AGENT/ADMIN)
    B->>B: 카테고리 자동 제안 적용 (입력 없을 때만)
    B->>B: AES-GCM 암호화 (callerName, callerPhone)
    B->>B: HMAC 해시 계산 (callerPhone → callerPhoneHash)
    rect rgb(240, 240, 255)
    Note over B,DB: 단일 트랜잭션
    B->>DB: INSERT issues
    B->>DB: INSERT issue_events(CREATED)
    alt assigneeId 있음
        B->>DB: UPDATE issues SET assigned_to=?, status='ASSIGNED'
        B->>DB: INSERT issue_events(ASSIGNED)
        B->>DB: INSERT notifications(recipient=assignee, kind=ISSUE_ASSIGNED)
    end
    end
    B-->>U: 201 { issue }
```

## 8.3 이미지 업로드 (EXIF 스트립 포함)

```mermaid
sequenceDiagram
    participant M as Mobile Browser
    participant N as Nginx
    participant B as Backend
    participant FS as Local FS
    participant DB as PostgreSQL

    M->>M: Canvas로 클라이언트 리사이즈 (max 1920px)
    M->>N: POST /api/issues/{id}/attachments (multipart)
    N->>B: forward (proxy_request_buffering on)
    B->>B: 권한 확인 (이슈 접근권한)
    B->>B: 크기/MIME/magic byte 검증
    B->>B: ExifStripper.strip(bytes) → cleanBytes
    B->>FS: write /var/smcs/files/2026/05/{uuid}.jpg
    B->>DB: INSERT attachments
    B-->>M: 201 { attachmentId, url }
    Note over M: 이미지 미리보기 즉시 렌더
```

## 8.4 보고서 자동 생성 스케줄러

```mermaid
sequenceDiagram
    participant S as @Scheduled (08:00 KST)
    participant B as ReportService
    participant DB as PostgreSQL
    participant FS as /var/smcs/reports
    participant N as NotificationService

    S->>B: generateDaily(yesterday)
    B->>B: idempotent 체크 (이미 생성된 보고서 있나)
    B->>DB: 통계 집계 쿼리 (8~10개)
    DB-->>B: aggregated data
    B->>B: PDFBox로 PDF 렌더 (NanumGothic 폰트)
    B->>FS: write report-daily-2026-05-14.pdf
    B->>DB: INSERT reports (kind=DAILY, file_path=...)
    B->>N: notifyAllAdmins("어제 일간 보고서가 준비되었습니다")
    N->>DB: INSERT notifications x (ADMIN 수)
```

## 8.5 알림 Polling (Frontend)

```mermaid
sequenceDiagram
    participant U as User Browser
    participant B as Backend
    participant DB as PostgreSQL

    loop 30초마다 (visible tab only)
        U->>U: document.visibilityState === 'visible' 체크
        U->>B: GET /api/notifications/unread-count
        B->>DB: SELECT COUNT(*) WHERE recipient_id=? AND read_at IS NULL
        DB-->>B: 7
        B-->>U: { count: 7 }
        U->>U: Bell 뱃지 업데이트 (Zustand store)
    end
    Note over U: 사용자가 벨 클릭
    U->>B: GET /api/notifications?page=0&size=10
    B->>DB: SELECT * ORDER BY created_at DESC LIMIT 10
    B-->>U: list
    U->>U: 드롭다운 렌더
    Note over U: 사용자가 알림 클릭
    U->>B: POST /api/notifications/{id}/read
    B->>DB: UPDATE notifications SET read_at=NOW() WHERE id=? AND recipient_id=?
    B-->>U: 204
    U->>U: 라우터: /issues/{issueId}로 이동
```

## 8.6 정적 파일 보안 서빙 (X-Accel-Redirect)

```mermaid
sequenceDiagram
    participant U as Browser
    participant N as Nginx
    participant B as Backend
    participant FS as /var/smcs/files

    U->>N: GET /files/2026/05/abc.jpg (Auth: Bearer ...)
    N->>B: /internal-files/check?path=2026/05/abc.jpg
    B->>B: JWT 검증 + 첨부 권한 확인
    alt 허용
        B-->>N: 204 + X-Accel-Redirect: /protected/2026/05/abc.jpg
        N->>FS: read file
        FS-->>N: bytes
        N-->>U: 200 image/jpeg
    else 거부
        B-->>N: 403
        N-->>U: 403
    end
```

---
