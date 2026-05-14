package ucs

import (
	"bytes"
	"context"
	"encoding/xml"
	"errors"
	"fmt"
	"log"
	"net"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/client/httpclient"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/utils"
)

const (
	apiPrefix              = "/nuova"
	outStatusSuccess       = "success"
	subjectUnknown         = "unknown"
	causeEquipmentDegraded = "equipment-degraded"
	healthGood             = "Good"
	severityMajor          = "major"
	severityMinor          = "minor"
)

var (
	ErrHostnameRequired                            = errors.New("UCS hostname is required")
	ErrUsernameRequired                            = errors.New("UCS username is required")
	ErrPasswordRequired                            = errors.New("UCS password is required")
	ErrNilContext                                  = errors.New("context must not be nil")
	ErrAAALogin                                    = errors.New("ucs aaaLogin failed")
	ErrAAALogout                                   = errors.New("ucs aaaLogout failed")
	ErrChangeMgmtKvmCertificateInvalidDN           = errors.New("ucs ChangeMgmtKvmCertificate: invalid DN in response")
	ErrChangeMgmtKvmCertificateUnexpectedFsmStatus = errors.New("ucs ChangeMgmtKvmCertificate: unexpected MgmtController FsmStatus")
	ErrInvalidResultsSuspect                       = errors.New("invalid results: suspect != no")
)

type (
	HttpClient interface {
		PostXML(ctx context.Context, url string, body []byte) ([]byte, int, error)
	}

	Config struct {
		Hostname  string `mapstructure:"Hostname"`
		Username  string `mapstructure:"Username"`
		Password  string `mapstructure:"Password"`
		Enabled   bool   `mapstructure:"Enabled"`
		VerifyTLS bool   `mapstructure:"VerifyTLS"`
	}

	Client struct {
		client HttpClient
		logger logging.Logger
		config Config
		cookie string
		isCIMC bool
	}

	KvmServer struct {
		Serial            string
		Dn                string
		EquipmentMgmtIP   string
		MgmtIPs           []string
		FsmStatus         string
		FsmCurrent        string
		FsmCompletionTime *time.Time
	}

	PowerSum struct {
		Sum   float64
		Min   float64
		Avg   float64
		Max   float64
		Count int
	}

	ServerPowerSum struct {
		Blades    PowerSum
		RackUnits PowerSum
	}
)

func (c *Config) Validate() error {
	if !c.Enabled {
		return nil // Disabled configurations don't need validation
	}
	if c.Hostname == "" {
		return ErrHostnameRequired
	}
	if c.Username == "" {
		return ErrUsernameRequired
	}
	if c.Password == "" {
		return ErrPasswordRequired
	}
	return nil
}

func NewClient(config Config, logger logging.Logger, isCIMC bool) (*Client, error) {
	if logger == nil {
		logger = logging.NewNoOpLogger()
	}

	httpClientConfig := httpclient.Config{
		Username:        config.Username,
		Password:        config.Password,
		EnableTLSVerify: config.VerifyTLS,
	}

	client, err := httpclient.NewClient(httpClientConfig, logger)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize http client: %w", err)
	}

	return &Client{
		config: config,
		client: client,
		logger: logger,
		isCIMC: isCIMC,
	}, nil
}

func (c *Client) Hostname() string {
	return c.config.Hostname
}

func (c *Client) IsEnabled() bool {
	return c.config.Enabled
}

func (c *Client) IsCIMC() bool {
	return c.isCIMC
}

func (c *Client) getBaseURL() string {
	return "https://" + c.config.Hostname + apiPrefix
}

func (s *KvmServer) MgmtIPsString() string {
	var sb strings.Builder
	for i, ip := range s.MgmtIPs {
		if i > 0 {
			sb.WriteString(",")
		}
		sb.WriteString(ip)
	}
	return sb.String()
}

