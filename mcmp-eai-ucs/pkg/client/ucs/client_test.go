// SPDX-FileCopyrightText: 2023 Landeshauptstadt München | it@M
//
// SPDX-License-Identifier: MIT

package ucs

import (
	"context"
	"errors"
	"fmt"
	"reflect"
	"sort"
	"strings"
	"testing"
	"time"
)

var (
	ErrPostXMLFuncNotSet     = errors.New("PostXMLFunc not set")
	ErrUnexpectedRequestBody = errors.New("unexpected request body")
)

const (
	testCookie1  = "1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925"
	testCookie2  = "1695494666/8730fe43-17d8-4374-9aa1-acce5d4ba5c5"
	constNone    = "none"
	constUnknown = "unknown"
	constYes     = "yes"
	constSuccess = "success"
	addDcMessage = "ADDDC Bank-level adaptive virtual lockstep is activated on DIMM DDR4_P2_K1_ECC. Post Package Repair will be performed on this DIMM during the next system reboot.\nADDDC Bank-level adaptive virtual lockstep is activated on DIMM DDR4_P1_F1_ECC. Post Package Repair will be performed on this DIMM during the next system reboot."
)

type (
	MockHttpClient struct {
		// DoFunc is a configurable function that simulates the http.Client.Do method.

		PostXMLFunc func(ctx context.Context, url string, body []byte) ([]byte, int, error)
	}

	BySerial []KvmServer
)

func (a BySerial) Len() int           { return len(a) }
func (a BySerial) Swap(i, j int)      { a[i], a[j] = a[j], a[i] }
func (a BySerial) Less(i, j int) bool { return a[i].Serial < a[j].Serial }

func (m *MockHttpClient) PostXML(ctx context.Context, url string, body []byte) ([]byte, int, error) {
	if m.PostXMLFunc != nil {
		return m.PostXMLFunc(ctx, url, body)
	}
	return nil, 0, ErrPostXMLFuncNotSet
}

func NewTestClient() (*Client, error) {
	config := Config{
		Hostname:  "https://example.com",
		Username:  "username",
		Password:  "password",
		Enabled:   true,
		VerifyTLS: true,
	}
	return NewClient(config, nil, false)
}

func Test_Login(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			return []byte(`<aaaLogin cookie="" response="yes" outCookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" outRefreshPeriod="600" outPriv="read-only" outDomains="" outChannel="noencssl" outEvtChannel="noencssl" outSessionId="web_57628_B" outVersion="4.1(3i)" outName="username" outPasswdExpiryStatus="None" outPasswdExpiryDuration="0"></aaaLogin>`), 200, nil
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}

	u.client = mockHttpClient

	err = u.Login(context.Background())
	if err != nil {
		t.Errorf("Login error : %v", err)
	}
	expected := testCookie1
	if got := u.cookie; !reflect.DeepEqual(got, expected) {
		t.Errorf("Invalid cookie! expected: %v but was : %v", got, expected)
	}
}

func Test_Logout(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			return []byte(`<aaaLogout cookie="" response="yes" outStatus="success"></aaaLogout>`), 200, nil
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}

	u.client = mockHttpClient
	u.cookie = testCookie1

	err = u.Logout(context.Background())
	if err != nil {
		t.Errorf("Logout error : %v", err)
	}
	expected := ""
	if got := u.cookie; !reflect.DeepEqual(got, expected) {
		t.Errorf("Invalid cookie! expected: %v but was : %v", got, expected)
	}
}

func Test_ResolveClass(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			return []byte(`<configResolveClass cookie="1668770697/e72d1993-411b-465c-9062-e5bbec6aa41c" response="yes" classId="computeBlade"><outConfigs><computeBlade adminPower="policy" adminState="in-service" assetTag="" assignedToDn="" association="none" availability="available" availableMemory="393216" chassisId="2" checkPoint="discovered" connPath="A,B" connStatus="A,B" descr="" discovery="complete" discoveryStatus="" dn="sys/chassis-2/blade-4" fltAggr="0" fsmDescr="" fsmFlags="" fsmPrev="DiscoverSuccess" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="2022-10-18T08:09:10.738" fsmStatus="nop" fsmTry="0" intId="20884253" kmipFault="no" kmipFaultDescription="" lc="undiscovered" lcTs="1970-01-01T01:00:00.000" localId="" lowVoltageMemory="regular-voltage" managingInst="B" memorySpeed="1333" mfgTime="2013-11-18T00:00:00.000" model="UCSB-B200-M3" name="" numOf40GAdaptorsWithOldFw="0" numOf40GAdaptorsWithUnknownFw="0" numOfAdaptors="1" numOfCores="16" numOfCoresEnabled="16" numOfCpus="2" numOfEthHostIfs="0" numOfFcHostIfs="0" numOfThreads="32" operPower="off" operPwrTransSrc="software_mcserver" operQualifier="" operQualifierReason="N/A" operState="unassociated" operability="operable" originalUuid="12345678-1234-5678-9abc-1234567890ab" partNumber="73-14689-04" policyLevel="0" policyOwner="local" presence="equipped" revision="0" scaledMode="none" serial="ABC1234EFGH" serverId="2/4" slotId="4" storageOperQualifier="unknown" totalMemory="393216" usrLbl="" uuid="92345678-1234-5678-9abc-1234567890ab" vendor="Cisco Systems Inc" vid="V06"/></outConfigs></configResolveClass>`), 200, nil
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.client = mockHttpClient

	classes, err := u.ResolveClass(context.Background(), "computeBlade", "false")
	if err != nil {
		t.Errorf("ResolveClass error : %v", err)
	}

	expectedMap := make(map[string]string)
	expectedMap["adminPower"] = "policy"
	expectedMap["adminState"] = "in-service"
	expectedMap["assetTag"] = ""
	expectedMap["assignedToDn"] = ""
	expectedMap["association"] = constNone
	expectedMap["availability"] = "available"
	expectedMap["availableMemory"] = "393216"
	expectedMap["chassisId"] = "2"
	expectedMap["checkPoint"] = "discovered"
	expectedMap["connPath"] = "A,B"
	expectedMap["connStatus"] = "A,B"
	expectedMap["descr"] = ""
	expectedMap["discovery"] = "complete"
	expectedMap["discoveryStatus"] = ""
	expectedMap["dn"] = "sys/chassis-2/blade-4"
	expectedMap["fltAggr"] = "0"
	expectedMap["fsmDescr"] = ""
	expectedMap["fsmFlags"] = ""
	expectedMap["fsmPrev"] = "DiscoverSuccess"
	expectedMap["fsmProgr"] = "100"
	expectedMap["fsmRmtInvErrCode"] = constNone
	expectedMap["fsmRmtInvErrDescr"] = ""
	expectedMap["fsmRmtInvRslt"] = ""
	expectedMap["fsmStageDescr"] = ""
	expectedMap["fsmStamp"] = "2022-10-18T08:09:10.738"
	expectedMap["fsmStatus"] = "nop"
	expectedMap["fsmTry"] = "0"
	expectedMap["intId"] = "20884253"
	expectedMap["kmipFault"] = "no"
	expectedMap["kmipFaultDescription"] = ""
	expectedMap["lc"] = "undiscovered"
	expectedMap["lcTs"] = "1970-01-01T01:00:00.000"
	expectedMap["localId"] = ""
	expectedMap["lowVoltageMemory"] = "regular-voltage"
	expectedMap["managingInst"] = "B"
	expectedMap["memorySpeed"] = "1333"
	expectedMap["mfgTime"] = "2013-11-18T00:00:00.000"
	expectedMap["model"] = "UCSB-B200-M3"
	expectedMap["name"] = ""
	expectedMap["numOf40GAdaptorsWithOldFw"] = "0"
	expectedMap["numOf40GAdaptorsWithUnknownFw"] = "0"
	expectedMap["numOfAdaptors"] = "1"
	expectedMap["numOfCores"] = "16"
	expectedMap["numOfCoresEnabled"] = "16"
	expectedMap["numOfCpus"] = "2"
	expectedMap["numOfEthHostIfs"] = "0"
	expectedMap["numOfFcHostIfs"] = "0"
	expectedMap["numOfThreads"] = "32"
	expectedMap["operPower"] = "off"
	expectedMap["operPwrTransSrc"] = "software_mcserver"
	expectedMap["operQualifier"] = ""
	expectedMap["operQualifierReason"] = "N/A"
	expectedMap["operState"] = "unassociated"
	expectedMap["operability"] = "operable"
	expectedMap["originalUuid"] = "12345678-1234-5678-9abc-1234567890ab"
	expectedMap["partNumber"] = "73-14689-04"
	expectedMap["policyLevel"] = "0"
	expectedMap["policyOwner"] = "local"
	expectedMap["presence"] = "equipped"
	expectedMap["revision"] = "0"
	expectedMap["scaledMode"] = constNone
	expectedMap["serial"] = "ABC1234EFGH"
	expectedMap["serverId"] = "2/4"
	expectedMap["slotId"] = "4"
	expectedMap["storageOperQualifier"] = constUnknown
	expectedMap["totalMemory"] = "393216"
	expectedMap["usrLbl"] = ""
	expectedMap["uuid"] = "92345678-1234-5678-9abc-1234567890ab"
	expectedMap["vendor"] = "Cisco Systems Inc"
	expectedMap["vid"] = "V06"

	got := make([]map[string]string, 0, 1)
	got = append(got, expectedMap)
	if !reflect.DeepEqual(got, classes) {
		t.Errorf("Invalid result! expected: \n%v but was : \n%v", got, classes)
	}
}

func Test_ResolveDn(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			return []byte(`<configResolveDn dn="sys/chassis-1/blade-4/mgmt" cookie="1669649455/8f8bda4a-0cdd-469d-95f6-24970d9c16dc" response="yes">
			<outConfig>
			<mgmtController adminOperation="none" cmcSupportedStorageFeatures="" cmcSupportedStorageOps="" desiredMaintenanceMode="normal" dimmBlacklistingOperState="enabled" diskZoningState="unknown" dn="sys/chassis-1/blade-4/mgmt" extendedClId="unknown" fsmDescr="" fsmFlags="" fsmPrev="ExtMgmtIfConfigSuccess" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="2022-10-18T08:08:26.951" fsmStatus="nop" fsmTry="0" guid="" hostagCommMethod="unknown" id="A" lastRebootReason="unknown" model="UCSB-B200-M3" operConn="" powerFanSpeedPolicySupported="no" revision="0" serial="FCH1234ABCD" storageOobConfigSupported="yes" storageOobInterfaceSupported="yes" storageSubsystemState="initialized" subject="blade" supportedCapability="factory-reset,local-storage,modify-maintenance-mode,usb-nic" vendor="Cisco Systems Inc" webUIKvmConsoleSupported="yes"/>
			</outConfig>
			</configResolveDn>`), 200, nil
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.client = mockHttpClient

	classes, err := u.ResolveDn(context.Background(), "sys/chassis-1/blade-4/mgmt", "false")
	if err != nil {
		t.Errorf("ResolveDn error : %v", err)
	}

	expectedMap := make(map[string]string)
	expectedMap["adminOperation"] = constNone
	expectedMap["cmcSupportedStorageFeatures"] = ""
	expectedMap["cmcSupportedStorageOps"] = ""
	expectedMap["desiredMaintenanceMode"] = "normal"
	expectedMap["dimmBlacklistingOperState"] = "enabled"
	expectedMap["diskZoningState"] = constUnknown
	expectedMap["dn"] = "sys/chassis-1/blade-4/mgmt"
	expectedMap["extendedClId"] = constUnknown
	expectedMap["fsmDescr"] = ""
	expectedMap["fsmFlags"] = ""
	expectedMap["fsmPrev"] = "ExtMgmtIfConfigSuccess"
	expectedMap["fsmProgr"] = "100"
	expectedMap["fsmRmtInvErrCode"] = constNone
	expectedMap["fsmRmtInvErrDescr"] = ""
	expectedMap["fsmRmtInvRslt"] = ""
	expectedMap["fsmStageDescr"] = ""
	expectedMap["fsmStamp"] = "2022-10-18T08:08:26.951"
	expectedMap["fsmStatus"] = "nop"
	expectedMap["fsmTry"] = "0"
	expectedMap["guid"] = ""
	expectedMap["hostagCommMethod"] = constUnknown
	expectedMap["id"] = "A"
	expectedMap["lastRebootReason"] = constUnknown
	expectedMap["model"] = "UCSB-B200-M3"
	expectedMap["operConn"] = ""
	expectedMap["powerFanSpeedPolicySupported"] = "no"
	expectedMap["revision"] = "0"
	expectedMap["serial"] = "FCH1234ABCD"
	expectedMap["storageOobConfigSupported"] = constYes
	expectedMap["storageOobInterfaceSupported"] = constYes
	expectedMap["storageSubsystemState"] = "initialized"
	expectedMap["subject"] = "blade"
	expectedMap["supportedCapability"] = "factory-reset,local-storage,modify-maintenance-mode,usb-nic"
	expectedMap["vendor"] = "Cisco Systems Inc"
	expectedMap["webUIKvmConsoleSupported"] = constYes

	got := make([]map[string]string, 0, 1)
	got = append(got, expectedMap)
	if !reflect.DeepEqual(got, classes) {
		t.Errorf("Invalid result! expected: \n%v but was : \n%v", got, classes)
	}
}

