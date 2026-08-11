package httpapi

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/lainsmain/mneme/server/internal/store"
)

func TestAuthenticatedObjectAndManifestRoundTrip(t *testing.T) {
	dataStore, err := store.Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	defer dataStore.Close()
	token, _, err := dataStore.CreateToken(context.Background(), "test phone")
	if err != nil {
		t.Fatal(err)
	}
	server := httptest.NewServer(New(dataStore, slog.New(slog.NewTextHandler(io.Discard, nil))).Handler())
	defer server.Close()

	health, err := http.Get(server.URL + "/v1/health")
	if err != nil || health.StatusCode != http.StatusOK {
		t.Fatalf("health: status=%v error=%v", health.Status, err)
	}
	health.Body.Close()

	payload := []byte("opaque encrypted diary object")
	digest := sha256.Sum256(payload)
	hash := hex.EncodeToString(digest[:])
	upload := authenticatedRequest(t, http.MethodPut, server.URL+"/v1/objects/"+hash, token, payload)
	if upload.StatusCode != http.StatusCreated {
		t.Fatalf("upload returned %s: %s", upload.Status, readBody(upload.Body))
	}
	upload.Body.Close()

	download := authenticatedRequest(t, http.MethodGet, server.URL+"/v1/objects/"+hash, token, nil)
	if got := readBody(download.Body); got != string(payload) {
		t.Fatalf("downloaded %q, want %q", got, payload)
	}
	download.Body.Close()

	manifestPayload := []byte("encrypted manifest")
	request, err := http.NewRequest(http.MethodPut, server.URL+"/v1/manifests/test-device", bytes.NewReader(manifestPayload))
	if err != nil {
		t.Fatal(err)
	}
	request.Header.Set("Authorization", "Bearer "+token)
	request.Header.Set("X-Mneme-Revision", "1")
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	if response.StatusCode != http.StatusCreated {
		t.Fatalf("manifest upload returned %s: %s", response.Status, readBody(response.Body))
	}
	response.Body.Close()

	manifest := authenticatedRequest(t, http.MethodGet, server.URL+"/v1/manifests/test-device", token, nil)
	if got := readBody(manifest.Body); got != string(manifestPayload) {
		t.Fatalf("manifest %q, want %q", got, manifestPayload)
	}
	manifest.Body.Close()
}

func TestProtectedEndpointRejectsMissingToken(t *testing.T) {
	dataStore, err := store.Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	defer dataStore.Close()
	server := httptest.NewServer(New(dataStore, slog.New(slog.NewTextHandler(io.Discard, nil))).Handler())
	defer server.Close()

	response, err := http.Get(server.URL + "/v1/vault/status")
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusUnauthorized {
		t.Fatalf("got %s, want 401 Unauthorized", response.Status)
	}
}

func TestAuthenticatedPlaceSearchProxiesPhoton(t *testing.T) {
	photon := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api" || r.URL.Query().Get("q") != "Antwerp" || r.URL.Query().Get("limit") != "4" {
			t.Fatalf("unexpected Photon request %s", r.URL.String())
		}
		w.Header().Set("Content-Type", "application/geo+json")
		_, _ = io.WriteString(w, `{"type":"FeatureCollection","features":[]}`)
	}))
	defer photon.Close()

	dataStore, err := store.Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	defer dataStore.Close()
	token, _, err := dataStore.CreateToken(context.Background(), "test phone")
	if err != nil {
		t.Fatal(err)
	}
	server := httptest.NewServer(New(
		dataStore,
		slog.New(slog.NewTextHandler(io.Discard, nil)),
		WithPhotonURL(photon.URL),
	).Handler())
	defer server.Close()

	response := authenticatedRequest(t, http.MethodGet, server.URL+"/v1/places/search?q=Antwerp&limit=4", token, nil)
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK || !strings.Contains(readBody(response.Body), "FeatureCollection") {
		t.Fatalf("unexpected response %s", response.Status)
	}
}

func authenticatedRequest(t *testing.T, method, url, token string, body []byte) *http.Response {
	t.Helper()
	request, err := http.NewRequest(method, url, bytes.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	request.Header.Set("Authorization", "Bearer "+token)
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	return response
}

func readBody(body io.Reader) string {
	value, _ := io.ReadAll(body)
	return string(value)
}
