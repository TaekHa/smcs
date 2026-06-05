import type { CSSProperties } from 'react';
import { useAuthObjectUrl } from '../hooks/useAuthObjectUrl';

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
 * <img> request), then renders it via an object URL. Used for the mobile attachment gallery (Story 2.6).
 */
export function AuthImage({ src, alt, width = 96, height = 96, style }: AuthImageProps) {
  const objectUrl = useAuthObjectUrl(src);

  const box: CSSProperties = { width, height, objectFit: 'cover', borderRadius: 4, ...style };
  return objectUrl ? (
    <img src={objectUrl} alt={alt} loading="lazy" style={box} />
  ) : (
    <div style={{ ...box, background: '#f0f0f0' }} role="img" aria-label={alt} />
  );
}
