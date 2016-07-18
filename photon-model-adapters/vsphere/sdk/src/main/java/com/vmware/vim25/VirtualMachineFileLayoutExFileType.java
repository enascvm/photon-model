
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineFileLayoutExFileType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineFileLayoutExFileType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="config"/&gt;
 *     &lt;enumeration value="extendedConfig"/&gt;
 *     &lt;enumeration value="diskDescriptor"/&gt;
 *     &lt;enumeration value="diskExtent"/&gt;
 *     &lt;enumeration value="digestDescriptor"/&gt;
 *     &lt;enumeration value="digestExtent"/&gt;
 *     &lt;enumeration value="diskReplicationState"/&gt;
 *     &lt;enumeration value="log"/&gt;
 *     &lt;enumeration value="stat"/&gt;
 *     &lt;enumeration value="namespaceData"/&gt;
 *     &lt;enumeration value="nvram"/&gt;
 *     &lt;enumeration value="snapshotData"/&gt;
 *     &lt;enumeration value="snapshotMemory"/&gt;
 *     &lt;enumeration value="snapshotList"/&gt;
 *     &lt;enumeration value="snapshotManifestList"/&gt;
 *     &lt;enumeration value="suspend"/&gt;
 *     &lt;enumeration value="suspendMemory"/&gt;
 *     &lt;enumeration value="swap"/&gt;
 *     &lt;enumeration value="uwswap"/&gt;
 *     &lt;enumeration value="core"/&gt;
 *     &lt;enumeration value="screenshot"/&gt;
 *     &lt;enumeration value="ftMetadata"/&gt;
 *     &lt;enumeration value="guestCustomization"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineFileLayoutExFileType")
@XmlEnum
public enum VirtualMachineFileLayoutExFileType {

    @XmlEnumValue("config")
    CONFIG("config"),
    @XmlEnumValue("extendedConfig")
    EXTENDED_CONFIG("extendedConfig"),
    @XmlEnumValue("diskDescriptor")
    DISK_DESCRIPTOR("diskDescriptor"),
    @XmlEnumValue("diskExtent")
    DISK_EXTENT("diskExtent"),
    @XmlEnumValue("digestDescriptor")
    DIGEST_DESCRIPTOR("digestDescriptor"),
    @XmlEnumValue("digestExtent")
    DIGEST_EXTENT("digestExtent"),
    @XmlEnumValue("diskReplicationState")
    DISK_REPLICATION_STATE("diskReplicationState"),
    @XmlEnumValue("log")
    LOG("log"),
    @XmlEnumValue("stat")
    STAT("stat"),
    @XmlEnumValue("namespaceData")
    NAMESPACE_DATA("namespaceData"),
    @XmlEnumValue("nvram")
    NVRAM("nvram"),
    @XmlEnumValue("snapshotData")
    SNAPSHOT_DATA("snapshotData"),
    @XmlEnumValue("snapshotMemory")
    SNAPSHOT_MEMORY("snapshotMemory"),
    @XmlEnumValue("snapshotList")
    SNAPSHOT_LIST("snapshotList"),
    @XmlEnumValue("snapshotManifestList")
    SNAPSHOT_MANIFEST_LIST("snapshotManifestList"),
    @XmlEnumValue("suspend")
    SUSPEND("suspend"),
    @XmlEnumValue("suspendMemory")
    SUSPEND_MEMORY("suspendMemory"),
    @XmlEnumValue("swap")
    SWAP("swap"),
    @XmlEnumValue("uwswap")
    UWSWAP("uwswap"),
    @XmlEnumValue("core")
    CORE("core"),
    @XmlEnumValue("screenshot")
    SCREENSHOT("screenshot"),
    @XmlEnumValue("ftMetadata")
    FT_METADATA("ftMetadata"),
    @XmlEnumValue("guestCustomization")
    GUEST_CUSTOMIZATION("guestCustomization");
    private final String value;

    VirtualMachineFileLayoutExFileType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineFileLayoutExFileType fromValue(String v) {
        for (VirtualMachineFileLayoutExFileType c: VirtualMachineFileLayoutExFileType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
