package utils

func ClampConcurrency(val, fallback, max int) int {
	if val <= 0 {
		return fallback
	}
	if val > max {
		return max
	}
	return val
}
