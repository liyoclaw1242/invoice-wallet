// Package transport carries the mechanisms for reaching the phone: waking it via FCM
// when its WebSocket is disconnected. The real FCM implementation needs a Firebase
// service account; until then NoopWaker suffices whenever the phone holds a live WS.
package transport

import "context"

// Waker wakes the phone so it (re)connects its WebSocket.
type Waker interface {
	// Wake sends a data push to fcmToken. A nil error means "delivered to FCM",
	// not "phone connected" — the caller still waits for the WebSocket.
	Wake(ctx context.Context, fcmToken string) error
}

// NoopWaker does nothing; correct for dev and whenever the phone keeps a live WS.
type NoopWaker struct{}

func (NoopWaker) Wake(context.Context, string) error { return nil }
