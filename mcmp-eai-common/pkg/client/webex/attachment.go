// SPDX-FileCopyrightText: 2023 Landeshauptstadt München | it@M
//
// SPDX-License-Identifier: MIT

package webex

import (
	"encoding/json"
	"log"
	"strconv"

	//"fmt"
	webexteams "github.com/euerla/go-cisco-webex-teams/sdk"
)

type AdaptiveCard struct {
	Type    string `json:"type"`
	Body    []Body `json:"body"`
	Schema  string `json:"$schema"`
	Version string `json:"version"`
}

type Body struct {
	Type                string   `json:"type"`
	Columns             []Column `json:"columns"`
	Spacing             string   `json:"spacing,omitempty"`
	HorizontalAlignment string   `json:"horizontalAlignment,omitempty"`
	Style               string   `json:"style,omitempty"`
	Bleed               bool     `json:"bleed,omitempty"`
	Text                string   `json:"text,omitempty"`
	Color               string   `json:"color,omitempty"`
	Size                string   `json:"size,omitempty"`
	Weight              string   `json:"weight,omitempty"`
	Wrap                bool     `json:"wrap,omitempty"`
}

type Column struct {
	Type    string `json:"type"`
	Items   []Item `json:"items"`
	Width   int    `json:"width"`
	Spacing string `json:"spacing,omitempty"`
}

type Item struct {
	Type                string `json:"type"`
	Size                string `json:"size,omitempty"`
	Text                string `json:"text,omitempty"`
	HorizontalAlignment string `json:"horizontalAlignment,omitempty"`
	Color               string `json:"color,omitempty"`
	Wrap                bool   `json:"wrap"`
	IsSubtle            bool   `json:"isSubtle"`
}

func createTextBlockItem(text string, horizontalAlignment string, color string, size string, wrap bool, isSubtle bool) Item {
	var item Item
	item.Type = "TextBlock"
	item.Text = text
	item.HorizontalAlignment = horizontalAlignment
	item.Color = color
	item.Wrap = wrap
	item.Size = size
	item.IsSubtle = isSubtle
	return item
}

func createColumnEntry(title string, value string, color string) Body {
	var colLeft Column
	colLeft.Type = "Column"
	colLeft.Width = 28
	colLeft.Items = []Item{createTextBlockItem(title+":", "Right", "Default", "Default", false, true)}
	colLeft.Spacing = "Small"
	var colRight Column
	colRight.Type = "Column"
	colRight.Width = 100 - colLeft.Width
	colRight.Items = []Item{createTextBlockItem(value, "Left", color, "Default", true, false)}
	colRight.Spacing = "Small"
	var colSet Body
	colSet.Type = "ColumnSet"
	colSet.Spacing = "Small"
	colSet.HorizontalAlignment = "Left"
	colSet.Columns = []Column{colLeft, colRight}
	return colSet
}

func createColumnTitle(title string, value string, style string) Body {
	var colLeft Column
	colLeft.Type = "Column"
	colLeft.Width = 60
	colLeft.Items = []Item{createTextBlockItem(title, "Left", "Default", "ExtraLarge", false, false)}
	var colRight Column
	colRight.Type = "Column"
	colRight.Width = 100 - colLeft.Width
	colRight.Items = []Item{createTextBlockItem(value, "Right", "Light", "Default", false, false)}
	var colSet Body
	colSet.Type = "ColumnSet"
	colSet.Spacing = "None"
	colSet.HorizontalAlignment = "Left"
	colSet.Style = style
	colSet.Bleed = true
	colSet.Columns = []Column{colLeft, colRight}
	return colSet
}

func createTextBlock(text string, color string, size string, weight string) Body {
	var textblock Body
	textblock.Type = "TextBlock"
	textblock.Text = text
	textblock.Color = color
	textblock.Size = size
	textblock.Weight = weight
	textblock.Wrap = true
	return textblock
}

