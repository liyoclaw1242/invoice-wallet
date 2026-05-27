package config

import (
	"crypto/rand"
	"encoding/base64"
	"math/big"
)

// pairingAlphabet excludes visually ambiguous characters (no I/L/O/0/1).
const pairingAlphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

// RandomURLToken returns a URL-safe Base64 token from nBytes of crypto entropy.
// 24 bytes → a 32-character token (used for the MCP secret URL suffix & device_secret).
func RandomURLToken(nBytes int) string {
	b := make([]byte, nBytes)
	if _, err := rand.Read(b); err != nil {
		panic(err) // crypto/rand failure is unrecoverable
	}
	return base64.RawURLEncoding.EncodeToString(b)
}

// PairingCode returns an 8-character code grouped as XXXX-XXXX (e.g. "A3F9-K2P7").
func PairingCode() string {
	chars := make([]byte, 8)
	for i := range chars {
		n, err := rand.Int(rand.Reader, big.NewInt(int64(len(pairingAlphabet))))
		if err != nil {
			panic(err)
		}
		chars[i] = pairingAlphabet[n.Int64()]
	}
	return string(chars[:4]) + "-" + string(chars[4:])
}
