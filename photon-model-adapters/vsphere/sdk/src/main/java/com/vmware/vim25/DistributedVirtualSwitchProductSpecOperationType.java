
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DistributedVirtualSwitchProductSpecOperationType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="DistributedVirtualSwitchProductSpecOperationType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="preInstall"/&gt;
 *     &lt;enumeration value="upgrade"/&gt;
 *     &lt;enumeration value="notifyAvailableUpgrade"/&gt;
 *     &lt;enumeration value="proceedWithUpgrade"/&gt;
 *     &lt;enumeration value="updateBundleInfo"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "DistributedVirtualSwitchProductSpecOperationType")
@XmlEnum
public enum DistributedVirtualSwitchProductSpecOperationType {

    @XmlEnumValue("preInstall")
    PRE_INSTALL("preInstall"),
    @XmlEnumValue("upgrade")
    UPGRADE("upgrade"),
    @XmlEnumValue("notifyAvailableUpgrade")
    NOTIFY_AVAILABLE_UPGRADE("notifyAvailableUpgrade"),
    @XmlEnumValue("proceedWithUpgrade")
    PROCEED_WITH_UPGRADE("proceedWithUpgrade"),
    @XmlEnumValue("updateBundleInfo")
    UPDATE_BUNDLE_INFO("updateBundleInfo");
    private final String value;

    DistributedVirtualSwitchProductSpecOperationType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static DistributedVirtualSwitchProductSpecOperationType fromValue(String v) {
        for (DistributedVirtualSwitchProductSpecOperationType c: DistributedVirtualSwitchProductSpecOperationType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
