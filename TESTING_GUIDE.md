# CCTV Scanner - Testing & Deployment Guide

## Prerequisites for Hardware Testing

### Equipment
- **Phone**: Samsung Galaxy S26 (or any Android 8+ device)
- **Network Adapter**: USB-C Gigabit Ethernet Adapter
- **Network Switch**: Small gigabit switch (or direct cable to device)
- **Cameras**: At least one IP camera (Speco, Hikvision, ONVIF, etc.)
- **Power**: USB-C charger or laptop for power during testing

### Network Setup
```
Galaxy S26 (USB-C)
    |
    └─ USB-C Ethernet Adapter
        |
        └─ Cat6 Ethernet Cable
            |
            └─ Network Switch (or direct to device)
                |
                ├─ Speco Camera (192.168.1.101)
                ├─ Speco NVR (192.168.1.10)
                └─ ONVIF Camera (192.168.1.102)
```

## Build & Deploy

### 1. Build Debug APK
```bash
cd /workspaces/CCTV-Scanner

# Build debug APK (requires Java 21)
./build.sh ./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

### 2. Install on Device
```bash
# Connect device via USB
adb devices

# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or use Android Studio to run directly
./build.sh ./gradlew installDebug
```

### 3. Verify Installation
```bash
# Check app installed
adb shell pm list packages | grep cctvscanner

# Launch app
adb shell am start -n com.cctvscanner/.MainActivity