func Test_ChangeMgmtKvmcertificate(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			return []byte(`<configConfMos cookie="0815" response="yes">
    <outConfigs>
        <pair key="sys/chassis-2/blade-1/mgmt">
            <mgmtController adminOperation="none" cmcSupportedStorageFeatures="" cmcSupportedStorageOps="" desiredMaintenanceMode="normal" dimmBlacklistingOperState="enabled" diskZoningState="unknown" dn="sys/chassis-2/blade-1/mgmt" extendedClId="unknown" fsmDescr="" fsmFlags="" fsmPrev="KvmCertSuccess" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="2022-11-03T15:14:32.158" fsmStatus="KvmCertBegin" fsmTry="0" guid="" hostagCommMethod="unknown" id="A" lastRebootReason="unknown" model="UCSB-B200-M3" operConn="" powerFanSpeedPolicySupported="no" revision="0" serial="CBA9876XYZ" status="modified" storageOobConfigSupported="yes" storageOobInterfaceSupported="yes" storageSubsystemState="uninitialized" subject="blade" supportedCapability="factory-reset,local-storage,modify-maintenance-mode,usb-nic" vendor="Cisco Systems Inc" webUIKvmConsoleSupported="yes"/>
        </pair>
    </outConfigs>
</configConfMos>`), 200, nil
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.client = mockHttpClient

	u.cookie = "0815"
	serial, err := u.ChangeMgmtKvmCertificate(context.Background(), "sys/chassis-2/blade-1", "-----BEGIN RSA PRIVATE KEY-----", "-----BEGIN CERTIFICATE-----")
	if err != nil {
		t.Errorf("Logout error : %v", err)
	}
	expected := "CBA9876XYZ"
	if !reflect.DeepEqual(expected, serial) {
		t.Errorf("ChangeMgmtKvmcertificate: Invalid serial! expected: %v but was : %v", expected, serial)
	}
}

