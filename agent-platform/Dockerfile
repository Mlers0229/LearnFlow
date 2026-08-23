ARG AGENT_RUNTIME_IMAGE=python:3.12-slim-trixie

FROM ${AGENT_RUNTIME_IMAGE}

WORKDIR /app

RUN apt-get update \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/*

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV HOME=/tmp

ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
ARG INSTALL_RERANK=false
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

COPY agent-platform/requirements.txt requirements.txt
COPY agent-platform/requirements-rerank.txt requirements-rerank.txt
RUN if [ "${INSTALL_RERANK}" = "true" ]; then \
        pip install --no-cache-dir -i "${PIP_INDEX_URL}" -r requirements-rerank.txt; \
    else \
        pip install --no-cache-dir -i "${PIP_INDEX_URL}" -r requirements.txt; \
    fi

COPY agent-platform .

EXPOSE 8000

USER 10001:10001

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health/live', timeout=2).read()"]

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--timeout-graceful-shutdown", "30"]
