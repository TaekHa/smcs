import { useEffect, useState } from 'react';
import type { CSSProperties } from 'react';
import { apiClient } from '../../api/client';

interface AuthImageProps {
  /** Absolute path under web root, e.g. "/files/2026/05/uuid.jpg". */
  src: string;
  alt: string;
  width?: number;
  height?: number;
  style?: CSSProperties;
}

/**
 * Loads a token-protected image as a blob (the browser does not attach the JWT to a plain
 * <img> request), then renders it via an object URL. Used for the attachment gallery (Story 2.6).
 */
export function AuthImage({ src, alt, width = 96, height = 96, style }: AuthImageProps) {
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
        /* leave placeholder on failure */
      });
    return () => {
      active = false;
      if (created) URL.revokeObjectURL(created);
    };
  }, [src]);

  const box: CSSProperties = { width, height, objectFit: 'cover', borderRadius: 4, ...style };
  return objectUrl ? (
    <img src={objectUrl} alt={alt} loading="lazy" style={box} />
  ) : (
    <div style={{ ...box, background: '#f0f0f0' }} role="img" aria-label={alt} />
  );
}