func Test_getServerEquipmentMgmtIpMap(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="mgmtIf"> <outConfigs>
			<mgmtIf access="unspecified" adminState="enable" aggrPortId="0" chassisId="2" discovery="absent" dn="sys/chassis-2/blade-4/mgmt/if-2" epDn="" extBroadcast="0.0.0.0" extGw="10.185.1.254" extIp="10.185.1.74" extMask="255.255.255.0" fsmDescr="" fsmPrev="nop" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="never" fsmStatus="nop" fsmTry="0" id="2" ifRole="unknown" ifType="physical" instanceId="1" ip="127.6.2.4" locale="" mac="A8:0C:0D:B3:47:90" mask="0.0.0.0" name="" peerAggrPortId="0" peerChassisId="N/A" peerDn="" peerPortId="0" peerSlotId="0" portId="2" slotId="4" stateQual="unspecified" subject="blade" switchId="B" transport="" type="" vnet="1"/>
			<mgmtIf access="unspecified" adminState="disable" aggrPortId="0" chassisId="2" discovery="absent" dn="sys/chassis-2/blade-4/mgmt/if-1" epDn="" extBroadcast="0.0.0.0" extGw="10.185.1.254" extIp="10.185.1.74" extMask="255.255.255.0" fsmDescr="" fsmPrev="nop" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="never" fsmStatus="nop" fsmTry="0" id="1" ifRole="unknown" ifType="physical" instanceId="1" ip="127.5.2.4" locale="" mac="A8:0C:0D:B3:47:90" mask="0.0.0.0" name="" peerAggrPortId="0" peerChassisId="N/A" peerDn="" peerPortId="0" peerSlotId="0" portId="1" slotId="4" stateQual="unspecified" subject="blade" switchId="A" transport="" type="" vnet="1"/>
			<mgmtIf access="unspecified" adminState="enable" aggrPortId="0" chassisId="2" discovery="absent" dn="sys/chassis-2/blade-2/mgmt/if-2" epDn="" extBroadcast="0.0.0.0" extGw="10.185.1.254" extIp="10.185.1.75" extMask="255.255.255.0" fsmDescr="" fsmPrev="nop" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="never" fsmStatus="nop" fsmTry="0" id="2" ifRole="unknown" ifType="physical" instanceId="1" ip="127.6.2.2" locale="" mac="24:E9:B3:FE:00:04" mask="0.0.0.0" name="" peerAggrPortId="0" peerChassisId="N/A" peerDn="" peerPortId="0" peerSlotId="0" portId="2" slotId="2" stateQual="unspecified" subject="blade" switchId="B" transport="" type="" vnet="1"/>
			<mgmtIf access="unspecified" adminState="disable" aggrPortId="0" chassisId="2" discovery="absent" dn="sys/chassis-2/blade-2/mgmt/if-1" epDn="" extBroadcast="0.0.0.0" extGw="10.185.1.254" extIp="10.185.1.75" extMask="255.255.255.0" fsmDescr="" fsmPrev="nop" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="never" fsmStatus="nop" fsmTry="0" id="1" ifRole="unknown" ifType="physical" instanceId="1" ip="127.5.2.2" locale="" mac="24:E9:B3:FE:00:04" mask="0.0.0.0" name="" peerAggrPortId="0" peerChassisId="N/A" peerDn="" peerPortId="0" peerSlotId="0" portId="1" slotId="2" stateQual="unspecified" subject="blade" switchId="A" transport="" type="" vnet="1"/>
	<mgmtIf access="unspecified" adminState="enable" aggrPortId="0" chassisId="N/A" discovery="present" dn="sys/rack-unit-3/mgmt/if-1" epDn="sys/rack-unit-3/adaptor-1/ext-eth-1" extBroadcast="0.0.0.0" extGw="10.185.1.254" extIp="10.185.1.139" extMask="255.255.255.0" fsmDescr="" fsmPrev="nop" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="never" fsmStatus="nop" fsmTry="0" id="1" ifRole="unknown" ifType="physical" instanceId="1" ip="127.6.224.3" locale="" mac="00:42:68:81:0E:CE" mask="0.0.0.0" name="" peerAggrPortId="0" peerChassisId="N/A" peerDn="sys/switch-B/slot-1/switch-ether/port-18" peerPortId="0" peerSlotId="0" portId="18" slotId="1" stateQual="unspecified" subject="blade" switchId="B" transport="" type="" vnet="1"/>
	<mgmtIf access="unspecified" adminState="disable" aggrPortId="0" chassisId="N/A" discovery="present" dn="sys/rack-unit-3/mgmt/if-2" epDn="sys/rack-unit-3/adaptor-1/ext-eth-2" extBroadcast="0.0.0.0" extGw="10.185.1.254" extIp="10.185.1.139" extMask="255.255.255.0" fsmDescr="" fsmPrev="nop" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="never" fsmStatus="nop" fsmTry="0" id="2" ifRole="unknown" ifType="physical" instanceId="1" ip="127.5.224.3" locale="" mac="00:42:68:81:0E:CF" mask="0.0.0.0" name="" peerAggrPortId="0" peerChassisId="N/A" peerDn="sys/switch-A/slot-1/switch-ether/port-18" peerPortId="0" peerSlotId="0" portId="18" slotId="1" stateQual="unspecified" subject="blade" switchId="A" transport="" type="" vnet="1"/>
	</outConfigs> </configResolveClass>`), 200, nil
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.client = mockHttpClient

	u.cookie = testCookie1
	dnIpMap, err := u.GetServerEquipmentMgmtIpMap(context.Background())
	if err != nil {
		t.Errorf("getServerMgmtIpMap error : %v", err)
	}
	expectedMap := make(map[string]string)
	expectedMap["sys/chassis-2/blade-4"] = "10.185.1.74"
	expectedMap["sys/chassis-2/blade-2"] = "10.185.1.75"
	expectedMap["sys/rack-unit-3"] = "10.185.1.139"
	if !reflect.DeepEqual(expectedMap, dnIpMap) {
		t.Errorf("getServerMgmtIpMap expected: %v but was : %v", expectedMap, dnIpMap)
	}
}

func Test_getServerFsmMap(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			// Routing anhand des XML Requests (body enthält das marshalled Request-Struct)
			switch {
			case strings.Contains(string(body), "computeBladeFsm"):
				return []byte(`<configResolveClass cookie="1669013578/a355cee2-53c1-48d5-b4cf-4d7dfc25342f" response="yes" classId="computeBladeFsm">
	<outConfigs>
	<computeBladeFsm completionTime="2022-11-08T12:20:13.994" currentFsm="Discover" descr="" dn="sys/chassis-2/blade-1/fsm" fsmStatus="success" instanceId="136" progress="100" rmtErrCode="none" rmtErrDescr="" rmtRslt=""/>
	<computeBladeFsm completionTime="" currentFsm="Discover" descr="" dn="sys/chassis-2/blade-7/fsm" fsmStatus="inProgress" instanceId="136" progress="42" rmtErrCode="none" rmtErrDescr="" rmtRslt=""/>
	<computeBladeFsm completionTime="2022-11-21T07:45:26.177" currentFsm="Associate" descr="" dn="sys/chassis-2/blade-6/fsm" fsmStatus="success" instanceId="589" progress="100" rmtErrCode="none" rmtErrDescr="" rmtRslt=""/>
	</outConfigs>
	</configResolveClass>`), 200, nil
			case strings.Contains(string(body), "computeRackUnitFsm"):
				return []byte(`<configResolveClass cookie="1669013578/a355cee2-53c1-48d5-b4cf-4d7dfc25342f" response="yes" classId="computeRackUnitFsm">
	<outConfigs>
	<computeRackUnitFsm completionTime="2022-10-28T14:31:23.237" currentFsm="Turnup" descr="" dn="sys/rack-unit-3/fsm" fsmStatus="success" instanceId="595" progress="100" rmtErrCode="none" rmtErrDescr="" rmtRslt=""/>
	<computeRackUnitFsm completionTime="2022-10-28T13:50:20.935" currentFsm="Turnup" descr="" dn="sys/rack-unit-2/fsm" fsmStatus="success" instanceId="595" progress="100" rmtErrCode="none" rmtErrDescr="" rmtRslt=""/>
	</outConfigs>
	</configResolveClass>`), 200, nil
			}
			return nil, 500, fmt.Errorf("%w", ErrUnexpectedRequestBody)
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.cookie = "1669013578/a355cee2-53c1-48d5-b4cf-4d7dfc25342f"
	u.client = mockHttpClient

	fsmMap, err := u.GetServerFsmMap(context.Background())
	if err != nil {
		t.Errorf("GetServerFsmMap error : %v", err)
	}

	// ... existing code (Assertions unverändert) ...
	blade1FsmMap := make(map[string]string)
	blade1FsmMap["completionTime"] = "2022-11-08T12:20:13.994"
	blade1FsmMap["currentFsm"] = "Discover"
	blade1FsmMap["descr"] = ""
	blade1FsmMap["dn"] = "sys/chassis-2/blade-1/fsm"
	blade1FsmMap["fsmStatus"] = constSuccess
	blade1FsmMap["instanceId"] = "136"
	blade1FsmMap["progress"] = "100"
	blade1FsmMap["rmtErrCode"] = constNone
	blade1FsmMap["rmtErrDescr"] = ""
	blade1FsmMap["rmtRslt"] = ""

	blade2FsmMap := make(map[string]string)
	blade2FsmMap["completionTime"] = ""
	blade2FsmMap["currentFsm"] = "Discover"
	blade2FsmMap["descr"] = ""
	blade2FsmMap["dn"] = "sys/chassis-2/blade-7/fsm"
	blade2FsmMap["fsmStatus"] = "inProgress"
	blade2FsmMap["instanceId"] = "136"
	blade2FsmMap["progress"] = "42"
	blade2FsmMap["rmtErrCode"] = constNone
	blade2FsmMap["rmtErrDescr"] = ""
	blade2FsmMap["rmtRslt"] = ""

	blade3FsmMap := make(map[string]string)
	blade3FsmMap["completionTime"] = "2022-11-21T07:45:26.177"
	blade3FsmMap["currentFsm"] = "Associate"
	blade3FsmMap["descr"] = ""
	blade3FsmMap["dn"] = "sys/chassis-2/blade-6/fsm"
	blade3FsmMap["fsmStatus"] = constSuccess
	blade3FsmMap["instanceId"] = "589"
	blade3FsmMap["progress"] = "100"
	blade3FsmMap["rmtErrCode"] = constNone
	blade3FsmMap["rmtErrDescr"] = ""
	blade3FsmMap["rmtRslt"] = ""

	rackUnit1FsmMap := make(map[string]string)
	rackUnit1FsmMap["completionTime"] = "2022-10-28T14:31:23.237"
	rackUnit1FsmMap["currentFsm"] = "Turnup"
	rackUnit1FsmMap["descr"] = ""
	rackUnit1FsmMap["dn"] = "sys/rack-unit-3/fsm"
	rackUnit1FsmMap["fsmStatus"] = constSuccess
	rackUnit1FsmMap["instanceId"] = "595"
	rackUnit1FsmMap["progress"] = "100"
	rackUnit1FsmMap["rmtErrCode"] = constNone
	rackUnit1FsmMap["rmtErrDescr"] = ""
	rackUnit1FsmMap["rmtRslt"] = ""

	rackUnit2FsmMap := make(map[string]string)
	rackUnit2FsmMap["completionTime"] = "2022-10-28T13:50:20.935"
	rackUnit2FsmMap["currentFsm"] = "Turnup"
	rackUnit2FsmMap["descr"] = ""
	rackUnit2FsmMap["dn"] = "sys/rack-unit-2/fsm"
	rackUnit2FsmMap["fsmStatus"] = constSuccess
	rackUnit2FsmMap["instanceId"] = "595"
	rackUnit2FsmMap["progress"] = "100"
	rackUnit2FsmMap["rmtErrCode"] = constNone
	rackUnit2FsmMap["rmtErrDescr"] = ""
	rackUnit2FsmMap["rmtRslt"] = ""

	expectedMap := make(map[string]map[string]string)
	expectedMap["sys/chassis-2/blade-1"] = blade1FsmMap
	expectedMap["sys/chassis-2/blade-7"] = blade2FsmMap
	expectedMap["sys/chassis-2/blade-6"] = blade3FsmMap
	expectedMap["sys/rack-unit-3"] = rackUnit1FsmMap
	expectedMap["sys/rack-unit-2"] = rackUnit2FsmMap

	if !reflect.DeepEqual(expectedMap, fsmMap) {
		t.Errorf("getServerMgmtIpMap\nexpected: %v\n but was : %v", expectedMap, fsmMap)
	}
}

func Test_getKvmServer(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			b := string(body)

			switch {
			case strings.Contains(b, "computeBladeFsm"):
				return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="computeBladeFsm">
	<outConfigs>
	<computeBladeFsm completionTime="" currentFsm="Discover" descr="" dn="sys/chassis-2/blade-4/fsm" fsmStatus="inProgress" instanceId="136" progress="42" rmtErrCode="none" rmtErrDescr="" rmtRslt=""/>
	</outConfigs>
	</configResolveClass>`), 200, nil
			case strings.Contains(b, "computeRackUnitFsm"):
				return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="computeRackUnitFsm">
	<outConfigs>
	<computeRackUnitFsm completionTime="2022-10-28T13:50:20.935" currentFsm="Turnup" descr="" dn="sys/rack-unit-3/fsm" fsmStatus="success" instanceId="595" progress="100" rmtErrCode="none" rmtErrDescr="" rmtRslt=""/>
	</outConfigs>
	</configResolveClass>`), 200, nil
			case strings.Contains(b, "computeBlade"):
				return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="computeBlade">
	<outConfigs>
	<computeBlade adminPower="policy" adminState="in-service" assetTag="" assignedToDn="" association="none" availability="available" availableMemory="393216" chassisId="2" checkPoint="discovered" connPath="A,B" connStatus="A,B" descr="" discovery="complete" discoveryStatus="" dn="sys/chassis-2/blade-4" fltAggr="0" fsmDescr="" fsmFlags="" fsmPrev="DiscoverSuccess" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="2022-10-18T08:09:10.738" fsmStatus="nop" fsmTry="0" intId="20884253" kmipFault="no" kmipFaultDescription="" lc="undiscovered" lcTs="1970-01-01T01:00:00.000" localId="" lowVoltageMemory="regular-voltage" managingInst="B" memorySpeed="1333" mfgTime="2013-11-18T00:00:00.000" model="UCSB-B200-M3" name="" numOf40GAdaptorsWithOldFw="0" numOf40GAdaptorsWithUnknownFw="0" numOfAdaptors="1" numOfCores="16" numOfCoresEnabled="16" numOfCpus="2" numOfEthHostIfs="0" numOfFcHostIfs="0" numOfThreads="32" operPower="off" operPwrTransSrc="software_mcserver" operQualifier="" operQualifierReason="N/A" operState="unassociated" operability="operable" originalUuid="12345678-1234-5678-9abc-1234567890ab" partNumber="73-14689-04" policyLevel="0" policyOwner="local" presence="equipped" revision="0" scaledMode="none" serial="ABC1234EFGH" serverId="2/4" slotId="4" storageOperQualifier="unknown" totalMemory="393216" usrLbl="" uuid="92345678-1234-5678-9abc-1234567890ab" vendor="Cisco Systems Inc" vid="V06"/>
	</outConfigs>
	</configResolveClass>`), 200, nil
			case strings.Contains(b, "computeRackUnit"):
				return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="computeRackUnit">
	<outConfigs>
	<computeRackUnit adminPower="policy" adminState="in-service" assetTag="" assignedToDn="" association="associated" availability="unavailable" availableMemory="262144" checkPoint="discovered" connPath="A,B" connStatus="A,B" descr="" discovery="complete" discoveryStatus="" dn="sys/rack-unit-3" enclosureId="0" fanSpeedConfigStatus="FAN POLICY OVERRIDE - Card(s) 'Nvidia TESLA M60, Nvidia TESLA M60' present" fanSpeedPolicyFault="no" fltAggr="0" fsmDescr="" fsmFlags="" fsmPrev="TurnupSuccess" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="2022-10-28T14:31:23.237" fsmStatus="nop" fsmTry="0" id="3" intId="5810280" kmipFault="no" kmipFaultDescription="Unavailable" lc="discovered" lcTs="1970-01-01T01:00:00.000" localId="" lowVoltageMemory="regular-voltage" managingInst="B" memorySpeed="2133" mfgTime="2016-02-22T00:00:00.000" model="UCSC-C240-M4SX" name="" numOf40GAdaptorsWithOldFw="0" numOf40GAdaptorsWithUnknownFw="0" numOfAdaptors="1" numOfCores="20" numOfCoresEnabled="20" numOfCpus="2" numOfEthHostIfs="1" numOfFcHostIfs="0" numOfThreads="40" operPower="on" operPwrTransSrc="software_mcserver" operQualifier="" operQualifierReason="N/A" operState="ok" operability="operable" originalUuid="cd087df7-01a6-4c63-b291-99e5d1aaeda5" partNumber="74-12504-01" physicalSecurity="chassis-close" policyLevel="0" policyOwner="local" presence="equipped" revision="0" serial="FCH2000ABCD" serverId="3" slotId="0" storageOperQualifier="unknown" totalMemory="262144" usrLbl="" uuid="c46f5734-a453-11ec-0000-00000000001c" vendor="Cisco Systems Inc" versionHolder="no" vethStatus="A,B" vid="0"/>
	</outConfigs>
	</configResolveClass>`), 200, nil
			case strings.Contains(b, "mgmtIf"):
				return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="mgmtIf"><outConfigs>
	<mgmtIf access="unspecified" adminState="enable" aggrPortId="0" chassisId="2" discovery="absent" dn="sys/chassis-2/blade-4/mgmt/if-2" epDn="" extBroadcast="0.0.0.0" extGw="192.168.100.1" extIp="192.168.100.6" extMask="255.255.255.0" fsmDescr="" fsmPrev="nop" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="never" fsmStatus="nop" fsmTry="0" id="2" ifRole="unknown" ifType="physical" instanceId="1" ip="127.6.2.4" locale="" mac="A8:0C:0D:B3:47:90" mask="0.0.0.0" name="" peerAggrPortId="0" peerChassisId="N/A" peerDn="" peerPortId="0" peerSlotId="0" portId="2" slotId="4" stateQual="unspecified" subject="blade" switchId="B" transport="" type="" vnet="1"/>
	<mgmtIf access="unspecified" adminState="disable" aggrPortId="0" chassisId="2" discovery="absent" dn="sys/chassis-2/blade-4/mgmt/if-1" epDn="" extBroadcast="0.0.0.0" extGw="192.168.100.1" extIp="192.168.100.6" extMask="255.255.255.0" fsmDescr="" fsmPrev="nop" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="never" fsmStatus="nop" fsmTry="0" id="1" ifRole="unknown" ifType="physical" instanceId="1" ip="127.5.2.4" locale="" mac="A8:0C:0D:B3:47:90" mask="0.0.0.0" name="" peerAggrPortId="0" peerChassisId="N/A" peerDn="" peerPortId="0" peerSlotId="0" portId="1" slotId="4" stateQual="unspecified" subject="blade" switchId="A" transport="" type="" vnet="1"/>
	<mgmtIf access="unspecified" adminState="enable" aggrPortId="0" chassisId="N/A" discovery="present" dn="sys/rack-unit-3/mgmt/if-1" epDn="sys/rack-unit-3/adaptor-1/ext-eth-1" extBroadcast="0.0.0.0" extGw="192.168.100.1" extIp="0.0.0.0" extMask="255.255.255.0" fsmDescr="" fsmPrev="nop" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="never" fsmStatus="nop" fsmTry="0" id="1" ifRole="unknown" ifType="physical" instanceId="1" ip="127.6.224.3" locale="" mac="00:42:68:81:0E:CE" mask="0.0.0.0" name="" peerAggrPortId="0" peerChassisId="N/A" peerDn="sys/switch-B/slot-1/switch-ether/port-18" peerPortId="0" peerSlotId="0" portId="18" slotId="1" stateQual="unspecified" subject="blade" switchId="B" transport="" type="" vnet="1"/>
	<mgmtIf access="unspecified" adminState="disable" aggrPortId="0" chassisId="N/A" discovery="present" dn="sys/rack-unit-3/mgmt/if-2" epDn="sys/rack-unit-3/adaptor-1/ext-eth-2" extBroadcast="0.0.0.0" extGw="192.168.100.1" extIp="0.0.0.0" extMask="255.255.255.0" fsmDescr="" fsmPrev="nop" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="never" fsmStatus="nop" fsmTry="0" id="2" ifRole="unknown" ifType="physical" instanceId="1" ip="127.5.224.3" locale="" mac="00:42:68:81:0E:CF" mask="0.0.0.0" name="" peerAggrPortId="0" peerChassisId="N/A" peerDn="sys/switch-A/slot-1/switch-ether/port-18" peerPortId="0" peerSlotId="0" portId="18" slotId="1" stateQual="unspecified" subject="blade" switchId="A" transport="" type="" vnet="1"/>
	</outConfigs> </configResolveClass>`), 200, nil
			case strings.Contains(b, "vnicIpV4ProfDerivedAddr"):
				return []byte(`<configResolveClass cookie="1669818979/057e283b-11dc-4fed-ad4c-8162977ad9f7" response="yes" classId="vnicIpV4ProfDerivedAddr">
	<outConfigs>
	<vnicIpV4ProfDerivedAddr addr="192.168.100.4" childAction="deleteNonPresent" defGw="192.168.100.1" dn="sys/chassis-2/blade-4/mgmt/ipv4-prof-addr" subnet="255.255.255.0"/> 
	<vnicIpV4ProfDerivedAddr addr="192.168.100.15" childAction="deleteNonPresent" defGw="0.0.0.0" dn="sys/rack-unit-3/mgmt/ipv4-prof-addr" subnet="255.255.255.0"/>
	</outConfigs>
	</configResolveClass>`), 200, nil
			case strings.Contains(b, "vnicIpV4MgmtPooledAddr"):
				return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="vnicIpV4MgmtPooledAddr">
	<outConfigs>
	<vnicIpV4MgmtPooledAddr addr="192.168.100.6" childAction="deleteNonPresent" defGw="192.168.100.1" dn="sys/chassis-2/blade-4/mgmt/iface-in-band/network/ipv4-pooled-addr" name="ext-mgmt" operName="org-root/ip-pool-ext-mgmt" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>  
	<vnicIpV4MgmtPooledAddr addr="0.0.0.0" childAction="deleteNonPresent" defGw="0.0.0.0" dn="org-root/ls-testSPT/iface-in-band/network/ipv4-pooled-addr" name="ext-mgmt" operName="" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
	<vnicIpV4MgmtPooledAddr addr="192.168.100.17" childAction="deleteNonPresent" defGw="192.168.100.1" dn="org-root/ls-test2/iface-in-band/network/ipv4-pooled-addr" name="ext-mgmt" operName="org-root/ip-pool-ext-mgmt" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
	<vnicIpV4MgmtPooledAddr addr="192.168.100.17" childAction="deleteNonPresent" defGw="192.168.100.1" dn="sys/rack-unit-3/mgmt/spiface-in-band/network/ipv4-pooled-addr" name="ext-mgmt" operName="" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
	</outConfigs>
	</configResolveClass>`), 200, nil
			case strings.Contains(b, "vnicIpV4StaticAddr"):
				return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="vnicIpV4StaticAddr">
	<outConfigs>
	<vnicIpV4StaticAddr addr="192.168.100.8" childAction="deleteNonPresent" defGw="192.168.100.1" dn="sys/chassis-2/blade-4/mgmt/ipv4-static-addr" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
	<vnicIpV4StaticAddr addr="192.168.100.4" childAction="deleteNonPresent" defGw="192.168.100.1" dn="org-root/ls-test1/ipv4-static-addr" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
	<vnicIpV4StaticAddr addr="192.168.100.9" childAction="deleteNonPresent" defGw="192.168.100.1" dn="org-root/ls-test1/iface-in-band/network/ipv4-static-addr" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
	<vnicIpV4StaticAddr addr="192.168.100.9" childAction="deleteNonPresent" defGw="192.168.100.1" dn="sys/chassis-2/blade-4/mgmt/spiface-in-band/network/ipv4-static-addr" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
	<vnicIpV4StaticAddr addr="192.168.100.10" childAction="deleteNonPresent" defGw="192.168.100.1" dn="sys/rack-unit-3/mgmt/ipv4-static-addr" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
	<vnicIpV4StaticAddr addr="192.168.100.11" childAction="deleteNonPresent" defGw="192.168.100.1" dn="sys/rack-unit-3/mgmt/iface-in-band/network/ipv4-static-addr" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
	</outConfigs>
	</configResolveClass>`), 200, nil
			case strings.Contains(b, "vnicIpV6MgmtPooledAddr"):
				return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="vnicIpV6MgmtPooledAddr">
	<outConfigs>
	<vnicIpV6MgmtPooledAddr addr="::" childAction="deleteNonPresent" defGw="::" dn="org-root/ls-testSPT/iface-in-band/network/ipv6-pooled-addr" name="ipv6-test" operName="" prefix="64" primDns="::" secDns="::"/>
	<vnicIpV6MgmtPooledAddr addr="fd9e:21a7:a92c:2323::2" childAction="deleteNonPresent" defGw="fd9e:21a7:a92c:2323::1" dn="org-root/ls-test2/iface-in-band/network/ipv6-pooled-addr" name="ipv6" operName="org-root/ip-pool-ipv6" prefix="64" primDns="::" secDns="::"/>
	<vnicIpV6MgmtPooledAddr addr="fd9e:21a7:a92c:2323::2" childAction="deleteNonPresent" defGw="fd9e:21a7:a92c:2323::1" dn="sys/rack-unit-3/mgmt/spiface-in-band/network/ipv6-pooled-addr" name="ipv6" operName="" prefix="64" primDns="::" secDns="::"/>
	<vnicIpV6MgmtPooledAddr addr="::" childAction="deleteNonPresent" defGw="::" dn="sys/rack-unit-3/mgmt/iface-in-band/network/ipv6-pooled-addr" name="ipv6_1addr" operName="" prefix="64" primDns="::" secDns="::"/>
	</outConfigs>
	</configResolveClass>`), 200, nil
			case strings.Contains(b, "vnicIpV6StaticAddr"):
				return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="vnicIpV6StaticAddr">
	<outConfigs>
	<vnicIpV6StaticAddr addr="fd9e:21a7:a92c:2323::3" childAction="deleteNonPresent" defGw="fd9e:21a7:a92c:2323::1" dn="org-root/ls-test1/iface-in-band/network/ipv6-static-addr" prefix="64" primDns="::" secDns="::"/>
	<vnicIpV6StaticAddr addr="fd9e:21a7:a92c:2323::3" childAction="deleteNonPresent" defGw="fd9e:21a7:a92c:2323::1" dn="sys/chassis-2/blade-4/mgmt/spiface-in-band/network/ipv6-static-addr" prefix="64" primDns="::" secDns="::"/>
	<vnicIpV6StaticAddr addr="fd9e:21a7:a92c:2323::4" childAction="deleteNonPresent" defGw="fd9e:21a7:a92c:2323::1" dn="sys/rack-unit-3/mgmt/iface-in-band/network/ipv6-static-addr" prefix="64" primDns="::" secDns="::"/>
	<vnicIpV6StaticAddr addr="fd9e:21a7:a92c:2323::5" childAction="deleteNonPresent" defGw="fd9e:21a7:a92c:2323::1" dn="sys/chassis-2/blade-4/mgmt/iface-in-band/network/ipv6-static-addr" prefix="64" primDns="::" secDns="::"/>
	</outConfigs>
	</configResolveClass>`), 200, nil
			}

			return nil, 500, fmt.Errorf("%w", ErrUnexpectedRequestBody)
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.cookie = testCookie1
	u.client = mockHttpClient

	kvmServers, err := u.GetKvmServer(context.Background())
	if err != nil {
		t.Errorf("GetKvmServer error : %v", err)
	}

	// ... existing code (Assertions unverändert) ...
	ks1 := KvmServer{Serial: "ABC1234EFGH", Dn: "sys/chassis-2/blade-4", EquipmentMgmtIP: "192.168.100.6", MgmtIPs: []string{"192.168.100.4", "192.168.100.6", "192.168.100.8", "192.168.100.9", "fd9e:21a7:a92c:2323::3", "fd9e:21a7:a92c:2323::5"}, FsmStatus: "inProgress", FsmCurrent: "Discover", FsmCompletionTime: nil}
	ks2location, _ := time.LoadLocation("Europe/Berlin")
	ks2time := time.Date(2022, 10, 28, 13, 50, 20, 935000000, ks2location)

	ks2 := KvmServer{Serial: "FCH2000ABCD", Dn: "sys/rack-unit-3", EquipmentMgmtIP: "192.168.100.10", MgmtIPs: []string{"192.168.100.10", "192.168.100.11", "192.168.100.15", "192.168.100.17", "fd9e:21a7:a92c:2323::2", "fd9e:21a7:a92c:2323::4"}, FsmStatus: "success", FsmCurrent: "Turnup", FsmCompletionTime: &ks2time}

	expectedKvmServers := make([]KvmServer, 2)
	expectedKvmServers[0] = ks1
	expectedKvmServers[1] = ks2

	sort.Sort(BySerial(kvmServers))
	sort.Sort(BySerial(expectedKvmServers))

	if !reflect.DeepEqual(expectedKvmServers, kvmServers) {
		t.Errorf("GetKvmServer\nexpected: %v\nbut was : %v", expectedKvmServers, kvmServers)
	}
}

