package ucs

import (
	"encoding/xml"
)

type (
	aaaLoginRequest struct {
		XMLName    struct{} `xml:"aaaLogin"`
		InName     string   `xml:"inName,attr"`
		InPassword string   `xml:"inPassword,attr"`
	}

	aaaLoginResponse struct {
		XMLName          struct{} `xml:"aaaLogin"`
		Cookie           string   `xml:"cookie,attr"`
		Response         string   `xml:"response,attr"`
		OutCookie        string   `xml:"outCookie,attr"`
		OutRefreshPeriod string   `xml:"outRefreshPeriod,attr"`
		OutPriv          string   `xml:"outPriv,attr"`
		ErrorCode        int      `xml:"errorCode,attr"`
		ErrorDescr       string   `xml:"errorDescr,attr"`
	}

	aaaLogoutRequest struct {
		XMLName  struct{} `xml:"aaaLogout"`
		InCookie string   `xml:"inCookie,attr"`
	}

	aaaLogoutResponse struct {
		XMLName   struct{} `xml:"aaaLogout"`
		Cookie    string   `xml:"cookie,attr"`
		Response  string   `xml:"response,attr"`
		OutStatus string   `xml:"outStatus,attr"`
	}

	mgmtControllerRequest struct {
		XMLName            xml.Name `xml:"mgmtController"`
		Dn                 string   `xml:"dn,attr"`
		Status             string   `xml:"status,attr"`
		Sacl               string   `xml:"sacl,attr"`
		MgmtKvmCertificate mgmtKvmCertificateRequest
	}

	mgmtControllerResponse struct {
		XMLName                      xml.Name `xml:"mgmtController"`
		AdminOperation               string   `xml:"adminOperation,attr"`
		CmcSupportedStorageFeatures  string   `xml:"cmcSupportedStorageFeatures,attr"`
		CmcSupportedStorageOps       string   `xml:"cmcSupportedStorageOps,attr"`
		DesiredMaintenanceMode       string   `xml:"desiredMaintenanceMode,attr"`
		DimmBlacklistingOperState    string   `xml:"dimmBlacklistingOperState,attr"`
		DiskZoningState              string   `xml:"diskZoningState,attr"`
		Dn                           string   `xml:"dn,attr"`
		ExtendedClId                 string   `xml:"extendedClId,attr"`
		FsmDescr                     string   `xml:"fsmDescr,attr"`
		FsmFlags                     string   `xml:"fsmFlags,attr"`
		FsmPrev                      string   `xml:"fsmPrev,attr"`
		FsmProgr                     string   `xml:"fsmProgr,attr"`
		FsmRmtInvErrCode             string   `xml:"fsmRmtInvErrCode,attr"`
		FsmRmtInvErrDescr            string   `xml:"fsmRmtInvErrDescr,attr"`
		FsmRmtInvRslt                string   `xml:"fsmRmtInvRslt,attr"`
		FsmStageDescr                string   `xml:"fsmStageDescr,attr"`
		FsmStamp                     string   `xml:"fsmStamp,attr"`
		FsmStatus                    string   `xml:"fsmStatus,attr"`
		FsmTry                       string   `xml:"fsmTry,attr"`
		Guid                         string   `xml:"guid,attr"`
		HostagCommMethod             string   `xml:"hostagCommMethod,attr"`
		ID                           string   `xml:"id,attr"`
		LastRebootReason             string   `xml:"lastRebootReason,attr"`
		Model                        string   `xml:"model,attr"`
		OperConn                     string   `xml:"operConn,attr"`
		PowerFanSpeedPolicySupported string   `xml:"powerFanSpeedPolicySupported,attr"`
		Revision                     string   `xml:"revision,attr"`
		Serial                       string   `xml:"serial,attr"`
		Status                       string   `xml:"status,attr"`
		StorageOobConfigSupported    string   `xml:"storageOobConfigSupported,attr"`
		StorageOobInterfaceSupported string   `xml:"storageOobInterfaceSupported,attr"`
		StorageSubsystemState        string   `xml:"storageSubsystemState,attr"`
		Subject                      string   `xml:"subject,attr"`
		SupportedCapability          string   `xml:"supportedCapability,attr"`
		Vendor                       string   `xml:"vendor,attr"`
		WebUIKvmConsoleSupported     string   `xml:"webUIKvmConsoleSupported,attr"`
	}

	mgmtKvmCertificateRequest struct {
		XMLName     xml.Name `xml:"mgmtKvmCertificate"`
		Certificate string   `xml:"certificate,attr"`
		Descr       string   `xml:"descr,attr"`
		Key         string   `xml:"key,attr"`
		Name        string   `xml:"name,attr"`
		PolicyOwner string   `xml:"policyOwner,attr"`
		Rn          string   `xml:"rn,attr"`
		Sacl        string   `xml:"sacl,attr"`
	}

	configResolveClassRequest struct {
		XMLName        struct{} `xml:"configResolveClass"`
		Cookie         string   `xml:"cookie,attr"`
		InHierarchical string   `xml:"inHierarchical,attr"`
		ClassId        string   `xml:"classId,attr"`
		InFilter       *inFilter
	}

	configResolveDnRequest struct {
		XMLName        struct{} `xml:"configResolveDn"`
		Cookie         string   `xml:"cookie,attr"`
		InHierarchical string   `xml:"inHierarchical,attr"`
		Dn             string   `xml:"dn,attr"`
	}

	pairRequest struct {
		XMLName        xml.Name `xml:"pair"`
		Key            string   `xml:"key,attr"`
		MgmtController mgmtControllerRequest
	}

	pairResponse struct {
		XMLName        xml.Name `xml:"pair"`
		Key            string   `xml:"key,attr"`
		MgmtController mgmtControllerResponse
	}

	inConfigs struct {
		XMLName xml.Name `xml:"inConfigs"`
		Pair    pairRequest
	}

	outConfigs struct {
		XMLName xml.Name `xml:"outConfigs"`
		Pair    pairResponse
	}

	configConfMosRequest struct {
		XMLName        xml.Name `xml:"configConfMos"`
		Cookie         string   `xml:"cookie,attr"`
		InHierarchical string   `xml:"inHierarchical,attr"`
		InConfigs      inConfigs
	}

	configConfMosResponse struct {
		XMLName    xml.Name `xml:"configConfMos"`
		Cookie     string   `xml:"cookie,attr"`
		Response   string   `xml:"response,attr"`
		OutConfigs outConfigs
	}

	inFilter struct {
		XMLName xml.Name `xml:"inFilter,omitempty"`
		Eq      *eq      `xml:"eq,omitempty"`
		Wcard   *wcard   `xml:"wcard,omitempty"`
	}

	// equality Filter
	eq struct {
		XMLName  struct{} `xml:"eq"`
		Class    string   `xml:"class,attr"`
		Property string   `xml:"property,attr"`
		Value    string   `xml:"value,attr"`
	}

	// wildcard filter
	wcard struct {
		XMLName  struct{} `xml:"wcard"`
		Class    string   `xml:"class,attr"`
		Property string   `xml:"property,attr"`
		Value    string   `xml:"value,attr"`
	}
)
