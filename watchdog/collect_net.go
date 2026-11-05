package main

import (
	"context"
	"os/exec"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
)

type hostNetData struct {
	dev      []string
	snmp     []string
	netstat  []string
	sockstat []string
	ctUsed   int
	ctMax    int
	arp      int
	routes   int
	meta     map[string]map[string]string
}

// readHostNet runs one command inside the host network namespace and returns
// every networking source we need. Inside a container /proc/net is the
// container's netns, so these must be read through nsenter.
func readHostNet() hostNetData {
	d := hostNetData{meta: map[string]map[string]string{}}

	script := `echo '@@DEV'; cat /proc/net/dev 2>/dev/null
echo '@@SNMP'; cat /proc/net/snmp 2>/dev/null
echo '@@NETSTAT'; cat /proc/net/netstat 2>/dev/null
echo '@@SOCK'; cat /proc/net/sockstat 2>/dev/null
echo '@@CT'; cat /proc/sys/net/netfilter/nf_conntrack_count 2>/dev/null; cat /proc/sys/net/netfilter/nf_conntrack_max 2>/dev/null
echo '@@ARP'; wc -l < /proc/net/arp 2>/dev/null
echo '@@ROUTE'; wc -l < /proc/net/route 2>/dev/null
echo '@@SYSFS'; grep -H . /sys/class/net/*/speed /sys/class/net/*/operstate /sys/class/net/*/duplex /sys/class/net/*/mtu /sys/class/net/*/address 2>/dev/null`

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	out, err := exec.CommandContext(ctx, "nsenter", "-t", "1", "-m", "-n", "--", "sh", "-c", script).Output()
	if err != nil && len(out) == 0 {
		return d
	}

	section := ""
	for _, line := range strings.Split(string(out), "\n") {
		if strings.HasPrefix(line, "@@") {
			section = strings.TrimPrefix(line, "@@")
			continue
		}
		switch section {
		case "DEV":
			if strings.TrimSpace(line) != "" {
				d.dev = append(d.dev, line)
			}
		case "SNMP":
			if strings.TrimSpace(line) != "" {
				d.snmp = append(d.snmp, line)
			}
		case "NETSTAT":
			if strings.TrimSpace(line) != "" {
				d.netstat = append(d.netstat, line)
			}
		case "SOCK":
			if strings.TrimSpace(line) != "" {
				d.sockstat = append(d.sockstat, line)
			}
		case "CT":
			v := strings.TrimSpace(line)
			if v == "" {
				continue
			}
			if d.ctUsed == 0 {
				d.ctUsed, _ = strconv.Atoi(v)
			} else {
				d.ctMax, _ = strconv.Atoi(v)
			}
		case "ARP":
			d.arp, _ = strconv.Atoi(strings.TrimSpace(line))
		case "ROUTE":
			d.routes, _ = strconv.Atoi(strings.TrimSpace(line))
		case "SYSFS":
			parts := strings.SplitN(strings.TrimSpace(line), ":", 2)
			if len(parts) != 2 {
				continue
			}
			path, value := parts[0], parts[1]
			iface := filepath.Base(filepath.Dir(path))
			key := filepath.Base(path)
			if d.meta[iface] == nil {
				d.meta[iface] = map[string]string{}
			}
			d.meta[iface][key] = value
		}
	}
	if d.arp > 0 {
		d.arp--
	}
	if d.routes > 0 {
		d.routes--
	}
	return d
}

func isVirtualIface(name string) bool {
	for _, p := range []string{"veth", "br-", "virbr", "docker", "vnet", "tap", "tun", "wg", "tailscale"} {
		if strings.HasPrefix(name, p) {
			return true
		}
	}
	return name == "lo"
}