func (c *Client) getXmlAttr(xmlData []byte, elementName string) (result []map[string]string) {
	decoder := xml.NewDecoder(bytes.NewBuffer(xmlData))
	for {
		token, err := decoder.Token()
		if err != nil {
			break
		}

		t, ok := token.(xml.StartElement)
		if !ok {
			continue
		}

		elmt := t
		name := elmt.Name.Local
		if name == elementName {
			entryMap := make(map[string]string)
			for _, attr := range t.Attr {
				attrName := attr.Name.Local
				attrValue := attr.Value
				entryMap[attrName] = attrValue
			}
			result = append(result, entryMap)
		}
	}
	c.logger.Debug("getXmlAttr", "result", result)
	return result
}

func (c *Client) post(ctx context.Context, data []byte) ([]byte, error) {
	if ctx == nil {
		return nil, ErrNilContext
	}
	body, statusCode, err := c.client.PostXML(ctx, c.getBaseURL(), data)
	if err != nil {
		return nil, fmt.Errorf("ucs PostXML failed: %w, status code: %d", err, statusCode)
	}
	return body, nil
}

func (c *Client) postXmlStruct(ctx context.Context, xmlStruct interface{}) ([]byte, error) {
	buf, err := xml.MarshalIndent(xmlStruct, "  ", "    ")
	if err != nil {
		return nil, fmt.Errorf("marshal xml request: %w", err)
	}
	re := regexp.MustCompile("></.*?>")
	result := re.ReplaceAllString(string(buf), " />")
	data := []byte(result)
	return c.post(ctx, data)
}

func (c *Client) postXmlResultMaps(ctx context.Context, xmlStruct interface{}, elementName string) ([]map[string]string, error) {
	resp, err := c.postXmlStruct(ctx, xmlStruct)
	if err != nil {
		return nil, err
	}
	return c.getXmlAttr(resp, elementName), nil
}

func (c *Client) postXml(ctx context.Context, xmlRequest, xmlResponse interface{}) error {
	resp, err := c.postXmlStruct(ctx, xmlRequest)
	if err != nil {
		return err
	}
	if err := xml.Unmarshal(resp, xmlResponse); err != nil {
		return fmt.Errorf("unmarshal xml response: %w", err)
	}
	return nil
}

func (c *Client) Login(ctx context.Context) error {
	aaaLoginReq := &aaaLoginRequest{InName: c.config.Username, InPassword: c.config.Password}
	aaaLoginRes := &aaaLoginResponse{}
	err := c.postXml(ctx, aaaLoginReq, aaaLoginRes)
	if err != nil {
		return err
	}
	c.logger.Debug("login", "error code", aaaLoginRes.ErrorCode)

	if aaaLoginRes.ErrorCode != 0 {
		return fmt.Errorf("%w: %s (%d)", ErrAAALogin, aaaLoginRes.ErrorDescr, aaaLoginRes.ErrorCode)
	}
	c.cookie = aaaLoginRes.OutCookie
	return nil
}

func (c *Client) Logout(ctx context.Context) error {
	aaaLogoutReq := &aaaLogoutRequest{InCookie: c.cookie}
	aaaLogoutRes := &aaaLogoutResponse{}
	err := c.postXml(ctx, aaaLogoutReq, aaaLogoutRes)
	if err != nil {
		return err
	}
	if aaaLogoutRes.OutStatus != outStatusSuccess {
		return fmt.Errorf("%w: outStatus=%s", ErrAAALogout, aaaLogoutRes.OutStatus)
	}
	c.cookie = ""
	return err
}

func (c *Client) ResolveClass(ctx context.Context, class, hierarchical string) ([]map[string]string, error) {
	req := &configResolveClassRequest{Cookie: c.cookie, InHierarchical: hierarchical, ClassId: class}
	return c.postXmlResultMaps(ctx, req, class)
}

func (c *Client) ResolveClassEq(ctx context.Context, class, hierarchical, eqProperty, eqValue string) ([]map[string]string, error) {
	req := &configResolveClassRequest{Cookie: c.cookie, InHierarchical: hierarchical, ClassId: class}
	if eqProperty != "" && eqValue != "" {
		req.InFilter = &inFilter{}
		req.InFilter.Eq = &eq{Class: class, Property: eqProperty, Value: eqValue}
	}
	return c.postXmlResultMaps(ctx, req, class)
}

