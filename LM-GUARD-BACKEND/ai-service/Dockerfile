FROM python:3.13-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

COPY requirements.txt .

# cv2 still dynamically links against a handful of X11/GL/glib shared libraries at import
# time - python:3.13-slim doesn't ship them, so `import cv2` fails outright before the app can
# even start (ImportError: libxcb.so.1, then libGL.so.1). rapidocr-onnxruntime's own dependency
# list pulls in plain opencv-python (not just opencv-python-headless) alongside the headless one
# already in requirements.txt, which is what drags in the GL requirement despite this being a
# server-only, no-display service.
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 \
    libgomp1 \
    libsm6 \
    libxext6 \
    libxrender1 \
    libxcb1 \
    libgl1 \
    tesseract-ocr \
    tesseract-ocr-hin \
    tesseract-ocr-tam \
    tesseract-ocr-tel \
    tesseract-ocr-kan \
    tesseract-ocr-mal \
    && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

EXPOSE 8000

CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000}"]