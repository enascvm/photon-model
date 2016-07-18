
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayUpdateOperation.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ArrayUpdateOperation"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="add"/&gt;
 *     &lt;enumeration value="remove"/&gt;
 *     &lt;enumeration value="edit"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ArrayUpdateOperation")
@XmlEnum
public enum ArrayUpdateOperation {

    @XmlEnumValue("add")
    ADD("add"),
    @XmlEnumValue("remove")
    REMOVE("remove"),
    @XmlEnumValue("edit")
    EDIT("edit");
    private final String value;

    ArrayUpdateOperation(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ArrayUpdateOperation fromValue(String v) {
        for (ArrayUpdateOperation c: ArrayUpdateOperation.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