# View logs
adb logcat com.cctvscanner:V *:S
```

## Test Execution

### Phase 1: Interface Detection
**Goal**: Verify USB Ethernet detection and configuration display

1. **Connect USB Ethernet adapter** to Galaxy S26
2. **Ensure devices on network** are powered (cameras, switch, etc.)
3. **Open CCTV Scanner app**
4. **Expected results**:
   - [ ] Interface shows "Ethernet" (not Wi-Fi)
   - [ ] Link Status: "Connected"
   - [ ] Speed: "1 Gbps"
   - [ ] IP Address: Valid IPv4 (e.g., 192.168.1.50)
   - [ ] Subnet: Valid mask (e.g., 255.255.255.0)
   - [ ] Gateway: Router IP (e.g., 192.168.1.1)
   - [ ] MAC: Hardware address in XX:XX:XX:XX:XX:XX format
5. **Disconnect and reconnect** adapter
   - [ ] App updates display
   - [ ] No crashes

### Phase 2: IP Scanning
**Goal**: Discover devices via TCP port scanning

1. **From Phase 1 screen**, tap **"SCAN NETWORK (IP)"**
2. **Wait for scan** to complete (~3-5 seconds)
3. **Expected results**:
   - [ ] Devices appear in list
   - [ ] Each device shows IP address
   - [ ] Open ports listed (80, 554, etc.)
   - [ ] Response time in milliseconds
   - [ ] Status = "Unknown Network Device"
4. **Verify each camera**:
   - Speco camera appears with known open ports
   - Response times reasonable (< 2000ms)
   - No UI freezing during scan
5. **Tap CANCEL** mid-scan
   - [ ] Scan stops
   - [ ] Partial results retained
   - [ ] No crash

### Phase 3: ONVIF Discovery
**Goal**: Discover ONVIF devices via multicast WS-Discovery

1. **From Phase 2**, tap **"ONVIF DISCOVERY"**
   - If no devices from IP scan, do IP scan first, then ONVIF
2. **Wait for multicast probe** (~4 seconds)
3. **Expected results**:
   - [ ] ONVIF devices discovered
   - [ ] "✓ ONVIF" badge displayed
   - [ ] Manufacturer extracted (e.g., "Speco")
   - [ ] Model extracted (e.g., "O4D9")
   - [ ] Device merged with IP scan results (no duplicates)
4. **Verify detection**:
   - Speco O4D9 camera shows ONVIF support
   - NVR shows as "Speco NVR" (not "Camera")
   - Firmware/serial shown if available
5. **Compare results**:
   - IP scan + ONVIF = complete device info
   - Discovery methods show ["IP Scan", "ONVIF"]

### Phase 4: MAC/OUI Resolution
**Goal**: Identify manufacturers from MAC addresses

1. **After IP or ONVIF scan**, check device cards
2. **Expected results**:
   - [ ] MAC address displayed (XX:XX:XX:XX:XX:XX)
   - [ ] Manufacturer shown (from OUI database)
   - [ ] Speco manufacturer recognized
   - [ ] Discovery methods include "MAC/OUI"
3. **Verify OUI database**:
   - Speco MAC (00:08:5B, 00:50:FC, etc.) → "Speco Technologies"
   - Other manufacturers recognized correctly
   - Unknown manufacturer → null/not shown

### Phase 5: Speco Discovery
**Goal**: Discover Speco devices via HTTP API

1. **From Phase 2 (after IP scan)**, tap **"SPECO DISCOVERY"**
2. **Wait for HTTP queries** (~2-3 seconds)
3. **Expected results**:
   - [ ] Speco devices identified
   - [ ] Model information retrieved
   - [ ] Serial number (if available)
   - [ ] Device type: Camera, NVR, or DVR
   - [ ] Status = "Speco Camera", "Speco NVR", etc.
4. **Verify Speco integration**:
   - Discovery method shows "Speco HTTP Discovery"
   - Devices merged with existing results
   - No duplicate entries
5. **Port enumeration**:
   - Test device on port 80, 8080, 443, etc.
   - Scanner finds it on correct port

### Phase 6: Combined Scan
**Goal**: Run all discovery methods simultaneously

1. **From Phase 1**, tap **"COMBINED SCAN"**
2. **Wait for completion** (~6-8 seconds)
3. **Expected results**:
   - [ ] All discovery methods run in parallel
   - [ ] Devices appear as they're discovered (streaming)
   - [ ] Final result includes all information
   - [ ] No duplicate devices
   - [ ] Discovery methods show multiple sources
4. **Device information completeness**:
   - IP Address (from IP scan)
   - Open Ports (from TCP scan)
   - MAC Address (from ARP + OUI)
   - Manufacturer (from OUI or ONVIF)
   - Model (from ONVIF or Speco)
   - ONVIF support (from multicast or HTTP)
5. **Performance**:
   - UI responsive throughout
   - No ANR (Application Not Responding) warnings
   - Scan completes in reasonable time

### Phase 7: Configuration (Speco)
**Goal**: Test IP configuration workflow

1. **From device list**, tap **"CONFIGURE"** button on Speco device
2. **Expected workflow**:
   - [ ] Current configuration displayed
   - [ ] Input fields for new IP, subnet, gateway
   - [ ] "Review Changes" button
   - [ ] Changes preview shown with warnings
   - [ ] "Apply Configuration" button (if confirmed)
3. **Safety checks**:
   - [ ] Warning: "Device IP will change to X.X.X.X"
   - [ ] Confirmation required before applying
   - [ ] Device reboot warning shown
4. **Post-configuration**:
   - [ ] Application status displayed
   - [ ] Verification attempted (with timeout)
   - [ ] Success/failure clearly indicated

### Phase 8: Error Handling
**Goal**: Verify app handles errors gracefully

1. **No Ethernet connected**:
   - [ ] App shows "No USB Ethernet"
   - [ ] Buttons disabled appropriately
   - [ ] No crashes
2. **Empty network (no devices)**:
   - [ ] IP scan completes with 0 results
   - [ ] No error message, graceful display
3. **Unresponsive device**:
   - [ ] Scan continues past slow/offline device
   - [ ] Timeouts respected (2-4 seconds)
   - [ ] Other devices still discovered
4. **Invalid configuration**:
   - [ ] Bad IP address rejected with message
   - [ ] Invalid subnet mask rejected
   - [ ] User prompted to fix before applying
5. **Network changes**:
   - [ ] Disconnect Ethernet during scan
   - [ ] Scan cancels gracefully
   - [ ] Reconnect, app detects new connection
   - [ ] Fresh scan works normally

## Logging & Debugging

### Enable Logcat
```bash
# View all logs from app
adb logcat com.cctvscanner:V *:S

