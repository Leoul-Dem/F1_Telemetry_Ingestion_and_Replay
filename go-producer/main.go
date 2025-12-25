package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"os"
	"os/signal"
	"strconv"
	"sync"
	"syscall"
	"time"

	"github.com/joho/godotenv"
	"github.com/redis/go-redis/v9"
)

// LocationPoint matches the plan's struct
type LocationPoint struct {
	SessionKey   int     `json:"session_key"`
	DriverNumber int     `json:"driver_number"`
	Date         string  `json:"date"`
	X            float64 `json:"x"`
	Y            float64 `json:"y"`
	Z            float64 `json:"z"`
}

// CarData for performance telemetry
type CarData struct {
	SessionKey   int    `json:"session_key"`
	DriverNumber int    `json:"driver_number"`
	Date         string `json:"date"`
	Speed        int    `json:"speed"`
	RPM          int    `json:"rpm"`
	NGear        int    `json:"n_gear"`
	Throttle     int    `json:"throttle"`
	Brake        int    `json:"brake"`
}

type RaceTimes struct {
	DateStart string `json:"date_start"`
	DateEnd   string `json:"date_end"`
}

const (
	baseLocationURL = "https://api.openf1.org/v1/location"
	baseCarDataURL  = "https://api.openf1.org/v1/car_data"
	baseTimesURL    = "https://api.openf1.org/v1/sessions"
	maxStreamLen    = 100000 // XTRIM limit
)

var (
	redisClient     *redis.Client
	ctx             = context.Background()
	lastFetchedTime string // Track last fetched timestamp to avoid duplicates
)

func main() {
	// Load .env (optional - will use environment vars if not found)
	_ = godotenv.Load()

	// Get config from environment
	redisAddr := getEnv("REDIS_ADDR", "localhost:6379")
	sessionKey := getEnv("SESSION_KEY", "9140")
	pollInterval := getEnvInt("POLL_INTERVAL_MS", 1000)

	// Initialize Redis client
	redisClient = redis.NewClient(&redis.Options{
		Addr: redisAddr,
	})

	// Test Redis connection
	if err := redisClient.Ping(ctx).Err(); err != nil {
		log.Fatalf("Failed to connect to Redis: %v", err)
	}
	log.Printf("Connected to Redis at %s", redisAddr)

	// Setup graceful shutdown
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, syscall.SIGINT, syscall.SIGTERM)

	// Start polling loop
	ticker := time.NewTicker(time.Duration(pollInterval) * time.Millisecond)
	defer ticker.Stop()

	log.Printf("Starting telemetry ingestion for session: %s", sessionKey)
	log.Printf("Poll interval: %dms", pollInterval)

	startEndTime := fetchTimes(sessionKey)

	reqIntervals := generateTimeIntervals(startEndTime)

	// Initial fetch
	fetchAndPush(sessionKey, reqIntervals[0])

	for i := 1; i < len(reqIntervals); i++ {
		select {
		case <-ticker.C:
			fetchAndPush(sessionKey, reqIntervals[i])
		case <-stop:
			log.Println("Shutting down gracefully...")
			redisClient.Close()
			return
		}
	}
}

func generateTimeIntervals(times RaceTimes) []RaceTimes {
	interval := 3 * time.Minute
	layout := time.RFC3339
	start, err := time.Parse(layout, times.DateStart)
	if err != nil {
		log.Fatalln("Could not parse time.")
	}
	end, err := time.Parse(layout, times.DateEnd)
	if err != nil {
		log.Fatalln("Could not parse time.")
	}
	var result []RaceTimes
	currStart := start
	for currStart.Before(end) {
		currEnd := currStart.Add(interval)

		if currEnd.After(end) {
			currEnd = end
		}

		result = append(result, RaceTimes{
			DateStart: currStart.Format(layout),
			DateEnd:   currEnd.Format(layout),
		})

		currStart = currEnd
	}
	return result
}

func fetchAndPush(sessionKey string, times RaceTimes) {
	var wg sync.WaitGroup
	wg.Add(2)

	go func() {
		defer wg.Done()
		locations := fetchLocations(sessionKey, times)
		if len(locations) > 0 {
			pushLocations(sessionKey, locations)
		}
	}()

	go func() {
		defer wg.Done()
		carData := fetchCarData(sessionKey, times)
		if len(carData) > 0 {
			pushCarData(sessionKey, carData)
		}
	}()

	wg.Wait()
}

