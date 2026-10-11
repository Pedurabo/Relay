# Relay production container

FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    RELAY_ENVIRONMENT=production \
    RELAY_BACKEND_HOST=0.0.0.0 \
    RELAY_BACKEND_PORT=9000 \
    RELAY_DATABASE_PATH=/data/relay.sqlite

WORKDIR /app

COPY requirements.txt ./requirements.txt

RUN pip install \
    --no-cache-dir \
    --disable-pip-version-check \
    -r requirements.txt

COPY relay_settings.py ./relay_settings.py
COPY relay_store.py ./relay_store.py
COPY relay_dev_server.py ./relay_dev_server.py

RUN mkdir -p /data

EXPOSE 9000

HEALTHCHECK \
    --interval=30s \
    --timeout=3s \
    --start-period=5s \
    --retries=3 \
    CMD python -c "import os, urllib.request; port = os.environ.get('RELAY_BACKEND_PORT', '9000'); urllib.request.urlopen('http://127.0.0.1:' + port + '/ready', timeout=2).read()"

CMD ["python", "relay_dev_server.py"]