# Save logs to file
adb logcat -d > cctv_logs.txt

# Real-time log display with timestamps
adb logcat -v threadtime com.cctvscanner:V *:S
```

### Key log points to check
- "Detecting USB Ethernet" - Interface detection
- "Scanning subnet" - IP scan start
- "Sending ONVIF Probe" - WS-Discovery start
- "Discovered device" - Device found
- "Parsing ONVIF response" - ONVIF parsing
- "Configuration applied" - Config change

### Crash diagnosis
```bash
# If app crashes, logcat shows:
adb logcat | grep FATAL

# Full crash stack trace
adb logcat | grep -A 20 "AndroidRuntime.*FATAL"
```

## Performance Benchmarks

### Expected timings
| Operation | Time |
|---|---|
| USB Ethernet detection | <1 sec |
| IP subnet calculation | <0.1 sec |
| Full /24 subnet scan | 3-5 sec |
| ONVIF multicast probe | ~4 sec |
| Speco HTTP queries | 2-3 sec |
| Combined scan | 6-8 sec |
| Configuration read | 2-3 sec |
| Configuration verification | 3-10 sec |

### Network conditions
- **LAN latency**: Typically <5ms (same switch)
- **TCP timeout**: 350ms per port per IP
- **Concurrent connections**: Limited by Android (usually 16-32)
- **Multicast**: May not work on some Wi-Fi networks (USB Ethernet should work)

## Test Environment Variations

### Scenario A: Direct Camera
- Phone → USB Ethernet → Camera (single cable)
- Expected: Device discovered at direct IP
- Test: IP scan should find 1-2 addresses

### Scenario B: Network Switch
- Phone → USB Ethernet → Switch → Multiple cameras
- Expected: All cameras discovered
- Test: Combined scan should find all devices

### Scenario C: Mixed Network
- Speco camera, ONVIF camera, NVR on same switch
- Expected: All discovered, properly classified
- Test: Discovery methods mix correctly

### Scenario D: Same-subnet vs Cross-subnet
- All devices on 192.168.1.0/24
- Expected: All found via IP scan + ONVIF
- Test: No special configuration needed

## Reporting Test Results

### For each test, record:
- ✓ Passed: Feature works as expected
- ✗ Failed: Feature doesn't work (describe issue)
- ? Partial: Feature partially works (describe limitation)
- N/A: Test not applicable (describe reason)

### Critical issues (blocking)
- [ ] App crashes
- [ ] USB Ethernet not detected
- [ ] No devices discovered
- [ ] Device list shows wrong info
- [ ] Configuration causes device disconnect

### Non-blocking issues
- [ ] Slow discovery (> expected time)
- [ ] Occasional timeout
- [ ] Missing optional fields
- [ ] UI layout issue on different screen size

## Post-Test Checklist

1. [ ] All 8 test phases completed
2. [ ] No critical issues
3. [ ] Performance acceptable
4. [ ] Error handling verified
5. [ ] Logs reviewed (no exceptions)
6. [ ] Multiple device types tested
7. [ ] Configuration (if tested) verified
8. [ ] Results documented

## Next Steps

- **If all tests pass**: Ready for Phase 7 (site management, photos, QR codes)
- **If minor issues**: Document and prioritize for fixes
- **If critical issues**: Debug and re-test before proceeding

---

**Test Date**: _________  
**Tester**: _________  
**Device**: Galaxy S26  
**Devices Tested**: _________  
**Overall Result**: ✓ PASS / ✗ FAIL / ? PARTIAL
