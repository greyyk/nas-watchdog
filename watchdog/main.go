package main

import (
	"context"
	"log"
	"os/signal"
	"syscall"
	"time"
)

var version = "1.0.0"

func main() {
	cfg := loadConfig()
	log.Printf("nas-watchdog %s starting (interval=%s pool=%s hostroot=%s)",
		version, cfg.Interval, cfg.PoolPath, cfg.HostRoot)

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	st := newStateStore(cfg.DataDir)
	// One day of context at the default 30-second collection interval.
	hist := newHistory(2880)
	col := newCollector(cfg)
	srv := newServer(cfg, hist, col, st)
	notif := newNotifier(cfg)

	go srv.run(ctx)
	go notif.heartbeatLoop(ctx)

	runOnce := func() {
		snap, err := col.Collect()
		if err != nil {
			log.Printf("collect: %v", err)
			return
		}
		if bootChanged := st.BootCheck(snap.Host.BootID); bootChanged {
			log.Printf("detected host reboot (boot_id changed)")
		}

		active := evaluateAlerts(cfg, st, snap)
		alerts, events := st.ApplyAlerts(active)
		snap.Alerts = alerts
		snap.Watchdog.AlertCount = len(alerts)

		hist.Add(snap)
		srv.publish(snap, events)
		for _, ev := range events {
			if ev.Kind == "fired" {
				log.Printf("ALERT [%s] %s: %s", ev.Alert.Severity, ev.Alert.Title, ev.Alert.Detail)
			} else {
				log.Printf("RESOLVED %s", ev.Alert.Title)
			}
			notif.Send(ev)
		}

		handleReboot(cfg, st, snap, notif)
	}

	runOnce()
	ticker := time.NewTicker(cfg.Interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			log.Printf("shutting down")
			return
		case <-ticker.C:
			runOnce()
		}
	}
}
