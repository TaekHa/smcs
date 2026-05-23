import { useState } from 'react';
import { Card, Col, Empty, Row, Segmented, Spin, Statistic, Typography } from 'antd';
import { Column, Line } from '@ant-design/charts';
import { useDashboardStats } from '../../shared/hooks/useDashboardStats';
import type { DashboardStats, StatsPeriod } from '../../types/stats';

const { Title, Text } = Typography;

const PERIOD_OPTIONS: { label: string; value: StatsPeriod }[] = [
  { label: '오늘', value: 'today' },
  { label: '이번주', value: 'week' },
  { label: '이번달', value: 'month' },
];

const PERIOD_LABEL: Record<StatsPeriod, string> = {
  today: '오늘',
  week: '이번주',
  month: '이번달',
};

function formatMinutes(minutes: number): string {
  if (!minutes || minutes <= 0) return '0분';
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  if (hours === 0) return `${mins}분`;
  return mins === 0 ? `${hours}시간` : `${hours}시간 ${mins}분`;
}

function KpiCards({ stats, period }: { stats: DashboardStats; period: StatsPeriod }) {
  const periodWord = PERIOD_LABEL[period];
  return (
    <Row gutter={[16, 16]}>
      <Col xs={12} md={6}>
        <Card>
          <Statistic title={`${periodWord} 신규`} value={stats.kpi.newCount} suffix="건" />
        </Card>
      </Col>
      <Col xs={12} md={6}>
        <Card>
          <Statistic title={`${periodWord} 처리`} value={stats.kpi.resolvedCount} suffix="건" />
        </Card>
      </Col>
      <Col xs={12} md={6}>
        <Card>
          {/* AC1 — openCount is always the current snapshot, period-independent. */}
          <Statistic title="미처리 총건" value={stats.kpi.openCount} suffix="건" />
          <Text type="secondary" style={{ fontSize: 12 }}>현재 시점 기준</Text>
        </Card>
      </Col>
      <Col xs={12} md={6}>
        <Card>
          <Statistic
            title={`${periodWord} 평균 처리시간`}
            value={formatMinutes(stats.kpi.avgResolveMinutes)}
          />
        </Card>
      </Col>
    </Row>
  );
}

function CategoryChart({ data }: { data: DashboardStats['byCategory'] }) {
  if (data.length === 0) return <Empty description="카테고리 데이터가 없습니다" />;
  return (
    <Column
      data={data}
      xField="name"
      yField="count"
      height={260}
      label={{ position: 'top' }}
      legend={false}
    />
  );
}

function AssigneeChart({ data }: { data: DashboardStats['byAssignee'] }) {
  if (data.length === 0) return <Empty description="담당자 데이터가 없습니다" />;
  return (
    <Column
      data={data}
      xField="name"
      yField="resolved"
      height={260}
      label={{ position: 'top' }}
      legend={false}
    />
  );
}

function TrendChart({ data }: { data: DashboardStats['trend'] }) {
  if (data.length === 0) return <Empty description="추세 데이터가 없습니다" />;
  // Flatten to (date, type, count) for a 2-series line ("신규" vs "처리").
  const series = data.flatMap((p) => [
    { date: p.date, type: '신규', count: p.newCount },
    { date: p.date, type: '처리', count: p.resolvedCount },
  ]);
  return (
    <Line
      data={series}
      xField="date"
      yField="count"
      colorField="type"
      height={260}
      legend={{ color: { position: 'top' } }}
    />
  );
}

/** ADMIN-only dashboard (Story 3.2). All chart libs live inside the lazy /dashboard chunk. */
export function DashboardView() {
  const [period, setPeriod] = useState<StatsPeriod>('today');
  const { data, isLoading, isError } = useDashboardStats(period);

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>
          대시보드
        </Title>
        <Segmented<StatsPeriod>
          options={PERIOD_OPTIONS}
          value={period}
          onChange={(v) => setPeriod(v as StatsPeriod)}
          aria-label="기간 필터"
        />
      </div>

      {isLoading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
          <Spin size="large" />
        </div>
      ) : isError || !data ? (
        <Empty description="대시보드 데이터를 불러오지 못했습니다" />
      ) : (
        <>
          <KpiCards stats={data} period={period} />
          <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
            <Col xs={24} lg={12}>
              <Card title="카테고리별 신규">
                <CategoryChart data={data.byCategory} />
              </Card>
            </Col>
            <Col xs={24} lg={12}>
              <Card title="담당자별 처리량">
                <AssigneeChart data={data.byAssignee} />
              </Card>
            </Col>
            <Col xs={24}>
              <Card title="일자별 추세 (신규 vs 처리)">
                <TrendChart data={data.trend} />
              </Card>
            </Col>
          </Row>
        </>
      )}
    </div>
  );
}