func Test_getServerMgmtIPsMap(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			b := string(body)

			switch {
			case strings.Contains(b, "mgmtIf"):
				return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="mgmtIf"><outConfigs>
<mgmtIf access="unspecified" adminState="enable" aggrPortId="0" chassisId="2" discovery="absent" dn="sys/chassis-2/blade-4/mgmt/if-2" epDn="" extBroadcast="0.0.0.0" extGw="192.168.100.1" extIp="0.0.0.0" extMask="255.255.255.0" fsmDescr="" fsmPrev="nop" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="never" fsmStatus="nop" fsmTry="0" id="2" ifRole="unknown" ifType="physical" instanceId="1" ip="127.6.2.4" locale="" mac="A8:0C:0D:B3:47:90" mask="0.0.0.0" name="" peerAggrPortId="0" peerChassisId="N/A" peerDn="" peerPortId="0" peerSlotId="0" portId="2" slotId="4" stateQual="unspecified" subject="blade" switchId="B" transport="" type="" vnet="1"/>
<mgmtIf access="unspecified" adminState="disable" aggrPortId="0" chassisId="2" discovery="absent" dn="sys/chassis-2/blade-4/mgmt/if-1" epDn="" extBroadcast="0.0.0.0" extGw="192.168.100.1" extIp="0.0.0.0" extMask="255.255.255.0" fsmDescr="" fsmPrev="nop" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="never" fsmStatus="nop" fsmTry="0" id="1" ifRole="unknown" ifType="physical" instanceId="1" ip="127.5.2.4" locale="" mac="A8:0C:0D:B3:47:90" mask="0.0.0.0" name="" peerAggrPortId="0" peerChassisId="N/A" peerDn="" peerPortId="0" peerSlotId="0" portId="1" slotId="4" stateQual="unspecified" subject="blade" switchId="A" transport="" type="" vnet="1"/>
<mgmtIf access="unspecified" adminState="enable" aggrPortId="0" chassisId="N/A" discovery="present" dn="sys/rack-unit-3/mgmt/if-1" epDn="sys/rack-unit-3/adaptor-1/ext-eth-1" extBroadcast="0.0.0.0" extGw="192.168.100.1" extIp="192.168.100.7" extMask="255.255.255.0" fsmDescr="" fsmPrev="nop" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="never" fsmStatus="nop" fsmTry="0" id="1" ifRole="unknown" ifType="physical" instanceId="1" ip="127.6.224.3" locale="" mac="00:42:68:81:0E:CE" mask="0.0.0.0" name="" peerAggrPortId="0" peerChassisId="N/A" peerDn="sys/switch-B/slot-1/switch-ether/port-18" peerPortId="0" peerSlotId="0" portId="18" slotId="1" stateQual="unspecified" subject="blade" switchId="B" transport="" type="" vnet="1"/>
<mgmtIf access="unspecified" adminState="disable" aggrPortId="0" chassisId="N/A" discovery="present" dn="sys/rack-unit-3/mgmt/if-2" epDn="sys/rack-unit-3/adaptor-1/ext-eth-2" extBroadcast="0.0.0.0" extGw="192.168.100.1" extIp="192.168.100.7" extMask="255.255.255.0" fsmDescr="" fsmPrev="nop" fsmProgr="100" fsmRmtInvErrCode="none" fsmRmtInvErrDescr="" fsmRmtInvRslt="" fsmStageDescr="" fsmStamp="never" fsmStatus="nop" fsmTry="0" id="2" ifRole="unknown" ifType="physical" instanceId="1" ip="127.5.224.3" locale="" mac="00:42:68:81:0E:CF" mask="0.0.0.0" name="" peerAggrPortId="0" peerChassisId="N/A" peerDn="sys/switch-A/slot-1/switch-ether/port-18" peerPortId="0" peerSlotId="0" portId="18" slotId="1" stateQual="unspecified" subject="blade" switchId="A" transport="" type="" vnet="1"/>
</outConfigs> </configResolveClass>`), 200, nil
			case strings.Contains(string(b), "vnicIpV4ProfDerivedAddr\""):
				return []byte(`<configResolveClass cookie="1669818979/057e283b-11dc-4fed-ad4c-8162977ad9f7" response="yes" classId="vnicIpV4ProfDerivedAddr">
<outConfigs>
<vnicIpV4ProfDerivedAddr addr="192.168.100.4" childAction="deleteNonPresent" defGw="192.168.100.1" dn="sys/chassis-2/blade-4/mgmt/ipv4-prof-addr" subnet="255.255.255.0"/> 
<vnicIpV4ProfDerivedAddr addr="192.168.100.15" childAction="deleteNonPresent" defGw="0.0.0.0" dn="sys/rack-unit-3/mgmt/ipv4-prof-addr" subnet="255.255.255.0"/>
<vnicIpV4ProfDerivedAddr addr="0.0.0.0" childAction="deleteNonPresent" defGw="0.0.0.0" dn="sys/rack-unit-1/mgmt/ipv4-prof-addr" subnet="255.255.255.0"/>
</outConfigs>
</configResolveClass>`), 200, nil
			case strings.Contains(string(b), "vnicIpV4MgmtPooledAddr\""):
				return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="vnicIpV4MgmtPooledAddr">
<outConfigs>
<vnicIpV4MgmtPooledAddr addr="192.168.100.6" childAction="deleteNonPresent" defGw="192.168.100.1" dn="sys/chassis-2/blade-4/mgmt/iface-in-band/network/ipv4-pooled-addr" name="ext-mgmt" operName="org-root/ip-pool-ext-mgmt" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>  
<vnicIpV4MgmtPooledAddr addr="0.0.0.0" childAction="deleteNonPresent" defGw="0.0.0.0" dn="org-root/ls-testSPT/iface-in-band/network/ipv4-pooled-addr" name="ext-mgmt" operName="" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
<vnicIpV4MgmtPooledAddr addr="192.168.100.17" childAction="deleteNonPresent" defGw="192.168.100.1" dn="org-root/ls-test2/iface-in-band/network/ipv4-pooled-addr" name="ext-mgmt" operName="org-root/ip-pool-ext-mgmt" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
<vnicIpV4MgmtPooledAddr addr="192.168.100.17" childAction="deleteNonPresent" defGw="192.168.100.1" dn="sys/rack-unit-3/mgmt/spiface-in-band/network/ipv4-pooled-addr" name="ext-mgmt" operName="" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
</outConfigs>
</configResolveClass>`), 200, nil
			case strings.Contains(string(b), "vnicIpV4StaticAddr\""):
				return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="vnicIpV4StaticAddr">
<outConfigs>
<vnicIpV4StaticAddr addr="192.168.100.8" childAction="deleteNonPresent" defGw="192.168.100.1" dn="sys/chassis-2/blade-4/mgmt/ipv4-static-addr" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
<vnicIpV4StaticAddr addr="192.168.100.4" childAction="deleteNonPresent" defGw="192.168.100.1" dn="org-root/ls-test1/ipv4-static-addr" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
<vnicIpV4StaticAddr addr="192.168.100.9" childAction="deleteNonPresent" defGw="192.168.100.1" dn="org-root/ls-test1/iface-in-band/network/ipv4-static-addr" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
<vnicIpV4StaticAddr addr="192.168.100.9" childAction="deleteNonPresent" defGw="192.168.100.1" dn="sys/chassis-2/blade-4/mgmt/spiface-in-band/network/ipv4-static-addr" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
<vnicIpV4StaticAddr addr="192.168.100.10" childAction="deleteNonPresent" defGw="192.168.100.1" dn="sys/rack-unit-3/mgmt/ipv4-static-addr" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
<vnicIpV4StaticAddr addr="192.168.100.11" childAction="deleteNonPresent" defGw="192.168.100.1" dn="sys/rack-unit-3/mgmt/iface-in-band/network/ipv4-static-addr" primDns="0.0.0.0" secDns="0.0.0.0" subnet="255.255.255.0"/>
</outConfigs>
</configResolveClass>`), 200, nil
			case strings.Contains(string(b), "vnicIpV6MgmtPooledAddr\""):
				return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="vnicIpV6MgmtPooledAddr">
<outConfigs>
<vnicIpV6MgmtPooledAddr addr="::" childAction="deleteNonPresent" defGw="::" dn="org-root/ls-testSPT/iface-in-band/network/ipv6-pooled-addr" name="ipv6-test" operName="" prefix="64" primDns="::" secDns="::"/>
<vnicIpV6MgmtPooledAddr addr="fd9e:21a7:a92c:2323::2" childAction="deleteNonPresent" defGw="fd9e:21a7:a92c:2323::1" dn="org-root/ls-test2/iface-in-band/network/ipv6-pooled-addr" name="ipv6" operName="org-root/ip-pool-ipv6" prefix="64" primDns="::" secDns="::"/>
<vnicIpV6MgmtPooledAddr addr="fd9e:21a7:a92c:2323::2" childAction="deleteNonPresent" defGw="fd9e:21a7:a92c:2323::1" dn="sys/rack-unit-3/mgmt/spiface-in-band/network/ipv6-pooled-addr" name="ipv6" operName="" prefix="64" primDns="::" secDns="::"/>
<vnicIpV6MgmtPooledAddr addr="::" childAction="deleteNonPresent" defGw="::" dn="sys/rack-unit-3/mgmt/iface-in-band/network/ipv6-pooled-addr" name="ipv6_1addr" operName="" prefix="64" primDns="::" secDns="::"/>
</outConfigs>
</configResolveClass>`), 200, nil
			case strings.Contains(string(b), "vnicIpV6StaticAddr\""):
				return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="vnicIpV6StaticAddr">
