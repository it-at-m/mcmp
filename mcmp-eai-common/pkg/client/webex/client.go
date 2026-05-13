// SPDX-FileCopyrightText: 2023 Landeshauptstadt München | it@M
//
// SPDX-License-Identifier: MIT

package webex

import (
	"fmt"
	"log"

	webexteams "github.com/euerla/go-cisco-webex-teams/sdk"
)

type Client struct {
	client *webexteams.Client
}

func New(authToken string, proxy string) *Client {
	c := new(Client)
	c.client = webexteams.NewClient()
	c.client.SetAuthToken(authToken)
	if len(proxy) > 0 {
		c.client.SetProxy(proxy)
	}
	return c
}

func (c Client) PrintRooms() {
	queryParams := &webexteams.ListRoomsQueryParams{
		Max: 10,
	}
	rooms, _, err := c.client.Rooms.ListRooms(queryParams)
	if err != nil {
		log.Println(err)
		return
	}
	for id, room := range rooms.Items {
		fmt.Println("GET Rooms:", id, room.ID, room.IsLocked, room.Title)
	}
}

func (c Client) PrintOrganizations() {
	queryParams := &webexteams.ListOrganizationsQueryParams{
		Max: 2,
	}
	organizations, _, err := c.client.Organizations.ListOrganizations(queryParams)
	if err != nil {
		log.Println(err)
		return
	}
	for id, organization := range organizations.Items {
		fmt.Println("GET Organization :", id, organization.ID, organization.DisplayName, organization.Created)
	}
}

func (c Client) PrintMessages(roomId string) {
	queryParams := &webexteams.ListMessagesQueryParams{
		RoomID: roomId,
	}
	messages, _, err := c.client.Messages.ListMessages(queryParams)
	if err != nil {
		log.Println(err)
		return
	}
	for id, message := range messages.Items {
		fmt.Println("GET Message:", id, message.ID, message.Text, message.Created, message.PersonEmail)
	}
}

func (c Client) PostTestMessage(text string, roomId string, parentId string) (string, error) {
	message := &webexteams.MessageCreateRequest{
		Text:     text,
		RoomID:   roomId,
		ParentID: parentId,
	}
	msg, _, err := c.client.Messages.CreateMessage(message)
	if err != nil {
		log.Println(err)
		return "", err
	}
	return msg.ID, nil
}

func (c Client) PostMarkdownMessage(markdown string, roomId string, parentId string) (string, error) {
	message := &webexteams.MessageCreateRequest{
		Markdown: markdown,
		RoomID:   roomId,
		ParentID: parentId,
	}
	msg, _, err := c.client.Messages.CreateMessage(message)
	if err != nil {
		log.Println(err)
		return "", err
	}
	return msg.ID, nil
}

func (c Client) PostAdaptiveCardMessage(attachment *webexteams.Attachment, roomId string, parentId string) (string, error) {
	message := &webexteams.MessageCreateRequest{
		Attachments: []webexteams.Attachment{*attachment},
		Text:        "AdaptiveCard",
		RoomID:      roomId,
		ParentID:    parentId,
	}
	msg, _, err := c.client.Messages.CreateMessage(message)
	if err != nil {
		log.Println(err)
		return "", err
	}
	return msg.ID, nil
}
