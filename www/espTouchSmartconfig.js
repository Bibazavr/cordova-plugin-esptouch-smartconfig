var espTouchSmartconfigName = "espTouchSmartconfig"

var espTouchSmartconfig = {
    start: function (ssid, bssid, pass, broadcast, maxDevices, encKey, successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, espTouchSmartconfigName, "startConfig", [ssid, bssid, pass, broadcast, maxDevices, encKey]);
    },
    stop: function (successCallback, errorCallback) {
        cordova.exec(successCallback, errorCallback, espTouchSmartconfigName, "stopConfig", []);
    }
}
module.exports = espTouchSmartconfig
