
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DiagnosticManagerLogCreator.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="DiagnosticManagerLogCreator"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="vpxd"/&gt;
 *     &lt;enumeration value="vpxa"/&gt;
 *     &lt;enumeration value="hostd"/&gt;
 *     &lt;enumeration value="serverd"/&gt;
 *     &lt;enumeration value="install"/&gt;
 *     &lt;enumeration value="vpxClient"/&gt;
 *     &lt;enumeration value="recordLog"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "DiagnosticManagerLogCreator")
@XmlEnum
public enum DiagnosticManagerLogCreator {

    @XmlEnumValue("vpxd")
    VPXD("vpxd"),
    @XmlEnumValue("vpxa")
    VPXA("vpxa"),
    @XmlEnumValue("hostd")
    HOSTD("hostd"),
    @XmlEnumValue("serverd")
    SERVERD("serverd"),
    @XmlEnumValue("install")
    INSTALL("install"),
    @XmlEnumValue("vpxClient")
    VPX_CLIENT("vpxClient"),
    @XmlEnumValue("recordLog")
    RECORD_LOG("recordLog");
    private final String value;

    DiagnosticManagerLogCreator(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static DiagnosticManagerLogCreator fromValue(String v) {
        for (DiagnosticManagerLogCreator c: DiagnosticManagerLogCreator.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
