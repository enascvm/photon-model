
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostHasComponentFailureHostComponentType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostHasComponentFailureHostComponentType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="Datastore"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostHasComponentFailureHostComponentType")
@XmlEnum
public enum HostHasComponentFailureHostComponentType {

    @XmlEnumValue("Datastore")
    DATASTORE("Datastore");
    private final String value;

    HostHasComponentFailureHostComponentType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostHasComponentFailureHostComponentType fromValue(String v) {
        for (HostHasComponentFailureHostComponentType c: HostHasComponentFailureHostComponentType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
