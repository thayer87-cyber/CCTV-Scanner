# CCTV Scanner - Source Code Architecture

## File Organization

```
app/src/main/
├── AndroidManifest.xml          (Permissions, app configuration)
├── java/com/cctvscanner/
│   ├── MainActivity.kt           (UI entry point, Compose screens)
│   ├── NetworkScanViewModel.kt   (State management, discovery orchestration)
│   │
│   ├── NetworkModels.kt          (Data classes: NetworkInterfaceInfo, DiscoveredDevice, NetworkUiState)
│   │
│   ├── NetworkInterfaceDetector.kt  (USB Ethernet detection)
│   ├── NetworkScanner.kt           (IP scanning + TCP port detection)
│   │
│   ├── OnvifDiscoveryProvider.kt   (WS-Discovery multicast)
│   ├── OnvifDeviceService.kt       (ONVIF device queries, SOAP)
│   │
│   ├── MacAddressResolver.kt       (ARP table parsing, MAC resolution)
│   ├── OuiDatabase.kt              (40+ manufacturer OUI mappings)
│   │
│   ├── DeviceDeduplicator.kt       (Multi-source device merging + sorting)
│   │
│   ├── SpecoDiscoveryProvider.kt   (Speco HTTP discovery)
│   │
│   ├── DeviceConfiguration.kt      (Config models + interface)
│   ├── SpecoConfigurationProvider.kt (Speco IP configuration)
│   └── ConfigurationProviderFactory.kt (Provider factory pattern)
│
└── res/
    ├── values/
    │   └── strings.xml
    └── (Compose UI - no XML layouts)
```

## Module Responsibilities

### Phase 1: USB Ethernet + IP Scanning

**NetworkInterfaceDetector.kt**
- Detects USB Ethernet adapter via ConnectivityManager
- Queries NetworkCapabilities for transport type
- Extracts IPv4 address, subnet mask, gateway from LinkProperties
- Resolves MAC address via NetworkInterface.getHardwareAddress()
- Returns: NetworkInterfaceInfo

**NetworkScanner.kt**
- Calculates usable IP range from interface's IPv4/subnet
- Converts IP <→ integer for efficient range calculation
- Concurrently scans IP addresses (sequential to avoid overwhelming network)
- Tests configurable TCP ports (80, 443, 554, 1025, 1080, 3389, 37777, 5000, 8000, 8080, 9000, 9100, 6036)
- Measures response time for each successful connection
- Returns: List<DiscoveredDevice>

**NetworkModels.kt**
- NetworkInterfaceInfo: Interface metadata (IP, subnet, gateway, MAC, speed, link status)
- DiscoveredDevice: Unified device model (IP, ports, manufacturer, model, ONVIF info, discovery methods)
- NetworkUiState: UI state holder (interface info, scanning flags, device list)
- ScanRange: Subnet range calculation helper

### Phase 2: ONVIF WS-Discovery

**OnvifDiscoveryProvider.kt**
- Creates multicast socket on 239.255.255.250:3702
- Acquires multicast lock to permit kernel UDP multicast
- Binds socket to USB Ethernet interface explicitly
- Sends ONVIF Probe message (proper SOAP XML, not string matching)
- Receives ProbeMatch responses (4-second timeout)
- Parses XML DOM (not regex) to extract:
  - EndpointReference (UUID)
  - Types (device types)
  - Scopes (manufacturer, model hints)
  - XAddrs (service endpoints)
- Returns: List<DiscoveredDevice> with ONVIF fields populated

