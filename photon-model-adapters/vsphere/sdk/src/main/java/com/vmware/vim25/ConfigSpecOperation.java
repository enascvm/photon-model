
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ConfigSpecOperation.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ConfigSpecOperation"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="add"/&gt;
 *     &lt;enumeration value="edit"/&gt;
 *     &lt;enumeration value="remove"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ConfigSpecOperation")
@XmlEnum
public enum ConfigSpecOperation {

    @XmlEnumValue("add")
    ADD("add"),
    @XmlEnumValue("edit")
    EDIT("edit"),
    @XmlEnumValue("remove")
    REMOVE("remove");
    private final String value;

    ConfigSpecOperation(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ConfigSpecOperation fromValue(String v) {
        for (ConfigSpecOperation c: ConfigSpecOperation.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
