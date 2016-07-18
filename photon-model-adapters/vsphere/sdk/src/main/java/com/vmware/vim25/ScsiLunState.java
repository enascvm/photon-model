
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ScsiLunState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ScsiLunState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="unknownState"/&gt;
 *     &lt;enumeration value="ok"/&gt;
 *     &lt;enumeration value="error"/&gt;
 *     &lt;enumeration value="off"/&gt;
 *     &lt;enumeration value="quiesced"/&gt;
 *     &lt;enumeration value="degraded"/&gt;
 *     &lt;enumeration value="lostCommunication"/&gt;
 *     &lt;enumeration value="timeout"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ScsiLunState")
@XmlEnum
public enum ScsiLunState {

    @XmlEnumValue("unknownState")
    UNKNOWN_STATE("unknownState"),
    @XmlEnumValue("ok")
    OK("ok"),
    @XmlEnumValue("error")
    ERROR("error"),
    @XmlEnumValue("off")
    OFF("off"),
    @XmlEnumValue("quiesced")
    QUIESCED("quiesced"),
    @XmlEnumValue("degraded")
    DEGRADED("degraded"),
    @XmlEnumValue("lostCommunication")
    LOST_COMMUNICATION("lostCommunication"),
    @XmlEnumValue("timeout")
    TIMEOUT("timeout");
    private final String value;

    ScsiLunState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ScsiLunState fromValue(String v) {
        for (ScsiLunState c: ScsiLunState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
