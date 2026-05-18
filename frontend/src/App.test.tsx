import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';

vi.mock('./api/me', () => ({
  getMe: () => Promise.reject(new Error('not authenticated')),
}));

import App from './App';

describe('App', () => {
  it('renders login screen when unauthenticated', async () => {
    render(<App />);
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /smcs/i })).toBeInTheDocument();
    });
  });
});
