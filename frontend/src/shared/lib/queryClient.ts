import { QueryClient } from '@tanstack/react-query';

// Single app-wide QueryClient instance. Exported as a module singleton so the
// non-React axios layer (api/client.ts 401 handler) and the auth store can
// clear it on logout — preventing one user's cached data from leaking into the
// next session (Story 4.7 Phase 2 UT-001).
export const queryClient = new QueryClient();