func (c *Client) ResolveClassWcard(ctx context.Context, class, hierarchical, wcardProperty, wcardValue string) ([]map[string]string, error) {
	req := &configResolveClassRequest{Cookie: c.cookie, InHierarchical: hierarchical, ClassId: class}
	if wcardProperty != "" && wcardValue != "" {
		req.InFilter = &inFilter{}
		req.InFilter.Wcard = &wcard{Class: class, Property: wcardProperty, Value: wcardValue}
	}
	return c.postXmlResultMaps(ctx, req, class)
}

func (c *Client) ResolveClassFilter(ctx context.Context, class, hierarchical string, inFilter *inFilter) ([]map[string]string, error) {
	req := &configResolveClassRequest{Cookie: c.cookie, InHierarchical: hierarchical, ClassId: class}
	req.InFilter = inFilter
	return c.postXmlResultMaps(ctx, req, class)
}

func (c *Client) ResolveDn(ctx context.Context, dn, hierarchical string) ([]map[string]string, error) {
	req := &configResolveDnRequest{Cookie: c.cookie, InHierarchical: hierarchical, Dn: dn}
	resp, err := c.postXmlStruct(ctx, req)
	if err != nil {
		return nil, err
	}
	search := "outConfig>"
	s := string(resp)
	elementName := strings.TrimSpace(s[strings.Index(s, search)+len(search):])
	elementName = elementName[1:strings.Index(elementName, " ")]
	return c.getXmlAttr(resp, elementName), nil
}

func (c *Client) ChangeMgmtKvmCertificate(ctx context.Context, serverDn, privKey, crt string) (serial string, err error) {
	mgmtControllerDn := serverDn + "/mgmt"
	req := &configConfMosRequest{
		Cookie:         c.cookie,
		InHierarchical: "false",
		InConfigs: inConfigs{
			Pair: pairRequest{
				Key: mgmtControllerDn,
				MgmtController: mgmtControllerRequest{
					Dn:     mgmtControllerDn,
					Status: "created,modified",
					Sacl:   "addchild,del,mod",
					MgmtKvmCertificate: mgmtKvmCertificateRequest{
						Certificate: crt,
						Descr:       "",
						Key:         privKey,
						Name:        "",
						PolicyOwner: "local",
						Rn:          "cert",
						Sacl:        "addchild,del,mod",
					},
				},
			},
		},
	}
	res := &configConfMosResponse{}
	xmlData, err := xml.Marshal(req)
	if err != nil {
		return "", fmt.Errorf("marshal ChangeMgmtKvmCertificate request: %w", err)
	}
	// xml.Marshal wandelt \n in &#xA; um, damit kommt die UCS-API beim Attribute certificate und key nicht klar!
	xmlData = []byte(strings.ReplaceAll(string(xmlData), "&#xA;", "\n"))
	body, err := c.post(ctx, xmlData)
	if err != nil {
		return "", err
	}
	err = xml.Unmarshal(body, &res)
	if err != nil {
		return "", fmt.Errorf("unmarshal ChangeMgmtKvmCertificate response: %w", err)
	}
	if mgmtControllerDn != res.OutConfigs.Pair.MgmtController.Dn {
		return "", fmt.Errorf(
			"%w: expected=%s got=%s",
			ErrChangeMgmtKvmCertificateInvalidDN,
			mgmtControllerDn,
			res.OutConfigs.Pair.MgmtController.Dn,
		)
	}
	if res.OutConfigs.Pair.MgmtController.FsmStatus != "KvmCertBegin" {
		return "", fmt.Errorf(
			"%w: status=%s",
			ErrChangeMgmtKvmCertificateUnexpectedFsmStatus,
			res.OutConfigs.Pair.MgmtController.FsmStatus,
		)
	}
	return res.OutConfigs.Pair.MgmtController.Serial, nil
}

