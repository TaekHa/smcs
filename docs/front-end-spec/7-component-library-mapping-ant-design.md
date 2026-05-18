# 7. Component Library Mapping (Ant Design)

| 디자인 요소 | Ant Design 컴포넌트 | 커스터마이징 |
|------------|---------------------|--------------|
| 페이지 레이아웃 | `<Layout>`, `<Layout.Header>`, `<Layout.Content>` | 헤더 흰색 + 그림자 |
| 사이드 메뉴 (없음 — 헤더만) | - | - |
| 이슈 리스트 테이블 | `<Table>` | 우선순위 행 좌측 색상 막대 (커스텀 cellRender) |
| 등록 폼 | `<Form>` + `<Form.Item>` + `<Input>`, `<Select>`, `<Radio.Group>`, `<TextArea>` | - |
| 카테고리 트리 모달 | `<Modal>` + `<Radio.Group>` 3개 또는 `<Tree>` | 커스텀 — 3열 가로 배치 |
| 우선순위 뱃지 | `<Tag>` 커스텀 | PriorityBadge 컴포넌트 (ARCH §9.6.1) |
| 상태 뱃지 | `<Tag>` 또는 `<Steps>` (진행 바) | StatusBadge 컴포넌트 |
| 모바일 카드 | `<Card>` | 좌측 색상 막대 추가 (CSS `border-left: 8px solid {color}`) |
| 사진 갤러리 | `<Image>` + `<Image.PreviewGroup>` | 클릭 시 풀스크린 자동 |
| 사진 업로드 | `<Upload>` | 카메라 직접 호출은 `<input accept="image/*" capture>` 추가 |
| 활동 로그 | `<Timeline>` | 아이콘별 색상 |
| 코멘트 입력 | `<Input.TextArea>` + 우측 버튼 | - |
| 알림 드롭다운 | `<Dropdown>` + `<Badge>` | 펄스 애니메이션 CSS |
| KPI 카드 | `<Statistic>` | 화살표 + 전일 대비 색상 |
| 차트 | `<Column>`, `<Line>` (`@ant-design/charts`) | - |
| 빈 상태 | `<Empty>` | 한국어 메시지 + 액션 버튼 |
| 모달 | `<Modal>` | confirm: `<Modal.confirm>` |
| 토스트 | `<message>` 또는 `<notification>` | 자동 사라짐 3초 |
| 페이지네이션 | `<Pagination>` | - |
| 검색 입력 | `<Input.Search>` | 디바운스 300ms |
| 필터 드롭다운 | `<Select>` mode="multiple" | - |
| 날짜 범위 | `<DatePicker.RangePicker>` | dayjs 사용 |
| 사용자 선택 | `<Select>` showSearch | UserSelect 컴포넌트 (ARCH §9.6.4) |
| Skeleton 로딩 | `<Skeleton>`, `<Skeleton.Image>` | - |

---
