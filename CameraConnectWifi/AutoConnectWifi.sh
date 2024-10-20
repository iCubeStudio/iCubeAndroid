#!/bin/bash

# Wait for 10 seconds
sleep 10
echo "Script execution starts 10 seconds after system boot." >> AutoConnectWifi.log

# WiFi SSID and password
SSID="PanConnection"
PASSWORD="PanConnection"

# Function to generate SHA-256 hash
generateSHA256Hash() {
    PASSWORD=$1
    echo -n "$PASSWORD" | sha256sum | awk '{print substr($1, 1, 20)}'
}

# Encrypt password using the function
ENCRYPTED_PASSWORD=$(generateSHA256Hash "$PASSWORD")
echo "SSID: $SSID" >> AutoConnectWifi.log
echo "ENCRYPTED_PASSWORD: $ENCRYPTED_PASSWORD" >> AutoConnectWifi.log

# Function to check if connected to the specified network
isConnectedToSSID() {
    # Get the current connected WiFi SSID
    CURRENT_SSID=$(nmcli -t -f SSID dev wifi | grep -Eo '^[^ ]+' | head -n 1)
    
    # Check if the current SSID matches the target SSID
    if [ "$CURRENT_SSID" == "$SSID" ]; then
        return 0 # Return success if match
    else
        return 1 # Return failure if no match
    fi
}

# Function to attempt connection
attemptConnection() {
    echo "Attempting to connect to $SSID..." >> AutoConnectWifi.log
    nmcli dev wifi connect "$SSID" password "$ENCRYPTED_PASSWORD"
    sleep 10
}

# Infinite loop to keep scanning for the specified SSID
while true; do
    if isConnectedToSSID; then
        echo "Already connected to $SSID. Exiting script." >> AutoConnectWifi.log
        break
    else
        echo "Not connected to $SSID. Scanning for network..." >> AutoConnectWifi.log

        # Scan for available WiFi networks
        AVAILABLE_SSID=$(nmcli -t -f SSID dev wifi)

        # Check if the target SSID is available
        if echo "$AVAILABLE_SSID" | grep -q "$SSID"; then
            echo "Network $SSID found, attempting to connect..." >> AutoConnectWifi.log

            # Attempt to connect to the network twice
            for i in {1..2}; do
                attemptConnection
                if isConnectedToSSID; then
                    echo "Successfully connected to $SSID! Exiting script." >> AutoConnectWifi.log
                    break
                fi
            done

            # Exit script regardless of connection success
            echo "Exiting script after two connection attempts." >> AutoConnectWifi.log
            break
        else
            echo "Network $SSID not found, scanning again..." >> AutoConnectWifi.log
        fi
    fi

    # Scan again after a delay
    sleep 10
done

echo "Script finished" >> AutoConnectWifi.log