func (c *Client) GetServerEquipmentMgmtIpMap(ctx context.Context) (map[string]string, error) {
	mgmtIpMap := make(map[string]string)
	mgmtIf, err := c.ResolveClassEq(ctx, "mgmtIf", "false", "access", "unspecified")
	if err != nil {
		return mgmtIpMap, err
	}
	for _, mgmtIfValueMap := range mgmtIf {
		if mgmtIfValueMap["access"] == "unspecified" &&
			mgmtIfValueMap["adminState"] == "enable" &&
			mgmtIfValueMap["subject"] != subjectUnknown {
			if serverDn, contains := GetServerDN(mgmtIfValueMap["dn"]); contains {
				mgmtIpMap[serverDn] = mgmtIfValueMap["extIp"]
			}
		}
	}
	return mgmtIpMap, nil
}

func addServerMgmtIPs(ipAttribute string, values []map[string]string, serverMgmtIpMap map[string][]string) {
	for _, value := range values {
		if serverDN, contains := GetServerDN(value["dn"]); contains {
			if _, contains := serverMgmtIpMap[serverDN]; !contains {
				serverMgmtIpMap[serverDN] = []string{value[ipAttribute]}
			} else {
				list := serverMgmtIpMap[serverDN]
				list = append(list, value[ipAttribute])
				serverMgmtIpMap[serverDN] = list
			}
		}
	}
}

func removeDuplicateIPs(values []string) []string {
	keys := make(map[string]bool)
	list := []string{}
	for _, value := range values {
		if value != "" && value != "0.0.0.0" && value != "::" {
			if _, contains := keys[value]; !contains {
				keys[value] = true
				list = append(list, value)
			}
		}
	}
	sort.Strings(list)
	return list
}

func sortIPs(ips []string) []string {
	uniqueIPs := removeDuplicateIPs(ips)
	netIPs := make([]net.IP, 0, len(uniqueIPs))
	for _, ip := range uniqueIPs {
		netIPs = append(netIPs, net.ParseIP(ip))
	}
	sort.Slice(netIPs, func(i, j int) bool {
		return bytes.Compare(netIPs[i], netIPs[j]) < 0
	})
	sortIPs := make([]string, 0, len(netIPs))
	for _, ip := range netIPs {
		if ip != nil {
			sortIPs = append(sortIPs, ip.String())
		}
	}
	return sortIPs
}

func equalsIgnoreCase(s1, s2 string) bool {
	return strings.EqualFold(s1, s2)
}

