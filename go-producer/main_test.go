package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

// Test generateTimeIntervals function
func TestGenerateTimeIntervals(t *testing.T) {
	tests := []struct {
		name     string
		input    RaceTimes
		expected int // expected number of intervals
	}{
		{
			name: "short session - single interval",
			input: RaceTimes{
				DateStart: "2024-05-12T14:00:00Z",
				DateEnd:   "2024-05-12T14:02:00Z",
			},
			expected: 1,
		},
		{
			name: "exactly 3 minutes - single interval",
			input: RaceTimes{
				DateStart: "2024-05-12T14:00:00Z",
				DateEnd:   "2024-05-12T14:03:00Z",
			},
			expected: 1,
		},
		{
			name: "6 minutes - two intervals",
			input: RaceTimes{
				DateStart: "2024-05-12T14:00:00Z",
				DateEnd:   "2024-05-12T14:06:00Z",
			},
			expected: 2,
		},
		{
			name: "10 minutes - four intervals",
			input: RaceTimes{
				DateStart: "2024-05-12T14:00:00Z",
				DateEnd:   "2024-05-12T14:10:00Z",
			},
			expected: 4,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := generateTimeIntervals(tt.input)
			if len(result) != tt.expected {
				t.Errorf("generateTimeIntervals() got %d intervals, want %d", len(result), tt.expected)
			}

			// Verify intervals are contiguous
			for i := 1; i < len(result); i++ {
				if result[i].DateStart != result[i-1].DateEnd {
					t.Errorf("Intervals not contiguous: interval %d ends at %s, interval %d starts at %s",
						i-1, result[i-1].DateEnd, i, result[i].DateStart)
				}
			}

			// Verify first interval starts at input start
			if len(result) > 0 && result[0].DateStart != tt.input.DateStart {
				t.Errorf("First interval should start at %s, got %s", tt.input.DateStart, result[0].DateStart)
			}

			// Verify last interval ends at input end
			if len(result) > 0 && result[len(result)-1].DateEnd != tt.input.DateEnd {
				t.Errorf("Last interval should end at %s, got %s", tt.input.DateEnd, result[len(result)-1].DateEnd)
			}
		})
	}
}

// Test fetchJSON with mock server
func TestFetchJSON(t *testing.T) {
	// Test successful fetch
	t.Run("successful fetch", func(t *testing.T) {
		expectedData := []LocationPoint{
			{SessionKey: 9140, DriverNumber: 1, Date: "2024-05-12T14:00:00Z", X: 100.5, Y: 200.5, Z: 0},
			{SessionKey: 9140, DriverNumber: 44, Date: "2024-05-12T14:00:00Z", X: 150.5, Y: 250.5, Z: 0},
		}

		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			json.NewEncoder(w).Encode(expectedData)
		}))
		defer server.Close()

		var result []LocationPoint
		err := fetchJSON(server.URL, &result)
		if err != nil {
			t.Fatalf("fetchJSON() error = %v", err)
		}

		if len(result) != len(expectedData) {
			t.Errorf("fetchJSON() got %d items, want %d", len(result), len(expectedData))
		}

		if result[0].DriverNumber != expectedData[0].DriverNumber {
			t.Errorf("fetchJSON() DriverNumber = %d, want %d", result[0].DriverNumber, expectedData[0].DriverNumber)
		}
	})

	// Test 404 response
	t.Run("404 response", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusNotFound)
		}))
		defer server.Close()

		var result []LocationPoint
		err := fetchJSON(server.URL, &result)
		if err == nil {
			t.Error("fetchJSON() expected error for 404, got nil")
		}
	})

	// Test invalid JSON
	t.Run("invalid JSON", func(t *testing.T) {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			w.Write([]byte("invalid json"))
		}))
		defer server.Close()

		var result []LocationPoint
		err := fetchJSON(server.URL, &result)
		if err == nil {
			t.Error("fetchJSON() expected error for invalid JSON, got nil")
		}
	})
}

// Test LocationPoint struct JSON parsing
func TestLocationPointParsing(t *testing.T) {
	jsonData := `{
		"session_key": 9140,
		"driver_number": 1,
		"date": "2024-05-12T14:00:00.123Z",
		"x": 1234.567,
		"y": 8901.234,
		"z": 0.5
	}`

	var point LocationPoint
	err := json.Unmarshal([]byte(jsonData), &point)
	if err != nil {
		t.Fatalf("Failed to parse LocationPoint: %v", err)
	}

	if point.SessionKey != 9140 {
		t.Errorf("SessionKey = %d, want 9140", point.SessionKey)
	}
	if point.DriverNumber != 1 {
		t.Errorf("DriverNumber = %d, want 1", point.DriverNumber)
	}
	if point.X != 1234.567 {
		t.Errorf("X = %f, want 1234.567", point.X)
	}
	if point.Y != 8901.234 {
		t.Errorf("Y = %f, want 8901.234", point.Y)
	}
}

