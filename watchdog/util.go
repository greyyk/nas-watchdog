package main

import (
	"bufio"
	"fmt"
	"math"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

func (c *Collector) hostPath(p string) string {
	return filepath.Join(c.cfg.HostRoot, p)
}

func readTrim(path string) string {
	b, err := os.ReadFile(path)
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(b))
}

func readInt64(path string) int64 {
	n, _ := readInt64OK(path)
	return n
}

// clampInt converts a kernel-reported counter to int without wrapping.
// Sysfs and /proc values fit easily, but a corrupt or hostile value must not
// turn into a negative reading.
func clampInt(v int64) int {
	switch {
	case v < 0:
		return 0
	case v > math.MaxInt:
		return math.MaxInt
	default:
		return int(v)
	}
}

// clampUintToInt converts an unsigned kernel counter to int without wrapping.
// Process counters never approach MaxInt32 on any supported platform, so an
// explicit bound keeps the conversion unambiguous for static analysis.
func clampUintToInt(v uint64) int {
	const max = 1<<31 - 1
	if v > max {
		return max
	}
	return int(v)
}

func readInt64OK(path string) (int64, bool) {
	text := readTrim(path)
	if text == "" {
		return 0, false
	}
	n, err := strconv.ParseInt(text, 10, 64)
	return n, err == nil
}

func parseMeminfo(path string) map[string]uint64 {
	out := map[string]uint64{}
	f, err := os.Open(path)
	if err != nil {
		return out
	}
	defer f.Close()
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		fields := strings.Fields(sc.Text())
		if len(fields) < 2 {
			continue
		}
		key := strings.TrimSuffix(fields[0], ":")
		v, err := strconv.ParseUint(fields[1], 10, 64)
		if err != nil {
			continue
		}
		out[key] = v * 1024
	}
	return out
}

func unescapeMount(s string) string {
	r := strings.NewReplacer(`\040`, " ", `\011`, "\t", `\012`, "\n", `\134`, `\`)
	return r.Replace(s)
}

func sanitizeKey(s string) string {
	s = strings.ToLower(s)
	r := strings.NewReplacer(" ", "_", "-", "_", ".", "_", "/", "_", ":", "_")
	return r.Replace(s)
}

func humanBytes(b uint64) string {
	const unit = 1024
	if b < unit {
		return fmt.Sprintf("%d B", b)
	}
	div, exp := uint64(unit), 0
	for n := b / unit; n >= unit; n /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.2f %ciB", float64(b)/float64(div), "KMGTPE"[exp])
}
