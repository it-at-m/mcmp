package mcmp

type EmailStatus string

const (
	EmailStatusNew     EmailStatus = "new"
	EmailStatusSent    EmailStatus = "sent"
	EmailStatusSkipped EmailStatus = "skipped"
	EmailStatusFailed  EmailStatus = "failed"
)

func (EmailStatus) GormDataType() string {
	return "email_status"
}
