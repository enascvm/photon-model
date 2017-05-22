
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostVmfsVolumeUnmapPriority.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostVmfsVolumeUnmapPriority"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="none"/&gt;
 *     &lt;enumeration value="low"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostVmfsVolumeUnmapPriority")
@XmlEnum
public enum HostVmfsVolumeUnmapPriority {

    @XmlEnumValue("none")
    NONE("none"),
    @XmlEnumValue("low")
    LOW("low");
    private final String value;

    HostVmfsVolumeUnmapPriority(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostVmfsVolumeUnmapPriority fromValue(String v) {
        for (HostVmfsVolumeUnmapPriority c: HostVmfsVolumeUnmapPriority.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
