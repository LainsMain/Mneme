package httpapi

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/egoisticfoil/mneme/server/internal/store"
)

const (
	maxObjectBytes   = int64(512 << 20)
	maxManifestBytes = int64(16 << 20)
)

type Server struct {
	store   *store.Store
	logger  *slog.Logger
	mux     *http.ServeMux
	version string
}

type Option func(*Server)

func WithVersion(value string) Option {
	return func(server *Server) { server.version = value }
}

func New(dataStore *store.Store, logger *slog.Logger, options ...Option) *Server {
	s := &Server{
		store:   dataStore,
		logger:  logger,
		mux:     http.NewServeMux(),
		version: "dev",
	}
	for _, option := range options {
		option(s)
	}
	s.mux.HandleFunc("GET /v1/health", s.health)
	s.mux.Handle("GET /v1/vault/status", s.authenticate(http.HandlerFunc(s.status)))
	s.mux.Handle("PUT /v1/objects/{hash}", s.authenticate(http.HandlerFunc(s.putObject)))
	s.mux.Handle("GET /v1/objects/{hash}", s.authenticate(http.HandlerFunc(s.getObject)))
	s.mux.Handle("HEAD /v1/objects/{hash}", s.authenticate(http.HandlerFunc(s.getObject)))
	s.mux.Handle("PUT /v1/manifests/{device}", s.authenticate(http.HandlerFunc(s.putManifest)))
	s.mux.Handle("GET /v1/manifests/{device}", s.authenticate(http.HandlerFunc(s.getManifest)))
	s.mux.Handle("GET /v1/manifests", s.authenticate(http.HandlerFunc(s.listManifests)))
	return s
}

func (s *Server) Handler() http.Handler {
	return s.recoverPanics(s.logRequests(s.securityHeaders(s.mux)))
}

func (s *Server) health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{"status": "ok", "apiVersion": 1})
}

func (s *Server) status(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"service":       "mneme",
		"apiVersion":    1,
		"mode":          "opaque-encrypted-backup",
		"serverVersion": s.version,
	})
}

func (s *Server) putObject(w http.ResponseWriter, r *http.Request) {
	hash := r.PathValue("hash")
	size, err := s.store.PutObject(r.Context(), hash, r.Body, maxObjectBytes)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{"hash": hash, "size": size})
}

func (s *Server) getObject(w http.ResponseWriter, r *http.Request) {
	object, err := s.store.OpenObject(r.PathValue("hash"))
	if err != nil {
		writeError(w, http.StatusNotFound, "object not found")
		return
	}
	defer object.Close()
	info, err := object.Stat()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "inspect object")
		return
	}
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("ETag", `"`+r.PathValue("hash")+`"`)
	w.Header().Set("Content-Length", strconv.FormatInt(info.Size(), 10))
	if r.Method == http.MethodHead {
		w.WriteHeader(http.StatusOK)
		return
	}
	http.ServeContent(w, r, r.PathValue("hash"), info.ModTime(), object)
}

func (s *Server) putManifest(w http.ResponseWriter, r *http.Request) {
	revision, err := strconv.ParseInt(r.Header.Get("X-Mneme-Revision"), 10, 64)
	if err != nil || revision < 1 {
		writeError(w, http.StatusBadRequest, "X-Mneme-Revision must be a positive integer")
		return
	}
	temporary := &limitedBuffer{remaining: maxManifestBytes}
	if _, err := io.Copy(temporary, r.Body); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	digest := sha256.Sum256(temporary.bytes)
	hash := hex.EncodeToString(digest[:])
	if _, err := s.store.PutObject(r.Context(), hash, bytes.NewReader(temporary.bytes), maxManifestBytes); err != nil {
		writeError(w, http.StatusInternalServerError, "store encrypted manifest")
		return
	}
	if err := s.store.PutManifest(r.Context(), r.PathValue("device"), revision, hash); err != nil {
		writeError(w, http.StatusConflict, err.Error())
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{"deviceId": r.PathValue("device"), "revision": revision, "objectHash": hash})
}

func (s *Server) getManifest(w http.ResponseWriter, r *http.Request) {
	manifest, err := s.store.Manifest(r.Context(), r.PathValue("device"))
	if err != nil {
		writeError(w, http.StatusNotFound, "manifest not found")
		return
	}
	w.Header().Set("X-Mneme-Revision", strconv.FormatInt(manifest.Revision, 10))
	r.SetPathValue("hash", manifest.ObjectHash)
	s.getObject(w, r)
}

func (s *Server) listManifests(w http.ResponseWriter, r *http.Request) {
	manifests, err := s.store.ListManifests(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, "list manifests")
		return
	}
	if manifests == nil {
		manifests = []store.Manifest{}
	}
	writeJSON(w, http.StatusOK, manifests)
}

func (s *Server) authenticate(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		scheme, value, found := strings.Cut(r.Header.Get("Authorization"), " ")
		if !found || !strings.EqualFold(scheme, "Bearer") {
			w.Header().Set("WWW-Authenticate", "Bearer")
			writeError(w, http.StatusUnauthorized, "missing bearer token")
			return
		}
		if _, ok := s.store.Authenticate(r.Context(), value); !ok {
			w.Header().Set("WWW-Authenticate", "Bearer")
			writeError(w, http.StatusUnauthorized, "invalid bearer token")
			return
		}
		next.ServeHTTP(w, r)
	})
}

func (s *Server) securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Referrer-Policy", "no-referrer")
		next.ServeHTTP(w, r)
	})
}

func (s *Server) logRequests(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		started := time.Now()
		next.ServeHTTP(w, r)
		s.logger.Info("request", "method", r.Method, "path", r.URL.Path, "duration", time.Since(started))
	})
}

func (s *Server) recoverPanics(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if value := recover(); value != nil {
				s.logger.Error("request panic", "error", fmt.Sprint(value))
				writeError(w, http.StatusInternalServerError, "internal server error")
			}
		}()
		next.ServeHTTP(w, r)
	})
}

type limitedBuffer struct {
	bytes     []byte
	remaining int64
}

func (b *limitedBuffer) Write(value []byte) (int, error) {
	if int64(len(value)) > b.remaining {
		return 0, errors.New("manifest exceeds configured size limit")
	}
	b.bytes = append(b.bytes, value...)
	b.remaining -= int64(len(value))
	return len(value), nil
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}