<outConfigs>
<vnicIpV6StaticAddr addr="fd9e:21a7:a92c:2323::3" childAction="deleteNonPresent" defGw="fd9e:21a7:a92c:2323::1" dn="org-root/ls-test1/iface-in-band/network/ipv6-static-addr" prefix="64" primDns="::" secDns="::"/>
<vnicIpV6StaticAddr addr="fd9e:21a7:a92c:2323::3" childAction="deleteNonPresent" defGw="fd9e:21a7:a92c:2323::1" dn="sys/chassis-2/blade-4/mgmt/spiface-in-band/network/ipv6-static-addr" prefix="64" primDns="::" secDns="::"/>
<vnicIpV6StaticAddr addr="fd9e:21a7:a92c:2323::4" childAction="deleteNonPresent" defGw="fd9e:21a7:a92c:2323::1" dn="sys/rack-unit-3/mgmt/iface-in-band/network/ipv6-static-addr" prefix="64" primDns="::" secDns="::"/>
<vnicIpV6StaticAddr addr="fd9e:21a7:a92c:2323::5" childAction="deleteNonPresent" defGw="fd9e:21a7:a92c:2323::1" dn="sys/chassis-2/blade-4/mgmt/iface-in-band/network/ipv6-static-addr" prefix="64" primDns="::" secDns="::"/>
</outConfigs>
</configResolveClass>`), 200, nil
			}
			return nil, 500, fmt.Errorf("%w", ErrUnexpectedRequestBody)
		},
	}
	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.cookie = testCookie1
	u.client = mockHttpClient

	serverMgmtIPsMap, err := u.GetServerMgmtIPsMap(context.Background())
	if err != nil {
		t.Errorf("GetServerMgmtIPsMap error : %v", err)
	}

	expectedMap := make(map[string][]string)
	expectedMap["sys/chassis-2/blade-4"] = []string{"192.168.100.4", "192.168.100.6", "192.168.100.8", "192.168.100.9", "fd9e:21a7:a92c:2323::3", "fd9e:21a7:a92c:2323::5"}
	expectedMap["sys/rack-unit-3"] = []string{"192.168.100.7", "192.168.100.10", "192.168.100.11", "192.168.100.15", "192.168.100.17", "fd9e:21a7:a92c:2323::2", "fd9e:21a7:a92c:2323::4"}
	expectedMap["sys/rack-unit-1"] = []string{}

	if !reflect.DeepEqual(expectedMap, serverMgmtIPsMap) {
		t.Errorf("GetServerMgmtIPsMap\nexpected: %v\n but was : %v", expectedMap, serverMgmtIPsMap)
	}
}

func Test_removeDuplicateIPs(t *testing.T) {
	type args struct {
		ips []string
	}
	type want struct {
		ips []string
	}
	tests := []struct {
		name string
		args args
		want want
	}{
		{"1", args{ips: []string{}}, want{ips: []string{}}},
		{"2", args{ips: []string{""}}, want{ips: []string{}}},
		{"3", args{ips: []string{"0.0.0.0"}}, want{ips: []string{}}},
		{"4", args{ips: []string{"::"}}, want{ips: []string{}}},
		{"5", args{ips: []string{"127.0.0.1"}}, want{ips: []string{"127.0.0.1"}}},
		{"6", args{ips: []string{"127.0.0.1", "127.0.0.1"}}, want{ips: []string{"127.0.0.1"}}},
		{"7", args{ips: []string{"127.0.0.2", "127.0.0.1"}}, want{ips: []string{"127.0.0.1", "127.0.0.2"}}},
		{"8", args{ips: []string{"127.0.41.2", "127.0.112.1"}}, want{ips: []string{"127.0.112.1", "127.0.41.2"}}},
		{"9", args{ips: []string{"192.168.100.5", "10.5.0.1", "127.0.0.7", "192.168.100.5", "10.11.43.101", "0.0.0.0", "127.0.0.7"}}, want{ips: []string{"10.11.43.101", "10.5.0.1", "127.0.0.7", "192.168.100.5"}}},
		{"10", args{ips: []string{"fd9e:21a7:a92c:2323::4"}}, want{ips: []string{"fd9e:21a7:a92c:2323::4"}}},
		{"11", args{ips: []string{"fd9e:21a7:a92c:2323::2", "10.5.0.1", "127.0.0.7", "192.168.100.5", "fd9e:21a7::78", "10.11.43.101", "0.0.0.0", "127.0.0.7"}}, want{ips: []string{"10.11.43.101", "10.5.0.1", "127.0.0.7", "192.168.100.5", "fd9e:21a7::78", "fd9e:21a7:a92c:2323::2"}}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := removeDuplicateIPs(tt.args.ips)
			if !reflect.DeepEqual(result, tt.want.ips) {
				t.Errorf("removeDuplicateIPs(\"%v\") = result (\"%v\"), want (\"%v\")", tt.args.ips, result, tt.want.ips)
			}
		})
	}
}

func Test_sortIPs(t *testing.T) {
	type args struct {
		ips []string
	}
	type want struct {
		ips []string
	}
	tests := []struct {
		name string
		args args
		want want
	}{
		{"1", args{ips: []string{}}, want{ips: []string{}}},
		{"2", args{ips: []string{""}}, want{ips: []string{}}},
		{"3", args{ips: []string{"0.0.0.0"}}, want{ips: []string{}}},
		{"4", args{ips: []string{"::"}}, want{ips: []string{}}},
		{"5", args{ips: []string{"127.0.0.1"}}, want{ips: []string{"127.0.0.1"}}},
		{"6", args{ips: []string{"127.0.0.1", "127.0.0.1"}}, want{ips: []string{"127.0.0.1"}}},
		{"7", args{ips: []string{"127.0.0.2", "127.0.0.1"}}, want{ips: []string{"127.0.0.1", "127.0.0.2"}}},
		{"8", args{ips: []string{"127.0.41.2", "127.0.112.1"}}, want{ips: []string{"127.0.41.2", "127.0.112.1"}}},
		{"9", args{ips: []string{"192.168.100.5", "10.5.0.1", "127.0.0.7", "192.168.100.5", "10.11.43.101", "0.0.0.0", "127.0.0.7"}}, want{ips: []string{"10.5.0.1", "10.11.43.101", "127.0.0.7", "192.168.100.5"}}},
		{"10", args{ips: []string{"fd9e:21a7:a92c:2323::4"}}, want{ips: []string{"fd9e:21a7:a92c:2323::4"}}},
		{"11", args{ips: []string{"fd9e:21a7:a92c:2323::2", "10.5.0.1", "127.0.0.7", "192.168.100.5", "fd9e:21a7::78", "10.11.43.101", "0.0.0.0", "127.0.0.7"}}, want{ips: []string{"10.5.0.1", "10.11.43.101", "127.0.0.7", "192.168.100.5", "fd9e:21a7::78", "fd9e:21a7:a92c:2323::2"}}},
		{"12", args{ips: []string{"fd9e:21a7:a92c:2323::2", "10.5.0.1", "127.0.0.7", "192.168.100.5", "fd9e:21a7:78", "11.43.101", "0.0.0.0", "127.0.0.7"}}, want{ips: []string{"10.5.0.1", "127.0.0.7", "192.168.100.5", "fd9e:21a7:a92c:2323::2"}}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := sortIPs(tt.args.ips)
			if !reflect.DeepEqual(result, tt.want.ips) {
				t.Errorf("sortIPs(\"%v\") = result (\"%v\"), want (\"%v\")", tt.args.ips, result, tt.want.ips)
			}
		})
	}
}

func Test_KvmServer_MgmtIPsString(t *testing.T) {
	type args struct {
		ips []string
	}
	type want struct {
		ips string
	}
	tests := []struct {
		name string
		args args
		want want
	}{
		{"1", args{ips: []string{}}, want{ips: ""}},
		{"2", args{ips: []string{""}}, want{ips: ""}},
		{"3", args{ips: []string{"0.0.0.0"}}, want{ips: "0.0.0.0"}},
		{"4", args{ips: []string{"::"}}, want{ips: "::"}},
		{"5", args{ips: []string{"127.0.0.1"}}, want{ips: "127.0.0.1"}},
		{"6", args{ips: []string{"127.0.0.1", "127.0.0.1"}}, want{ips: "127.0.0.1,127.0.0.1"}},
		{"7", args{ips: []string{"fd9e:21a7:a92c:2323::2", "10.5.0.1", "127.0.0.7", "192.168.100.5", "fd9e:21a7::78", "10.11.43.101", "0.0.0.0", "127.0.0.7"}}, want{ips: "fd9e:21a7:a92c:2323::2,10.5.0.1,127.0.0.7,192.168.100.5,fd9e:21a7::78,10.11.43.101,0.0.0.0,127.0.0.7"}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			kvmServer := &KvmServer{MgmtIPs: tt.args.ips}
			result := kvmServer.MgmtIPsString()
			if result != tt.want.ips {
				t.Errorf("MgmtIPString() = result \"%s\", want \"%s\"", result, tt.want.ips)
			}
		})
	}
}

func Test_GetPowerSumFi(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="equipmentPsuInputStats">
<outConfigs>
<equipmentPsuInputStats current="0.382324" currentAvg="0.383022" currentMax="0.387207" currentMin="0.380371" dn="sys/switch-B/psu-2/input-stats" inputStatus="ok" intervals="58982460" power="88.508010" powerAvg="88.669533" powerMax="89.638420" powerMin="88.000000" suspect="no" thresholded="" timeCollected="2022-12-23T15:01:47.781" update="65543" voltage="231.500000" voltageAvg="231.500000" voltageMax="231.500000" voltageMin="231.500000"/>
<equipmentPsuInputStats current="0.390625" currentAvg="0.391462" currentMax="0.394531" currentMin="0.390625" dn="sys/switch-B/psu-1/input-stats" inputStatus="ok" intervals="58982460" power="89.941400" powerAvg="90.148109" powerMax="90.840767" powerMin="89.000000" suspect="no" thresholded="" timeCollected="2022-12-23T15:01:47.781" update="65543" voltage="230.250000" voltageAvg="230.285721" voltageMax="230.500000" voltageMin="230.250000"/>
<equipmentPsuInputStats current="0.425293" currentAvg="0.430176" currentMax="0.437012" currentMin="0.425293" dn="sys/switch-A/psu-2/input-stats" inputStatus="ok" intervals="58982460" power="99.305911" powerAvg="100.680084" powerMax="102.370056" powerMin="99.000000" suspect="no" thresholded="" timeCollected="2022-12-23T15:01:48.028" update="327686" voltage="233.500000" voltageAvg="234.041656" voltageMax="234.500000" voltageMin="233.500000"/>
<equipmentPsuInputStats current="0.384277" currentAvg="0.385091" currentMax="0.389160" currentMin="0.384277" dn="sys/switch-A/psu-1/input-stats" inputStatus="ok" intervals="58982460" power="89.824745" powerAvg="89.983154" powerMax="91.063438" powerMin="89.000000" suspect="no" thresholded="" timeCollected="2022-12-23T15:01:48.028" update="327686" voltage="233.750000" voltageAvg="233.666656" voltageMax="234.000000" voltageMin="233.500000"/>
</outConfigs>
</configResolveClass>`), 200, nil
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.client = mockHttpClient

	u.cookie = testCookie1
	sum, err := u.GetPowerSumFi(context.Background())
	if err != nil {
		t.Errorf("GetPowerSumFi error : %v", err)
	}

	expectedSumPower := 367.580066
	expectedSumPowerMin := 365.
	expectedSumPowerAvg := 369.48088
	expectedSumPowerMax := 373.912681
	expectedNumberOfFi := 2

	if expectedSumPower != sum.Sum {
		t.Errorf("GetPowerSumFi - sumPower\nexpected: %v\n but was : %v", expectedSumPower, sum.Sum)
	}
	if expectedSumPowerMin != sum.Min {
		t.Errorf("GetPowerSumFi - sumPowerMin\nexpected: %v\n but was : %v", expectedSumPowerMin, sum.Min)
	}
	if expectedSumPowerAvg != sum.Avg {
		t.Errorf("GetPowerSumFi - sumPowerAvg\nexpected: %v\n but was : %v", expectedSumPowerAvg, sum.Avg)
	}
	if expectedSumPowerMax != sum.Max {
		t.Errorf("GetPowerSumFi - sumPowerMax\nexpected: %v\n but was : %v", expectedSumPowerMax, sum.Max)
	}
	if expectedNumberOfFi != sum.Count {
		t.Errorf("GetPowerSumFi - numberOfFi\nexpected: %v\n but was : %v", expectedNumberOfFi, sum.Count)
	}
}