func fetchTimes(sessionKey string) RaceTimes {
	url := fmt.Sprintf("%s?session_key=%s", baseTimesURL, sessionKey)

	var raceTimes []RaceTimes
	if err := fetchJSON(url, &raceTimes); err != nil {
		log.Fatalf("Error fetching the start and end times: %v", err)
	}

	return raceTimes[0]
}

func fetchLocations(sessionKey string, times RaceTimes) []LocationPoint {
	params := url.Values{}
	params.Add("session_key", sessionKey)
	params.Add("date>", times.DateStart)
	params.Add("date<", times.DateEnd)
	url := fmt.Sprintf("%s?%s", baseLocationURL, params.Encode())

	//url := fmt.Sprintf("%s?session_key=%s&date%%3E%s&date%%3C%s", baseLocationURL, sessionKey, times.DateStart, times.DateEnd)
	log.Println(url)

	var locations []LocationPoint
	if err := fetchJSON(url, &locations); err != nil {
		log.Printf("Error fetching locations: %v", err)
		return nil
	}

	log.Printf("Fetched %d location points", len(locations))
	return locations
}

func fetchCarData(sessionKey string, times RaceTimes) []CarData {
	params := url.Values{}
	params.Add("session_key", sessionKey)
	params.Add("date>", times.DateStart)
	params.Add("date<", times.DateEnd)
	url := fmt.Sprintf("%s?%s", baseCarDataURL, params.Encode())

	//url := fmt.Sprintf("%s?session_key=%s&date%%3E%s&date%%3C%s", baseCarDataURL, sessionKey, times.DateStart, times.DateEnd)

	var carData []CarData
	if err := fetchJSON(url, &carData); err != nil {
		log.Printf("Error fetching car data: %v", err)
		return nil
	}

	log.Printf("Fetched %d car data points", len(carData))
	return carData
}

func fetchJSON(url string, target interface{}) error {
	resp, err := http.Get(url)
	if err != nil {
		return fmt.Errorf("HTTP GET failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("unexpected status: %s", resp.Status)
	}

	return json.NewDecoder(resp.Body).Decode(target)
}

func pushLocations(sessionKey string, locations []LocationPoint) {
	streamKey := fmt.Sprintf("telemetry:location:%s", sessionKey)

	for _, loc := range locations {
		data, _ := json.Marshal(loc)

		err := redisClient.XAdd(ctx, &redis.XAddArgs{
			Stream: streamKey,
			Values: map[string]interface{}{
				"driver_number": loc.DriverNumber,
				"x":             loc.X,
				"y":             loc.Y,
				"z":             loc.Z,
				"timestamp":     loc.Date,
				"data":          data,
			},
		}).Err()

		if err != nil {
			log.Printf("Error pushing to Redis: %v", err)
		}
	}

	// Trim stream to prevent unbounded growth
	redisClient.XTrimMaxLenApprox(ctx, streamKey, maxStreamLen, 0)
	log.Printf("Pushed %d locations to %s", len(locations), streamKey)
}

func pushCarData(sessionKey string, carData []CarData) {
	streamKey := fmt.Sprintf("telemetry:cardata:%s", sessionKey)

	for _, cd := range carData {
		data, _ := json.Marshal(cd)

		err := redisClient.XAdd(ctx, &redis.XAddArgs{
			Stream: streamKey,
			Values: map[string]interface{}{
				"driver_number": cd.DriverNumber,
				"speed":         cd.Speed,
				"rpm":           cd.RPM,
				"gear":          cd.NGear,
				"throttle":      cd.Throttle,
				"brake":         cd.Brake,
				"timestamp":     cd.Date,
				"data":          data,
			},
		}).Err()

		if err != nil {
			log.Printf("Error pushing to Redis: %v", err)
		}
	}

	redisClient.XTrimMaxLenApprox(ctx, streamKey, maxStreamLen, 0)
	log.Printf("Pushed %d car data points to %s", len(carData), streamKey)
}

func getEnv(key, defaultVal string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return defaultVal
}

func getEnvInt(key string, defaultVal int) int {
	if val := os.Getenv(key); val != "" {
		if i, err := strconv.Atoi(val); err == nil {
			return i
		}
	}
	return defaultVal
}