func CreateFaultInstAttachment(env string, severity string, id uint, createdAt string, code string, errorType string, cause string, affectedObject string, description string, serverUsrLabel string, sn string, healthMsg string, comment string) *webexteams.Attachment {
	var card AdaptiveCard
	card.Type = "AdaptiveCard"
	card.Schema = "http://adaptivecards.io/schemas/adaptive-card.json"
	card.Version = "1.3"
	var body []Body
	body = append(body, createColumnTitle("⚠ Hardwarefehler", "it@M - IBS41", "attention"))
	body = append(body, createColumnEntry("Umgebung", "["+env+"](https://"+env+"/app/ucsm/index.html)", "Default"))
	if len(serverUsrLabel) > 0 {
		body = append(body, createColumnEntry("Server", serverUsrLabel, "Attention"))
	}
	if len(sn) > 0 {
		body = append(body, createColumnEntry("Serial", sn, "Default"))
	}
	body = append(body, createColumnEntry("Severity", severity, "Default"))
	body = append(body, createColumnEntry("ID", strconv.FormatUint(uint64(id), 10), "Default"))
	body = append(body, createColumnEntry("Created at", createdAt, "Default"))
	body = append(body, createColumnEntry("Code", code, "Default"))
	body = append(body, createColumnEntry("Type", errorType, "Default"))
	body = append(body, createColumnEntry("Cause", cause, "Default"))
	body = append(body, createColumnEntry("DN", affectedObject, "Default"))
	body = append(body, createColumnEntry("Description", description, "Default"))
	if len(healthMsg) > 0 {
		body = append(body, createColumnEntry("Health tab", healthMsg, "Default"))
	}
	if len(comment) > 0 {
		body = append(body, createTextBlock(comment, "Attention", "Large", "Bolder"))
	}
	card.Body = body
	b, err := json.Marshal(card)
	if err != nil {
		log.Println("error CreateFaultInstAttachment :", err)
	}
	// fmt.Printf(string(b))
	jsonMap := make(map[string]interface{})
	err = json.Unmarshal(b, &jsonMap)
	if err != nil {
		return nil
	}
	return &webexteams.Attachment{
		Content:     jsonMap,
		ContentType: "application/vnd.microsoft.card.adaptive",
	}
}

func CreateAwxAttachment(title string, vcenter string, vcenterUUID string, esxi string, jobId int, status string) *webexteams.Attachment {
	var card AdaptiveCard
	card.Type = "AdaptiveCard"
	card.Schema = "http://adaptivecards.io/schemas/adaptive-card.json"
	card.Version = "1.3"
	var body []Body
	body = append(body, createColumnTitle("🛈 Info", "it@M - IBS41", "accent"))
	body = append(body, createTextBlock(title, "Attention", "Large", "Bolder"))
	body = append(body, createColumnEntry("vCenter", "["+vcenter+"](https://"+vcenter+"/ui)", "Default"))
	body = append(body, createColumnEntry("vCenter UUID", vcenterUUID, "Default"))
	body = append(body, createColumnEntry("ESXi", esxi, "Default"))
	body = append(body, createColumnEntry("AWX-Job", "["+strconv.Itoa(jobId)+"](https://awx.muenchen.de/#/jobs/playbook/"+strconv.Itoa(jobId)+"/output)", "Default"))
	if status == "pending" {
		body = append(body, createColumnEntry("Status", "⌛"+status, "Default"))
	} else {
		body = append(body, createColumnEntry("Status", status, "Default"))
	}
	card.Body = body
	b, err := json.Marshal(card)
	if err != nil {
		log.Println("error CreateAwxAttachment :", err)
	}
	// fmt.Printf(string(b))
	jsonMap := make(map[string]interface{})
	err = json.Unmarshal(b, &jsonMap)
	if err != nil {
		return nil
	}
	return &webexteams.Attachment{
		Content:     jsonMap,
		ContentType: "application/vnd.microsoft.card.adaptive",
	}
}
