import { Image } from 'antd';
import type { CSSProperties } from 'react';
import { useAuthObjectUrl } from '../hooks/useAuthObjectUrl';

interface AuthPreviewImageProps {
  /** Absolute path under web root, e.g. "/files/2026/05/uuid.jpg". */
  src: string;
  alt: string;
  width?: number;
  height?: number;
  style?: CSSProperties;
}

/**
 * antd <Image> (click-to-zoom, Image.PreviewGroup-compatible) backed by a JWT-authenticated
 * blob fetch — for the desktop attachment gallery where AGENT/ADMIN view field photos (UT-005).
 * A plain antd <Image src="/files/.."> sends no Authorization header → FileController returns 401.
 */
export function AuthPreviewImage({ src, alt, width = 120, height = 120, style }: AuthPreviewImageProps) {
  const objectUrl = useAuthObjectUrl(src);

  if (!objectUrl) {
    return (
      <div
        style={{ width, height, background: '#f0f0f0', borderRadius: 4, ...style }}
        role="img"
        aria-label={alt}
      />
    );
  }

  return (
    <Image
      src={objectUrl}
      alt={alt}
      width={width}
      height={height}
      style={{ objectFit: 'cover', ...style }}
      loading="lazy"
    />
  );
}
