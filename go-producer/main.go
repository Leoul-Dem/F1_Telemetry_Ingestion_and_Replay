package main

import (
	//"context"

	"fmt"
	"log"
	"net/http"
	"strconv"
	"encoding/json"
	"sync"
	//"github.com/redis/go-redis/v9"
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
		log.Fatalf("Failed to fetch data from: %s", url)
	}
	log.Printf("URL: %s | Response status: %s\n", url, resp.Status)
	defer resp.Body.Close()
	if err := json.NewDecoder(resp.Body).Decode(&target); err != nil {
		if syntaxErr, ok := err.(*json.SyntaxError); ok {
        	fmt.Printf("JSON syntax error at byte %d: %w", syntaxErr.Offset, err)
	    }
	    if typeErr, ok := err.(*json.UnmarshalTypeError); ok {
	        fmt.Printf("JSON type mismatch: expected %s but got %s at field %s", 
	            typeErr.Type, typeErr.Value, typeErr.Field)
	    }
	}
}

func pushData(data interface{}) {
	log.Printf("Pushing data to : %v\n", data)
}

func main() {

	api_url := fmt.Sprintf("%s%s&%s", baseURL(Location), sessionKey(9161), date("2023-09-16T13:03:35.200", "2023-09-16T13:03:35.800"))
	api_url2 := fmt.Sprintf("%s%s&%s", baseURL(CarData), sessionKey(9161), date("2023-09-16T13:03:35.200", "2023-09-16T13:03:35.800"))

	var wg sync.WaitGroup
	location := []carLocation{}
	performance := []carPerformance{}
	
	wg.Add(2)
	go func() {
		defer wg.Done()
		fetchJson(api_url, &location)
	}()
	go func() {
		defer wg.Done()
		fetchJson(api_url2, &performance)
	}()
	wg.Wait()
	
	fmt.Println(len(location))
	fmt.Println(len(performance))

	// log.Printf("Fetching data from: %s\n", api_url)
	// resp, err := http.Get(api_url)
	// if err != nil {
	// 	log.Fatalf("Failed to fetch data from: %s", api_url)
	// }
	
	
	// log.Printf("Fetching data from: %s\n", api_url2)
	// resp2, err2 := http.Get(api_url2)
	// if err2 != nil {
	// 	log.Fatalf("Failed to fetch data from: %s", api_url2)
	// }
	
	

	// defer resp.Body.Close()
	// defer resp2.Body.Close()
	
	
	// fmt.Println(resp)
	// fmt.Println()
	// fmt.Println(resp2)

	// var points []driver_info
	// if err := json.NewDecoder(resp.Body).Decode(&points); err != nil {
	// 	log.Fatalf("Failed to decode JSON: %v", err)
	// }

	// log.Printf("Successfully fetched %d data points", len(points))

	// for i, p := range points {
	// 	fmt.Printf("%d. Session Key: %d\n", i+1, p.SessionKey)
	// 	fmt.Printf("%d. Driver Number: %d\n", i+1, p.DriverNumber)
	// 	fmt.Printf("%d. Speed: %d\n", i+1, p.Speed)
	// 	fmt.Printf("%d. Date: %s\n", i+1, p.Date)
	// 	fmt.Println("____________________")
	// }

}