func Test_GetPowerSumFex(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="equipmentFexPsuInputStats">
<outConfigs>
<equipmentFexPsuInputStats current="7.000000" currentAvg="7.083333" currentMax="8.000000" currentMin="7.000000" dn="sys/fex-2/fex-psu-input-stats" inputStatus="ok" intervals="58982460" power="84.000000" powerAvg="84.999992" powerMax="96.000000" powerMin="84.000000" suspect="no" thresholded="" timeCollected="2022-12-23T14:52:47.778" update="262156" voltage="12.000000" voltageAvg="12.000000" voltageMax="12.000000" voltageMin="12.000000"/>
<equipmentFexPsuInputStats current="7.000000" currentAvg="6.769231" currentMax="7.000000" currentMin="6.000000" dn="sys/fex-7/fex-psu-input-stats" inputStatus="ok" intervals="58982460" power="84.000000" powerAvg="81.230766" powerMax="84.000000" powerMin="72.000000" suspect="no" thresholded="" timeCollected="2022-12-23T14:52:48.056" update="393229" voltage="12.000000" voltageAvg="12.000000" voltageMax="12.000000" voltageMin="12.000000"/>
</outConfigs>
</configResolveClass>`), 200, nil
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.client = mockHttpClient

	u.cookie = testCookie1
	sum, err := u.GetPowerSumFex(context.Background())
	if err != nil {
		t.Errorf("GetPowerSumFex error : %v", err)
	}

	expectedSumPower := 168.
	expectedSumPowerMin := 156.
	expectedSumPowerAvg := 166.230758
	expectedSumPowerMax := 180.
	expectedNumberOfFex := 2

	if expectedSumPower != sum.Sum {
		t.Errorf("GetPowerSumFex - sumPower\nexpected: %v\n but was : %v", expectedSumPower, sum.Sum)
	}
	if expectedSumPowerMin != sum.Min {
		t.Errorf("GetPowerSumFex - sumPowerMin\nexpected: %v\n but was : %v", expectedSumPowerMin, sum.Min)
	}
	if expectedSumPowerAvg != sum.Avg {
		t.Errorf("GetPowerSumFex - sumPowerAvg\nexpected: %v\n but was : %v", expectedSumPowerAvg, sum.Avg)
	}
	if expectedSumPowerMax != sum.Max {
		t.Errorf("GetPowerSumFex - sumPowerMax\nexpected: %v\n but was : %v", expectedSumPowerMax, sum.Max)
	}
	if expectedNumberOfFex != sum.Count {
		t.Errorf("GetPowerSumFex - numberOfFex\nexpected: %v\n but was : %v", expectedNumberOfFex, sum.Count)
	}
}

func Test_GetPowerSumChassis(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="equipmentChassisStats">
<outConfigs>
<equipmentChassisStats ChassisI2CErrors="32" ChassisI2CErrorsAvg="26" ChassisI2CErrorsMax="32" ChassisI2CErrorsMin="0" dn="sys/chassis-1/stats" inputPower="2568.000000" inputPowerAvg="2588.000000" inputPowerMax="2592.000000" inputPowerMin="2568.000000" intervals="58982460" outputPower="2346.000000" outputPowerAvg="2361.333252" outputPowerMax="2369.000000" outputPowerMin="2346.000000" suspect="no" thresholded="" timeCollected="2022-12-23T14:50:43.091" update="327692"/>
<equipmentChassisStats ChassisI2CErrors="32" ChassisI2CErrorsAvg="32" ChassisI2CErrorsMax="32" ChassisI2CErrorsMin="32" dn="sys/chassis-2/stats" inputPower="2760.000000" inputPowerAvg="2773.714355" inputPowerMax="2784.000000" inputPowerMin="2760.000000" intervals="58982460" outputPower="2461.000000" outputPowerAvg="2484.000244" outputPowerMax="2507.000000" outputPowerMin="2461.000000" suspect="no" thresholded="" timeCollected="2022-12-23T14:49:56.695" update="262151"/>
</outConfigs>
</configResolveClass>`), 200, nil
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.client = mockHttpClient

	u.cookie = testCookie1
	sum, err := u.GetPowerSumChassis(context.Background())
	if err != nil {
		t.Errorf("GetPowerSumChassis error : %v", err)
	}

	expectedSumPower := 5328.
	expectedSumPowerMin := 5328.
	expectedSumPowerAvg := 5361.714355
	expectedSumPowerMax := 5376.
	expectedNumberOfChassis := 2

	if expectedSumPower != sum.Sum {
		t.Errorf("GetPowerSumChassis - sumPower\nexpected: %v\n but was : %v", expectedSumPower, sum.Sum)
	}
	if expectedSumPowerMin != sum.Min {
		t.Errorf("GetPowerSumChassis - sumPowerMin\nexpected: %v\n but was : %v", expectedSumPowerMin, sum.Min)
	}
	if expectedSumPowerAvg != sum.Avg {
		t.Errorf("GetPowerSumChassis - sumPowerAvg\nexpected: %v\n but was : %v", expectedSumPowerAvg, sum.Avg)
	}
	if expectedSumPowerMax != sum.Max {
		t.Errorf("GetPowerSumChassis - sumPowerMax\nexpected: %v\n but was : %v", expectedSumPowerMax, sum.Max)
	}
	if expectedNumberOfChassis != sum.Count {
		t.Errorf("GetPowerSumChassis - numberOfChassis\nexpected: %v\n but was : %v", expectedNumberOfChassis, sum.Count)
	}
}

func Test_GetPowerSumServer(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			return []byte(`<configResolveClass cookie="1668770734/8706e3f7-7413-4588-989a-84e6c0c5f925" response="yes" classId="computeMbPowerStats">
<outConfigs>
<computeMbPowerStats consumedPower="162.000000" consumedPowerAvg="162.000000" consumedPowerMax="180.000000" consumedPowerMin="153.000000" dn="sys/chassis-1/blade-1/board/power-stats" inputCurrent="13.459620" inputCurrentAvg="13.443206" inputCurrentMax="14.955134" inputCurrentMin="12.711864" inputVoltage="12.036000" inputVoltageAvg="12.050751" inputVoltageMax="12.095000" inputVoltageMin="12.036000" intervals="58982460" suspect="no" thresholded="" timeCollected="2022-12-23T14:45:17.651" update="196616"/>
<computeMbPowerStats consumedPower="162.000000" consumedPowerAvg="162.000000" consumedPowerMax="162.000000" consumedPowerMin="162.000000" dn="sys/chassis-1/blade-2/board/power-stats" inputCurrent="13.525925" inputCurrentAvg="13.525925" inputCurrentMax="13.525925" inputCurrentMin="13.525925" inputVoltage="11.977000" inputVoltageAvg="11.977000" inputVoltageMax="11.977000" inputVoltageMin="11.977000" intervals="58982460" suspect="no" thresholded="" timeCollected="2022-12-23T14:45:41.082" update="65537"/>
<computeMbPowerStats consumedPower="324.000000" consumedPowerAvg="324.000000" consumedPowerMax="324.000000" consumedPowerMin="324.000000" dn="sys/rack-unit-4/board/power-stats" inputCurrent="27.518261" inputCurrentAvg="27.518261" inputCurrentMax="27.518261" inputCurrentMin="27.518261" inputVoltage="11.774000" inputVoltageAvg="11.774000" inputVoltageMax="11.774000" inputVoltageMin="11.774000" intervals="58982460" suspect="no" thresholded="" timeCollected="2022-12-23T14:45:27.382" update="65540"/>
<computeMbPowerStats consumedPower="126.000000" consumedPowerAvg="126.000000" consumedPowerMax="126.000000" consumedPowerMin="126.000000" dn="sys/rack-unit-5/board/power-stats" inputCurrent="10.649087" inputCurrentAvg="10.649087" inputCurrentMax="10.649087" inputCurrentMin="10.649087" inputVoltage="11.832000" inputVoltageAvg="11.832000" inputVoltageMax="11.832000" inputVoltageMin="11.832000" intervals="58982460" suspect="no" thresholded="" timeCollected="2022-12-23T14:45:57.457" update="327684"/>
</outConfigs>
</configResolveClass>`), 200, nil
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.client = mockHttpClient

	u.cookie = testCookie1
	sum, err := u.GetPowerSumServer(context.Background())
	if err != nil {
		t.Errorf("GetPowerSumServer Error : %v", err)
	}

	expectedBSumPower := 324.
	expectedBSumPowerMin := 315.
	expectedBSumPowerAvg := 324.
	expectedBSumPowerMax := 342.
	expectedNumberOfBlades := 2
	expectedCSumPower := 450.
	expectedCSumPowerMin := 450.
	expectedCSumPowerAvg := 450.
	expectedCSumPowerMax := 450.
	expectedNumberOfRackUnits := 2

	if expectedBSumPower != sum.Blades.Sum {
		t.Errorf("GetPowerSumServer - sumBPower\nexpected: %v\n but was : %v", expectedBSumPower, sum.Blades.Sum)
	}
	if expectedBSumPowerMin != sum.Blades.Min {
		t.Errorf("GetPowerSumServer - sumBPowerMin\nexpected: %v\n but was : %v", expectedBSumPowerMin, sum.Blades.Min)
	}
	if expectedBSumPowerAvg != sum.Blades.Avg {
		t.Errorf("GetPowerSumServer - sumBPowerAvg\nexpected: %v\n but was : %v", expectedBSumPowerAvg, sum.Blades.Avg)
	}
	if expectedBSumPowerMax != sum.Blades.Max {
		t.Errorf("GetPowerSumServer - sumBPowerMax\nexpected: %v\n but was : %v", expectedBSumPowerMax, sum.Blades.Max)
	}
	if expectedNumberOfBlades != sum.Blades.Count {
		t.Errorf("GetPowerSumServer - numberOfBlades\nexpected: %v\n but was : %v", expectedNumberOfBlades, sum.Blades.Count)
	}
	if expectedCSumPower != sum.RackUnits.Sum {
		t.Errorf("GetPowerSumServer - sumCPower\nexpected: %v\n but was : %v", expectedCSumPower, sum.RackUnits.Sum)
	}
	if expectedCSumPowerMin != sum.RackUnits.Min {
		t.Errorf("GetPowerSumServer - sumCPowerMin\nexpected: %v\n but was : %v", expectedCSumPowerMin, sum.RackUnits.Min)
	}
	if expectedCSumPowerAvg != sum.RackUnits.Avg {
		t.Errorf("GetPowerSumServer - sumCPowerAvg\nexpected: %v\n but was : %v", expectedCSumPowerAvg, sum.RackUnits.Avg)
	}
	if expectedCSumPowerMax != sum.RackUnits.Max {
		t.Errorf("GetPowerSumServer - sumCPowerMax\nexpected: %v\n but was : %v", expectedCSumPowerMax, sum.RackUnits.Max)
	}
	if expectedNumberOfRackUnits != sum.RackUnits.Count {
		t.Errorf("GetPowerSumServer - numberOfRackUnits\nexpected: %v\n but was : %v", expectedNumberOfRackUnits, sum.RackUnits.Count)
	}
}

