
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostCryptoState.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostCryptoState"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="incapable"/&gt;
 *     &lt;enumeration value="prepared"/&gt;
 *     &lt;enumeration value="safe"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostCryptoState")
@XmlEnum
public enum HostCryptoState {

    @XmlEnumValue("incapable")
    INCAPABLE("incapable"),
    @XmlEnumValue("prepared")
    PREPARED("prepared"),
    @XmlEnumValue("safe")
    SAFE("safe");
    private final String value;

    HostCryptoState(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostCryptoState fromValue(String v) {
        for (HostCryptoState c: HostCryptoState.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