func (c *Client) GetFaultInstMaps(ctx context.Context, ucsm bool) ([]map[string]string, error) {
	faultInstList, err := c.ResolveClass(ctx, "faultInst", "false")
	if err != nil {
		return nil, err
	}
	if !ucsm {
		storageRaidBatteryList, err2 := c.ResolveClass(ctx, "storageRaidBattery", "false")
		if err2 != nil {
			log.Printf("GetFaultInstMaps: ResolveClass storageRaidBattery failed: %s\n", err2)
		}
		for _, storageRaidBattery := range storageRaidBatteryList {
			health, exists := storageRaidBattery["health"]
			if !exists || equalsIgnoreCase(health, healthGood) {
				continue
			}

			storageRaidBatteryFaultInst := make(map[string]string)

			severity := severityMinor
			createdAt := time.Now().Format("2006-01-02T15:04:05.000")
			storageRaidBatteryFaultInst["ack"] = "no"
			storageRaidBatteryFaultInst["cause"] = causeEquipmentDegraded
			storageRaidBatteryFaultInst["changeSet"] = ""
			storageRaidBatteryFaultInst["code"] = "F0997"
			storageRaidBatteryFaultInst["created"] = createdAt
			storageRaidBatteryFaultInst["descr"] = "Storage Raid Battery degraded: please check the battery."
			storageRaidBatteryFaultInst["dn"] = storageRaidBattery["dn"]
			storageRaidBatteryFaultInst["highestSeverity"] = severity
			storageRaidBatteryFaultInst["id"] = "1"
			storageRaidBatteryFaultInst["lastTransition"] = createdAt
			storageRaidBatteryFaultInst["lc"] = ""
			storageRaidBatteryFaultInst["occur"] = "1"
			storageRaidBatteryFaultInst["origSeverity"] = severity
			storageRaidBatteryFaultInst["prevSeverity"] = severity
			storageRaidBatteryFaultInst["rule"] = "-"
			storageRaidBatteryFaultInst["severity"] = severity
			storageRaidBatteryFaultInst["tags"] = "raid-battery"
			storageRaidBatteryFaultInst["type"] = "equipment"
			storageRaidBatteryFaultInst["mgmtHealthAttr"] = "health = " + storageRaidBattery["health"] + " / " +
				"batteryStatus = " + storageRaidBattery["batteryStatus"] + " / " +
				"batteryType=" + storageRaidBattery["batteryType"] + " / " +
				"temperature = " + storageRaidBattery["temperature"] + " / " +
				"temperatureHigh = " + storageRaidBattery["temperatureHigh"] + " / " +
				"designVoltage = " + storageRaidBattery["designVoltage"] + " / " +
				"voltage = " + storageRaidBattery["voltage"] + " / " +
				"current = " + storageRaidBattery["current"] + " / " +
				"manufacturer = " + storageRaidBattery["manufacturer"] + " / " +
				"dateOfManufacture = " + storageRaidBattery["dateOfManufacture"] + " / " +
				"serialNumber = " + storageRaidBattery["serialNumber"] + " / " +
				"firmwareVersion = " + storageRaidBattery["firmwareVersion"]
			faultInstList = append(faultInstList, storageRaidBatteryFaultInst)
		}
	}

	mgmtHealthAttr, err := c.ResolveClass(ctx, "mgmtHealthAttr", "false")
	if err != nil {
		log.Println(err)
		return faultInstList, nil
	}
	if len(mgmtHealthAttr) == 0 {
		return faultInstList, nil
	}
	healthAttrMap := convertMgmtHealthAttrMaps(mgmtHealthAttr)
	for _, faultInstMap := range faultInstList {
		healthDN, isHealthDN := GetHealthDN(faultInstMap["dn"])
		if isHealthDN {
			if severityMap, containsDN := healthAttrMap[healthDN]; containsDN {
				if descr, containsSeverity := severityMap[faultInstMap["origSeverity"]]; containsSeverity {
					faultInstMap["mgmtHealthAttr"] = descr
				}
			}
		}
	}
	return faultInstList, nil
}

func convertMgmtHealthAttrMaps(mgmtHealthMaps []map[string]string) map[string]map[string]string {
	healthAttrDnMap := make(map[string]map[string]string)
	if len(mgmtHealthMaps) >= 1 {
		for _, mgmtHealthMap := range mgmtHealthMaps {
			healthDN, isHealthDN := GetHealthDN(mgmtHealthMap["dn"])
			if isHealthDN {
				severity := mgmtHealthMap["severity"]
				description := mgmtHealthMap["description"]
				severityMap, containsDN := healthAttrDnMap[healthDN]
				if !containsDN {
					severityMap = make(map[string]string)
					healthAttrDnMap[healthDN] = severityMap
				}
				descr, containsSeverity := severityMap[severity]
				if !containsSeverity {
					severityMap[severity] = description
				} else {
					severityMap[severity] = descr + "\n" + description
				}
			}
		}
	}
	return healthAttrDnMap
}

func (c *Client) GetServerMgmtIPsMap(ctx context.Context) (map[string][]string, error) {
	serverMap := make(map[string][]string)
	mgmtIf, err := c.ResolveClass(ctx, "mgmtIf", "false")
	if err != nil {
		return nil, err
	}
	addServerMgmtIPs("extIp", mgmtIf, serverMap)
	ip4d, err := c.ResolveClass(ctx, "vnicIpV4ProfDerivedAddr", "false")
	if err != nil {
		return nil, err
	}
	addServerMgmtIPs("addr", ip4d, serverMap)
	ip4p, err := c.ResolveClass(ctx, "vnicIpV4MgmtPooledAddr", "false")
	if err != nil {
		return nil, err
	}
	addServerMgmtIPs("addr", ip4p, serverMap)
	ip4s, err := c.ResolveClass(ctx, "vnicIpV4StaticAddr", "false")
	if err != nil {
		return nil, err
	}
	addServerMgmtIPs("addr", ip4s, serverMap)
	ip6p, err := c.ResolveClass(ctx, "vnicIpV6MgmtPooledAddr", "false")
	if err != nil {
		return nil, err
	}
	addServerMgmtIPs("addr", ip6p, serverMap)
	ip6s, err := c.ResolveClass(ctx, "vnicIpV6StaticAddr", "false")
	if err != nil {
		return nil, err
	}
	addServerMgmtIPs("addr", ip6s, serverMap)

	result := make(map[string][]string)
	for serverDN, mgmtIPs := range serverMap {
		result[serverDN] = sortIPs(mgmtIPs)
	}
	return result, err
}

