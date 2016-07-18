
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PropertyChangeOp.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PropertyChangeOp"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="add"/&gt;
 *     &lt;enumeration value="remove"/&gt;
 *     &lt;enumeration value="assign"/&gt;
 *     &lt;enumeration value="indirectRemove"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PropertyChangeOp")
@XmlEnum
public enum PropertyChangeOp {

    @XmlEnumValue("add")
    ADD("add"),
    @XmlEnumValue("remove")
    REMOVE("remove"),
    @XmlEnumValue("assign")
    ASSIGN("assign"),
    @XmlEnumValue("indirectRemove")
    INDIRECT_REMOVE("indirectRemove");
    private final String value;

    PropertyChangeOp(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static PropertyChangeOp fromValue(String v) {
        for (PropertyChangeOp c: PropertyChangeOp.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
