#!/bin/sh
# Read-only ZettLabs hardware inventory for compatibility issues.
# REVIEW every line before sharing. Do not add SMART, /stats, env, or raw logs:
# those outputs can contain serials, tokens, addresses, process data, or secrets.
set -eu

first_line() {
    if [ -r "$1" ]; then
        IFS= read -r value < "$1" || true
        printf '%s' "$value"
    else
        printf 'not exposed'
    fi
}

printf 'ZettLabs NAS Watchdog hardware report\n'
printf 'Model: %s\n' "${ZETTLABS_MODEL:-set ZETTLABS_MODEL before running}"
printf 'Kernel architecture: '; uname -m
printf 'Kernel release: '; uname -r
if [ -r /etc/os-release ]; then
    sed -n 's/^PRETTY_NAME=/OS: /p' /etc/os-release
else
    printf 'OS: not exposed\n'
fi
printf 'CPU model: '
sed -n 's/^model name[[:space:]]*: //p' /proc/cpuinfo | sed -n '1p'

printf '\n[Block devices: names only; no serials or SMART data]\n'
for device in /sys/block/*; do
    [ -e "$device" ] || continue
    printf '%s\n' "${device##*/}"
done

printf '\n[Hwmon temperatures and cooling]\n'
for chip in /sys/class/hwmon/hwmon*; do
    [ -r "$chip/name" ] || continue
    printf '\n%s name=%s\n' "${chip##*/}" "$(first_line "$chip/name")"
    if command -v readlink >/dev/null 2>&1; then
        printf 'device_path=%s\n' "$(readlink -f "$chip" 2>/dev/null || printf 'unavailable')"
    fi
    for input in "$chip"/temp*_input "$chip"/fan*_input; do
        [ -r "$input" ] || continue
        stem=${input%_input}
        printf '%s_input=%s\n' "${stem##*/}" "$(first_line "$input")"
        for suffix in label min max lcrit crit emergency alarm min_alarm max_alarm lcrit_alarm crit_alarm emergency_alarm; do
            value_file=${stem}_$suffix
            if [ -r "$value_file" ]; then
                printf '  %s_%s=%s\n' "${stem##*/}" "$suffix" "$(first_line "$value_file")"
            fi
        done
    done
    for pwm in "$chip"/pwm[0-9]* "$chip"/fan[0-9]*_pwm; do
        [ -r "$pwm" ] || continue
        case "$pwm" in *_enable|*_mode|*_start|*_stop) continue ;; esac
        printf '  %s=%s\n' "${pwm##*/}" "$(first_line "$pwm")"
    done
done

printf '\n[Thermal zones]\n'
for zone in /sys/class/thermal/thermal_zone[0-9]*; do
    [ -r "$zone/type" ] || continue
    printf '\n%s type=%s temp=%s mode=%s policy=%s\n' \
        "${zone##*/}" "$(first_line "$zone/type")" "$(first_line "$zone/temp")" \
        "$(first_line "$zone/mode")" "$(first_line "$zone/policy")"
    for trip in "$zone"/trip_point_[0-9]*_temp; do
        [ -r "$trip" ] || continue
        stem=${trip%_temp}
        printf '  %s temp=%s type=%s hyst=%s\n' "${stem##*/}" \
            "$(first_line "$trip")" "$(first_line "${stem}_type")" "$(first_line "${stem}_hyst")"
    done
done

printf '\n[Btrfs mounts: targets and options only]\n'
awk '$3 == "btrfs" { print $2, $4 }' /proc/mounts

printf '\n[Tool versions]\n'
if command -v docker >/dev/null 2>&1; then
    printf 'Docker client: '; docker --version 2>/dev/null || printf 'unavailable\n'
    printf 'Compose: '; docker compose version 2>/dev/null || printf 'unavailable\n'
    printf 'Docker server access: '
    if docker version --format '{{.Server.Version}}' >/dev/null 2>&1; then
        printf 'available\n'
    else
        printf 'unavailable without additional permissions (safe to ignore for a sensor report)\n'
    fi
else
    printf 'Docker: not found in PATH\n'
fi

printf '\nReview complete output for sensitive paths or labels before sharing.\n'