func (c *Collector) collectNet(elapsed float64) []NetIface {
	hn := readHostNet()

	prev := map[string]netCounters{}
	c.mu.Lock()
	for k, v := range c.prevNet {
		prev[k] = v
	}
	c.mu.Unlock()

	var out []NetIface
	next := map[string]netCounters{}

	for _, line := range hn.dev {
		idx := strings.Index(line, ":")
		if idx < 0 {
			continue
		}
		name := strings.TrimSpace(line[:idx])
		if name == "lo" {
			continue
		}
		fields := strings.Fields(line[idx+1:])
		if len(fields) < 16 {
			continue
		}
		u := func(i int) uint64 { v, _ := strconv.ParseUint(fields[i], 10, 64); return v }
		cur := netCounters{
			rxBytes: u(0), rxPackets: u(1), rxErrs: u(2), rxDrops: u(3),
			txBytes: u(8), txPackets: u(9), txErrs: u(10), txDrops: u(11),
		}
		next[name] = cur

		iface := NetIface{
			Name: name, RxBytes: cur.rxBytes, TxBytes: cur.txBytes,
			RxPackets: cur.rxPackets, TxPackets: cur.txPackets,
			RxErrs: cur.rxErrs, TxErrs: cur.txErrs,
			RxDrops: cur.rxDrops, TxDrops: cur.txDrops,
			Virtual: isVirtualIface(name),
		}
		if p, ok := prev[name]; ok && elapsed > 0 {
			iface.RxRate = float64(cur.rxBytes-p.rxBytes) / elapsed
			iface.TxRate = float64(cur.txBytes-p.txBytes) / elapsed
		}
		if m := hn.meta[name]; m != nil {
			iface.State = m["operstate"]
			iface.Duplex = m["duplex"]
			iface.MAC = m["address"]
			iface.MTU, _ = strconv.Atoi(m["mtu"])
			if sp, err := strconv.ParseInt(m["speed"], 10, 64); err == nil && sp > 0 {
				iface.SpeedMbps = sp
			}
		}
		out = append(out, iface)
	}

	c.mu.Lock()
	c.prevNet = next
	c.mu.Unlock()

	sort.Slice(out, func(i, j int) bool {
		if out[i].Virtual != out[j].Virtual {
			return !out[i].Virtual
		}
		return out[i].Name < out[j].Name
	})
	return out
}

func (c *Collector) collectNetStats() NetStatsInfo {
	hn := readHostNet()
	n := NetStatsInfo{}

	for _, line := range hn.sockstat {
		fields := strings.Fields(line)
		if len(fields) < 3 {
			continue
		}
		switch strings.TrimSuffix(fields[0], ":") {
		case "sockets":
			n.SocketsUsed, _ = strconv.Atoi(fields[2])
		case "TCP":
			for i := 1; i+1 < len(fields); i += 2 {
				v, _ := strconv.Atoi(fields[i+1])
				switch fields[i] {
				case "inuse":
					n.TCPInUse = v
				case "tw":
					n.TCPTimeWait = v
				case "alloc":
					n.TCPAlloc = v
				}
			}
		}
	}

	var header []string
	for _, line := range hn.snmp {
		fields := strings.Fields(line)
		if len(fields) < 2 || strings.TrimSuffix(fields[0], ":") != "Tcp" {
			continue
		}
		if header == nil {
			header = fields
			continue
		}
		for i := 1; i < len(header) && i < len(fields); i++ {
			v, _ := strconv.ParseUint(fields[i], 10, 64)
			switch header[i] {
			case "ActiveOpens":
				n.ActiveOpens = v
			case "PassiveOpens":
				n.PassiveOpens = v
			case "CurrEstab":
				n.CurrEstab = v
			case "InSegs":
				n.InSegs = v
			case "OutSegs":
				n.OutSegs = v
			case "RetransSegs":
				n.RetransSegs = v
			case "InErrs":
				n.InErrs = v
			case "OutRsts":
				n.OutRsts = v
			}
		}
	}
	if n.OutSegs > 0 {
		n.RetransPct = float64(n.RetransSegs) / float64(n.OutSegs) * 100
	}

	var extHeader []string
	for _, line := range hn.netstat {
		fields := strings.Fields(line)
		if len(fields) < 2 || strings.TrimSuffix(fields[0], ":") != "TcpExt" {
			continue
		}
		if extHeader == nil {
			extHeader = fields
			continue
		}
		for i := 1; i < len(extHeader) && i < len(fields); i++ {
			v, _ := strconv.ParseUint(fields[i], 10, 64)
			switch extHeader[i] {
			case "ListenOverflows":
				n.ListenOverflows = v
			case "ListenDrops":
				n.ListenDrops = v
			}
		}
	}

	n.ConntrackUsed = hn.ctUsed
	n.ConntrackMax = hn.ctMax
	n.ARPEntries = hn.arp
	n.Routes = hn.routes
	return n
}
