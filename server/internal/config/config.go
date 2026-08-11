package config

import "os"

type Config struct {
	ListenAddress string
	DataDirectory string
	PhotonURL     string
}

func FromEnvironment() Config {
	return Config{
		ListenAddress: valueOrDefault("MNEME_LISTEN", ":8080"),
		DataDirectory: valueOrDefault("MNEME_DATA_DIR", "./data"),
		PhotonURL:     valueOrDefault("MNEME_PHOTON_URL", "http://127.0.0.1:2322"),
	}
}

func valueOrDefault(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}
