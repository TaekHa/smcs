import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { ForbiddenView } from './ForbiddenView';

function renderApp() {
  return render(
    <MemoryRouter initialEntries={['/403']}>
      <Routes>
        <Route path="/403" element={<ForbiddenView />} />
        <Route path="/" element={<div>Home Redirect</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('ForbiddenView', () => {
  it('renders 403 title and subtitle', () => {
    renderApp();
    expect(screen.getByText('권한이 없습니다')).toBeInTheDocument();
    expect(screen.getByText(/접근할 수 있는 권한이 없습니다/)).toBeInTheDocument();
  });

  it('navigates to / when action button clicked', async () => {
    const user = userEvent.setup();
    renderApp();
    await user.click(screen.getByRole('button', { name: '내 메인 화면으로' }));
    expect(screen.getByText('Home Redirect')).toBeInTheDocument();
  });
});