**OnvifDeviceService.kt**
- Queries ONVIF device service endpoint (typically http://ip:port/onvif/device_service)
- Sends SOAP GetDeviceInformation request
- Parses XML response to extract:
  - Manufacturer
  - Model
  - Firmware version
  - Serial number
  - Hardware ID
- Gracefully handles devices requiring authentication (returns null)
- Returns: DeviceInfo or null

### Phase 3: MAC/OUI Identification

**OuiDatabase.kt**
- Static OUI map (first 3 octets → manufacturer)
- 40+ manufacturers: Speco, Hikvision, Dahua, Axis, Hanwha, Bosch, Panasonic, Canon, Vivotek, Arecont, Cisco, Microsoft, VMware, etc.
- getManufacturer(mac): Lookup by OUI
- isCameraManufacturer(mac): Boolean check for known camera brands

**MacAddressResolver.kt**
- getMacAddressForIp(): Attempts NetworkInterface lookup, falls back to ARP table
- readArpTable(): Parses /proc/net/arp (Linux/Android)
- getManufacturerForIp(): Chains MAC resolution + OUI lookup
- formatMacAddress(): Converts between formats (XX:XX:XX, XX-XX-XX, XXXXXXXXXXXX)
- isValidMacAddress(): Regex validation

**DeviceDeduplicator.kt**
- mergeDiscoveryResults(): Combines IP scan + ONVIF + MAC results
  - Prioritizes by: ONVIF UUID > MAC > IP
  - Merges discovery methods
  - Retains all metadata
- enrichDevice(): Adds MAC address + manufacturer to device
- determineDeviceStatus(): Intelligent status naming
  - ONVIF NVR/DVR detection
  - Camera manufacturer recognition
  - RTSP/HTTP port heuristics
- sortByRelevance(): Camera manufacturers first, then by manufacturer name, then by IP

### Phase 5: Speco-Specific Discovery

**SpecoDiscoveryProvider.kt**
- Queries known Speco endpoints on each discovered IP:
  - /cgi-bin/api/info/device
  - /api/device/info
  - /device/info
- Tries Speco ports: 80, 443, 8080, 8443, 8888, 9000
- Parses JSON and XML responses
- Extracts: Model, serial number, firmware, MAC
- Detects device type (Camera, NVR, DVR, Recorder)
- Checks ONVIF support on device
- Returns: List<DiscoveredDevice> with Speco-specific data
- Integrates with existing IP scan results

### Phase 6: IP Configuration

**DeviceConfiguration.kt**
- DeviceConfiguration: Current/proposed settings (IP, subnet, gateway, DNS, DHCP)
- ConfigurationChangeProposal: Wraps proposal with validation
  - validate(): Checks IP format, subnet mask, gateway, DNS
  - haChanges(): Detects differences
  - warnings: User-facing change notifications (e.g., "IP will change to X.X.X.X")
- DeviceConfigurationProvider: Generic interface (abstract base for vendors)
  - readConfiguration(device, username, password)
  - proposeConfiguration(current, newIp, newSubnet, newGateway)
  - applyConfiguration(proposal, device, username, password)
  - verifyConfiguration(device, expectedConfig, timeout)

**SpecoConfigurationProvider.kt**
- Implements DeviceConfigurationProvider for Speco devices
- readConfiguration(): Queries /cgi-bin/api/network/config
- proposeConfiguration(): Creates proposal with safety warnings
  - Warns if IP change may disconnect device
  - Detects subnet/gateway changes
- applyConfiguration(): POSTs configuration to device
  - Builds URL-encoded payload
  - Includes Basic auth if credentials provided
- verifyConfiguration(): Polls device after config change (10-second timeout, 2-second retries)
- Handles JSON and XML responses

**ConfigurationProviderFactory.kt**
- Factory pattern: Returns appropriate provider by manufacturer
- Currently supports Speco
- Ready for Hikvision, Dahua, etc.

### UI & State Management

**MainActivity.kt**
- Compose-based single-activity architecture
- Two main screens:
  1. NetworkScannerScreen: Primary interface
  2. DeviceCard: Reusable device display component
- Displays network interface information
- Shows discovery buttons (IP Scan, ONVIF, Speco, Combined)
- Lists devices with detailed information cards
- Shows "CONFIGURE" button for supported devices

**NetworkScanViewModel.kt**
- StateFlow-based reactive state management
- initialize(): Detects interface, initializes UI state
- startScan(): IP scanning + MAC enrichment
- startOnvifDiscovery(): ONVIF discovery + merging
- startCombinedScan(): Parallel IP + ONVIF + Speco
- startSpecoDiscovery(): Speco HTTP discovery
- cancelScan(): Cancels active discovery
- Uses DeviceDeduplicator for intelligent merging
- Uses ConfigurationProviderFactory for config support

## Data Flow

### IP Scanning Flow
```
NetworkScanViewModel.startScan()
  ├─> NetworkScanner.scanSubnet(interface)
  │   ├─> Calculates subnet range
  │   ├─> Tries each IP + configurable ports
  │   └─> Returns List<DiscoveredDevice>
  ├─> DeviceDeduplicator.enrichDevice() for each device
  │   ├─> MacAddressResolver.getMacAddressForIp()
  │   ├─> OuiDatabase.getManufacturer(mac)
  │   └─> Adds discovery method "MAC/OUI"
  ├─> DeviceDeduplicator.sortByRelevance()
  └─> UI updates with devices
```

### Combined Scan Flow
```
NetworkScanViewModel.startCombinedScan()
  ├─> NetworkScanner.scanSubnet() [parallel]
  ├─> OnvifDiscoveryProvider.discoverOnvifDevices() [parallel]
  └─> Wait for both to complete
      └─> DeviceDeduplicator.mergeDiscoveryResults()
          ├─> Deduplicates by: UUID > MAC > IP
          ├─> Merges discovery methods
          └─> Returns merged list
      ├─> Enrich each device (MAC/OUI)
      ├─> Determine status for each device
      ├─> Sort by relevance
      └─> UI updates
```

### Configuration Flow
```
User taps CONFIGURE on device
  ├─> ConfigurationProviderFactory.getProvider(device)
  │   └─> SpecoConfigurationProvider if Speco
  ├─> readConfiguration() - Get current settings
  ├─> User enters new IP, subnet, gateway
  ├─> proposeConfiguration() - Validate + create proposal
  │   └─> Show warnings: "IP will change to X.X.X.X"
  ├─> User confirms
  ├─> applyConfiguration() - Send to device
  │   └─> HTTP POST to /cgi-bin/api/network/config
  └─> verifyConfiguration() - Retry until matches or timeout
      └─> Show success/failure
```

## Extensibility Points

### Adding New Discovery Provider
1. Create `XyzDiscoveryProvider.kt`
2. Implement discovery logic (return List<DiscoveredDevice>)
3. Add to ViewModel's relevant scan method
4. Devices automatically deduplicated and merged

### Adding New Configuration Provider
1. Create `XyzConfigurationProvider.kt`
2. Implement DeviceConfigurationProvider interface
3. Add mapping to ConfigurationProviderFactory.getProvider()
4. UI automatically shows CONFIGURE button for your devices

### Adding New Manufacturer to OUI Database
1. Edit OuiDatabase.kt
2. Add OUI entry: "XX:XX:XX" to "Manufacturer Name"
3. Update isCameraManufacturer() if camera brand
4. All existing scans benefit from OUI enhancement

## Performance Characteristics

| Operation | Time | Method |
|---|---|---|
| Interface detection | <1ms | Android ConnectivityManager |
| Subnet calculation | <1ms | Bitwise IP math |
| Single IP probe | 350ms | Socket with timeout |
| Full /24 scan | 3-5s | 254 IPs × 13 ports, sequential |
| ONVIF multicast | 4s | Hardcoded timeout, receives responses continuously |
| ARP lookup | 10-20ms | File I/O from /proc/net/arp |
| OUI lookup | <1ms | HashMap hash |
| Speco HTTP query | 200-500ms | Single HTTP request per port |
| Device merge/dedupe | <10ms | HashMap operations |
| XML parsing | 10-50ms | DOM parser per device |

## Thread Safety

- All discovery operations in Dispatchers.IO (background thread pool)
- UI updates via viewModelScope (main thread)
- StateFlow handles thread-safe state updates
- NetworkInterface/ConnectivityManager calls safe from any thread

## Error Handling

- All discovery methods: Try-catch with graceful return (empty list or null)
- No stack traces exposed to user
- No discovery method failure crashes app
- Configuration validation prevents invalid settings
- ARP table access: Fallback if permission denied
- Multicast: Continues if binding fails
- HTTP timeouts: 2-3 seconds, retries in verify
