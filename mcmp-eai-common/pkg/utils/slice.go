package utils

func Split[T any](slice []T, size int) [][]T {
	if len(slice) < 1 || size < 1 {
		return nil
	}
	var chunks [][]T
	for start := 0; start < len(slice); start += size {
		end := start + size
		if end > len(slice) {
			end = len(slice)
		}
		chunks = append(chunks, slice[start:end])
	}
	return chunks
}
