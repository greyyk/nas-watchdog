package main

import (
	"math"
	"testing"
)

func TestClampInt(t *testing.T) {
	cases := []struct {
		in   int64
		want int
	}{
		{-5, 0},
		{0, 0},
		{1500, 1500},
		{math.MaxInt64, math.MaxInt},
	}
	for _, c := range cases {
		if got := clampInt(c.in); got != c.want {
			t.Errorf("clampInt(%d) = %d, want %d", c.in, got, c.want)
		}
	}
}

func TestClampUintToInt(t *testing.T) {
	if got := clampUintToInt(3); got != 3 {
		t.Errorf("clampUintToInt(3) = %d, want 3", got)
	}
	if got := clampUintToInt(math.MaxUint64); got != math.MaxInt32 {
		t.Errorf("clampUintToInt(MaxUint64) = %d, want %d", got, math.MaxInt32)
	}
}
