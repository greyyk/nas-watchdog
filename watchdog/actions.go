package main

import (
	"context"
	"encoding/binary"
	"fmt"
	"io"
	"net/http"
	"os/exec"
	"time"
)

type ActionRequest struct {
	Action  string `json:"action"`
	Target  string `json:"target"`
	Confirm bool   `json:"confirm"`
}

type ActionResult struct {
	OK      bool   `json:"ok"`
	Message string `json:"message"`
}

var containerVerbs = map[string]string{
	"container_start":   "start",
	"container_stop":    "stop",
	"container_restart": "restart",
}

func dockerPost(socket, path string) error {
	client := dockerHTTPClient(socket)
	req, err := http.NewRequest(http.MethodPost, "http://docker"+path, nil)
	if err != nil {
		return err
	}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 512))
		return fmt.Errorf("docker API %d: %s", resp.StatusCode, string(body))
	}
	return nil
}

func runBtrfsAction(args ...string) error {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	out, err := exec.CommandContext(ctx, "btrfs", args...).CombinedOutput()
	if err != nil {
		return fmt.Errorf("%v: %s", err, string(out))
	}
	return nil
}

func (s *Server) handleAction(w http.ResponseWriter, r *http.Request, req ActionRequest) {
	if !s.cfg.AllowActions {
		s.writeJSON(w, http.StatusForbidden, ActionResult{
			OK:      false,
			Message: "Actions are disabled. Set WD_ALLOW_ACTIONS=true and mount the docker socket read-write.",
		})
		return
	}
	if !req.Confirm {
		s.writeJSON(w, http.StatusBadRequest, ActionResult{OK: false, Message: "confirmation required"})
		return
	}

	s.mu.RLock()
	snap := s.latest
	s.mu.RUnlock()
	if snap == nil {
		s.writeJSON(w, http.StatusServiceUnavailable, ActionResult{OK: false, Message: "no sample yet"})
		return
	}

	switch req.Action {
	case "container_start", "container_stop", "container_restart":
		verb, ok := containerVerbs[req.Action]
		if !ok {
			s.writeJSON(w, http.StatusBadRequest, ActionResult{OK: false, Message: "unknown action"})
			return
		}
		var id string
		for _, c := range snap.Containers {
			if c.Name == req.Target {
				id = c.ID
				break
			}
		}
		if id == "" {
			s.writeJSON(w, http.StatusNotFound, ActionResult{OK: false, Message: "container not found"})
			return
		}
		if err := dockerPost(s.col.dockerSocket(), "/containers/"+id+"/"+verb); err != nil {
			s.writeJSON(w, http.StatusInternalServerError, ActionResult{OK: false, Message: err.Error()})
			return
		}
		s.writeJSON(w, http.StatusOK, ActionResult{OK: true, Message: fmt.Sprintf("%s %s issued", verb, req.Target)})

	case "scrub_start":
		if err := runBtrfsAction("scrub", "start", s.cfg.PoolPath); err != nil {
			s.writeJSON(w, http.StatusInternalServerError, ActionResult{OK: false, Message: err.Error()})
			return
		}
		s.writeJSON(w, http.StatusOK, ActionResult{OK: true, Message: "scrub started"})

	case "scrub_cancel":
		if err := runBtrfsAction("scrub", "cancel", s.cfg.PoolPath); err != nil {
			s.writeJSON(w, http.StatusInternalServerError, ActionResult{OK: false, Message: err.Error()})
			return
		}
		s.writeJSON(w, http.StatusOK, ActionResult{OK: true, Message: "scrub cancel issued"})

	case "reboot":
		if err := issueReboot(s.cfg); err != nil {
			s.writeJSON(w, http.StatusInternalServerError, ActionResult{OK: false, Message: err.Error()})
			return
		}
		s.writeJSON(w, http.StatusOK, ActionResult{OK: true, Message: "reboot issued"})

	case "reboot_cancel":
		s.state.ClearReboot()
		s.writeJSON(w, http.StatusOK, ActionResult{OK: true, Message: "pending auto-reboot cancelled"})

	default:
		s.writeJSON(w, http.StatusBadRequest, ActionResult{OK: false, Message: "unknown action"})
	}
}

// demux splits Docker's multiplexed log stream into plain text.
func demuxDockerLogs(r io.Reader) string {
	var out []byte
	header := make([]byte, 8)
	for {
		if _, err := io.ReadFull(r, header); err != nil {
			break
		}
		size := binary.BigEndian.Uint32(header[4:8])
		if size == 0 || size > 8*1024*1024 {
			break
		}
		payload := make([]byte, size)
		if _, err := io.ReadFull(r, payload); err != nil {
			break
		}
		out = append(out, payload...)
	}
	return string(out)
}

func fetchContainerLogs(socket, id string, tail int) (string, error) {
	client := dockerHTTPClient(socket)
	url := fmt.Sprintf("http://docker/containers/%s/logs?stdout=1&stderr=1&timestamps=1&tail=%d", id, tail)
	resp, err := client.Get(url)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("docker logs: HTTP %d", resp.StatusCode)
	}
	return demuxDockerLogs(resp.Body), nil
}
