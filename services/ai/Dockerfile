# syntax=docker/dockerfile:1
ARG BAKE_WHISPER_MODEL=false
FROM scratch AS models-false
COPY models/.gitkeep /models/.gitkeep
FROM scratch AS models-true
COPY models/ /models/
FROM models-${BAKE_WHISPER_MODEL} AS selected-models

FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 PIP_NO_CACHE_DIR=1 \
    PORT=8000 UVICORN_WORKERS=1 WHISPER_DEVICE=cpu WHISPER_COMPUTE_TYPE=int8 \
    WHISPER_MODEL=small WHISPER_MODEL_DIR=/opt/whisper/small \
    WHISPER_LOCAL_FILES_ONLY=true WHISPER_STARTUP_MODE=required \
    WHISPER_STARTUP_TIMEOUT_SEC=90 HF_HUB_OFFLINE=1 OMP_NUM_THREADS=1
WORKDIR /app
# CPU CTranslate2 OpenMP/C++ runtimes. PyAV wheels bundle FFmpeg libraries.
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 libstdc++6 ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 appuser && useradd --uid 10001 --gid 10001 --create-home appuser
COPY requirements.lock ./requirements.lock
RUN python -m pip install --no-cache-dir -r requirements.lock && python -m pip check
COPY --chown=10001:10001 app ./app
COPY --from=selected-models --chown=10001:10001 /models/ /opt/whisper/
USER 10001:10001
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD python -c "import os,urllib.request; urllib.request.urlopen('http://127.0.0.1:'+os.getenv('PORT','8000')+'/ready',timeout=3)"
CMD ["python", "-m", "app.launch"]