func (c *Client) GetServerFsmMap(ctx context.Context) (map[string]map[string]string, error) {
	fsmMap := make(map[string]map[string]string)
	bladesFsm, err := c.ResolveClass(ctx, "computeBladeFsm", "false")
	if err != nil {
		return fsmMap, err
	}
	rackUnitsFsm, err := c.ResolveClass(ctx, "computeRackUnitFsm", "false")
	if err != nil {
		return fsmMap, err
	}

	bladesFsm = append(bladesFsm, rackUnitsFsm...)
	for _, fsmValueMap := range bladesFsm {
		if serverDn, contains := GetServerDN(fsmValueMap["dn"]); contains {
			fsmMap[serverDn] = fsmValueMap
		}
	}
	return fsmMap, nil
}

func (c *Client) GetServerMap(ctx context.Context) (map[string]map[string]string, error) {
	serverMap := make(map[string]map[string]string)

	if c.isCIMC {
		rackUnits, err := c.ResolveClass(ctx, "computeRackUnit", "false")
		if err != nil {
			return nil, err
		}
		for _, serverValueMap := range rackUnits {
			if serverDn, contains := GetServerDN(serverValueMap["dn"]); contains {
				serverMap[serverDn] = serverValueMap
			}
		}
		return serverMap, nil
	}

	blades, err := c.ResolveClass(ctx, "computeBlade", "false")
	if err != nil {
		return nil, err
	}
	rackUnits, err := c.ResolveClass(ctx, "computeRackUnit", "false")
	if err != nil {
		return nil, err
	}

	blades = append(blades, rackUnits...)
	for _, serverValueMap := range blades {
		if serverDn, contains := GetServerDN(serverValueMap["dn"]); contains {
			serverMap[serverDn] = serverValueMap
		}
	}
	return serverMap, nil
}

func (c *Client) GetKvmServer(ctx context.Context) ([]KvmServer, error) {
	servers, err := c.GetServerMap(ctx)
	if err != nil {
		return nil, err
	}
	equipmentMgmtIpsMap, err := c.GetServerEquipmentMgmtIpMap(ctx)
	if err != nil {
		return nil, err
	}
	mgmtIPsMap, err := c.GetServerMgmtIPsMap(ctx)
	if err != nil {
		return nil, err
	}
	serverFsmMap, err := c.GetServerFsmMap(ctx)
	if err != nil {
		return nil, err
	}
	var kvmServers []KvmServer
	for _, server := range servers {
		kvmServer := KvmServer{}
		kvmServer.Serial = server["serial"]
		kvmServer.Dn = server["dn"]
		equipmentMgmtIP := equipmentMgmtIpsMap[kvmServer.Dn]
		if equipmentMgmtIP != "" && equipmentMgmtIP != "0.0.0.0" {
			kvmServer.EquipmentMgmtIP = equipmentMgmtIP
		}
		mgmtIPs := mgmtIPsMap[kvmServer.Dn]
		if len(mgmtIPs) > 0 {
			kvmServer.MgmtIPs = mgmtIPs
			if kvmServer.EquipmentMgmtIP == "" {
				kvmServer.EquipmentMgmtIP = mgmtIPs[0]
			}
		}
		fsmMap := serverFsmMap[kvmServer.Dn]
		if fsmMap != nil {
			kvmServer.FsmStatus = fsmMap["fsmStatus"]
			kvmServer.FsmCurrent = fsmMap["currentFsm"]
			fsmCompletionTime, err := utils.ParseUcsTime(fsmMap["completionTime"])
			if err == nil && fsmCompletionTime != nil {
				kvmServer.FsmCompletionTime = fsmCompletionTime
			}
		}
		kvmServers = append(kvmServers, kvmServer)
	}
	return kvmServers, nil
}

