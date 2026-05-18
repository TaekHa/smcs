import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { NotFoundView } from './NotFoundView';

function renderApp() {
  return render(
    <MemoryRouter initialEntries={['/does-not-exist']}>
      <Routes>
        <Route path="/" element={<div>Home Redirect</div>} />
        <Route path="*" element={<NotFoundView />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('NotFoundView', () => {
  it('renders 404 title', () => {
    renderApp();
    expect(screen.getByText('페이지를 찾을 수 없습니다')).toBeInTheDocument();
  });

  it('navigates to / when action button clicked', async () => {
    const user = userEvent.setup();
    renderApp();
    await user.click(screen.getByRole('button', { name: '홈으로' }));
    expect(screen.getByText('Home Redirect')).toBeInTheDocument();
  });
});
