package cipher

import (
	"encoding/hex"
	"reflect"
	"testing"
)

func Test_Decrypt(t *testing.T) {
	type args struct {
		passphrase string
		ciphertext string
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		{"1", args{"my secret password 0815", "29b4b83aeb03cd57872a68188d94d4bca8b2d28acaf6186bffb580668cac30f3548b0e5772dc55ce2db3bd2a0d848516a22ad5d85313ac6e068cd6ea725f40e693017760c368"}, "Testing encryption and decryption."},
		{"2", args{"my secret password 0815", "b56a8787e9d1d88d96eae2ca0aca38f1eae9bd6629c80c95e71864c9e6dc102efc064004ecacfaa429fc2d712c923b7e93b1aed00f671cd61297354d192f53a151c32b1760f4"}, "Testing encryption and decryption."},
		{"3", args{"my secret password 0815", "7d81f9664a4eb0eba5f6bb6f30c0e51b4df09fbfbdc5139468caac3b8d724fd15900b264e48b6ea3fe67f34b1edd38629664f5d0fcfbb32fe86d3d933b45101152b6db5e95b6"}, "Testing encryption and decryption."},
		{"4", args{"my secret password", "fc81bc85a66faf36ba62457ed9a87f3a1f666e6e166612121414fa1a1599a5a25b3c58a9cc8c640e2fc80d8158ee0f8f6e23c13d341b15c1b0c4785b5030a943871dd9cb154f"}, "Testing encryption and decryption."},
		{"5", args{"mysecretpassword", "2557eea2be5573f59a4d8a593c5847dd1f4e8ad4068d03e5d8039f5788b693efd8f0abbfe3841d776f9a9aaa2e9f504c2b95a555915d9555bb27a1c2720df7d2d5f853599b37"}, "Testing encryption and decryption."},
		{"6", args{"mysecretpassword", "67cdb8d29675a818a764e403ba001e9c19dd49cdc40180c469203d4620a420ccf7ac327ca8ec4ce42e508fb0a484ecbf"}, "Hello world!"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ciphertext, _ := hex.DecodeString(tt.args.ciphertext)
			x, _ := Decrypt(tt.args.passphrase, ciphertext)
			if got := string(x); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("Decrypt() = \"%v\", want \"%v\"", got, tt.want)
			}
		})
	}
}

func Test_Encrypt_Decrypt(t *testing.T) {
	type args struct {
		passphrase string
		data       string
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		{"1", args{"my secret password 0815", "Testing encryption and decryption."}, "Testing encryption and decryption."},
		{"2", args{"my secret password", "Testing encryption and decryption."}, "Testing encryption and decryption."},
		{"3", args{"mysecretpassword", "Testing encryption and decryption."}, "Testing encryption and decryption."},
		{"4", args{"mysecretpassword", "Hello world!"}, "Hello world!"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ciphertext, _ := Encrypt(tt.args.passphrase, []byte(tt.args.data))
			plaintext, _ := Decrypt(tt.args.passphrase, ciphertext)
			if got := string(plaintext); !reflect.DeepEqual(got, tt.want) {
				t.Errorf("Encrypt -> Decrypt() = \"%v\", want \"%v\"", got, tt.want)
			}
		})
	}
}

func Test_DecryptString(t *testing.T) {
	type args struct {
		passphrase string
		ciphertext string
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		{"1", args{"my secret password 0815", "29b4b83aeb03cd57872a68188d94d4bca8b2d28acaf6186bffb580668cac30f3548b0e5772dc55ce2db3bd2a0d848516a22ad5d85313ac6e068cd6ea725f40e693017760c368"}, "Testing encryption and decryption."},
		{"2", args{"my secret password 0815", "b56a8787e9d1d88d96eae2ca0aca38f1eae9bd6629c80c95e71864c9e6dc102efc064004ecacfaa429fc2d712c923b7e93b1aed00f671cd61297354d192f53a151c32b1760f4"}, "Testing encryption and decryption."},
		{"3", args{"my secret password 0815", "7d81f9664a4eb0eba5f6bb6f30c0e51b4df09fbfbdc5139468caac3b8d724fd15900b264e48b6ea3fe67f34b1edd38629664f5d0fcfbb32fe86d3d933b45101152b6db5e95b6"}, "Testing encryption and decryption."},
		{"4", args{"my secret password", "fc81bc85a66faf36ba62457ed9a87f3a1f666e6e166612121414fa1a1599a5a25b3c58a9cc8c640e2fc80d8158ee0f8f6e23c13d341b15c1b0c4785b5030a943871dd9cb154f"}, "Testing encryption and decryption."},
		{"5", args{"mysecretpassword", "2557eea2be5573f59a4d8a593c5847dd1f4e8ad4068d03e5d8039f5788b693efd8f0abbfe3841d776f9a9aaa2e9f504c2b95a555915d9555bb27a1c2720df7d2d5f853599b37"}, "Testing encryption and decryption."},
		{"6", args{"mysecretpassword", "67cdb8d29675a818a764e403ba001e9c19dd49cdc40180c469203d4620a420ccf7ac327ca8ec4ce42e508fb0a484ecbf"}, "Hello world!"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			x, _ := DecryptString(tt.args.passphrase, tt.args.ciphertext)
			if got := x; !reflect.DeepEqual(got, tt.want) {
				t.Errorf("Decrypt() = \"%v\", want \"%v\"", got, tt.want)
			}
		})
	}
}

func Test_EncryptString_DecryptString(t *testing.T) {
	type args struct {
		passphrase string
		data       string
	}
	tests := []struct {
		name string
		args args
		want string
	}{
		{"1", args{"my secret password 0815", "Testing encryption and decryption."}, "Testing encryption and decryption."},
		{"2", args{"my secret password", "Testing encryption and decryption."}, "Testing encryption and decryption."},
		{"3", args{"mysecretpassword", "Testing encryption and decryption."}, "Testing encryption and decryption."},
		{"4", args{"mysecretpassword", "Hello world!"}, "Hello world!"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ciphertext, _ := EncryptString(tt.args.passphrase, tt.args.data)
			plaintext, _ := DecryptString(tt.args.passphrase, ciphertext)
			if got := plaintext; !reflect.DeepEqual(got, tt.want) {
				t.Errorf("Encrypt -> Decrypt() = \"%v\", want \"%v\"", got, tt.want)
			}
		})
	}
}
