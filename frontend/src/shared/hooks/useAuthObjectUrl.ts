import { useEffect, useState } from 'react';
import { apiClient } from '../../api/client';

/**
 * Loads a token-protected resource as a blob (the browser does not attach the JWT to a
 * plain <img> request) and returns an object URL, revoked on unmount / src change.
 * Shared by AuthImage (mobile thumbnail) and AuthPreviewImage (desktop antd gallery).
 */
export function useAuthObjectUrl(src: string): string | undefined {
  const [objectUrl, setObjectUrl] = useState<string>();

  useEffect(() => {
    let active = true;
    let created: string | undefined;
    // baseURL '' so the absolute /files path is hit directly; the request interceptor adds the JWT.
    apiClient
      .get(src, { responseType: 'blob', baseURL: '' })
      .then((res) => {
        if (!active) return;
        created = URL.createObjectURL(res.data as Blob);
        setObjectUrl(created);
      })
      .catch(() => {
        /* leave undefined on failure */
      });
    return () => {
      active = false;
      if (created) URL.revokeObjectURL(created);
    };
  }, [src]);

  return objectUrl;
}