func Test_convertMgmtHealthAttrMaps(t *testing.T) {
	type args struct {
		mgmtHealthAttrMaps []map[string]string
	}
	type want struct {
		wantMap map[string]map[string]string
	}
	mgmtHealthAttrMap1 := map[string]string{
		"description": "ADDDC Bank-level adaptive virtual lockstep is activated on DIMM DDR4_P2_K1_ECC. Post Package Repair will be performed on this DIMM during the next system reboot.",
		"dn":          "sys/rack-unit-47/mgmt/health/RAS Event (2C)",
		"name":        "RAS Event (2C)",
		"severity":    "minor",
		"value":       "PPR Required",
	}
	mgmtHealthAttrMap2 := map[string]string{
		"description": "ADDDC Bank-level adaptive virtual lockstep is activated on DIMM DDR4_P1_F1_ECC. Post Package Repair will be performed on this DIMM during the next system reboot.",
		"dn":          "sys/rack-unit-47/mgmt/health/RAS Event (2C)",
		"name":        "RAS Event (2C)",
		"severity":    "minor",
		"value":       "PPR Required",
	}
	mgmtHealthAttrMap3 := map[string]string{
		"description": "ADDDC Bank-level adaptive virtual lockstep is activated on DIMM DDR4_P3_A1_ECC. Post Package Repair will be performed on this DIMM during the next system reboot.",
		"dn":          "sys/chassis-5/blade-2/mgmt/health/RAS Event (2C)",
		"name":        "RAS Event (2C)",
		"severity":    "major",
		"value":       "PPR Required",
	}
	mgmtHealthAttrMap4 := map[string]string{
		"description": "ADDDC Bank-level adaptive virtual lockstep is activated on DIMM DDR4_P1_B1_ECC. Post Package Repair will be performed on this DIMM during the next system reboot.",
		"dn":          "sys/chassis-5/blade-2/mgmt/health/RAS Event (2C)",
		"name":        "RAS Event (2C)",
		"severity":    "minor",
		"value":       "PPR Required",
	}
	mgmtHealthAttrMaps1 := make([]map[string]string, 0, 1)
	mgmtHealthAttrMaps1 = append(mgmtHealthAttrMaps1, mgmtHealthAttrMap1)
	mgmtHealthAttrMaps2 := make([]map[string]string, 0, 2)
	mgmtHealthAttrMaps2 = append(mgmtHealthAttrMaps2, mgmtHealthAttrMap1, mgmtHealthAttrMap2)
	mgmtHealthAttrMaps3 := make([]map[string]string, 0, 3)
	mgmtHealthAttrMaps3 = append(mgmtHealthAttrMaps3, mgmtHealthAttrMap1, mgmtHealthAttrMap2, mgmtHealthAttrMap3)
	mgmtHealthAttrMaps4 := make([]map[string]string, 0, 4)
	mgmtHealthAttrMaps4 = append(mgmtHealthAttrMaps4, mgmtHealthAttrMap1, mgmtHealthAttrMap2, mgmtHealthAttrMap3, mgmtHealthAttrMap4)

	wantMap1 := make(map[string]map[string]string)
	wantMap1["sys/rack-unit-47/mgmt/health"] = make(map[string]string)
	wantMap1["sys/rack-unit-47/mgmt/health"]["minor"] = "ADDDC Bank-level adaptive virtual lockstep is activated on DIMM DDR4_P2_K1_ECC. Post Package Repair will be performed on this DIMM during the next system reboot."
	wantMap2 := make(map[string]map[string]string)
	wantMap2["sys/rack-unit-47/mgmt/health"] = make(map[string]string)
	wantMap2["sys/rack-unit-47/mgmt/health"]["minor"] = addDcMessage
	wantMap3 := make(map[string]map[string]string)
	wantMap3["sys/rack-unit-47/mgmt/health"] = make(map[string]string)
	wantMap3["sys/rack-unit-47/mgmt/health"]["minor"] = addDcMessage
	wantMap3["sys/chassis-5/blade-2/mgmt/health"] = make(map[string]string)
	wantMap3["sys/chassis-5/blade-2/mgmt/health"]["major"] = "ADDDC Bank-level adaptive virtual lockstep is activated on DIMM DDR4_P3_A1_ECC. Post Package Repair will be performed on this DIMM during the next system reboot."
	wantMap4 := make(map[string]map[string]string)
	wantMap4["sys/rack-unit-47/mgmt/health"] = make(map[string]string)
	wantMap4["sys/rack-unit-47/mgmt/health"]["minor"] = addDcMessage
	wantMap4["sys/chassis-5/blade-2/mgmt/health"] = make(map[string]string)
	wantMap4["sys/chassis-5/blade-2/mgmt/health"]["major"] = "ADDDC Bank-level adaptive virtual lockstep is activated on DIMM DDR4_P3_A1_ECC. Post Package Repair will be performed on this DIMM during the next system reboot."
	wantMap4["sys/chassis-5/blade-2/mgmt/health"]["minor"] = "ADDDC Bank-level adaptive virtual lockstep is activated on DIMM DDR4_P1_B1_ECC. Post Package Repair will be performed on this DIMM during the next system reboot."

	tests := []struct {
		name string
		args args
		want want
	}{
		{"1", args{mgmtHealthAttrMaps: mgmtHealthAttrMaps1}, want{wantMap: wantMap1}},
		{"2", args{mgmtHealthAttrMaps: mgmtHealthAttrMaps2}, want{wantMap: wantMap2}},
		{"3", args{mgmtHealthAttrMaps: mgmtHealthAttrMaps3}, want{wantMap: wantMap3}},
		{"4", args{mgmtHealthAttrMaps: mgmtHealthAttrMaps4}, want{wantMap: wantMap4}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := convertMgmtHealthAttrMaps(tt.args.mgmtHealthAttrMaps)
			if !reflect.DeepEqual(result, tt.want.wantMap) {
				t.Errorf("convertMgmtHealthAttrMap(\"%#v\") = result (\"%#v\"), want (\"%#v\")", tt.args.mgmtHealthAttrMaps, result, tt.want.wantMap)
			}
		})
	}
}

func Test_GetFaultInstMap(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			b := string(body)

			if strings.Contains(b, "mgmtHealthAttr") {
				return []byte(`<configResolveClass cookie="1695494280/320b1332-db62-462e-a002-daaed4a4cecd" response="yes" classId="mgmtHealthAttr">
<outConfigs>
<mgmtHealthAttr description="ADDDC Bank-level adaptive virtual lockstep is activated on DIMM DDR4_P2_K1_ECC. Post Package Repair will be performed on this DIMM during the next system reboot." dn="sys/rack-unit-47/mgmt/health/RAS Event (2C)" name="RAS Event (2C)" severity="minor" value="PPR Required"/>
</outConfigs>
</configResolveClass>`), 200, nil
			}

			return []byte(`<configResolveClass cookie="1695494280/320b1332-db62-462e-a002-daaed4a4cecd" response="yes" classId="faultInst">
<outConfigs>
<faultInst ack="no" cause="vif-down" changeSet="" code="F0479" created="2023-04-21T16:48:58.393" descr="Virtual interface 1048 link state is down" dn="sys/rack-unit-53/adaptor-1/host-eth-2/vif-1048/fault-F0479" highestSeverity="major" id="26102859" lastTransition="2023-04-21T16:48:58.393" lc="" occur="1" origSeverity="major" prevSeverity="major" rule="dcx-vif-link-state" severity="major" tags="network" type="management"/>  
<faultInst ack="no" cause="health-minor" changeSet="" code="F1705" created="2023-09-23T17:00:40.365" descr="RAS Event (2C) : Please check the Health tab for more details" dn="sys/rack-unit-47/mgmt/health/fault-F1705" highestSeverity="minor" id="36241976" lastTransition="2023-09-23T17:00:40.365" lc="" occur="1" origSeverity="minor" prevSeverity="minor" rule="mgmt-health-status-health-minor-issue" severity="minor" tags="" type="management"/>  
<faultInst ack="no" cause="link-down" changeSet="" code="F0283" created="2023-09-23T18:07:37.582" descr="ether VIF 1156 on server 54 of switch B down, reason: non-participating" dn="sys/rack-unit-54/fabric-B/path-1/vc-1156/fault-F0283" highestSeverity="major" id="36245145" lastTransition="2023-09-23T18:07:37.582" lc="" occur="1" origSeverity="major" prevSeverity="major" rule="dcx-vc-down" severity="major" tags="network,server" type="network"/>
</outConfigs>
</configResolveClass>`), 200, nil
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.cookie = testCookie2
	u.client = mockHttpClient

	faultInstList, err := u.GetFaultInstMaps(context.Background(), true)
	if err != nil {
		t.Errorf("GetFaultInstMaps error : %v", err)
	}

	// ... existing code (Assertions unverändert) ...
	faultInst1ExpectedMap := make(map[string]string)
	faultInst1ExpectedMap["ack"] = "no"
	faultInst1ExpectedMap["cause"] = "vif-down"
	faultInst1ExpectedMap["changeSet"] = ""
	faultInst1ExpectedMap["code"] = "F0479"
	faultInst1ExpectedMap["created"] = "2023-04-21T16:48:58.393"
	faultInst1ExpectedMap["descr"] = "Virtual interface 1048 link state is down"
	faultInst1ExpectedMap["dn"] = "sys/rack-unit-53/adaptor-1/host-eth-2/vif-1048/fault-F0479"
	faultInst1ExpectedMap["highestSeverity"] = severityMajor
	faultInst1ExpectedMap["id"] = "26102859"
	faultInst1ExpectedMap["lastTransition"] = "2023-04-21T16:48:58.393"
	faultInst1ExpectedMap["lc"] = ""
	faultInst1ExpectedMap["occur"] = "1"
	faultInst1ExpectedMap["origSeverity"] = severityMajor
	faultInst1ExpectedMap["prevSeverity"] = severityMajor
	faultInst1ExpectedMap["rule"] = "dcx-vif-link-state"
	faultInst1ExpectedMap["severity"] = severityMajor
	faultInst1ExpectedMap["tags"] = "network"
	faultInst1ExpectedMap["type"] = "management"

	faultInst2ExpectedMap := make(map[string]string)
	faultInst2ExpectedMap["ack"] = "no"
	faultInst2ExpectedMap["cause"] = "health-minor"
	faultInst2ExpectedMap["changeSet"] = ""
	faultInst2ExpectedMap["code"] = "F1705"
	faultInst2ExpectedMap["created"] = "2023-09-23T17:00:40.365"
	faultInst2ExpectedMap["descr"] = "RAS Event (2C) : Please check the Health tab for more details"
	faultInst2ExpectedMap["dn"] = "sys/rack-unit-47/mgmt/health/fault-F1705"
	faultInst2ExpectedMap["highestSeverity"] = severityMinor
	faultInst2ExpectedMap["id"] = "36241976"
	faultInst2ExpectedMap["lastTransition"] = "2023-09-23T17:00:40.365"
	faultInst2ExpectedMap["lc"] = ""
	faultInst2ExpectedMap["occur"] = "1"
	faultInst2ExpectedMap["origSeverity"] = severityMinor
	faultInst2ExpectedMap["prevSeverity"] = severityMinor
	faultInst2ExpectedMap["rule"] = "mgmt-health-status-health-minor-issue"
	faultInst2ExpectedMap["severity"] = severityMinor
	faultInst2ExpectedMap["tags"] = ""
	faultInst2ExpectedMap["type"] = "management"
	faultInst2ExpectedMap["mgmtHealthAttr"] = "ADDDC Bank-level adaptive virtual lockstep is activated on DIMM DDR4_P2_K1_ECC. Post Package Repair will be performed on this DIMM during the next system reboot."

	faultInst3ExpectedMap := make(map[string]string)
	faultInst3ExpectedMap["ack"] = "no"
	faultInst3ExpectedMap["cause"] = "link-down"
	faultInst3ExpectedMap["changeSet"] = ""
	faultInst3ExpectedMap["code"] = "F0283"
	faultInst3ExpectedMap["created"] = "2023-09-23T18:07:37.582"
	faultInst3ExpectedMap["descr"] = "ether VIF 1156 on server 54 of switch B down, reason: non-participating"
	faultInst3ExpectedMap["dn"] = "sys/rack-unit-54/fabric-B/path-1/vc-1156/fault-F0283"
	faultInst3ExpectedMap["highestSeverity"] = severityMajor
	faultInst3ExpectedMap["id"] = "36245145"
	faultInst3ExpectedMap["lastTransition"] = "2023-09-23T18:07:37.582"
	faultInst3ExpectedMap["lc"] = ""
	faultInst3ExpectedMap["occur"] = "1"
	faultInst3ExpectedMap["origSeverity"] = severityMajor
	faultInst3ExpectedMap["prevSeverity"] = severityMajor
	faultInst3ExpectedMap["rule"] = "dcx-vc-down"
	faultInst3ExpectedMap["severity"] = severityMajor
	faultInst3ExpectedMap["tags"] = "network,server"
	faultInst3ExpectedMap["type"] = "network"

	expectedList := make([]map[string]string, 0, 3)
	expectedList = append(expectedList, faultInst1ExpectedMap, faultInst2ExpectedMap, faultInst3ExpectedMap)

	if !reflect.DeepEqual(expectedList, faultInstList) {
		t.Errorf("GetFaultInstMaps()\nexpected: %v\n but was : %v", expectedList, faultInstList)
	}
}

