
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmObjectType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PbmObjectType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="virtualMachine"/&gt;
 *     &lt;enumeration value="virtualMachineAndDisks"/&gt;
 *     &lt;enumeration value="virtualDiskId"/&gt;
 *     &lt;enumeration value="virtualDiskUUID"/&gt;
 *     &lt;enumeration value="datastore"/&gt;
 *     &lt;enumeration value="unknown"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PbmObjectType")
@XmlEnum
public enum PbmObjectType {

    @XmlEnumValue("virtualMachine")
    VIRTUAL_MACHINE("virtualMachine"),
    @XmlEnumValue("virtualMachineAndDisks")
    VIRTUAL_MACHINE_AND_DISKS("virtualMachineAndDisks"),
    @XmlEnumValue("virtualDiskId")
    VIRTUAL_DISK_ID("virtualDiskId"),
    @XmlEnumValue("virtualDiskUUID")
    VIRTUAL_DISK_UUID("virtualDiskUUID"),
    @XmlEnumValue("datastore")
    DATASTORE("datastore"),
    @XmlEnumValue("unknown")
    UNKNOWN("unknown");
    private final String value;

    PbmObjectType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PbmObjectType fromValue(String v) {
        for (PbmObjectType c: PbmObjectType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
