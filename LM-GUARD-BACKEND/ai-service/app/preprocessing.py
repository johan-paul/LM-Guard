"""Image quality assessment and preprocessing.

Every threshold here is a heuristic, not a legal or metrological standard -- there is no
calibration reference in an arbitrary phone photo, so nothing here claims physical accuracy
(mm, contrast ratio, lux). The output is used for exactly two things: (1) deciding whether the
image is even worth running OCR/VLM on, and (2) surfacing a human-readable warning. It never
produces a compliance verdict -- that boundary belongs to the Java rule engine alone.
"""
from __future__ import annotations

from dataclasses import dataclass

import cv2
import numpy as np


@dataclass
class ImageQuality:
    score: float          # 0..1, coarse overall usability estimate
    blur: bool
    glare: bool
    low_resolution: bool
    low_contrast: bool
    sharpness: float       # raw Laplacian variance, for logging/debugging
    width: int
    height: int

    @property
    def usable(self) -> bool:
        return self.score >= 0.25 and not (self.blur and self.low_resolution)

    def warnings(self) -> list[str]:
        out = []
        if self.low_resolution:
            out.append(f"Image resolution is low ({self.width}x{self.height}); small text may be unreadable.")
        if self.blur:
            out.append(f"Image appears blurred (sharpness={self.sharpness:.1f}); some text may be unreliable.")
        if self.glare:
            out.append("Glare/overexposure detected on part of the image; affected text may be unreadable.")
        if self.low_contrast:
            out.append("Low contrast between text and background detected.")
        return out


MIN_DIMENSION_FOR_FULL_SCORE = 800
BLUR_VARIANCE_THRESHOLD = 60.0      # below this, the Laplacian variance suggests a blurred photo
GLARE_PIXEL_FRACTION_THRESHOLD = 0.08  # fraction of near-white saturated pixels
LOW_CONTRAST_STD_THRESHOLD = 30.0


def assess_quality(image_bgr: np.ndarray) -> ImageQuality:
    height, width = image_bgr.shape[:2]
    gray = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY)

    sharpness = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    blur = sharpness < BLUR_VARIANCE_THRESHOLD

    low_resolution = min(width, height) < MIN_DIMENSION_FOR_FULL_SCORE

    # Glare: fraction of pixels that are both very bright and very low-saturation (blown highlights).
    hsv = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2HSV)
    bright_lowsat = (hsv[:, :, 2] > 245) & (hsv[:, :, 1] < 25)
    glare_fraction = float(np.mean(bright_lowsat))
    glare = glare_fraction > GLARE_PIXEL_FRACTION_THRESHOLD

    contrast_std = float(gray.std())
    low_contrast = contrast_std < LOW_CONTRAST_STD_THRESHOLD

    score = 1.0
    if blur:
        score -= 0.4
    if low_resolution:
        score -= 0.25
    if glare:
        score -= 0.2
    if low_contrast:
        score -= 0.2
    score = max(0.0, min(1.0, score))

    return ImageQuality(
        score=score,
        blur=blur,
        glare=glare,
        low_resolution=low_resolution,
        low_contrast=low_contrast,
        sharpness=sharpness,
        width=width,
        height=height,
    )


def enhance_for_ocr(image_bgr: np.ndarray) -> np.ndarray:
    """Denoise + CLAHE contrast enhancement + light sharpening, tuned for printed package
    labels rather than natural photographs. Returns a BGR image the same shape as the input."""
    gray = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY)
    denoised = cv2.fastNlMeansDenoising(gray, h=10)
    clahe = cv2.createCLAHE(clipLimit=2.5, tileGridSize=(8, 8))
    enhanced = clahe.apply(denoised)
    # Unsharp mask for text-edge crispness.
    blurred = cv2.GaussianBlur(enhanced, (0, 0), sigmaX=3)
    sharpened = cv2.addWeighted(enhanced, 1.5, blurred, -0.5, 0)
    return cv2.cvtColor(sharpened, cv2.COLOR_GRAY2BGR)


def resize_if_needed(image_bgr: np.ndarray, max_dimension: int = 2000) -> np.ndarray:
    """Caps resolution for cost/latency control without discarding useful detail; upscales
    tiny images slightly so OCR has enough pixels per character."""
    height, width = image_bgr.shape[:2]
    longest = max(height, width)
    if longest > max_dimension:
        scale = max_dimension / longest
        return cv2.resize(image_bgr, (int(width * scale), int(height * scale)), interpolation=cv2.INTER_AREA)
    if longest < 600:
        scale = 600 / longest
        return cv2.resize(image_bgr, (int(width * scale), int(height * scale)), interpolation=cv2.INTER_CUBIC)
    return image_bgr
