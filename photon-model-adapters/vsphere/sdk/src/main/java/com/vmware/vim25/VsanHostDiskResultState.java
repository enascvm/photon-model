
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VsanHostDiskResultState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VsanHostDiskResultState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="inUse"/&gt;
 *     &lt;enumeration value="eligible"/&gt;
 *     &lt;enumeration value="ineligible"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VsanHostDiskResultState")
@XmlEnum
public enum VsanHostDiskResultState {

    @XmlEnumValue("inUse")
    IN_USE("inUse"),
    @XmlEnumValue("eligible")
    ELIGIBLE("eligible"),
    @XmlEnumValue("ineligible")
    INELIGIBLE("ineligible");
    private final String value;

    VsanHostDiskResultState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VsanHostDiskResultState fromValue(String v) {
        for (VsanHostDiskResultState c: VsanHostDiskResultState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