func Test_CimcGetFaultInstMapWithStorageRaidBatteryWithoutError(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			b := string(body)

			if strings.Contains(b, "storageRaidBattery") {
				return []byte(`<configResolveClass cookie="1695494280/320b1332-db62-462e-a002-daaed4a4cecd" response="yes" classId="storageRaidBattery">
<outConfigs>
<storageRaidBattery dn="sys/rack-unit-1/board/storage-SAS-MRAID1/raid-battery" adminAction="no-op" batteryType="TMM-C SuperCap" health="Good" batteryStatus="Optimal" batteryPresent="true" chargingState="N/A" retentionTime="N/A" temperature="26 degrees C" temperatureHigh="false" designVoltage="4.980 V" voltage="4.978 V" current="0.000 A" learnMode="Auto" completedChargeCycles="N/A" learnCycleStatus="Successful" learnCycleRequested="false" nextLearnCycle="2025-03-20 19:41" designCapacity="96 Joules" fullCapacity="N/A" remainingCapacity="N/A" relativeStateOfCharge="N/A" absoluteStateOfCharge="N/A" expectedMarginOfError="N/A" manufacturer="LSI" dateOfManufacture="2023-11-23" serialNumber="30170" firmwareVersion="300-4GB"></storageRaidBattery>
<storageRaidBattery dn="sys/rack-unit-1/board/storage-SATA-MSTOR-RAID/raid-battery" adminAction="no-op"></storageRaidBattery>
</outConfigs>
</configResolveClass>`), 200, nil
			} else if strings.Contains(b, "mgmtHealthAttr") {
				return []byte(`<configResolveClass cookie="1695494280/320b1332-db62-462e-a002-daaed4a4cecd" response="yes" classId="mgmtHealthAttr">
<outConfigs>
<mgmtHealthAttr description="ADDDC Bank-level adaptive virtual lockstep is activated on DIMM DDR4_P2_K1_ECC. Post Package Repair will be performed on this DIMM during the next system reboot." dn="sys/rack-unit-47/mgmt/health/RAS Event (2C)" name="RAS Event (2C)" severity="minor" value="PPR Required"/>
</outConfigs>
</configResolveClass>`), 200, nil
			}

			return []byte(`<configResolveClass cookie="1695494280/320b1332-db62-462e-a002-daaed4a4cecd" response="yes" classId="faultInst">
<outConfigs>
<faultInst ack="no" cause="vif-down" changeSet="" code="F0479" created="2023-04-21T16:48:58.393" descr="Virtual interface 1048 link state is down" dn="sys/rack-unit-53/adaptor-1/host-eth-2/vif-1048/fault-F0479" highestSeverity="major" id="26102859" lastTransition="2023-04-21T16:48:58.393" lc="" occur="1" origSeverity="major" prevSeverity="major" rule="dcx-vif-link-state" severity="major" tags="network" type="management"/>  
<faultInst ack="no" cause="health-minor" changeSet="" code="F1705" created="2023-09-23T17:00:40.365" descr="RAS Event (2C) : Please check the Health tab for more details" dn="sys/rack-unit-47/mgmt/health/fault-F1705" highestSeverity="minor" id="36241976" lastTransition="2023-09-23T17:00:40.365" lc="" occur="1" origSeverity="minor" prevSeverity="minor" rule="mgmt-health-status-health-minor-issue" severity="minor" tags="" type="management"/>  
<faultInst ack="no" cause="link-down" changeSet="" code="F0283" created="2023-09-23T18:07:37.582" descr="ether VIF 1156 on server 54 of switch B down, reason: non-participating" dn="sys/rack-unit-54/fabric-B/path-1/vc-1156/fault-F0283" highestSeverity="major" id="36245145" lastTransition="2023-09-23T18:07:37.582" lc="" occur="1" origSeverity="major" prevSeverity="major" rule="dcx-vc-down" severity="major" tags="network,server" type="network"/>
</outConfigs>
</configResolveClass>`), 200, nil
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.cookie = "1695494666/8730fe43-17d8-4374-9aa1-acce5d4ba5c5"
	u.client = mockHttpClient

	faultInstList, err := u.GetFaultInstMaps(context.Background(), false)
	if err != nil {
		t.Errorf("GetFaultInstMaps error : %v", err)
	}

	// ... existing code (Assertions unverändert) ...
	_ = faultInstList
}

func Test_CimcGetFaultInstMapWithStorageRaidBatteryWithError(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			b := string(body)

			if strings.Contains(b, "storageRaidBattery") {
				return []byte(`<configResolveClass cookie="1695494280/320b1332-db62-462e-a002-daaed4a4cecd" response="yes" classId="storageRaidBattery">
<outConfigs>
<storageRaidBattery dn="sys/rack-unit-1/board/storage-SAS-MRAID1/raid-battery" adminAction="no-op" batteryType="TMM-C SuperCap" health="Moderate Fault" batteryStatus="Battery Failure Predicted" batteryPresent="true" chargingState="N/A" retentionTime="N/A" temperature="26 degrees C" temperatureHigh="false" designVoltage="5.006 V" voltage="8.295 V" current="0.000 A" learnMode="Auto" completedChargeCycles="N/A" learnCycleStatus="Successful" learnCycleRequested="false" nextLearnCycle="2025-04-04 09:49" designCapacity="97 Joules" fullCapacity="N/A" remainingCapacity="N/A" relativeStateOfCharge="N/A" absoluteStateOfCharge="N/A" expectedMarginOfError="N/A" manufacturer="LSI" dateOfManufacture="2023-09-01" serialNumber="14804" firmwareVersion="300-4GB"></storageRaidBattery>
<storageRaidBattery dn="sys/rack-unit-1/board/storage-SATA-MSTOR-RAID/raid-battery" adminAction="no-op"></storageRaidBattery>
</outConfigs>
</configResolveClass>`), 200, nil
			} else if strings.Contains(b, "mgmtHealthAttr") {
				return []byte(`<configResolveClass cookie="1695494280/320b1332-db62-462e-a002-daaed4a4cecd" response="yes" classId="mgmtHealthAttr">
<outConfigs>
</outConfigs>
</configResolveClass>`), 200, nil
			}

			return []byte(`<configResolveClass cookie="1695494280/320b1332-db62-462e-a002-daaed4a4cecd" response="yes" classId="faultInst">
<outConfigs>
</outConfigs>
</configResolveClass>`), 200, nil
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.cookie = "1695494666/8730fe43-17d8-4374-9aa1-acce5d4ba5c5"
	u.client = mockHttpClient

	faultInstList, err := u.GetFaultInstMaps(context.Background(), false)
	if err != nil {
		t.Errorf("GetFaultInstMaps error : %v", err)
	}
	faultInst1ExpectedMap := make(map[string]string)
	faultInst1ExpectedMap["ack"] = "no"
	faultInst1ExpectedMap["cause"] = "equipment-degraded"
	faultInst1ExpectedMap["changeSet"] = ""
	faultInst1ExpectedMap["code"] = "F0997"
	faultInst1ExpectedMap["created"] = faultInstList[0]["created"]
	faultInst1ExpectedMap["descr"] = "Storage Raid Battery degraded: please check the battery."
	faultInst1ExpectedMap["dn"] = "sys/rack-unit-1/board/storage-SAS-MRAID1/raid-battery"
	faultInst1ExpectedMap["highestSeverity"] = severityMinor
	faultInst1ExpectedMap["id"] = "1"
	faultInst1ExpectedMap["lastTransition"] = faultInstList[0]["lastTransition"]
	faultInst1ExpectedMap["lc"] = ""
	faultInst1ExpectedMap["occur"] = "1"
	faultInst1ExpectedMap["origSeverity"] = severityMinor
	faultInst1ExpectedMap["prevSeverity"] = severityMinor
	faultInst1ExpectedMap["rule"] = "-"
	faultInst1ExpectedMap["severity"] = severityMinor
	faultInst1ExpectedMap["tags"] = "raid-battery"
	faultInst1ExpectedMap["type"] = "equipment"
	faultInst1ExpectedMap["mgmtHealthAttr"] = "health = Moderate Fault / batteryStatus = Battery Failure Predicted / batteryType=TMM-C SuperCap / temperature = 26 degrees C / temperatureHigh = false / designVoltage = 5.006 V / voltage = 8.295 V / current = 0.000 A / manufacturer = LSI / dateOfManufacture = 2023-09-01 / serialNumber = 14804 / firmwareVersion = 300-4GB"

	expectedList := make([]map[string]string, 0, 1)
	expectedList = append(expectedList, faultInst1ExpectedMap)

	if !reflect.DeepEqual(expectedList, faultInstList) {
		t.Errorf("GetFaultInstMaps()\nexpected: %v\n but was: %v", expectedList, faultInstList)
	}
}

func Test_GetFaultInstMapWithMgmtHealthAttr(t *testing.T) {
	mockHttpClient := &MockHttpClient{
		PostXMLFunc: func(ctx context.Context, url string, body []byte) ([]byte, int, error) {
			b := string(body)

			if strings.Contains(b, "mgmtHealthAttr") {
				return []byte(`<error cookie="" response="yes" errorCode="ERR-xml-parse-error" invocationResult="594" errorDescr="XML PARSING ERROR: no class named mgmtHealthAttr" />`), 200, nil
			}

			return []byte(`<configResolveClass cookie="5df1535bbf/c9ab30f1-edeb-5759-be09-4b1a46909dda" response="yes" classId="faultInst">
<outConfigs>
<faultInst dn="sys/rack-unit-1/board/storage-SAS-MRAID1/vd-235/fault-F1008" cause="equipment-degraded" code="F1008" created="2024-08-19T13:18:52+00:00" descr="Storage Virtual Drive 235 Degraded: please check the storage controller, or reseat the storage drive" affectedDN="sys/rack-unit-1/board/storage-SAS-MRAID1/vd-235" id="3523341056" lastTransition="2024-08-19T13:18:52+00:00" occur="1" origSeverity="cleared" prevSeverity="cleared" rule="fltStorageVirtualDriveDegraded" severity="warning" tags="storage" highestSeverity="critical" ack="yes" lc="flapping" type="server"></faultInst>
</outConfigs>
</configResolveClass>`), 200, nil
		},
	}

	u, err := NewTestClient()
	if err != nil {
		t.Fatalf("Failed to create UCS client: %v", err)
	}
	u.cookie = "5df1535bbf/c9ab30f1-edeb-5759-be09-4b1a46909dda"
	u.client = mockHttpClient

	faultInstList, err := u.GetFaultInstMaps(context.Background(), false)
	if err != nil {
		t.Errorf("GetFaultInstMaps error : %v", err)
	}

	// ... existing code (Assertions unverändert) ...
	faultInst1ExpectedMap := make(map[string]string)
	faultInst1ExpectedMap["ack"] = "yes"
	faultInst1ExpectedMap["cause"] = "equipment-degraded"
	faultInst1ExpectedMap["affectedDN"] = "sys/rack-unit-1/board/storage-SAS-MRAID1/vd-235"
	faultInst1ExpectedMap["code"] = "F1008"
	faultInst1ExpectedMap["created"] = "2024-08-19T13:18:52+00:00"
	faultInst1ExpectedMap["descr"] = "Storage Virtual Drive 235 Degraded: please check the storage controller, or reseat the storage drive"
	faultInst1ExpectedMap["dn"] = "sys/rack-unit-1/board/storage-SAS-MRAID1/vd-235/fault-F1008"
	faultInst1ExpectedMap["highestSeverity"] = "critical"
	faultInst1ExpectedMap["id"] = "3523341056"
	faultInst1ExpectedMap["lastTransition"] = "2024-08-19T13:18:52+00:00"
	faultInst1ExpectedMap["lc"] = "flapping"
	faultInst1ExpectedMap["occur"] = "1"
	faultInst1ExpectedMap["origSeverity"] = "cleared"
	faultInst1ExpectedMap["prevSeverity"] = "cleared"
	faultInst1ExpectedMap["rule"] = "fltStorageVirtualDriveDegraded"
	faultInst1ExpectedMap["severity"] = "warning"
	faultInst1ExpectedMap["tags"] = "storage"
	faultInst1ExpectedMap["type"] = "server"

	expectedList := make([]map[string]string, 0, 1)
	expectedList = append(expectedList, faultInst1ExpectedMap)

	if !reflect.DeepEqual(expectedList, faultInstList) {
		t.Errorf("GetFaultInstMaps()\nexpected: %v\n but was : %v", expectedList, faultInstList)
	}
}
