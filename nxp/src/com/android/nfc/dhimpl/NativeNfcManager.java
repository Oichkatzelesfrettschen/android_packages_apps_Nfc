/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.nfc.dhimpl;

import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.tech.Ndef;
import android.nfc.tech.TagTechnology;
import android.util.Log;

import com.android.nfc.DeviceHost;
import com.android.nfc.NfcDiscoveryParameters;
import com.android.nfc.NfcVendorNciResponse;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * DeviceHost for the NXP PN544, which speaks ETSI HCI through libnfc-nxp.
 *
 * The controller has no NCI command set, no listen-mode routing table, no
 * NFCEE management and no observe mode, so the corresponding DeviceHost
 * methods report the feature as absent. Card emulation stays with the
 * controller's own secure-element paths, which initialization switches off.
 */
public class NativeNfcManager implements DeviceHost {
    private static final String TAG = "NativeNfcManager";

    private static final String NFC_CONTROLLER_FIRMWARE_FILE_NAME =
            "/vendor/firmware/libpn544_fw.so";

    static final String PREF = "NxpDeviceHost";

    private static final String PREF_FIRMWARE_MODTIME = "firmware_modtime";
    private static final long FIRMWARE_MODTIME_DEFAULT = -1;

    static final String DRIVER_NAME = "nxp";

    /* NCI_VERSION_1_0: NfcService keeps its NCI 1.0 feature set for this host. */
    private static final int NCI_VERSION_1_0 = 0x10;

    /* android.nfc.NfcOemExtension.COMMIT_ROUTING_STATUS_FAILED */
    private static final int COMMIT_ROUTING_STATUS_FAILED = 3;

    /* NfcService.NCI_STATUS_FAILED */
    private static final byte NCI_STATUS_FAILED = 0x03;

    /* android.nfc.T4tNdefNfcee.WRITE_DATA_ERROR_INTERNAL */
    private static final int WRITE_DATA_ERROR_INTERNAL = -1;

    static {
        System.loadLibrary("nfc_jni");
    }

    /* Native structure, written by initializeNativeStructure(). */
    private long mNative;

    private final DeviceHostListener mListener;
    private final Context mContext;

    public NativeNfcManager(Context context, DeviceHostListener listener) {
        mListener = listener;
        initializeNativeStructure();
        mContext = context;
    }

    public native boolean initializeNativeStructure();

    private native boolean doDownload();

    @Override
    public boolean checkFirmware() {
        // The PN544 runs its chip-resident firmware unless a downloadable
        // image is present. Without the image, a download attempt only cycles
        // the controller through PN544_SET_PWR download mode and fails.
        File firmwareFile = new File(NFC_CONTROLLER_FIRMWARE_FILE_NAME);
        if (!firmwareFile.exists()) {
            Log.d(TAG, "No firmware image at " + NFC_CONTROLLER_FIRMWARE_FILE_NAME
                    + ", keeping chip-resident firmware");
            return true;
        }

        long modtime = firmwareFile.lastModified();
        SharedPreferences prefs = mContext.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long prevModtime = prefs.getLong(PREF_FIRMWARE_MODTIME, FIRMWARE_MODTIME_DEFAULT);
        Log.d(TAG, "prev modtime: " + prevModtime + ", new modtime: " + modtime);
        if (prevModtime == modtime) {
            return true;
        }

        for (int retry = 0; retry < 5; retry++) {
            Log.d(TAG, "Perform Download");
            if (doDownload()) {
                Log.d(TAG, "Download Success");
                prefs.edit().putLong(PREF_FIRMWARE_MODTIME, modtime).apply();
                return true;
            }
            Log.d(TAG, "Download Failed");
        }
        return false;
    }

    private native boolean doInitialize();

    @Override
    public boolean initialize() {
        return doInitialize();
    }

    @Override
    public void setPartialInitMode(int mode) {
        // HCI initialization has a single, full mode.
    }

    private native void doEnableDtaMode();

    @Override
    public void enableDtaMode() {
        doEnableDtaMode();
    }

    private native void doDisableDtaMode();

    @Override
    public void disableDtaMode() {
        doDisableDtaMode();
    }

    private native void doFactoryReset();

    @Override
    public void factoryReset() {
        doFactoryReset();
    }

    @Override
    public boolean setPowerSavingMode(boolean flag) {
        return false;
    }

    private native void doShutdown();

    @Override
    public void shutdown() {
        doShutdown();
    }

    private native boolean doDeinitialize();

    @Override
    public boolean deinitialize() {
        return doDeinitialize();
    }

    @Override
    public String getName() {
        return DRIVER_NAME;
    }

    @Override
    public boolean sendRawFrame(byte[] data) {
        return false;
    }

    @Override
    public boolean routeAid(byte[] aid, int route, int aidInfo, int power) {
        return false;
    }

    @Override
    public boolean unrouteAid(byte[] aid) {
        return false;
    }

    @Override
    public int commitRouting() {
        return COMMIT_ROUTING_STATUS_FAILED;
    }

    /**
     * Raw NCI notification injection; HCI framing has no NCI notification
     * path, so the data is dropped.
     */
    public void injectNtf(byte[] data) {
        Log.w(TAG, "injectNtf: no NCI notification path on " + DRIVER_NAME);
    }

    @Override
    public boolean isObserveModeSupported() {
        return false;
    }

    @Override
    public boolean isFirmwareExitFramesSupported() {
        return false;
    }

    @Override
    public boolean setObserveMode(boolean enabled) {
        return false;
    }

    @Override
    public boolean isObserveModeEnabled() {
        return false;
    }

    @Override
    public int getT4TNfceePowerState() {
        return 0;
    }

