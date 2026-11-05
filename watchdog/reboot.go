package main

import (
	"context"
	"fmt"
	"log"
	"os/exec"
	"time"
)

func issueReboot(cfg *Config) error {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	cmd := exec.CommandContext(ctx, "sh", "-c", cfg.RebootCommand)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("%v: %s", err, string(out))
	}
	if len(out) > 0 {
		log.Printf("reboot command output: %s", string(out))
	}
	return nil
}

func handleReboot(cfg *Config, st *StateStore, snap *Snapshot, n *Notifier) {
	poolRO := snap.Pool.ReadOnly && snap.Pool.ExpectedRW

	if !poolRO {
		st.ClearReboot()
		return
	}
	if !cfg.AllowReboot {
		return
	}

	if st.RebootDue() {
		if st.RecentReboot(cfg.RebootCooldown) {
			log.Printf("auto-reboot suppressed: reboot within cooldown %s", cfg.RebootCooldown)
			st.ClearReboot()
			return
		}
		if st.RebootCountSince(cfg.RebootWindow) >= 2 {
			log.Printf("auto-reboot suppressed: reboot loop detected")
			st.ClearReboot()
			return
		}
		log.Printf("issuing guarded reboot")
		st.MarkRebootIssued()
		n.Send(Event{Kind: "fired", Alert: Alert{
			ID:       "auto_reboot",
			Severity: "critical",
			Title:    "Issuing guarded reboot",
			Detail:   "Pool stayed read-only past the grace period; rebooting to restore writable state. Diagnostics were captured first.",
		}})
		if err := issueReboot(cfg); err != nil {
			log.Printf("reboot failed: %v", err)
		}
		return
	}

	if st.RebootPending() {
		return
	}

	st.ScheduleReboot(cfg.RebootDelay)
	log.Printf("pool read-only; auto-reboot scheduled in %s", cfg.RebootDelay)
	n.Send(Event{Kind: "fired", Alert: Alert{
		ID:       "auto_reboot_scheduled",
		Severity: "warning",
		Title:    "Guarded reboot scheduled",
		Detail: fmt.Sprintf("Pool is read-only. Rebooting in %s unless it recovers first. "+
			"Reboot is suppressed if another reboot happened recently or a loop is detected.", cfg.RebootDelay),
	}})
}
