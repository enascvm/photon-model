
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDeviceFileExtension.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualDeviceFileExtension"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="iso"/&gt;
 *     &lt;enumeration value="flp"/&gt;
 *     &lt;enumeration value="vmdk"/&gt;
 *     &lt;enumeration value="dsk"/&gt;
 *     &lt;enumeration value="rdm"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualDeviceFileExtension")
@XmlEnum
public enum VirtualDeviceFileExtension {

    @XmlEnumValue("iso")
    ISO("iso"),
    @XmlEnumValue("flp")
    FLP("flp"),
    @XmlEnumValue("vmdk")
    VMDK("vmdk"),
    @XmlEnumValue("dsk")
    DSK("dsk"),
    @XmlEnumValue("rdm")
    RDM("rdm");
    private final String value;

    VirtualDeviceFileExtension(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualDeviceFileExtension fromValue(String v) {
        for (VirtualDeviceFileExtension c: VirtualDeviceFileExtension.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