    @Override
    public int getNdefNfceeRouteId() {
        return 0;
    }

    @Override
    public int doWriteData(byte[] fileId, byte[] data) {
        return WRITE_DATA_ERROR_INTERNAL;
    }

    @Override
    public byte[] doReadData(byte[] fileId) {
        return new byte[0];
    }

    @Override
    public boolean doClearNdefData() {
        return false;
    }

    @Override
    public boolean isNdefOperationOngoing() {
        return false;
    }

    @Override
    public boolean isNdefNfceeEmulationSupported() {
        return false;
    }

    @Override
    public void registerT3tIdentifier(byte[] t3tIdentifier) {
    }

    @Override
    public void deregisterT3tIdentifier(byte[] t3tIdentifier) {
    }

    @Override
    public void clearT3tIdentifiersCache() {
    }

    @Override
    public int getLfT3tMax() {
        return 0;
    }

    @Override
    public native void doSetScreenState(int screen_state_mask, boolean alwaysPoll);

    @Override
    public native int getNciVersion();

    private native void doEnableDiscovery(int techMask,
                                          boolean enableLowPowerPolling,
                                          boolean enableReaderMode,
                                          boolean restart);

    @Override
    public void enableDiscovery(NfcDiscoveryParameters params, boolean restart) {
        doEnableDiscovery(params.getTechMask(), params.shouldEnableLowPowerDiscovery(),
                params.shouldEnableReaderMode(), restart);
    }

    @Override
    public native void disableDiscovery();

    private native void doResetTimeouts();

    @Override
    public void resetTimeouts() {
        doResetTimeouts();
    }

    @Override
    public native void doAbort(String msg);

    private native boolean doSetTimeout(int tech, int timeout);

    @Override
    public boolean setTimeout(int tech, int timeout) {
        return doSetTimeout(tech, timeout);
    }

    private native int doGetTimeout(int tech);

    @Override
    public int getTimeout(int tech) {
        return doGetTimeout(tech);
    }

    @Override
    public boolean canMakeReadOnly(int ndefType) {
        return (ndefType == Ndef.TYPE_1 || ndefType == Ndef.TYPE_2 ||
                ndefType == Ndef.TYPE_MIFARE_CLASSIC);
    }

    @Override
    public int getMaxTransceiveLength(int technology) {
        switch (technology) {
            case (TagTechnology.NFC_A):
            case (TagTechnology.MIFARE_CLASSIC):
            case (TagTechnology.MIFARE_ULTRALIGHT):
                return 253; // PN544 RF buffer = 255 bytes, subtract two for CRC
            case (TagTechnology.NFC_B):
                return 0; // PN544 does not support transceive of raw NfcB
            case (TagTechnology.NFC_V):
                return 253; // PN544 RF buffer = 255 bytes, subtract two for CRC
            case (TagTechnology.ISO_DEP):
                /* The maximum length of a normal IsoDep frame consists of:
                 * CLA, INS, P1, P2, LC, LE + 255 payload bytes = 261 bytes
                 * such a frame is supported. Extended length frames however
                 * are not supported.
                 */
                return 261; // Will be automatically split in two frames on the RF layer
            case (TagTechnology.NFC_F):
                return 252; // PN544 RF buffer = 255 bytes, subtract one for SoD, two for CRC
            default:
                return 0;
        }
    }

    @Override
    public int getAidTableSize() {
        return 0;
    }

    @Override
    public boolean getExtendedLengthApdusSupported() {
        // Not supported on the PN544
        return false;
    }

    private native void doDump(FileDescriptor fd);

    @Override
    public void dump(PrintWriter pw, FileDescriptor fd) {
        pw.println("DeviceHost=" + DRIVER_NAME + " (PN544 HCI)");
        doDump(fd);
    }

    @Override
    public boolean setNfcSecure(boolean enable) {
        return true;
    }

    private native void doStartStopPolling(boolean start);

    @Override
    public void startStopPolling(boolean start) {
        doStartStopPolling(start);
    }

    private native void doSetNfceePowerAndLinkCtrl(boolean enable);

    @Override
    public void setNfceePowerAndLinkCtrl(boolean enable) {
        doSetNfceePowerAndLinkCtrl(enable);
    }

    @Override
    public native byte[] getRoutingTable();

    @Override
    public native int getMaxRoutingTableSize();

    @Override
    public boolean isMultiTag() {
        return false;
    }

    @Override
    public Map<String, Integer> dofetchActiveNfceeList() {
        return new HashMap<String, Integer>();
    }

    @Override
    public NfcVendorNciResponse sendRawVendorCmd(int mt, int gid, int oid, byte[] payload) {
        return new NfcVendorNciResponse(NCI_STATUS_FAILED, gid, oid, new byte[0]);
    }

    @Override
    public void enableVendorNciNotifications(boolean enabled) {
    }

    @Override
    public void setDiscoveryTech(int pollTech, int listenTech) {
    }

    @Override
    public void resetDiscoveryTech() {
    }

    @Override
    public void clearRoutingEntry(int clearFlags) {
    }

    @Override
    public void setIsoDepProtocolRoute(int route) {
    }

    @Override
    public void setTechnologyABFRoute(int route, int felicaRoute) {
    }

    @Override
    public void setSystemCodeRoute(int route) {
    }

    /**
     * Notifies Ndef Message (TODO: rename into notifyTargetDiscovered)
     */
    private void notifyNdefMessageListeners(NativeNfcTag tag) {
        mListener.onRemoteEndpointDiscovered(tag);
    }

    private void notifyRfFieldActivated() {
        mListener.onRemoteFieldActivated();
    }

    private void notifyRfFieldDeactivated() {
        mListener.onRemoteFieldDeactivated();
    }
}
