FROM golang:1.23-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY *.go ./
COPY ui ./ui
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w -X main.version=1.0.0" -o /out/watchdog .

FROM alpine:3.20
RUN apk add --no-cache btrfs-progs smartmontools util-linux ca-certificates tzdata
COPY --from=build /out/watchdog /usr/local/bin/watchdog
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/watchdog"]
