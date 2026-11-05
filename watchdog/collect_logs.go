package main

import (
	"bufio"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"time"
)

type logSource struct {
	ID    string
	Label string
	Path  string
}

type logPattern struct {
	Name string
	Re   *regexp.Regexp
}

var logPatterns = []logPattern{
	{"btrfs", regexp.MustCompile(`BTRFS (error|warning) \(device`)},
	{"readonly", regexp.MustCompile(`(?i)forced readonly|read-only file system|EROFS`)},
	{"io_error", regexp.MustCompile(`(?i)I/O error|Buffer I/O|blk_update_request|critical medium error`)},
	{"memory", regexp.MustCompile(`EDAC|Machine check|Hardware Error|Memory failure|uncorrected memory|MCE:`)},
	{"oom", regexp.MustCompile(`(?i)out of memory|oom-kill|oom_reaper|Killed process`)},
	{"crash", regexp.MustCompile(`(?i)segfault|general protection fault|kernel panic|Call Trace|BUG:`)},
	{"disk", regexp.MustCompile(`(?i)ata[0-9].*error|nvme.*(error|reset|timeout)|uncorrectable|rejecting I/O`)},
	{"service", regexp.MustCompile(`(?i)failed to start|unit .* failed|exited with status|panic:|FATAL`)},
}

const logScanBytes = 384 * 1024

func (c *Collector) baseLogSources() []logSource {
	names := []string{"kern.log", "syslog", "auth.log", "daemon.log", "user.log", "dpkg.log"}
	var out []logSource
	for _, n := range names {
		p := c.hostPath(filepath.Join("var/log", n))
		if st, err := os.Stat(p); err == nil && !st.IsDir() {
			out = append(out, logSource{ID: strings.TrimSuffix(n, ".log"), Label: n, Path: p})
		}
	}
	return out
}

func (c *Collector) appLogSources() []logSource {
	root := c.hostPath("zettos/raid/non-snapshot/log/app")
	apps, err := os.ReadDir(root)
	if err != nil {
		return nil
	}
	dateRe := regexp.MustCompile(`^\d{4}-\d{2}-\d{2}$`)
	var out []logSource

	for _, app := range apps {
		if !app.IsDir() {
			continue
		}
		appDir := filepath.Join(root, app.Name())
		days, err := os.ReadDir(appDir)
		if err != nil {
			continue
		}
		var latest string
		for _, d := range days {
			if d.IsDir() && dateRe.MatchString(d.Name()) && d.Name() > latest {
				latest = d.Name()
			}
		}
		if latest == "" {
			continue
		}
		dayDir := filepath.Join(appDir, latest)
		for _, kind := range []string{"error.log", "warn.log", "info.log"} {
			p := filepath.Join(dayDir, kind)
			st, err := os.Stat(p)
			if err != nil || st.IsDir() {
				continue
			}
			if kind == "info.log" && st.Size() > 8*1024*1024 {
				continue
			}
			out = append(out, logSource{
				ID:    sanitizeKey(app.Name() + "-" + strings.TrimSuffix(kind, ".log")),
				Label: app.Name() + "/" + latest + "/" + kind,
				Path:  p,
			})
		}
	}
	return out
}

func (c *Collector) allLogSources() []logSource {
	out := append(c.baseLogSources(), c.appLogSources()...)
	sort.Slice(out, func(i, j int) bool { return out[i].ID < out[j].ID })
	return out
}

func readTail(path string, maxBytes int64) ([]string, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	st, err := f.Stat()
	if err != nil {
		return nil, err
	}
	start := int64(0)
	if st.Size() > maxBytes {
		start = st.Size() - maxBytes
	}
	if _, err := f.Seek(start, io.SeekStart); err != nil {
		return nil, err
	}
	data, err := io.ReadAll(f)
	if err != nil {
		return nil, err
	}
	lines := strings.Split(string(data), "\n")
	if start > 0 && len(lines) > 0 {
		lines = lines[1:]
	}
	return lines, nil
}

func scanLogSource(src logSource) LogSummary {
	sum := LogSummary{Source: src.ID, Path: src.Path, Matches: map[string]int{}}
	st, err := os.Stat(src.Path)
	if err != nil {
		sum.Error = err.Error()
		return sum
	}
	sum.Size = st.Size()
	sum.ModTime = st.ModTime()

	lines, err := readTail(src.Path, logScanBytes)
	if err != nil {
		sum.Error = err.Error()
		return sum
	}
	for _, line := range lines {
		if line == "" {
			continue
		}
		for _, p := range logPatterns {
			if p.Re.MatchString(line) {
				sum.Matches[p.Name]++
				if len(line) > 400 {
					line = line[:400]
				}
				sum.LastMatch = line
			}
		}
	}
	return sum
}

func (c *Collector) collectLogs() []LogSummary {
	c.logMu.Lock()
	defer c.logMu.Unlock()

	if time.Since(c.logAt) < c.cfg.LogInterval && c.logCache != nil {
		out := make([]LogSummary, len(c.logCache))
		copy(out, c.logCache)
		return out
	}

	sources := c.allLogSources()
	out := make([]LogSummary, 0, len(sources))
	for _, src := range sources {
		out = append(out, scanLogSource(src))
	}
	c.logCache = out
	c.logAt = time.Now()
	return out
}

func (c *Collector) tailLog(sourceID string, maxLines int, filter string) ([]string, error) {
	if maxLines <= 0 || maxLines > 2000 {
		maxLines = 200
	}
	var target *logSource
	for _, src := range c.allLogSources() {
		if src.ID == sourceID {
			s := src
			target = &s
			break
		}
	}
	if target == nil {
		return nil, os.ErrNotExist
	}
	lines, err := readTail(target.Path, 2*1024*1024)
	if err != nil {
		return nil, err
	}
	var re *regexp.Regexp
	if filter != "" {
		re, err = regexp.Compile(filter)
		if err != nil {
			return nil, err
		}
	}
	var out []string
	for _, line := range lines {
		if line == "" {
			continue
		}
		if re != nil && !re.MatchString(line) {
			continue
		}
		out = append(out, line)
	}
	if len(out) > maxLines {
		out = out[len(out)-maxLines:]
	}
	return out, nil
}

var _ = bufio.NewScanner
