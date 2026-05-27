package relay

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
)

func req(t *testing.T, srv *Server, method, path, contentType, body string) *httptest.ResponseRecorder {
	t.Helper()
	r := httptest.NewRequest(method, path, strings.NewReader(body))
	if contentType != "" {
		r.Header.Set("Content-Type", contentType)
	}
	rec := httptest.NewRecorder()
	srv.Handler().ServeHTTP(rec, r)
	return rec
}

func TestProtectedResourceMetadata(t *testing.T) {
	rec := req(t, newTestServer(t), http.MethodGet, "/.well-known/oauth-protected-resource", "", "")
	if rec.Code != http.StatusOK {
		t.Fatalf("want 200, got %d", rec.Code)
	}
	var meta map[string]any
	_ = json.Unmarshal(rec.Body.Bytes(), &meta)
	if meta["authorization_servers"] == nil || meta["resource"] == nil {
		t.Fatalf("missing fields: %v", meta)
	}
}

func TestAuthServerMetadataAdvertisesEndpoints(t *testing.T) {
	rec := req(t, newTestServer(t), http.MethodGet, "/.well-known/oauth-authorization-server", "", "")
	var meta map[string]any
	_ = json.Unmarshal(rec.Body.Bytes(), &meta)
	for _, k := range []string{"authorization_endpoint", "token_endpoint", "registration_endpoint"} {
		if meta[k] == nil {
			t.Fatalf("missing %s in %v", k, meta)
		}
	}
	methods, _ := meta["code_challenge_methods_supported"].([]any)
	if len(methods) == 0 || methods[0] != "S256" {
		t.Fatalf("expected S256 PKCE, got %v", meta["code_challenge_methods_supported"])
	}
}

func TestOAuthFullHTTPFlowYieldsToken(t *testing.T) {
	srv := newTestServer(t)
	redirect := "https://client.example/cb"

	// 1. Dynamic Client Registration
	regRec := req(t, srv, http.MethodPost, "/register", "application/json",
		`{"redirect_uris":["`+redirect+`"]}`)
	if regRec.Code != http.StatusCreated {
		t.Fatalf("register: want 201, got %d", regRec.Code)
	}
	var reg map[string]any
	_ = json.Unmarshal(regRec.Body.Bytes(), &reg)
	clientID, _ := reg["client_id"].(string)
	if clientID == "" {
		t.Fatal("no client_id")
	}

	// 2. Authorize (PKCE) → 302 redirect with code
	verifier := "verifier-abcdefghijklmnopqrstuvwxyz"
	sum := sha256.Sum256([]byte(verifier))
	challenge := base64.RawURLEncoding.EncodeToString(sum[:])
	authURL := "/authorize?response_type=code&client_id=" + url.QueryEscape(clientID) +
		"&redirect_uri=" + url.QueryEscape(redirect) +
		"&code_challenge=" + challenge + "&code_challenge_method=S256&state=xyz"
	authRec := req(t, srv, http.MethodGet, authURL, "", "")
	if authRec.Code != http.StatusFound {
		t.Fatalf("authorize: want 302, got %d", authRec.Code)
	}
	loc, _ := url.Parse(authRec.Header().Get("Location"))
	if loc.Query().Get("state") != "xyz" {
		t.Fatalf("state not echoed: %q", authRec.Header().Get("Location"))
	}
	code := loc.Query().Get("code")
	if code == "" {
		t.Fatal("no code in redirect")
	}

	// 3. Token exchange (form-encoded) → access_token
	form := url.Values{
		"grant_type":    {"authorization_code"},
		"code":          {code},
		"redirect_uri":  {redirect},
		"client_id":     {clientID},
		"code_verifier": {verifier},
	}
	tokRec := req(t, srv, http.MethodPost, "/token", "application/x-www-form-urlencoded", form.Encode())
	if tokRec.Code != http.StatusOK {
		t.Fatalf("token: want 200, got %d (%s)", tokRec.Code, tokRec.Body.String())
	}
	var tok map[string]any
	_ = json.Unmarshal(tokRec.Body.Bytes(), &tok)
	if tok["access_token"] == nil || tok["token_type"] != "Bearer" {
		t.Fatalf("bad token response: %v", tok)
	}
}
