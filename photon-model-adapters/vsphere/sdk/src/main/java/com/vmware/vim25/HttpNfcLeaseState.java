
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HttpNfcLeaseState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HttpNfcLeaseState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="initializing"/&gt;
 *     &lt;enumeration value="ready"/&gt;
 *     &lt;enumeration value="done"/&gt;
 *     &lt;enumeration value="error"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HttpNfcLeaseState")
@XmlEnum
public enum HttpNfcLeaseState {

    @XmlEnumValue("initializing")
    INITIALIZING("initializing"),
    @XmlEnumValue("ready")
    READY("ready"),
    @XmlEnumValue("done")
    DONE("done"),
    @XmlEnumValue("error")
    ERROR("error");
    private final String value;

    HttpNfcLeaseState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HttpNfcLeaseState fromValue(String v) {
        for (HttpNfcLeaseState c: HttpNfcLeaseState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
