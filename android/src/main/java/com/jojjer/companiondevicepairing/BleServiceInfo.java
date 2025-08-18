package com.jojjer.companiondevicepairing;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

public class BleServiceInfo {
    private String serviceUuid;
    private List<String> characteristics;

    public BleServiceInfo(String serviceUuid) {
        this.serviceUuid = serviceUuid;
        this.characteristics = new ArrayList<>();
    }

    public void addCharacteristic(String uuid) {
        characteristics.add(uuid);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("serviceUuid", serviceUuid);
        result.put("characteristics", characteristics);
        return result;
    }
}
