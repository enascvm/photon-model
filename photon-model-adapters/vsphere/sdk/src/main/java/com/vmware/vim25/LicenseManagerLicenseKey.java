
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for LicenseManagerLicenseKey.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="LicenseManagerLicenseKey"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="esxFull"/&gt;
 *     &lt;enumeration value="esxVmtn"/&gt;
 *     &lt;enumeration value="esxExpress"/&gt;
 *     &lt;enumeration value="san"/&gt;
 *     &lt;enumeration value="iscsi"/&gt;
 *     &lt;enumeration value="nas"/&gt;
 *     &lt;enumeration value="vsmp"/&gt;
 *     &lt;enumeration value="backup"/&gt;
 *     &lt;enumeration value="vc"/&gt;
 *     &lt;enumeration value="vcExpress"/&gt;
 *     &lt;enumeration value="esxHost"/&gt;
 *     &lt;enumeration value="gsxHost"/&gt;
 *     &lt;enumeration value="serverHost"/&gt;
 *     &lt;enumeration value="drsPower"/&gt;
 *     &lt;enumeration value="vmotion"/&gt;
 *     &lt;enumeration value="drs"/&gt;
 *     &lt;enumeration value="das"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "LicenseManagerLicenseKey")
@XmlEnum
public enum LicenseManagerLicenseKey {

    @XmlEnumValue("esxFull")
    ESX_FULL("esxFull"),
    @XmlEnumValue("esxVmtn")
    ESX_VMTN("esxVmtn"),
    @XmlEnumValue("esxExpress")
    ESX_EXPRESS("esxExpress"),
    @XmlEnumValue("san")
    SAN("san"),
    @XmlEnumValue("iscsi")
    ISCSI("iscsi"),
    @XmlEnumValue("nas")
    NAS("nas"),
    @XmlEnumValue("vsmp")
    VSMP("vsmp"),
    @XmlEnumValue("backup")
    BACKUP("backup"),
    @XmlEnumValue("vc")
    VC("vc"),
    @XmlEnumValue("vcExpress")
    VC_EXPRESS("vcExpress"),
    @XmlEnumValue("esxHost")
    ESX_HOST("esxHost"),
    @XmlEnumValue("gsxHost")
    GSX_HOST("gsxHost"),
    @XmlEnumValue("serverHost")
    SERVER_HOST("serverHost"),
    @XmlEnumValue("drsPower")
    DRS_POWER("drsPower"),
    @XmlEnumValue("vmotion")
    VMOTION("vmotion"),
    @XmlEnumValue("drs")
    DRS("drs"),
    @XmlEnumValue("das")
    DAS("das");
    private final String value;

    LicenseManagerLicenseKey(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static LicenseManagerLicenseKey fromValue(String v) {
        for (LicenseManagerLicenseKey c: LicenseManagerLicenseKey.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
