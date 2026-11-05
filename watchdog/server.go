package main

import (
	"context"
	"crypto/subtle"
	"embed"
	"encoding/json"
	"fmt"
	"io/fs"
	"log"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"
)

//go:embed all:ui
var uiFS embed.FS

type Server struct {
	cfg   *Config
	hist  *History
	col   *Collector
	state *StateStore

	mu     sync.RWMutex
	latest *Snapshot
	events []Event
}

func newServer(cfg *Config, hist *History, col *Collector, st *StateStore) *Server {
	return &Server{cfg: cfg, hist: hist, col: col, state: st}
}

func (s *Server) publish(snap *Snapshot, events []Event) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.latest = snap
	if len(events) > 0 {
		s.events = append(s.events, events...)
		if len(s.events) > 200 {
			s.events = s.events[len(s.events)-200:]
		}
	}
}

func (s *Server) auth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if s.cfg.APIToken == "" {
			next(w, r)
			return
		}
		tok := strings.TrimSpace(strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer "))
		if tok == "" {
			tok = r.URL.Query().Get("token")
		}
		if subtle.ConstantTimeCompare([]byte(tok), []byte(s.cfg.APIToken)) != 1 {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		next(w, r)
	}
}

func (s *Server) writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	enc := json.NewEncoder(w)
	enc.SetEscapeHTML(false)
	_ = enc.Encode(v)
}

