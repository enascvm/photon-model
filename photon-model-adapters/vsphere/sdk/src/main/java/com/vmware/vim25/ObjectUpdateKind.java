
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ObjectUpdateKind.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="ObjectUpdateKind"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="modify"/&gt;
 *     &lt;enumeration value="enter"/&gt;
 *     &lt;enumeration value="leave"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "ObjectUpdateKind")
@XmlEnum
public enum ObjectUpdateKind {

    @XmlEnumValue("modify")
    MODIFY("modify"),
    @XmlEnumValue("enter")
    ENTER("enter"),
    @XmlEnumValue("leave")
    LEAVE("leave");
    private final String value;

    ObjectUpdateKind(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static ObjectUpdateKind fromValue(String v) {
        for (ObjectUpdateKind c: ObjectUpdateKind.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
