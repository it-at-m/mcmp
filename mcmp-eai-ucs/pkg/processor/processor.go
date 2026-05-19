package processor

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/it-at-m/mcmp/mcmp-eai-common/pkg/logging"
	"github.com/it-at-m/mcmp/mcmp-eai-ucs/pkg/client/ucs"
)

var ErrNilClient = errors.New("UCS client must not be nil")

type Processor struct {
	ucsClient *ucs.Client
	logger    logging.Logger
}

func NewProcessor(client *ucs.Client, logger logging.Logger) (*Processor, error) {
	if client == nil {
		return nil, ErrNilClient
	}
	if logger == nil {
		logger = logging.NewNoOpLogger()
	}
	return &Processor{ucsClient: client, logger: logger}, nil
}

func (p *Processor) AggregateData(ctx context.Context) (*Cloud, error) {
	err := p.ucsClient.Login(ctx)
	if err != nil {
		return nil, fmt.Errorf("ucs login: %w", err)
	}

	defer func() {
		if logoutErr := p.ucsClient.Logout(ctx); logoutErr != nil {
			p.logger.Error("ucs logout failed", "error", logoutErr)
		}
	}()

	serverMap, err := p.ucsClient.GetServerMap(ctx)
	if err != nil {
		return nil, fmt.Errorf("ucs get server map: %w", err)
	}

	servers := p.parseServers(serverMap)

	var cloudType CloudType
	if p.ucsClient.IsCIMC() {
		cloudType = CloudTypeUcsCimc
	} else {
		cloudType = CloudTypeUcsManager
	}

	ucsData := &Cloud{
		Cloud:     p.ucsClient.Hostname(),
		CloudType: cloudType,
		Servers:   servers,
	}

	return ucsData, nil
}

func (p *Processor) parseServers(serverMap map[string]map[string]string) []Server {
	var servers []Server

	for _, serverAttrs := range serverMap {
		if ucs.IsBladeServer(serverAttrs["dn"]) {
			server := p.parseBladeAttributes(serverAttrs)
			servers = append(servers, server)
		} else if ucs.IsRackUnitServer(serverAttrs["dn"]) {
			server := p.parseRackUnitAttributes(serverAttrs)
			servers = append(servers, server)
		}
	}
	return servers
}

func (p *Processor) parseBladeAttributes(attrs map[string]string) Server {
	return p.parseServerAttributes(attrs, ServerTypeCiscoBlade)
}

func (p *Processor) parseRackUnitAttributes(attrs map[string]string) Server {
	return p.parseServerAttributes(attrs, ServerTypeCiscoRackUnit)
}

func (p *Processor) parseServerAttributes(attrs map[string]string, serverType ServerType) Server {
	mfgTime, err := ucs.ParseUcsTime(attrs["mfgTime"])
	if err != nil {
		mfgTime = nil
	}

	var mfgTimeStr *string
	if mfgTime != nil {
		mfgTimeStr = new(mfgTime.Format(time.RFC3339))
	}

	server := Server{
		DN:                new(attrs["dn"]),
		Name:              new(attrs["usrLbl"]),
		Association:       new(attrs["association"]),
		MemorySpeed:       new(parseUint(attrs["memorySpeed"])),
		MfgTime:           mfgTimeStr,
		Model:             new(attrs["model"]),
		NumOfAdaptors:     new(parseUint(attrs["numOfAdaptors"])),
		NumCoresPerSocket: new(parseUint(attrs["numOfCores"])),
		NumOfCoresEnabled: new(parseUint(attrs["numOfCoresEnabled"])),
		NumCPU:            new(parseUint(attrs["numOfCpus"])),
		NumOfEthHostIfs:   new(parseUint(attrs["numOfEthHostIfs"])),
		NumOfFcHostIfs:    new(parseUint(attrs["numOfFcHostIfs"])),
		NumOfThreads:      new(parseUint(attrs["numOfThreads"])),
		PowerState:        new(attrs["operPower"]),
		OperState:         new(attrs["operState"]),
		UUID:              new(attrs["serial"]),
		//	ServerId:          new(parseUint(attrs["serverId"])),
		AvailableMemory: new(parseUint64(attrs["availableMemory"])),
		MemoryMB:        new(parseUint64(attrs["totalMemory"])),
		Vendor:          new(attrs["vendor"]),
		Vid:             new(attrs["vid"]),
		ServerType:      serverType,
		ServerKind:      ServerKindHardware,
	}

	switch serverType {
	case ServerTypeCiscoBlade:
		server.ServerId = nil
		server.ChassisId = new(parseUint(attrs["chassisId"]))
		server.SlotId = new(parseUint(attrs["slotId"]))

	case ServerTypeCiscoRackUnit:
		server.ServerId = new(parseUint(attrs["serverId"]))
		server.ChassisId = nil
		server.SlotId = nil
	}

	return server
}

func parseUint(value string) uint {
	if value == "" {
		return 0
	}
	u, err := ucs.ParseUint(value)
	if err != nil {
		return 0
	}
	return u
}

func parseUint64(value string) uint64 {
	if value == "" {
		return 0
	}
	u, err := ucs.ParseUint(value)
	if err != nil {
		return 0
	}
	return uint64(u)
}
