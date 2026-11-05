package main

import (
	"bytes"
	"context"
	"encoding/json"
	"log"
	"net/http"
	"strings"
	"time"
)

type Notifier struct {
	cfg    *Config
	client *http.Client
}

func newNotifier(cfg *Config) *Notifier {
	return &Notifier{cfg: cfg, client: &http.Client{Timeout: 10 * time.Second}}
}

func (n *Notifier) Send(ev Event) {
	if n == nil || n.cfg == nil {
		return
	}
	a := ev.Alert
	title := a.Title
	body := a.Detail
	tag := "warning"
	priority := "3"

	switch a.Severity {
	case "critical":
		tag, priority = "rotating_light", "5"
	case "warning":
		tag, priority = "warning", "3"
	default:
		tag, priority = "information_source", "2"
	}
	if ev.Kind == "resolved" {
		title = "Resolved: " + title
		tag, priority = "white_check_mark", "2"
	}

	if n.cfg.NtfyURL != "" && n.cfg.NtfyTopic != "" {
		url := strings.TrimRight(n.cfg.NtfyURL, "/") + "/" + n.cfg.NtfyTopic
		req, err := http.NewRequest(http.MethodPost, url, strings.NewReader(body))
		if err == nil {
			req.Header.Set("Title", asciiHeader(title))
			req.Header.Set("Priority", priority)
			req.Header.Set("Tags", tag)
			if n.cfg.NtfyToken != "" {
				req.Header.Set("Authorization", "Bearer "+n.cfg.NtfyToken)
			}
			resp, err := n.client.Do(req)
			if err != nil {
				log.Printf("notify ntfy: %v", err)
			} else {
				resp.Body.Close()
			}
		}
	}

	if n.cfg.WebhookURL != "" {
		payload := map[string]any{
			"kind":     ev.Kind,
			"id":       a.ID,
			"severity": a.Severity,
			"title":    title,
			"detail":   body,
			"since":    a.Since.Format(time.RFC3339),
		}
		b, _ := json.Marshal(payload)
		resp, err := n.client.Post(n.cfg.WebhookURL, "application/json", bytes.NewReader(b))
		if err != nil {
			log.Printf("notify webhook: %v", err)
		} else {
			resp.Body.Close()
		}
	}
}

func asciiHeader(s string) string {
	var b strings.Builder
	for _, r := range s {
		if r < 32 || r > 126 {
			b.WriteRune('?')
			continue
		}
		b.WriteRune(r)
	}
	out := b.String()
	if len(out) > 120 {
		out = out[:120]
	}
	return out
}

func (n *Notifier) heartbeatLoop(ctx context.Context) {
	if n.cfg.HeartbeatURL == "" {
		return
	}
	interval := n.cfg.HeartbeatInterval
	if interval < 10*time.Second {
		interval = 10 * time.Second
	}
	t := time.NewTicker(interval)
	defer t.Stop()
	ping := func() {
		resp, err := n.client.Get(n.cfg.HeartbeatURL)
		if err != nil {
			log.Printf("heartbeat: %v", err)
			return
		}
		resp.Body.Close()
	}
	ping()
	for {
		select {
		case <-ctx.Done():
			return
		case <-t.C:
			ping()
		}
	}
}
