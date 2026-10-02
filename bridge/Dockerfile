FROM python:3.12-slim
WORKDIR /app
COPY bridge/bridge.py .
USER 10001:10001
ENV PYTHONUNBUFFERED=1
CMD ["python", "bridge.py"]