func parseFloat(value string) (float64, error) {
	v, err := strconv.ParseFloat(value, 64)
	if err != nil {
		log.Print(err)
		return 0., fmt.Errorf("parse float %q: %w", value, err)
	}
	return v, nil
}

func (c *Client) getSumValues(statsList []map[string]string, attributeValue, attributeMin, attributeAvg, attributeMax string) (PowerSum, error) {
	var s PowerSum

	for _, statsMap := range statsList {
		if statsMap["suspect"] != "no" {
			return PowerSum{}, ErrInvalidResultsSuspect
		}

		value, valueErr := parseFloat(statsMap[attributeValue])
		minValue, minErr := parseFloat(statsMap[attributeMin])
		avgValue, avgErr := parseFloat(statsMap[attributeAvg])
		maxValue, maxErr := parseFloat(statsMap[attributeMax])
		if valueErr != nil || minErr != nil || avgErr != nil || maxErr != nil {
			continue
		}

		s.Sum += value
		s.Min += minValue
		s.Avg += avgValue
		s.Max += maxValue
		s.Count++
	}

	return s, nil
}

func (c *Client) GetPowerSumFi(ctx context.Context) (PowerSum, error) {
	statsList, err := c.ResolveClass(ctx, "equipmentPsuInputStats", "false")
	if err != nil {
		return PowerSum{}, err
	}

	sum, err := c.getSumValues(statsList, "power", "powerMin", "powerAvg", "powerMax")
	if err != nil {
		return PowerSum{}, err
	}

	// equipmentPsuInputStats liefert PSUs, pro FI sind es typischerweise 2
	sum.Count /= 2

	return sum, nil
}

func (c *Client) GetPowerSumFex(ctx context.Context) (PowerSum, error) {
	statsList, err := c.ResolveClassWcard(ctx, "equipmentFexPsuInputStats", "false", "dn", "sys/fex-*./fex-psu-input-stats")
	if err != nil {
		return PowerSum{}, err
	}
	return c.getSumValues(statsList, "power", "powerMin", "powerAvg", "powerMax")
}

func (c *Client) GetPowerSumChassis(ctx context.Context) (PowerSum, error) {
	statsList, err := c.ResolveClass(ctx, "equipmentChassisStats", "false")
	if err != nil {
		return PowerSum{}, err
	}
	return c.getSumValues(statsList, "inputPower", "inputPowerMin", "inputPowerAvg", "inputPowerMax")
}

func (c *Client) GetPowerSumServer(ctx context.Context) (ServerPowerSum, error) {
	statsList, err := c.ResolveClass(ctx, "computeMbPowerStats", "false")
	if err != nil {
		return ServerPowerSum{}, err
	}

	var bladesList []map[string]string
	var rackUnitsList []map[string]string
	for _, statsMap := range statsList {
		if strings.HasPrefix(statsMap["dn"], "sys/rack-unit-") {
			rackUnitsList = append(rackUnitsList, statsMap)
		} else {
			bladesList = append(bladesList, statsMap)
		}
	}

	blades, err := c.getSumValues(bladesList, "consumedPower", "consumedPowerMin", "consumedPowerAvg", "consumedPowerMax")
	if err != nil {
		return ServerPowerSum{}, err
	}

	rackUnits, err := c.getSumValues(rackUnitsList, "consumedPower", "consumedPowerMin", "consumedPowerAvg", "consumedPowerMax")
	if err != nil {
		return ServerPowerSum{}, err
	}

	return ServerPowerSum{
		Blades:    blades,
		RackUnits: rackUnits,
	}, nil
}