func (s *Server) run(ctx context.Context) {
	mux := http.NewServeMux()

	mux.HandleFunc("/api/v1/stats", s.auth(func(w http.ResponseWriter, r *http.Request) {
		s.mu.RLock()
		snap := s.latest
		s.mu.RUnlock()
		if snap == nil {
			s.writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "no sample yet"})
			return
		}
		s.writeJSON(w, http.StatusOK, snap)
	}))

	mux.HandleFunc("/api/v1/history", s.auth(func(w http.ResponseWriter, r *http.Request) {
		samples := s.hist.Snapshot()
		var window time.Duration
		switch r.URL.Query().Get("range") {
		case "5m":
			window = 5 * time.Minute
		case "1h":
			window = time.Hour
		case "6h":
			window = 6 * time.Hour
		case "24h":
			window = 24 * time.Hour
		}
		if window > 0 {
			cutoff := time.Now().Add(-window)
			filtered := samples[:0]
			for _, smp := range samples {
				if smp.TS.After(cutoff) {
					filtered = append(filtered, smp)
				}
			}
			samples = filtered
		}
		s.writeJSON(w, http.StatusOK, map[string]any{"samples": samples})
	}))

	mux.HandleFunc("/api/v1/stream", s.auth(func(w http.ResponseWriter, r *http.Request) {
		flusher, ok := w.(http.Flusher)
		if !ok {
			http.Error(w, "streaming unsupported", http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "text/event-stream")
		w.Header().Set("Cache-Control", "no-cache")
		w.Header().Set("Connection", "keep-alive")
		w.WriteHeader(http.StatusOK)

		ticker := time.NewTicker(5 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-r.Context().Done():
				return
			case <-ticker.C:
				s.mu.RLock()
				snap := s.latest
				s.mu.RUnlock()
				if snap == nil {
					continue
				}
				compact := map[string]any{
					"ts":       snap.TS,
					"cpu":      snap.CPU.UsagePct,
					"mem":      snap.Memory.UsedPct,
					"load1":    snap.Host.Load1,
					"pool_ro":  snap.Pool.ReadOnly,
					"pool_pct": snap.Pool.UsedPct,
					"temps":    snap.Temps,
					"alerts":   snap.Alerts,
				}
				b, _ := json.Marshal(compact)
				fmt.Fprintf(w, "event: stats\ndata: %s\n\n", b)
				flusher.Flush()
			}
		}
	}))

	mux.HandleFunc("/api/v1/containers/logs", s.auth(func(w http.ResponseWriter, r *http.Request) {
		name := r.URL.Query().Get("name")
		if name == "" {
			s.writeJSON(w, http.StatusBadRequest, map[string]string{"error": "name required"})
			return
		}
		tail, _ := strconv.Atoi(r.URL.Query().Get("tail"))
		if tail <= 0 {
			tail = 200
		}
		if tail > 2000 {
			tail = 2000
		}
		s.mu.RLock()
		snap := s.latest
		s.mu.RUnlock()
		if snap == nil {
			s.writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "no sample yet"})
			return
		}
		var id string
		for _, c := range snap.Containers {
			if c.Name == name {
				id = c.ID
				break
			}
		}
		if id == "" {
			s.writeJSON(w, http.StatusNotFound, map[string]string{"error": "container not found"})
			return
		}
		out, err := fetchContainerLogs(s.col.dockerSocket(), id, tail)
		if err != nil {
			s.writeJSON(w, http.StatusBadGateway, map[string]string{"error": err.Error()})
			return
		}
		s.writeJSON(w, http.StatusOK, map[string]any{"name": name, "text": out})
	}))

	mux.HandleFunc("/api/v1/journal", s.auth(func(w http.ResponseWriter, r *http.Request) {
		lines, _ := strconv.Atoi(r.URL.Query().Get("lines"))
		out, err := s.col.tailJournal(lines, r.URL.Query().Get("q"))
		if err != nil {
			s.writeJSON(w, http.StatusBadGateway, map[string]string{"error": err.Error()})
			return
		}
		s.writeJSON(w, http.StatusOK, map[string]any{"lines": out})
	}))

	mux.HandleFunc("/api/v1/dmesg", s.auth(func(w http.ResponseWriter, r *http.Request) {
		lines, _ := strconv.Atoi(r.URL.Query().Get("lines"))
		out, err := s.col.tailDmesg(lines, r.URL.Query().Get("q"))
		if err != nil {
			s.writeJSON(w, http.StatusBadGateway, map[string]string{"error": err.Error()})
			return
		}
		s.writeJSON(w, http.StatusOK, map[string]any{"lines": out})
	}))

	mux.HandleFunc("/api/v1/actions", s.auth(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		var req ActionRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			s.writeJSON(w, http.StatusBadRequest, ActionResult{OK: false, Message: "invalid json"})
			return
		}
		s.handleAction(w, r, req)
	}))

	mux.HandleFunc("/api/v1/events", s.auth(func(w http.ResponseWriter, r *http.Request) {
		s.mu.RLock()
		ev := make([]Event, len(s.events))
		copy(ev, s.events)
		s.mu.RUnlock()
		s.writeJSON(w, http.StatusOK, map[string]any{"events": ev})
	}))

	mux.HandleFunc("/api/v1/logs/sources", s.auth(func(w http.ResponseWriter, r *http.Request) {
		s.mu.RLock()
		snap := s.latest
		s.mu.RUnlock()
		if snap == nil {
			s.writeJSON(w, http.StatusOK, map[string]any{"sources": []any{}})
			return
		}
		type src struct {
			ID      string         `json:"id"`
			Path    string         `json:"path"`
			Size    int64          `json:"size"`
			ModTime time.Time      `json:"mod_time"`
			Matches map[string]int `json:"matches"`
			Error   string         `json:"error,omitempty"`
		}
		out := make([]src, 0, len(snap.Logs))
		for _, l := range snap.Logs {
			out = append(out, src{
				ID: l.Source, Path: l.Path, Size: l.Size,
				ModTime: l.ModTime, Matches: l.Matches, Error: l.Error,
			})
		}
		s.writeJSON(w, http.StatusOK, map[string]any{"sources": out})
	}))

	mux.HandleFunc("/api/v1/logs", s.auth(func(w http.ResponseWriter, r *http.Request) {
		source := r.URL.Query().Get("source")
		if source == "" {
			s.writeJSON(w, http.StatusBadRequest, map[string]string{"error": "source required"})
			return
		}
		lines, _ := strconv.Atoi(r.URL.Query().Get("lines"))
		filter := r.URL.Query().Get("q")
		out, err := s.col.tailLog(source, lines, filter)
		if err != nil {
			s.writeJSON(w, http.StatusNotFound, map[string]string{"error": err.Error()})
			return
		}
		s.writeJSON(w, http.StatusOK, map[string]any{
			"source": source,
			"lines":  out,
		})
	}))

	mux.HandleFunc("/api/v1/health", func(w http.ResponseWriter, r *http.Request) {
		s.mu.RLock()
		snap := s.latest
		s.mu.RUnlock()
		status := http.StatusOK
		body := map[string]any{"ok": true, "ts": time.Now().UTC()}
		if snap == nil {
			status = http.StatusServiceUnavailable
			body["ok"] = false
		} else {
			body["hostname"] = snap.Host.Hostname
			body["uptime_s"] = snap.Host.UptimeSec
			body["pool_read_only"] = snap.Pool.ReadOnly
			body["alerts"] = len(snap.Alerts)
			if snap.Pool.ReadOnly {
				status = http.StatusServiceUnavailable
				body["ok"] = false
			}
		}
		s.writeJSON(w, status, body)
	})

	sub, err := fs.Sub(uiFS, "ui")
	if err != nil {
		log.Printf("ui embed: %v", err)
	} else {
		fileServer := http.FileServer(http.FS(sub))
		mux.Handle("/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Cache-Control", "no-store")
			fileServer.ServeHTTP(w, r)
		}))
	}

	srv := &http.Server{
		Addr:              s.cfg.Listen,
		Handler:           mux,
		ReadHeaderTimeout: 10 * time.Second,
	}

	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = srv.Shutdown(shutdownCtx)
	}()

	log.Printf("http listening on %s", s.cfg.Listen)
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Printf("http server: %v", err)
	}
}
