FROM golang:1.23-alpine AS build
ARG VERSION=1.0.0
WORKDIR /src
COPY go.mod ./
COPY *.go ./
COPY ui ./ui
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w -X main.version=${VERSION}" -o /out/watchdog .

FROM alpine:3.20
ARG VERSION=1.0.0
LABEL org.opencontainers.image.title="ZettLabs NAS Watchdog" \
      org.opencontainers.image.description="ZettOS-first host telemetry, alerts, and Android dashboard for ZettLabs NAS systems" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.version="${VERSION}"
RUN apk add --no-cache btrfs-progs smartmontools util-linux ca-certificates tzdata
COPY --from=build /out/watchdog /usr/local/bin/watchdog
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/watchdog"]
