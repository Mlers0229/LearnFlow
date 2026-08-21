FROM python:3.12-slim-trixie

WORKDIR /app

RUN apt-get update \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/*

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

COPY agent-platform/requirements.txt requirements.txt
RUN pip install --no-cache-dir -i "${PIP_INDEX_URL}" -r requirements.txt

COPY agent-platform .

EXPOSE 8000

USER 10001:10001

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--timeout-graceful-shutdown", "30"]
