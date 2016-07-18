
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ReplicationVmState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ReplicationVmState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="none"/&gt;
 *     &lt;enumeration value="paused"/&gt;
 *     &lt;enumeration value="syncing"/&gt;
 *     &lt;enumeration value="idle"/&gt;
 *     &lt;enumeration value="active"/&gt;
 *     &lt;enumeration value="error"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ReplicationVmState")
@XmlEnum
public enum ReplicationVmState {

    @XmlEnumValue("none")
    NONE("none"),
    @XmlEnumValue("paused")
    PAUSED("paused"),
    @XmlEnumValue("syncing")
    SYNCING("syncing"),
    @XmlEnumValue("idle")
    IDLE("idle"),
    @XmlEnumValue("active")
    ACTIVE("active"),
    @XmlEnumValue("error")
    ERROR("error");
    private final String value;

    ReplicationVmState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ReplicationVmState fromValue(String v) {
        for (ReplicationVmState c: ReplicationVmState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
