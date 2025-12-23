package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"strconv"
	"sync"
	"reflect"
	"github.com/joho/godotenv"
	"os"
	"github.com/redis/go-redis/v9"
)

type carLocation struct {
	DriverNumber int    `json:"driver_number"`
	X            int    `json:"x"`
	Y            int    `json:"y"`
	Date         string `json:"date"`
}

type carPerformance struct {
	DriverNumber int    `json:"driver_number"`
	Speed        int    `json:"speed"`
	RPM          int    `json:"rpm"`
	NGear        int    `json:"n_gear"`
	Throttle     int    `json:"throttle"`
	Brake        int    `json:"brake"`
	Date         string `json:"date"`
}

type apiType int

const (
	Location apiType = iota
	CarData
)

func baseURL(apiType apiType) string {
	switch apiType {
	case Location:
		return "https://api.openf1.org/v1/location?"
	case CarData:
		return "https://api.openf1.org/v1/car_data?"
	default:
		return "https://api.openf1.org/v1"
	}
}
func driver(number int) string {
	return "driver_number=" + strconv.Itoa(number)
}
func sessionKey(sessionKey int) string {
	return "session_key=" + strconv.Itoa(sessionKey)
}
func date(date1 string, date2 string) string {
	return "date%3E" + date1 + "&date%3C" + date2
}

func fetchJson(url string, target interface{}) {
	log.Printf("Fetching data from: %s\n", url)
	resp, err := http.Get(url)
	if err != nil {
		log.Printf("Failed to fetch data from: %s", url)
		return
	}
	log.Printf("URL: %s | Response status: %s\n", url, resp.Status)
	defer resp.Body.Close()
	
	err = json.NewDecoder(resp.Body).Decode(&target)
	if err != nil {
		log.Printf("Failed to decode JSON response: %v", err)
	}
}

func pushData(client *redis.Client, stream string, data interface{}) {
	ctx := context.Background()
	val := reflect.ValueOf(data)

	if val.Kind() != reflect.Slice {
		log.Println("pushData: data is not a slice")
		return
	}
	log.Printf("Pushing %d items to stream %s...", val.Len(), stream)
	for i := 0; i < val.Len(); i++ {
		item := val.Index(i).Interface()
		jsonData, err := json.Marshal(item)
		if err != nil {
			log.Printf("Error marshaling item: %v\n", err)
			continue
		}
		
		err = client.XAdd(ctx, &redis.XAddArgs{
			Stream: stream,
			Values: map[string]interface{}{
				"data": jsonData,
			},
		}).Err()
		if err != nil {
			log.Printf("Error pushing to Redis stream %s: %v\n", stream, err)
		}
	}
	log.Printf("Finished pushing to %s", stream)
}

func main() {

	err := godotenv.Load()
	if err != nil {
		log.Fatal("Error loading .env file")
	}
	client := redis.NewClient(&redis.Options{
		Addr:     os.Getenv("REDIS_ADDR"),
	})
	api_url := fmt.Sprintf("%s%s&%s", baseURL(Location), sessionKey(9161), date("2023-09-16T13:03:35.200", "2023-09-16T13:03:35.800"))
	api_url2 := fmt.Sprintf("%s%s&%s", baseURL(CarData), sessionKey(9161), date("2023-09-16T13:03:35.200", "2023-09-16T13:03:35.800"))

	var wg sync.WaitGroup
	// for{
		wg.Add(2)
		go func() {
			defer wg.Done()
			location := []carLocation{}
			fetchJson(api_url, &location)
			pushData(client, "location", location)
		}()
		go func() {
			defer wg.Done()
			performance := []carPerformance{}
			fetchJson(api_url2, &performance)
			pushData(client, "performance", performance)
		}()
		wg.Wait()
	// }	
}
