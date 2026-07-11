FROM python:3.12-alpine

WORKDIR /app

COPY server.py .

ENV PORT=8080
ENV HOST=0.0.0.0

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/v1/health || exit 1

USER nobody

CMD ["python3", "server.py"]
