package cipher

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha512"
	"encoding/hex"

	"golang.org/x/crypto/pbkdf2"
)

func deriveKey(passphrase string, salt []byte) ([]byte, []byte, error) {
	if salt == nil {
		salt = make([]byte, 8)
		if _, err := rand.Read(salt); err != nil {
			return nil, nil, err
		}
	}
	key := pbkdf2.Key([]byte(passphrase), salt, 4096, 32, sha512.New)
	return key, salt, nil
}

func Encrypt(passphrase string, data []byte) ([]byte, error) {
	key, salt, err := deriveKey(passphrase, nil)
	if err != nil {
		return nil, err
	}
	iv := make([]byte, 12)
	if _, err := rand.Read(iv); err != nil {
		return nil, err
	}
	blockCipher, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	gcm, err := cipher.NewGCM(blockCipher)
	if err != nil {
		return nil, err
	}
	cipherdata := gcm.Seal(nil, iv, data, nil)
	return append(append(salt, iv...), cipherdata...), nil
}

func Decrypt(passphrase string, data []byte) ([]byte, error) {
	salt := data[:8]
	iv := data[8:20]
	cipherdata := data[20:]
	key, _, _ := deriveKey(passphrase, salt)
	blockCipher, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	gcm, err := cipher.NewGCM(blockCipher)
	if err != nil {
		return nil, err
	}
	plaindata, err := gcm.Open(nil, iv, cipherdata, nil)
	if err != nil {
		return nil, err
	}
	return plaindata, nil
}

func EncryptString(passphrase string, data string) (string, error) {
	encryptData, err := Encrypt(passphrase, []byte(data))
	if err != nil {
		return "", err
	}
	return hex.EncodeToString(encryptData), nil
}

func DecryptString(passphrase string, data string) (string, error) {
	decodedData, err := hex.DecodeString(data)
	if err != nil {
		return "", err
	}
	decryptData, err := Decrypt(passphrase, decodedData)
	if err != nil {
		return "", err
	}
	return string(decryptData), nil
}
