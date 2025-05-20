FROM debian:bookworm
RUN apt-get update && apt-get install -y wget aapt
RUN wget -qO- https://astral.sh/uv/install.sh | sh
RUN /root/.local/bin/uvx playwright install firefox --with-deps
ADD . /app
WORKDIR /app
RUN /root/.local/bin/uv run main.py --check manifests/Facebook_AndroidManifest.xmldump
ENTRYPOINT ["/app/entrypoint.sh"]
