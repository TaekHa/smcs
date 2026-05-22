package com.smcs.attachment;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Validates JPEG/PNG by magic byte and strips metadata by re-encoding via {@code javax.imageio}
 * (§6.6/§6.7). No third-party imaging dependency. JPEG re-encoded at quality 0.95.
 */
@Component
public class ImageProcessor {

	private static final float JPEG_QUALITY = 0.95f;

	/** Validated + metadata-stripped image bytes with the resolved format/MIME. */
	public record ProcessedImage(byte[] bytes, String ext, String mimeType) {
	}

	public ProcessedImage process(byte[] raw) {
		String format = detectFormat(raw); // "jpg" | "png", or throws
		BufferedImage image = read(raw);
		byte[] clean = "jpg".equals(format) ? writeJpeg(image) : writePng(image);
		String mime = "jpg".equals(format) ? "image/jpeg" : "image/png";
		return new ProcessedImage(clean, format, mime);
	}

	private String detectFormat(byte[] b) {
		if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
			return "jpg"; // JPEG: FF D8 FF
		}
		if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) {
			return "png"; // PNG: 89 50 4E 47
		}
		throw new InvalidImageException("unsupported image (only JPEG/PNG)");
	}

	private BufferedImage read(byte[] raw) {
		try {
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
			if (img == null) {
				throw new InvalidImageException("not a decodable image");
			}
			return img;
		} catch (IOException e) {
			throw new InvalidImageException("failed to read image");
		}
	}

	private byte[] writePng(BufferedImage image) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out); // PNG is lossless; re-encode drops metadata
			return out.toByteArray();
		} catch (IOException e) {
			throw new InvalidImageException("failed to encode png");
		}
	}

	private byte[] writeJpeg(BufferedImage source) {
		// JPEG has no alpha — flatten to RGB to avoid pink/black artifacts.
		BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
		rgb.createGraphics().drawImage(source, 0, 0, java.awt.Color.WHITE, null);
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
		if (!writers.hasNext()) {
			throw new InvalidImageException("no jpeg writer");
		}
		ImageWriter writer = writers.next();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(ios);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(JPEG_QUALITY);
			writer.write(null, new IIOImage(rgb, null, null), param);
			return out.toByteArray();
		} catch (IOException e) {
			throw new InvalidImageException("failed to encode jpeg");
		} finally {
			writer.dispose();
		}
	}
}