// Test CarData struct JSON parsing
func TestCarDataParsing(t *testing.T) {
	jsonData := `{
		"session_key": 9140,
		"driver_number": 44,
		"date": "2024-05-12T14:00:00.123Z",
		"speed": 325,
		"rpm": 12500,
		"n_gear": 8,
		"throttle": 100,
		"brake": 0
	}`

	var carData CarData
	err := json.Unmarshal([]byte(jsonData), &carData)
	if err != nil {
		t.Fatalf("Failed to parse CarData: %v", err)
	}

	if carData.DriverNumber != 44 {
		t.Errorf("DriverNumber = %d, want 44", carData.DriverNumber)
	}
	if carData.Speed != 325 {
		t.Errorf("Speed = %d, want 325", carData.Speed)
	}
	if carData.RPM != 12500 {
		t.Errorf("RPM = %d, want 12500", carData.RPM)
	}
	if carData.NGear != 8 {
		t.Errorf("NGear = %d, want 8", carData.NGear)
	}
}

// Test RaceTimes struct JSON parsing
func TestRaceTimesParsing(t *testing.T) {
	jsonData := `{
		"date_start": "2024-05-12T14:00:00Z",
		"date_end": "2024-05-12T16:00:00Z"
	}`

	var times RaceTimes
	err := json.Unmarshal([]byte(jsonData), &times)
	if err != nil {
		t.Fatalf("Failed to parse RaceTimes: %v", err)
	}

	if times.DateStart != "2024-05-12T14:00:00Z" {
		t.Errorf("DateStart = %s, want 2024-05-12T14:00:00Z", times.DateStart)
	}
	if times.DateEnd != "2024-05-12T16:00:00Z" {
		t.Errorf("DateEnd = %s, want 2024-05-12T16:00:00Z", times.DateEnd)
	}
}

// Test getEnv function
func TestGetEnv(t *testing.T) {
	// Test with default value (env var not set)
	result := getEnv("NON_EXISTENT_VAR_12345", "default_value")
	if result != "default_value" {
		t.Errorf("getEnv() = %s, want default_value", result)
	}

	// Test with actual env var
	t.Setenv("TEST_VAR_12345", "actual_value")
	result = getEnv("TEST_VAR_12345", "default_value")
	if result != "actual_value" {
		t.Errorf("getEnv() = %s, want actual_value", result)
	}
}

// Test getEnvInt function
func TestGetEnvInt(t *testing.T) {
	// Test with default value
	result := getEnvInt("NON_EXISTENT_INT_VAR", 42)
	if result != 42 {
		t.Errorf("getEnvInt() = %d, want 42", result)
	}

	// Test with valid int
	t.Setenv("TEST_INT_VAR", "100")
	result = getEnvInt("TEST_INT_VAR", 42)
	if result != 100 {
		t.Errorf("getEnvInt() = %d, want 100", result)
	}

	// Test with invalid int (should return default)
	t.Setenv("TEST_INVALID_INT", "not_a_number")
	result = getEnvInt("TEST_INVALID_INT", 42)
	if result != 42 {
		t.Errorf("getEnvInt() with invalid int = %d, want 42", result)
	}
}

// Test time interval boundaries
func TestGenerateTimeIntervalsBoundaries(t *testing.T) {
	input := RaceTimes{
		DateStart: "2024-05-12T14:00:00Z",
		DateEnd:   "2024-05-12T14:09:30Z", // 9.5 minutes
	}

	result := generateTimeIntervals(input)

	// Should produce 4 intervals: 0-3, 3-6, 6-9, 9-9.5
	if len(result) != 4 {
		t.Errorf("Expected 4 intervals for 9.5 minutes, got %d", len(result))
	}

	// Verify each interval is at most 3 minutes
	for i, interval := range result {
		start, _ := time.Parse(time.RFC3339, interval.DateStart)
		end, _ := time.Parse(time.RFC3339, interval.DateEnd)
		duration := end.Sub(start)

		if duration > 3*time.Minute {
			t.Errorf("Interval %d duration %v exceeds 3 minutes", i, duration)
		}
	}
}

// Benchmark generateTimeIntervals
func BenchmarkGenerateTimeIntervals(b *testing.B) {
	input := RaceTimes{
		DateStart: "2024-05-12T14:00:00Z",
		DateEnd:   "2024-05-12T16:00:00Z", // 2 hour race
	}

	for i := 0; i < b.N; i++ {
		generateTimeIntervals(input)
	}
}
